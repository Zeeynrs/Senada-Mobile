#!/usr/bin/env python3
"""Uji pemutaran latar belakang Senada di emulator Android lewat adb.

Memasang APK debug, lalu menguji kontrol aplikasi, pemutaran HTTPS, dan sesi media
saat layar mati. Screenshot, dumpsys dan logcat disimpan di folder keluaran.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "io.github.zeeynrs.senada"
ACTIVITY = f"{PKG}/senada.platform.MainActivity"
APK = "composeApp/build/outputs/apk/debug/composeApp-debug.apk"
FIRST = "Selepas Hujan"
SECOND = "Sebelum Pagi"
QUEUE_ACTION = "Tambah ke antrean"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "build/android-e2e")
STATES = {"0": "NONE", "1": "STOPPED", "2": "PAUSED", "3": "PLAYING", "6": "BUFFERING", "7": "ERROR"}

results: list[tuple[str, str, str]] = []


def run(*args: str) -> str:
    # Logcat bisa memuat byte di luar UTF-8; tetap simpan bagian log yang bisa dibaca.
    return subprocess.run(args, check=True, capture_output=True, encoding="utf-8", errors="replace").stdout


def shell(command: str) -> str:
    return run("adb", "shell", command)


def save(name: str, text: str) -> None:
    (OUT / name).write_text(text, encoding="utf-8")


def screenshot(name: str) -> None:
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    (OUT / f"{name}.png").write_bytes(png)


def record(status: str, name: str, detail: object = "") -> None:
    results.append((status, name, str(detail or "")))
    print(f"{status}: {name}" + (f" ({detail})" if detail else ""), flush=True)


def check(ok: object, name: str, detail: object = "") -> bool:
    record("PASS" if ok else "FAIL", name, detail)
    return bool(ok)


def wait_for(predicate, timeout: float, interval: float = 0.5):
    deadline = time.monotonic() + timeout
    while True:
        value = predicate()
        if value or time.monotonic() >= deadline:
            return value
        time.sleep(interval)


def session() -> dict[str, str] | None:
    """Status sesi media Senada menurut sistem, seperti yang dilihat layar kunci dan notifikasi."""
    lines = shell("dumpsys media_session").splitlines()
    for i, line in enumerate(lines):
        if line.strip() != f"package={PKG}":
            continue
        block = "\n".join(lines[i : i + 25])
        state = re.search(r"PlaybackState \{state=([^,]+), position=(-?\d+).*?\bspeed=([\d.\-]+)", block)
        if not state:
            continue
        code = re.search(r"\d+", state.group(1))
        description = re.search(r"description=(.*)", block)
        return {
            "state": STATES.get(code.group(0) if code else "", state.group(1)),
            "position": state.group(2),
            "speed": state.group(3),
            "description": description.group(1).strip() if description else "",
        }
    return None


def wait_session(
    state: str, title: str, timeout: float, *, min_ms: int | None = None, max_ms: int | None = None,
) -> dict[str, str] | None:
    def matches():
        current = session()
        if not current or current["state"] != state or title not in current["description"]:
            return None
        position = int(current["position"])
        if (min_ms is not None and position < min_ms) or (max_ms is not None and position > max_ms):
            return None
        return current

    return wait_for(matches, timeout)


def pause_for_setup(title: str) -> dict[str, str] | None:
    def request_pause():
        current = session()
        if current and title in current["description"]:
            if current["state"] == "PAUSED":
                return current
            if current["state"] == "PLAYING":
                shell("cmd media_session dispatch pause")
        return None

    # Sesi baru bisa melaporkan PLAYING sebelum sistem meneruskan tombol media kepadanya.
    return wait_for(request_pause, 5, interval=0.25)


def media_notification() -> str:
    out = shell("dumpsys notification --noredact")
    records = [line.strip() for line in out.splitlines() if "NotificationRecord(" in line and f"pkg={PKG}" in line]
    return records[0] if records else ""


def foreground_service() -> bool:
    return "isForeground=true" in shell(f"dumpsys activity services {PKG}")


def wakefulness() -> str:
    match = re.search(r"mWakefulness=(\w+)", shell("dumpsys power"))
    return match.group(1) if match else "?"


def screen_size() -> tuple[int, int]:
    match = re.search(r"(\d+)x(\d+)", shell("wm size"))
    return (int(match.group(1)), int(match.group(2))) if match else (1080, 2400)


def screen_density() -> int:
    out = shell("wm density")
    match = re.search(r"Override density:\s*(\d+)", out) or re.search(r"Physical density:\s*(\d+)", out)
    return int(match.group(1)) if match else 420


def parse_bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    nums = re.findall(r"\d+", node.get("bounds", ""))
    return (int(nums[0]), int(nums[1]), int(nums[2]), int(nums[3])) if len(nums) == 4 else None


def ui_tree(save_as: str | None = None) -> ET.Element | None:
    for _ in range(5):
        # Hapus hasil dump lama agar kegagalan idle uiautomator tidak membaca layar tab sebelumnya.
        dumped = shell("rm -f /sdcard/senada-ui.xml && uiautomator dump /sdcard/senada-ui.xml 2>&1 || true")
        if "ERROR:" in dumped:
            time.sleep(1)
            continue
        xml = shell("cat /sdcard/senada-ui.xml 2>/dev/null || true")
        if "<?xml" in xml:
            cleaned = xml[xml.index("<?xml") :]
            if save_as:
                save(save_as, cleaned)
            return ET.fromstring(cleaned)
        time.sleep(1)
    return None


def parent_map(root: ET.Element) -> dict[ET.Element, ET.Element]:
    return {child: parent for parent in root.iter("node") for child in parent}


def interactive_node(node: ET.Element, parents: dict[ET.Element, ET.Element]) -> ET.Element:
    """Naik ke node tombol/sakelar/radio pembungkus karena uiautomator memisahkan anak TextView."""
    current: ET.Element | None = node
    while current is not None:
        if current.get("clickable") == "true" or current.get("checkable") == "true":
            return current
        current = parents.get(current)
    return node


def find_interactive(
    label: str, *, attribute: str = "text", root: ET.Element | None = None,
) -> list[tuple[tuple[int, int, int, int], ET.Element]]:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return []
    parents = parent_map(tree)
    matches = []
    for node in tree.iter("node"):
        if node.get(attribute) == label:
            target = interactive_node(node, parents)
            box = parse_bounds(target)
            if box:
                matches.append((box, target))
    return sorted(matches, key=lambda item: item[0][1] + item[0][3])


def ui_bounds(label: str, *, attribute: str = "text", root: ET.Element | None = None) -> list[tuple[int, int, int, int]]:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return []
    bounds = []
    for node in tree.iter("node"):
        if node.get(attribute) == label:
            box = parse_bounds(node)
            if box:
                bounds.append(box)
    return sorted(bounds, key=lambda box: box[1] + box[3])


def ui_points(label: str, *, attribute: str = "text") -> list[tuple[int, int]]:
    return [((x1 + x2) // 2, (y1 + y2) // 2) for x1, y1, x2, y2 in ui_bounds(label, attribute=attribute)]


def tap(point: tuple[int, int]) -> None:
    x, y = point
    shell(f"input tap {x} {y}")


def tap_text(text: str, index: int = 0, scrolls: int = 4) -> bool:
    width, height = screen_size()
    for _ in range(scrolls + 1):
        points = ui_points(text)
        if len(points) > index:
            tap(points[index])
            time.sleep(0.7)
            return True
        shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 300")
        time.sleep(0.7)
    return False


def desc_point(label: str, index: int = 0) -> tuple[int, int] | None:
    matches = find_interactive(label, attribute="content-desc")
    if len(matches) <= index:
        return None
    x1, y1, x2, y2 = matches[index][0]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_desc(label: str, index: int = 0) -> bool:
    point = desc_point(label, index)
    if point is None:
        return False
    tap(point)
    time.sleep(0.8)
    return True


def open_tab(label: str) -> bool:
    """Tab di navigasi bawah yang mengambang punya content-desc sesuai labelnya."""
    return tap_desc(label)


def open_now_playing() -> bool:
    return tap_desc("Buka Sedang diputar") and bool(wait_for(lambda: find_interactive("Tutup", attribute="content-desc"), 5))


def now_playing_closed() -> bool:
    tree = ui_tree()
    return tree is not None and not find_interactive("Tutup", attribute="content-desc", root=tree) and bool(
        find_interactive("Buka Sedang diputar", attribute="content-desc", root=tree)
    )


def check_now_playing_swipes() -> None:
    name = "Geser mini player ke atas membuka Sedang diputar dan geser header ke bawah menutupnya"
    mini_player = desc_point("Buka Sedang diputar")
    if mini_player is None:
        check(False, name, "Mini player tidak ditemukan")
        return

    width, height = screen_size()
    x, y = mini_player
    print("INFO: Menggeser mini player untuk membuka Sedang diputar", flush=True)
    shell(f"input swipe {x} {y} {x} {y - height * 2 // 5} 1000")
    close = wait_for(lambda: desc_point("Tutup"), 5)
    if close is None:
        check(False, name, "Sedang diputar tidak terbuka setelah digeser")
        return

    _, header_y = close
    shell(f"input swipe {width // 2} {header_y} {width // 2} {header_y + height * 2 // 5} 1000")
    check(wait_for(now_playing_closed, 5), name)


def dismiss_system_dialogs() -> bool:
    """Tutup dialog "isn't responding" milik aplikasi sistem emulator (mis. Pixel Launcher) yang menutupi layar."""
    root = ui_tree()
    if root is None or not any("isn't responding" in (node.get("text") or "") for node in root.iter("node")):
        return False
    waits = find_interactive("Wait", root=root)
    if waits:
        x1, y1, x2, y2 = waits[0][0]
        tap(((x1 + x2) // 2, (y1 + y2) // 2))
    else:
        shell("input keyevent KEYCODE_BACK")
    time.sleep(1)
    return True


def open_shade(name: str) -> None:
    shell("cmd statusbar expand-notifications")
    time.sleep(2)
    screenshot(name)
    shell("cmd statusbar collapse")
    time.sleep(1)


def check_paused_seek(before: dict[str, str]) -> None:
    name = "Slider saat dijeda berpindah ke tengah, tetap diam, lalu maju setelah Putar"
    sliders = ui_bounds("Posisi pemutaran", attribute="content-desc")
    if not check(sliders, "Slider 'Posisi pemutaran' terlihat"):
        return
    x1, y1, x2, y2 = sliders[0]
    y = (y1 + y2) // 2
    before_ms = int(before["position"])
    start_x = x1 + round((x2 - x1) * max(0.04, before_ms / 12_000))
    shell(f"input swipe {start_x} {y} {(x1 + x2) // 2} {y} 1000")

    # Geseran ke tengah mendarat sekitar 4,5–6 detik tergantung jarak sentuh awal; yang diuji adalah
    # posisi pindah jauh dari awal dan tidak melewati akhir lagu.
    sought = wait_session("PAUSED", FIRST, 5, min_ms=max(3_000, before_ms + 1_500), max_ms=8_000)
    if not sought:
        check(False, name, f"Dari {before_ms} ms, setelah geser: {session()}")
        return
    sought_ms = int(sought["position"])
    time.sleep(1)
    held = session()
    stayed = held and held["state"] == "PAUSED" and FIRST in held["description"] and int(held["position"]) == sought_ms

    resumed = tap_desc("Putar") and wait_session("PLAYING", FIRST, 4)
    resumed_ms = int(resumed["position"]) if resumed else -1
    advanced = resumed and wait_session("PLAYING", FIRST, 3, min_ms=resumed_ms + 500)
    check(
        stayed and sought_ms - 250 <= resumed_ms <= sought_ms + 1_500 and advanced,
        name,
        f"awal {before_ms} ms, seek {sought_ms} ms, tertahan {held}, lanjut {resumed_ms} ms, maju {advanced}",
    )


def check_app_navigation() -> None:
    next_point = desc_point("Berikutnya")
    if next_point is not None:
        tap(next_point)
    second = next_point is not None and wait_session("PLAYING", SECOND, 5)
    # Bekukan posisi entri kedua sebelum pencarian tombol lewat uiautomator.
    paused = second and pause_for_setup(SECOND)
    early = paused and int(paused["position"]) < 3_000
    check(second, "Tombol 'Berikutnya' di aplikasi memutar entri kedua", second or session())

    first = early and tap_desc("Sebelumnya") and wait_session("PLAYING", FIRST, 5)
    check(
        first,
        "Tombol 'Sebelumnya' di aplikasi sebelum 3 detik kembali ke entri pertama",
        f"posisi entri kedua: {paused or session()}, hasil: {first}",
    )


def check_sleep_timer() -> None:
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")
    shell("cmd media_session dispatch play")
    if not check(wait_session("PLAYING", SECOND, 8, max_ms=2_000), "Entri terakhir diputar ulang untuk uji timer"):
        return
    shell("cmd media_session dispatch previous")
    paused = pause_for_setup(FIRST)
    if paused:
        shell("cmd media_session dispatch previous")
        paused = wait_session("PAUSED", FIRST, 5, max_ms=1_000)
    if not check(paused and open_now_playing(), "Sedang diputar dibuka pada entri pertama untuk uji timer"):
        return

    timed = tap_desc("Timer tidur") and tap_text("15 menit", scrolls=0)
    check(
        timed and tap_desc("Timer tidur. Berhenti dalam 15 menit") and tap_text("Matikan", scrolls=1),
        "Pilihan 15 menit menampilkan sisa waktu dan dapat dimatikan",
    )
    armed = tap_desc("Timer tidur") and tap_text("Akhir lagu ini", scrolls=1)
    timer_label = armed and desc_point("Timer tidur. Berhenti di akhir lagu")
    # Kembali ke tab agar uji HTTPS dan skala font berikutnya dimulai dari navigasi utama.
    closed = tap_desc("Tutup")
    if not check(timer_label and closed, "Timer akhir lagu tetap aktif setelah Sedang diputar ditutup"):
        return

    shell("input keyevent KEYCODE_HOME")
    shell("cmd media_session dispatch play")
    shell("input keyevent KEYCODE_SLEEP")
    stopped = wait_session("PAUSED", FIRST, 20, min_ms=11_500)
    if check(stopped and wakefulness() != "Awake", "Timer menjeda di akhir lagu tanpa maju saat layar mati", stopped):
        shell("cmd media_session dispatch play")
        resumed = wait_session("PLAYING", SECOND, 5)
        check(resumed, "Putar melanjutkan ke entri kedua setelah timer habis", resumed or session())


def tap_profile_item(label: str, taps: int = 1) -> bool:
    """Ketuk teks saat terlihat di atas mini player, termasuk pada skala font besar."""
    width, height = screen_size()
    for _ in range(9):
        root = ui_tree()
        if root is not None:
            for x1, y1, x2, y2 in ui_bounds(label, root=root):
                y = (y1 + y2) // 2
                if y < overlay_top(root, height) and y2 > y1:
                    for _ in range(taps):
                        tap(((x1 + x2) // 2, y))
                    time.sleep(0.7)
                    return True
        shell(f"input swipe {width // 2} {height * 3 // 5} {width // 2} {height // 4} 350")
        time.sleep(0.5)
    return False


def back_to_profile() -> bool:
    shell("input keyevent KEYCODE_BACK")
    return bool(wait_for(lambda: desc_point("Profil"), 5))


def screenshot_full_profile() -> None:
    """Perpanjang layar sebentar agar seluruh Profil terlihat seperti kanvas mockup."""
    original_size = re.search(r"Override size: (\d+x\d+)", shell("wm size"))
    density = screen_density() / 160
    width, height = round(390 * density), round(1320 * density)
    try:
        shell(f"wm size {width}x{height}")

        def full_profile_visible():
            root = ui_tree()
            if root is None or not ui_bounds("Halo lagi, Uji!", root=root):
                return False
            return any(
                y2 < overlay_top(root, height)
                for _, _, _, y2 in ui_bounds("Tentang Senada", root=root)
            )

        check(wait_for(full_profile_visible, 10), "Sapaan hingga kartu versi terlihat pada kanvas Profil")
        screenshot("v2-40-profile-full")
    finally:
        shell("wm size " + (original_size.group(1) if original_size else "reset"))
        wait_for(lambda: ui_bounds("Halo lagi, Uji!") and desc_point("Profil"), 5)


def check_profile_shell() -> None:
    if not check(open_tab("Beranda"), "Beranda terbuka dengan navbar v2"):
        return
    screenshot("v2-01-home-navbar")
    if not check(open_tab("Profil") and ui_bounds("Halo!"), "Profil menampilkan sapaan"):
        return
    screenshot("v2-02-profile")
    if not check(tap_desc("Ubah nama dan foto") and ui_bounds("Ubah profil"), "Lembar Ubah profil terbuka"):
        return
    screenshot("v2-41-profile-edit-sheet")
    if not check(tap_desc("Nama profil"), "Kolom nama profil bisa diedit"):
        return
    shell("input text Uji")
    shell("input keyevent KEYCODE_BACK")
    if not check(tap_text("Simpan", scrolls=0), "Nama profil tersimpan"):
        return
    if not check(wait_for(lambda: ui_bounds("Halo lagi, Uji!"), 5), "Sapaan memakai nama yang disimpan"):
        return
    root = ui_tree()
    nav = desc_point("Profil")
    initial_in_nav = root is not None and nav is not None and any(
        x1 <= nav[0] <= x2 and y1 <= nav[1] <= y2
        for x1, y1, x2, y2 in ui_bounds("U", root=root)
    )
    check(initial_in_nav, "Navbar menampilkan inisial U")
    screenshot_full_profile()
    if not check(tap_desc("Cari pengaturan") and tap_desc("Kata kunci pengaturan"), "Kolom pencarian pengaturan terbuka"):
        return
    shell("input text sudut")
    root = ui_tree()
    if not check(root is not None and ui_bounds("Tampilan", root=root) and not ui_bounds("Audio Lab", root=root),
                 "Pencarian pengaturan menyaring berdasarkan subjudul"):
        return
    if not check(tap_profile_item("Tampilan") and desc_point("Kembali ke Profil"), "Tampilan terbuka dari hasil pencarian Profil"):
        return
    screenshot("v2-03-profile-appearance")
    if not check(back_to_profile(), "Kembali dari Tampilan ke Profil"):
        return
    for label, screen in (
        ("Audio Lab", "v2-04-profile-audio-lab"),
        ("Pemutaran", "v2-05-profile-playback"),
        ("Backup library", "v2-06-profile-backup"),
        ("Penyimpanan", "v2-07-profile-storage"),
        ("Tentang Senada", "v2-08-profile-about"),
        ("LAGU TERATAS", "v2-09-profile-most-played"),
    ):
        if not check(tap_profile_item(label) and desc_point("Kembali ke Profil"), f"Profil membuka {label}"):
            return
        screenshot(screen)
        if label == "Audio Lab":
            tree = ui_tree()
            nav_hidden = tree is not None and not find_interactive("Beranda", attribute="content-desc", root=tree)
            mini_boxes = find_interactive("Buka Sedang diputar", attribute="content-desc", root=tree) if tree is not None else []
            _, height = screen_size()
            max_bottom_margin_px = int(48 * screen_density() / 160)
            mini_at_bottom = bool(mini_boxes) and (height - mini_boxes[0][0][3]) <= max_bottom_margin_px
            check(
                nav_hidden and mini_at_bottom,
                "Audio Lab menyembunyikan navbar tetapi menampilkan mini player di bawah",
                f"nav_hidden={nav_hidden}, mini={mini_boxes}, height={height}",
            )
            if mini_boxes:
                opened_player = open_now_playing()
                shell("input keyevent KEYCODE_BACK")
                back_to_lab = wait_for(
                    lambda: (t := ui_tree()) is not None
                    and not find_interactive("Tutup", attribute="content-desc", root=t)
                    and bool(find_interactive("Kembali ke Profil", attribute="content-desc", root=t))
                    and bool(find_interactive("Buka Sedang diputar", attribute="content-desc", root=t)),
                    5,
                )
                check(
                    opened_player and back_to_lab,
                    "Mengetuk mini player di halaman turunan membuka Sedang diputar",
                )
        elif label == "Pemutaran":
            min_px = min_touch_px()
            before_sw = find_interactive("Hemat data seluler")
            row_ok = bool(before_sw) and before_sw[0][1].get("checked") == "false" and (
                before_sw[0][0][3] - before_sw[0][0][1]
            ) >= min_px
            check(
                row_ok,
                "Switch Hemat data seluler tampil mati dengan area sentuh minimal 48 dp",
                before_sw,
            )
            tapped_on = bool(before_sw) and tap_text("Hemat data seluler", scrolls=0)
            sw_on = tapped_on and wait_for(
                lambda: [
                    item for item in find_interactive("Hemat data seluler")
                    if item[1].get("checked") == "true"
                ],
                5,
            )
            check(bool(sw_on), "Switch Hemat data seluler menyala saat diketuk", sw_on)
            screenshot("v2-11-switch-on")
            tapped_off = bool(sw_on) and tap_text("Hemat data seluler", scrolls=0)
            sw_off = tapped_off and wait_for(
                lambda: [
                    item for item in find_interactive("Hemat data seluler")
                    if item[1].get("checked") == "false"
                ],
                5,
            )
            check(bool(sw_off), "Switch Hemat data seluler kembali mati setelah diketuk ulang", sw_off)
        if not check(back_to_profile(), f"Kembali dari {label} ke Profil"):
            return


def wait_playing_at_speed(title: str, speed: float) -> dict[str, str] | None:
    def matches():
        current = session()
        if current and current["state"] == "PLAYING" and title in current["description"]:
            if abs(float(current["speed"]) - speed) < 0.01:
                return current
        return None

    return wait_for(matches, 5)


def check_playback_speed(title: str, speed: float) -> None:
    name = f"Posisi audio maju sekitar {speed:g}× waktu nyata"
    before = pause_for_setup(title)
    if not before:
        check(False, name, f"Sesi tidak dapat dijeda: {session()}")
        return

    # Posisi mentah dumpsys bisa tetap sama selama PLAYING. Ambil posisi aktual di kedua jeda.
    started = time.monotonic()
    shell("cmd media_session dispatch play")
    time.sleep(3)
    elapsed = time.monotonic() - started
    shell("cmd media_session dispatch pause")
    after = wait_session("PAUSED", title, 5)
    if after:
        delta_ms = int(after["position"]) - int(before["position"])
        measured = delta_ms / (elapsed * 1000)
        check(abs(measured - speed) <= 0.18, name, f"{delta_ms} ms / {elapsed:.2f} dtk = {measured:.2f}×")
    else:
        check(False, name, f"Sesi tidak dapat dijeda setelah pengukuran: {session()}")
    shell("cmd media_session dispatch play")
    check(wait_playing_at_speed(title, speed), f"Pemutaran {speed:g}× berlanjut setelah pengukuran", session())


def return_to_profile_tab() -> bool:
    for _ in range(4):
        if desc_point("Profil"):
            return True
        shell("input keyevent KEYCODE_BACK")
        time.sleep(0.5)
    return bool(desc_point("Profil"))


def check_audio_lab_effects() -> bool:
    app_pid = shell(f"pidof -s {PKG}").strip()
    errors_command = f"logcat -d --pid={app_pid} '*:E'"
    prior_errors = set(shell(errors_command).splitlines())
    title = f"{FIRST}, null, null"
    min_px = min_touch_px()

    if not check(return_to_profile_tab() and tap_profile_item("Audio Lab") and desc_point("Kembali ke Profil"),
                 "Audio Lab terbuka dari Profil untuk uji efek"):
        return False

    # 1. Equalizer: nyalakan, pilih Bass+, periksa semantik band, lalu tangkap layar.
    if not check(tap_profile_item("Equalizer") and desc_point("Kembali ke Audio Lab"),
                 "Halaman Equalizer terbuka dari Audio Lab"):
        return_to_profile_tab()
        return False

    eq_sw = find_interactive("Equalizer", attribute="content-desc")
    eq_on = bool(eq_sw) and tap_desc("Equalizer") and bool(wait_for(
        lambda: [item for item in find_interactive("Equalizer", attribute="content-desc")
                 if item[1].get("checked") == "true"],
        5,
    ))
    check(eq_on, "Sakelar Equalizer dapat dinyalakan", eq_sw)

    bass_tapped = tap_profile_item("Bass+")
    bass_applied = bass_tapped and bool(wait_for(
        lambda: ui_bounds("BASS+") and ui_bounds("31 Hz, +7 dB", attribute="content-desc"),
        5,
    ))
    check(bass_applied, "Preset Bass+ memperbarui label preset dan semantik slider band (31 Hz, +7 dB)")
    eq_targets = [
        ("Equalizer", find_interactive("Equalizer", attribute="content-desc")),
        ("Bass+", find_interactive("Bass+")),
    ]
    eq_undersized = [
        f"{label}={matches[0][0]}"
        for label, matches in eq_targets
        if not matches
        or (matches[0][0][2] - matches[0][0][0]) < min_px
        or (matches[0][0][3] - matches[0][0][1]) < min_px
    ]
    check(not eq_undersized, "Kontrol Equalizer memenuhi ukuran sentuh minimal 48 dp", eq_undersized)
    screenshot("v2-51-equalizer-bass")

    # 2. Kembali ke Audio Lab, pastikan status AKTIF · BASS+ tampil, lalu tangkap layar.
    back_lab = tap_desc("Kembali ke Audio Lab") and bool(wait_for(lambda: ui_bounds("AKTIF · BASS+"), 5))
    check(back_lab, "Audio Lab menampilkan status AKTIF · BASS+ setelah Equalizer Bass+ aktif")
    screenshot("v2-50-audiolab")

    # 3. Efek suara: tangkap layar kontrol redup saat Reverb mati, pastikan tetap bisa diubah, pilih 1,25×, lalu tangkap layar.
    if not check(tap_profile_item("Efek suara") and desc_point("Kembali ke Audio Lab"),
                 "Halaman Efek suara terbuka dari Audio Lab"):
        return_to_profile_tab()
        return False

    reverb_sw = find_interactive("Reverb", attribute="content-desc")
    reverb_was_on = bool(reverb_sw) and reverb_sw[0][1].get("checked") == "true"
    if reverb_was_on:
        reverb_off = tap_desc("Reverb") and bool(wait_for(
            lambda: [item for item in find_interactive("Reverb", attribute="content-desc")
                     if item[1].get("checked") == "false"],
            5,
        ))
        check(reverb_off, "Sakelar Reverb dimatikan untuk verifikasi kontrol redup", reverb_sw)

    screenshot("v2-80-soundfx-dim")

    studio_selected = tap_profile_item("Studio") and bool(wait_for(
        lambda: [item for item in find_interactive("Studio")
                 if any(item[1].get(attr) == "true" for attr in ("selected", "checked"))],
        5,
    ))
    reverb_still_off = [
        item for item in find_interactive("Reverb", attribute="content-desc")
        if item[1].get("checked") == "false"
    ]
    check(
        studio_selected and bool(reverb_still_off),
        "Chip ruang tetap bisa diubah saat Reverb mati tanpa menyalakan efek",
        f"studio_selected={studio_selected}, reverb={reverb_still_off}",
    )
    kamar_restored = tap_profile_item("Kamar") and bool(wait_for(
        lambda: [item for item in find_interactive("Kamar")
                 if any(item[1].get(attr) == "true" for attr in ("selected", "checked"))],
        5,
    ))
    check(kamar_restored, "Chip ruang Kamar dikembalikan setelah uji kontrol redup")

    if reverb_was_on:
        reverb_restored = tap_desc("Reverb") and bool(wait_for(
            lambda: [item for item in find_interactive("Reverb", attribute="content-desc")
                     if item[1].get("checked") == "true"],
            5,
        ))
        check(reverb_restored, "Sakelar Reverb dikembalikan ke keadaan semula")

    speed_125 = tap_profile_item("1,25×") and bool(wait_for(
        lambda: [item for item in find_interactive("1,25×")
                 if any(item[1].get(attr) == "true" for attr in ("selected", "checked"))],
        5,
    ))
    check(speed_125, "Chip kecepatan 1,25× terpilih di Efek suara")
    screenshot("v2-52-soundfx")

    speed_status_ok = tap_desc("Kembali ke Audio Lab") and bool(wait_for(
        lambda: ui_bounds("1,25× · NADA TERKUNCI"),
        5,
    ))
    check(speed_status_ok, "Audio Lab menampilkan status 1,25× · NADA TERKUNCI")
    if not check(return_to_profile_tab() and tap_profile_item("Pengembang"),
                 "Kembali ke Pengembang untuk memutar sampel lokal pada 1,25×"):
        return_to_profile_tab()
        return False

    # 4. Putar sampel lokal dan verifikasi kecepatan 1,25×.
    playing_125 = tap_text("Putar lokal", scrolls=0) and wait_playing_at_speed(title, 1.25)
    check(playing_125, "Sesi media melaporkan 1,25× saat Equalizer Bass+ dan Efek suara 1,25× aktif",
          playing_125 or session())
    if playing_125:
        check_playback_speed(title, 1.25)
        pause_for_setup(title)

    # 5. Kembalikan semua pengaturan lewat UI nyata Audio Lab.
    if not check(return_to_profile_tab() and tap_profile_item("Audio Lab") and desc_point("Kembali ke Profil"),
                 "Audio Lab dibuka kembali untuk mereset efek"):
        return_to_profile_tab()
        return False

    if check(tap_profile_item("Equalizer") and desc_point("Kembali ke Audio Lab"),
             "Equalizer dibuka untuk atur ulang dan dimatikan"):
        reset_ok = tap_profile_item("Atur ulang") and bool(wait_for(lambda: ui_bounds("DATAR"), 5))
        check(reset_ok, "Tombol Atur ulang mengembalikan Equalizer ke Datar")
        scroll_to_top(2)
        eq_off = tap_desc("Equalizer") and bool(wait_for(
            lambda: [item for item in find_interactive("Equalizer", attribute="content-desc")
                     if item[1].get("checked") == "false"],
            5,
        ))
        check(eq_off, "Sakelar Equalizer kembali dimatikan")
        tap_desc("Kembali ke Audio Lab")

    # Mengetuk kartu Kecepatan & nada membuka Efek suara dan langsung menggulir ke kartunya,
    # sehingga tombol kembali di puncak daftar berada di luar layar sementara Kunci nada dan 1,00× langsung terlihat.
    speed_card_opened = tap_profile_item("Kecepatan & nada") and bool(wait_for(
        lambda: desc_point("Kunci nada") and ui_bounds("1,00×"),
        5,
    ))
    if check(speed_card_opened, "Kartu Kecepatan & nada membuka Efek suara dan menggulir ke kartunya"):
        speed_100 = tap_profile_item("1,00×") and bool(wait_for(
            lambda: [item for item in find_interactive("1,00×")
                     if any(item[1].get(attr) == "true" for attr in ("selected", "checked"))],
            5,
        ))
        check(speed_100, "Chip kecepatan 1,00× terpilih kembali di Efek suara")
        shell("input keyevent KEYCODE_BACK")
        wait_for(lambda: desc_point("Kembali ke Profil"), 5)

    restored_lab = bool(wait_for(
        lambda: ui_bounds("MATI") and ui_bounds("1,00× · NADA TERKUNCI"),
        5,
    ))
    check(restored_lab, "Audio Lab kembali menampilkan status MATI dan 1,00× · NADA TERKUNCI")

    if not check(return_to_profile_tab() and tap_profile_item("Pengembang"),
                 "Pengembang dibuka kembali untuk verifikasi 1,0×"):
        return_to_profile_tab()
        return False

    # 6. Putar sampel lokal dan verifikasi kecepatan kembali 1,0×.
    playing_100 = tap_text("Putar lokal", scrolls=0) and wait_playing_at_speed(title, 1.0)
    check(playing_100, "Pemutaran kembali berjalan pada 1× setelah pengaturan Audio Lab dikembalikan",
          playing_100 or session())
    if playing_100:
        check_playback_speed(title, 1.0)

    errors = [
        line for line in shell(errors_command).splitlines()
        if line not in prior_errors and re.search(r"\b[\w$.]*(?:Exception|Error)\b|FATAL EXCEPTION", line)
    ]
    check(not errors, "Tidak ada exception aplikasi saat menguji efek lewat Audio Lab", "\n".join(errors)[-2000:])
    return True


def check_https() -> None:
    name = "Tombol 'Putar HTTPS' memutar sampel internet (PLAYING)"
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")
    prior_errors = shell("logcat -d -s ExoPlayerImplInternal:E")
    check_profile_shell()
    if not check(open_tab("Profil") and tap_profile_item("VERSI & TENTANG", taps=7)
                 and tap_profile_item("Pengembang"), "Tujuh ketukan kartu versi membuka Pengembang"):
        return
    screenshot("v2-10-profile-developer")
    if not check_audio_lab_effects():
        return
    # Sampel lokal dan HTTPS memakai judul yang sama. Akhiri status PLAYING lokal
    # sebelum menunggu PLAYING untuk HTTPS.
    local = session()
    if local and local["state"] == "PLAYING" and f"{FIRST}, null, null" in local["description"]:
        if not check(pause_for_setup(f"{FIRST}, null, null"), "Sampel lokal dijeda sebelum uji HTTPS"):
            back_to_profile()
            return
    if not tap_text("Putar HTTPS", scrolls=8):
        check(False, name, "Tombol 'Putar HTTPS' tidak ditemukan")
        return

    # Sampel HTTPS tampil sendiri di sesi media tanpa metadata artis dari antrean.
    playing = wait_session("PLAYING", f"{FIRST}, null, null", 45)
    back_to_profile()
    if playing:
        check(playing, name, playing)
        return

    errors = shell("logcat -d -s ExoPlayerImplInternal:E").removeprefix(prior_errors)
    network = re.search(
        r"InvalidResponseCodeException: Response code: \d+|UnknownHostException|"
        r"ConnectException|SocketTimeoutException|SocketException|NoRouteToHostException|SSLException|SSLHandshakeException",
        errors,
    )
    if network:
        record("DILEWATI", name, f"Kendala jaringan emulator: {network.group()}")
    else:
        check(False, name, f"Sesi HTTPS tidak mencapai PLAYING: {session()}")


def check_listening_stats() -> None:
    home_open = open_tab("Beranda")
    root = ui_tree() if home_open else None
    avatar = ui_bounds("Buka profil", attribute="content-desc", root=root) if root is not None else []
    initials = ui_bounds("U", root=root) if root is not None else []
    initial_in_avatar = any(
        ax1 <= x1 and ay1 <= y1 and x2 <= ax2 and y2 <= ay2
        for ax1, ay1, ax2, ay2 in avatar
        for x1, y1, x2, y2 in initials
    )
    check(initial_in_avatar, "Avatar di bar atas Beranda menampilkan inisial U")
    if home_open:
        screenshot("v2-43-home-profile-avatar")
    opened_profile = (
        home_open
        and tap_desc("Buka profil")
        and bool(wait_for(lambda: ui_bounds("Halo lagi, Uji!"), 5))
    )
    check(opened_profile, "Avatar di bar atas Beranda membuka tab Profil")
    opened = (opened_profile or open_tab("Profil")) and tap_profile_item("Lihat statistik lengkap")
    if not check(opened, "Statistik dengar dapat dibuka dari Profil"):
        return

    def chart_description():
        root = ui_tree()
        return next((
            node.get("content-desc") for node in root.iter("node")
            if node.get("content-desc", "").startswith("Menit per hari. Terbanyak")
        ), None) if root is not None else None

    chart = wait_for(chart_description, 5)
    check(chart and "Total" in chart, "Grafik statistik memiliki ringkasan waktu dengar untuk pembaca layar", chart)
    screenshot("08-listening-stats")
    screenshot("v2-42-stats")
    found = collect_while_scrolling([(f"Putar {SECOND}", "content-desc")], scrolls=4)
    check(f"Putar {SECOND}" in found, "Lagu yang didengar di latar belakang bisa diputar dari statistik")
    shell("input keyevent KEYCODE_BACK")


def hero_play_target(root: ET.Element | None = None) -> tuple[str, tuple[int, int, int, int]] | None:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return None
    for title in (FIRST, SECOND):
        matches = find_interactive(f"Putar {title}", attribute="content-desc", root=tree)
        if matches:
            return title, matches[0][0]
    return None


def check_home_shelves() -> None:
    if not open_tab("Beranda"):
        check(False, "Beranda menampilkan minimal satu judul rak atau tombol Hemat data", "Tab Beranda gagal dibuka")
        return
    scroll_to_top(2)
    shelf_titles = {
        "Baru diputar",
        "Untuk kamu",
        "Mood & genre",
        "Lagu untuk kamu",
        "Lagu populer",
        "Artis yang mungkin kamu suka",
        "Artis populer",
        "Album untuk kamu",
        "Album populer",
    }
    data_saver_button = "Muat rekomendasi YouTube Music"

    def inspect_home(root: ET.Element | None) -> list[str]:
        if root is None:
            return []
        hits = []
        for node in root.iter("node"):
            text = (node.get("text") or "").strip()
            desc = (node.get("content-desc") or "").strip()
            if text == data_saver_button or text in shelf_titles:
                hits.append(text)
            elif desc == "Segarkan rekomendasi YouTube Music":
                hits.append(desc)
            elif desc.startswith(("Album ", "Playlist ", "Artis ", "Mood ")):
                hits.append(desc)
        return list(dict.fromkeys(hits))

    def settled_tree() -> ET.Element | None:
        tree = ui_tree()
        if tree is None:
            return None
        loading = any(
            "Memuat rekomendasi" in (node.get("content-desc") or "") or "Memuat rekomendasi" in (node.get("text") or "")
            for node in tree.iter("node")
        )
        if not loading and inspect_home(tree):
            return tree
        return None

    wait_for(settled_tree, 10, interval=1.0)
    width, height = screen_size()
    min_px = min_touch_px()
    top_tree = ui_tree(save_as="v2-30-home-ui.xml")
    screenshot("v2-30-home")
    screenshot("08b-home-shelves")

    avatar = find_interactive("Buka profil", attribute="content-desc", root=top_tree) if top_tree is not None else []
    hero_play = hero_play_target(top_tree)
    hero_view = find_interactive("Lihat lagu yang paling sering diputar", attribute="content-desc", root=top_tree) if top_tree is not None else []
    has_brand = top_tree is not None and bool(ui_bounds("Senada", root=top_tree))
    has_recent = top_tree is not None and bool(ui_bounds("Baru diputar", root=top_tree))
    has_favorites = top_tree is not None and bool(ui_bounds("Favorit", root=top_tree)) and bool(ui_bounds("Daftar favoritmu", root=top_tree))
    shortcuts_removed = top_tree is not None and not ui_bounds("Statistik dengar", root=top_tree) and not ui_bounds("Paling sering diputar", root=top_tree)
    touch_targets = [
        ("Buka profil", avatar[0][0]) if avatar else ("Buka profil", None),
        ("Putar hero", hero_play[1]) if hero_play else ("Putar hero", None),
        ("Lihat hero", hero_view[0][0]) if hero_view else ("Lihat hero", None),
    ]
    undersized = [
        f"{label}={box}"
        for label, box in touch_targets
        if box is None or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
    ]
    check(
        has_brand and has_recent and has_favorites and shortcuts_removed and not undersized,
        "Beranda v2 menampilkan bar atas Senada, hero dengan tombol kaca minimal 48 dp, Baru diputar dengan Favorit, tanpa pintasan Statistik/Teratas",
        f"brand={has_brand}, recent={has_recent}, fav={has_favorites}, no_shortcuts={shortcuts_removed}, targets={undersized}",
    )

    if hero_view:
        x1, y1, x2, y2 = hero_view[0][0]
        tap(((x1 + x2) // 2, (y1 + y2) // 2))
        most_played_opened = bool(wait_for(lambda: desc_point("Kembali ke Profil"), 5)) and bool(ui_bounds("Paling sering diputar"))
        check(most_played_opened, "Tombol 'Lihat' pada hero lagu membuka Paling sering diputar")
        back_to_profile()
        open_tab("Beranda")
        scroll_to_top(2)

    fav_matches = find_interactive("Daftar favoritmu")
    if fav_matches:
        x1, y1, x2, y2 = fav_matches[0][0]
        tap(((x1 + x2) // 2, (y1 + y2) // 2))
        fav_opened = bool(wait_for(lambda: ui_bounds("Putar Favorit", attribute="content-desc"), 5))
        check(fav_opened, "Kartu Favorit pada Baru diputar membuka halaman Favorit di Library")
        open_tab("Beranda")
        scroll_to_top(2)

    found: list[str] = []
    for step in range(4):
        tree = ui_tree(save_as="08b-home-shelves-ui.xml" if step == 0 else None)
        for hit in inspect_home(tree):
            if hit not in found:
                found.append(hit)
        if step == 1:
            screenshot("v2-31-home-scrolled")
        if step >= 1 and any(h == data_saver_button or h in shelf_titles or h == "Segarkan rekomendasi YouTube Music" for h in found):
            break
        shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 300")
        time.sleep(0.6)
    check(
        bool(found),
        "Beranda menampilkan minimal satu judul rak atau tombol Hemat data",
        ", ".join(found[:6]) if found else "tidak ada judul rak maupun tombol Hemat data",
    )


def screenshot_full_appearance() -> None:
    """Perpanjang layar sebentar agar keempat kartu Tampilan terlihat pada kanvas 390×1180 dp."""
    original_size = re.search(r"Override size: (\d+x\d+)", shell("wm size"))
    density = screen_density() / 160
    width, height = round(390 * density), round(1180 * density)
    try:
        shell(f"wm size {width}x{height}")

        def full_appearance_visible():
            root = ui_tree()
            if root is None or not ui_bounds("Tampilan", root=root):
                return False
            return any(
                y2 < overlay_top(root, height)
                for _, _, _, y2 in ui_bounds("Mati", root=root)
            )

        check(wait_for(full_appearance_visible, 10), "Seluruh kartu Tampilan terlihat pada kanvas v2-70-appearance")
        screenshot("v2-70-appearance")
    finally:
        shell("wm size " + (original_size.group(1) if original_size else "reset"))
        wait_for(lambda: ui_bounds("Tampilan") and desc_point("Kembali ke Profil"), 5)


def is_selected_label(label: str, *, attribute: str = "text", root: ET.Element | None = None) -> bool:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return False
    parents = parent_map(tree)
    for node in tree.iter("node"):
        if node.get(attribute) != label:
            continue
        for candidate in (node, parents.get(node), interactive_node(node, parents)):
            if candidate is not None and any(candidate.get(attr) == "true" for attr in ("selected", "checked")):
                return True
    return False


def is_disabled_label(label: str, *, attribute: str = "text", root: ET.Element | None = None) -> bool:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return False
    parents = parent_map(tree)
    for node in tree.iter("node"):
        if node.get(attribute) != label:
            continue
        for candidate in (node, parents.get(node), interactive_node(node, parents)):
            if candidate is not None and candidate.get("enabled") == "false":
                return True
    return False


def find_visible_node(
    label: str, *, attribute: str = "text", scrolls: int = 4,
) -> tuple[tuple[int, int, int, int], ET.Element] | None:
    width, height = screen_size()
    min_px = min_touch_px()

    def current_match():
        root = ui_tree()
        if root is None:
            return None
        top = overlay_top(root, height)
        for box, node in find_interactive(label, attribute=attribute, root=root):
            if box[1] > 0 and box[3] < top and (box[3] - box[1]) >= min_px:
                return box, node
        return None

    hit = current_match()
    if hit is not None:
        return hit
    scroll_to_top(2)
    for _ in range(scrolls + 1):
        hit = current_match()
        if hit is not None:
            return hit
        shell(f"input swipe {width // 2} {height * 3 // 5} {width // 2} {height // 4} 350")
        time.sleep(0.5)
    return None


def current_cover_radius(root: ET.Element | None = None) -> int | None:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return None
    for node in tree.iter("node"):
        match = re.fullmatch(r"(\d+)dp", (node.get("text") or "").strip())
        if match:
            return int(match.group(1))
    return None


def set_cover_radius(target_dp: int) -> bool:
    for _ in range(4):
        current = current_cover_radius()
        if current == target_dp:
            return True
        desc = "Kurangi sudut sampul" if current is None or target_dp < current else "Tambah sudut sampul"
        match = find_visible_node(desc, attribute="content-desc")
        if match is None:
            return False
        current = current_cover_radius()
        if current == target_dp:
            return True
        if current is None:
            return False
        steps = abs(target_dp - current) // 2
        if steps == 0:
            return False
        x1, y1, x2, y2 = match[0]
        pt = ((x1 + x2) // 2, (y1 + y2) // 2)
        for _ in range(steps):
            tap(pt)
        if wait_for(lambda: ui_bounds(f"{target_dp}dp"), 2):
            return True
    return bool(ui_bounds(f"{target_dp}dp"))


def set_appearance_switch(label: str, target_checked: bool) -> bool:
    expected = "true" if target_checked else "false"
    match = find_visible_node(label)
    if match is None:
        return False
    if match[1].get("checked") == expected:
        return True
    x1, y1, x2, y2 = match[0]
    tap(((x1 + x2) // 2, (y1 + y2) // 2))
    time.sleep(0.7)
    return bool(wait_for(
        lambda: [item for item in find_interactive(label) if item[1].get("checked") == expected],
        5,
    ))


def check_appearance_v2() -> None:
    if not check(
        open_tab("Profil") and (scroll_to_top(2) or True) and tap_profile_item("Tampilan") and desc_point("Kembali ke Profil"),
        "Halaman Tampilan v2 dapat dibuka dari Profil",
    ):
        return

    # Emulator CI memakai disable-animations=true (animator_duration_scale=0): pastikan chip Mati terkunci.
    width, height = screen_size()
    locked_ok = False
    for _ in range(4):
        tree = ui_tree()
        if tree is not None and ui_bounds("Diatur oleh sistem", root=tree) and ui_bounds("Mati", root=tree):
            locked_ok = is_selected_label("Mati", root=tree) and is_disabled_label("Mati", root=tree)
            break
        shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 300")
        time.sleep(0.5)
    check(
        locked_ok,
        "Pengurangan gerak sistem mengunci kecepatan animasi ke Mati dengan keterangan 'Diatur oleh sistem'",
    )

    try:
        shell("settings put global animator_duration_scale 1.0")
        unlocked = wait_for(
            lambda: (t := ui_tree()) is not None
            and not ui_bounds("Diatur oleh sistem", root=t)
            and is_selected_label("Normal", root=t),
            5,
        )
        check(bool(unlocked), "Kecepatan animasi kembali ke Normal saat pengurangan gerak sistem dimatikan")
        scroll_to_top(3)

        dark_selected = tap_text("Gelap", scrolls=0) and bool(wait_for(lambda: is_selected_label("Gelap"), 5))
        check(dark_selected, "Segmen tema Gelap dapat dipilih")
        screenshot_full_appearance()

        light_selected = tap_text("Terang", scrolls=0) and bool(wait_for(lambda: is_selected_label("Terang"), 5))
        system_selected = tap_text("Ikuti sistem", scrolls=0) and bool(wait_for(lambda: is_selected_label("Ikuti sistem"), 5))
        check(light_selected and system_selected, "Segmen tema Terang dan Ikuti sistem dapat dipilih")

        follow_on = wait_for(
            lambda: [item for item in find_interactive("Warna mengikuti sampul") if item[1].get("checked") == "true"],
            5,
        )
        indigo_picked = bool(follow_on) and tap_desc("Warna aksen Indigo") and bool(wait_for(
            lambda: [item for item in find_interactive("Warna mengikuti sampul") if item[1].get("checked") == "false"],
            5,
        ))
        follow_re_on = indigo_picked and tap_text("Warna mengikuti sampul", scrolls=0) and bool(wait_for(
            lambda: [item for item in find_interactive("Warna mengikuti sampul") if item[1].get("checked") == "true"],
            5,
        ))
        restored_indigo = follow_re_on and tap_text("Warna mengikuti sampul", scrolls=0) and bool(wait_for(
            lambda: is_selected_label("Warna aksen Indigo", attribute="content-desc"),
            5,
        ))
        reset_follow = restored_indigo and tap_desc("Warna aksen Senada") and tap_text("Warna mengikuti sampul", scrolls=0) and bool(wait_for(
            lambda: [item for item in find_interactive("Warna mengikuti sampul") if item[1].get("checked") == "true"],
            5,
        ))
        check(
            bool(reset_follow),
            "Bulatan warna aksen dan sakelar Warna mengikuti sampul bekerja serta memulihkan warna terakhir",
        )

        radius_default = bool(ui_bounds("20dp")) or tap_profile_item("20dp")
        radius_inc = radius_default and tap_desc("Tambah sudut sampul") and bool(wait_for(lambda: ui_bounds("22dp"), 5))
        radius_dec = radius_inc and tap_desc("Kurangi sudut sampul") and bool(wait_for(lambda: ui_bounds("20dp"), 5))
        check(radius_dec, "Kartu Sudut sampul dapat ditambah dan dikurangi dengan langkah 2 dp")

        glass_off = tap_profile_item("Efek kaca") and bool(wait_for(
            lambda: [item for item in find_interactive("Efek kaca") if item[1].get("checked") == "false"],
            5,
        ))
        check(glass_off, "Efek kaca dapat dimatikan di halaman Tampilan")

        if glass_off and back_to_profile() and open_tab("Beranda"):
            scroll_to_top(2)
            wait_for(lambda: ui_bounds("Baru diputar"), 5)
            screenshot("v2-71-glass-off")

        glass_restored = (
            open_tab("Profil")
            and (scroll_to_top(2) or True)
            and tap_profile_item("Tampilan")
            and tap_profile_item("Efek kaca")
            and bool(wait_for(
                lambda: [item for item in find_interactive("Efek kaca") if item[1].get("checked") == "true"],
                5,
            ))
        )
        check(glass_restored, "Efek kaca kembali dinyalakan setelah tangkapan layar Beranda")

        plain_backdrop = set_appearance_switch("Latar pemutar dari sampul", False)
        check(plain_backdrop, "Latar pemutar dari sampul dapat dimatikan di halaman Tampilan")
        radius_zero = set_cover_radius(0)
        check(radius_zero, "Sudut sampul dapat diatur ke 0dp lewat tombol kurang")

        plain_opened = open_now_playing()
        if plain_opened:
            time.sleep(0.4)
            screenshot("v2-63-nowplaying-plain")
        plain_closed = plain_opened and tap_desc("Tutup") and bool(wait_for(now_playing_closed, 5))
        check(
            plain_opened and plain_closed,
            "Sedang diputar dapat dibuka dengan latar polos dan sudut sampul 0dp",
        )

        radius_restored = set_cover_radius(20)
        backdrop_restored = set_appearance_switch("Latar pemutar dari sampul", True)
        check(
            radius_restored and backdrop_restored,
            "Latar pemutar dari sampul dan sudut sampul 20dp dikembalikan ke bawaan",
            f"radius={radius_restored}, backdrop={backdrop_restored}",
        )
        back_to_profile()
    finally:
        shell("settings put global animator_duration_scale 0.0")


def check_home_hero_play() -> None:
    shell("input keyevent KEYCODE_BACK")
    wait_for(now_playing_closed, 5)
    shell("settings put system font_scale 1.0")
    time.sleep(1.5)
    wait_for(lambda: find_interactive("Beranda", attribute="content-desc"), 5)
    if not open_tab("Beranda"):
        check(False, "Tombol 'Putar' pada hero Beranda memutar lagu teratas", "Tab Beranda gagal dibuka")
        return
    scroll_to_top(2)
    shell("cmd media_session dispatch pause")
    time.sleep(0.5)
    target = hero_play_target()
    if target is None:
        check(False, "Tombol 'Putar' pada hero Beranda memutar lagu teratas", "Tombol Putar hero tidak ditemukan")
        return
    hero_title, (x1, y1, x2, y2) = target
    tap(((x1 + x2) // 2, (y1 + y2) // 2))
    playing = wait_session("PLAYING", hero_title, 10)
    check(playing, f"Tombol 'Putar' pada hero Beranda memutar '{hero_title}'", playing or session())


def check_collection_header() -> None:
    if open_tab("Cari"):
        scroll_to_top(3)
        if tap_desc(f"Buka menu {FIRST}"):
            if not tap_text("Tambah ke favorit", scrolls=0):
                shell("input keyevent KEYCODE_BACK")

    if not check(open_tab("Library"), "Tab Library dapat dibuka untuk memeriksa header koleksi Favorit"):
        return
    scroll_to_top(2)
    min_px = min_touch_px()
    play_matches = wait_for(lambda: find_interactive("Putar Favorit", attribute="content-desc"), 5) or []
    play_ok = bool(play_matches) and all(
        (box[2] - box[0]) >= min_px and (box[3] - box[1]) >= min_px
        for box, _ in play_matches
    )
    check(
        play_ok,
        "Halaman Favorit menampilkan tombol Putar dengan content-desc 'Putar Favorit' minimal 48 dp",
        [box for box, _ in play_matches],
    )
    screenshot("09-favorites-header")
    screenshot("v2-96-library-favorites")
    check_library_chips(min_px)


def check_library_chips(min_px: int) -> None:
    if tap_text("Playlist", scrolls=0) and wait_for(lambda: find_interactive("Playlist baru"), 5):
        if tap_text("Playlist baru", scrolls=0) and tap_text("Nama playlist", scrolls=0):
            shell("input text Pagi")
            time.sleep(0.5)
            if not ui_points("Buat") and "mInputShown=true" in shell("dumpsys input_method"):
                shell("input keyevent KEYCODE_BACK")
                time.sleep(0.4)
            tap_text("Buat", scrolls=0)
        row_matches = wait_for(lambda: find_interactive("Buka playlist Pagi", attribute="content-desc"), 5) or []
        menu_matches = find_interactive("Buka menu playlist Pagi", attribute="content-desc")
        no_buka_button = not find_interactive("Buka")
        row_ok = bool(row_matches) and bool(menu_matches) and no_buka_button and all(
            (box[2] - box[0]) >= min_px and (box[3] - box[1]) >= min_px
            for box, _ in row_matches + menu_matches
        )
        check(
            row_ok,
            "Daftar playlist menampilkan baris ketuk 'Buka playlist Pagi' dan menu ⋯ tanpa tombol Buka",
            f"row={row_matches}, menu={menu_matches}, no_buka={no_buka_button}",
        )
        screenshot("09b-library-playlists")
        screenshot("v2-97-library-playlists")
        if row_matches:
            x1, y1, x2, y2 = row_matches[0][0]
            tap(((x1 + x2) // 2, (y1 + y2) // 2))
            back_btn = wait_for(lambda: find_interactive("Kembali ke semua playlist", attribute="content-desc"), 5) or []
            empty_msg = ui_bounds("Playlist ini masih kosong.")
            detail_ok = bool(back_btn) and bool(empty_msg) and all(
                (box[2] - box[0]) >= min_px and (box[3] - box[1]) >= min_px
                for box, _ in back_btn
            )
            check(
                detail_ok,
                "Halaman detail playlist di Library menampilkan tombol kembali v2 minimal 48 dp dan kartu kosong",
                f"back={back_btn}, empty={empty_msg}",
            )
            screenshot("v2-98-library-playlist-detail")
            shell("input keyevent KEYCODE_BACK")
            wait_for(lambda: find_interactive("Playlist baru"), 5)

    if tap_text("Riwayat", scrolls=0):
        history_play = wait_for(lambda: find_interactive("Putar Riwayat", attribute="content-desc"), 5) or []
        history_shuffle = find_interactive("Putar acak Riwayat", attribute="content-desc")
        history_ok = bool(history_play) and bool(history_shuffle) and all(
            (box[2] - box[0]) >= min_px and (box[3] - box[1]) >= min_px
            for box, _ in history_play + history_shuffle
        )
        check(
            history_ok,
            "Halaman Riwayat menampilkan header ringkas dengan tombol Putar dan Acak minimal 48 dp",
            f"play={history_play}, shuffle={history_shuffle}",
        )
        screenshot("09c-library-history")
        screenshot("v2-99-library-history")

    if tap_text("File impor", scrolls=0):
        imported_action = wait_for(lambda: find_interactive("Impor file audio"), 5) or []
        imported_play = ui_bounds("Putar File impor", attribute="content-desc")
        imported_ok = bool(imported_action) and bool(imported_play) and all(
            (box[2] - box[0]) >= min_px and (box[3] - box[1]) >= min_px
            for box, _ in imported_action
        )
        check(
            imported_ok,
            "Halaman File impor menampilkan header ringkas dan aksi Impor file audio minimal 48 dp",
            f"action={imported_action}, play={imported_play}",
        )
        screenshot("09d-library-imported")
        screenshot("v2-99b-library-imported")

    tap_text("Favorit", scrolls=0)


def find_interactive_prefix(
    prefix: str, *, attribute: str = "content-desc", root: ET.Element | None = None,
) -> list[tuple[tuple[int, int, int, int], ET.Element, str]]:
    tree = root if root is not None else ui_tree()
    if tree is None:
        return []
    parents = parent_map(tree)
    matches = []
    for node in tree.iter("node"):
        value = node.get(attribute) or ""
        if value.startswith(prefix):
            target = interactive_node(node, parents)
            box = parse_bounds(target)
            if box:
                matches.append((box, target, value))
    return sorted(matches, key=lambda item: item[0][1] + item[0][3])


def check_browse_v2() -> None:
    """Verifikasi tampilan dan navigasi v2 untuk Cari, riwayat pencarian, halaman artis, dan halaman album."""
    if not check(open_tab("Cari"), "Tab Cari v2 dapat dibuka"):
        return
    scroll_to_top(2)
    min_px = min_touch_px()
    width, height = screen_size()
    search_tree = ui_tree(save_as="v2-90-search-ui.xml")
    screenshot("v2-90-search")

    chip_labels = ("Lagu", "Artis", "Album", "Playlist")
    chip_targets = [
        (label, find_interactive(label, root=search_tree) if search_tree is not None else [])
        for label in chip_labels
    ]
    chip_bad = [
        f"{label}={matches[0][0] if matches else None}"
        for label, matches in chip_targets
        if not matches
        or (matches[0][0][2] - matches[0][0][0]) < min_px
        or (matches[0][0][3] - matches[0][0][1]) < min_px
    ]
    check(
        search_tree is not None
        and bool(ui_bounds("Cari", root=search_tree))
        and bool(ui_bounds("Cari judul atau artis", root=search_tree))
        and not chip_bad,
        "Tab Cari v2 menampilkan judul, kolom cari, dan keempat chip filter minimal 48 dp",
        chip_bad,
    )

    if tap_text("Artis", scrolls=0):
        prompt_ok = bool(wait_for(lambda: ui_bounds("Ketik nama artis untuk mencari di YouTube Music."), 5))
        check(prompt_ok, "Filter Artis tanpa kueri menampilkan kartu petunjuk pencarian")

    if not tap_text("Cari judul atau artis", scrolls=0):
        tap_text("Lagu", scrolls=0)
        return
    shell("input text Tulus")
    time.sleep(0.4)
    shell("input keyevent KEYCODE_ENTER")
    time.sleep(0.8)
    if "mInputShown=true" in shell("dumpsys input_method"):
        shell("input keyevent KEYCODE_BACK")
        time.sleep(0.5)

    def artist_search_settled():
        tree = ui_tree()
        if tree is None:
            return None
        artists = find_interactive_prefix("Buka artis ", root=tree)
        if artists:
            return ("artists", artists, tree)
        if ui_bounds("Coba lagi", root=tree) or ui_bounds("Artis tidak ditemukan.", root=tree):
            return ("fallback", [], tree)
        return None

    outcome = wait_for(artist_search_settled, 15, interval=1.0)
    if outcome and outcome[0] == "artists":
        screenshot("v2-92-search-artists")
        artists = outcome[1]
        artist_tree = outcome[2]
        top_limit = overlay_top(artist_tree, height)
        visible_artists = [item for item in artists if item[0][1] > 0 and item[0][3] < top_limit]
        picked = visible_artists[0] if visible_artists else artists[0]
        x1, y1, x2, y2 = picked[0]
        tap(((x1 + x2) // 2, (y1 + y2) // 2))

        def artist_page_ready():
            tree = ui_tree()
            if tree is None:
                return None
            if not find_interactive("Kembali ke Cari", attribute="content-desc", root=tree):
                return None
            if (
                ui_bounds("Lagu terpopuler", root=tree)
                or ui_bounds("Album terpopuler", root=tree)
                or ui_bounds("Album terbaru", root=tree)
                or ui_bounds("Coba lagi", root=tree)
                or ui_bounds("Belum ada lagu atau album untuk artis ini.", root=tree)
            ):
                return tree
            return None

        artist_page = wait_for(artist_page_ready, 15, interval=1.0)
        screenshot("v2-94-artist")
        if artist_page is not None:
            back_artist = find_interactive("Kembali ke Cari", attribute="content-desc", root=artist_page)
            play_artist = find_interactive_prefix("Putar lagu terpopuler ", root=artist_page)
            shuffle_artist = find_interactive_prefix("Putar acak lagu terpopuler ", root=artist_page)
            artist_targets = [
                ("Kembali ke Cari", back_artist[0][0] if back_artist else None),
                ("Putar artis", play_artist[0][0] if play_artist else None),
                ("Acak artis", shuffle_artist[0][0] if shuffle_artist else None),
            ]
            artist_bad = [
                f"{label}={box}"
                for label, box in artist_targets
                if box is None or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
            ]
            check(
                not artist_bad,
                "Halaman artis v2 menampilkan tombol kembali, Putar, dan Acak minimal 48 dp",
                artist_bad,
            )

            album_hit = None
            for _ in range(5):
                tree = ui_tree()
                if tree is not None:
                    limit = overlay_top(tree, height)
                    albums = [
                        item for item in find_interactive_prefix("Buka album ", root=tree)
                        if item[0][1] > 0 and item[0][3] < limit and (item[0][3] - item[0][1]) >= min_px
                    ]
                    if albums:
                        album_hit = albums[0]
                        break
                shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 350")
                time.sleep(0.5)

            if album_hit is not None:
                ax1, ay1, ax2, ay2 = album_hit[0]
                tap(((ax1 + ax2) // 2, (ay1 + ay2) // 2))

                def album_page_ready():
                    tree = ui_tree()
                    if tree is None:
                        return None
                    if not find_interactive_prefix("Kembali ke ", root=tree):
                        return None
                    if (
                        ui_bounds("Daftar lagu", root=tree)
                        or ui_bounds("Belum ada lagu di album ini.", root=tree)
                        or ui_bounds("Coba lagi", root=tree)
                    ):
                        return tree
                    return None

                album_page = wait_for(album_page_ready, 15, interval=1.0)
                screenshot("v2-95-album")
                if album_page is not None:
                    back_album = find_interactive_prefix("Kembali ke ", root=album_page)
                    menu_album = find_interactive_prefix("Buka menu ", root=album_page)
                    shuffle_album = find_interactive_prefix("Putar acak ", root=album_page)
                    play_album = [
                        item for item in find_interactive_prefix("Putar ", root=album_page)
                        if not item[2].startswith("Putar acak ")
                    ]
                    album_targets = [
                        ("Kembali album", back_album[0][0] if back_album else None),
                        ("Menu album", menu_album[0][0] if menu_album else None),
                        ("Acak album", shuffle_album[0][0] if shuffle_album else None),
                        ("Putar album", play_album[0][0] if play_album else None),
                    ]
                    album_bad = [
                        f"{label}={box}"
                        for label, box in album_targets
                        if box is None or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
                    ]
                    check(
                        not album_bad,
                        "Halaman album v2 menampilkan tombol kembali, menu ⋯, Acak, dan Putar minimal 48 dp",
                        album_bad,
                    )
                else:
                    record("DILEWATI", "Halaman album v2 dimuat dari halaman artis", "Waktu tunggu habis")
                shell("input keyevent KEYCODE_BACK")
                wait_for(lambda: find_interactive("Kembali ke Cari", attribute="content-desc"), 5)
            else:
                record("DILEWATI", "Halaman album v2 dari halaman artis", "Tidak ada kartu album pada hasil artis")
        else:
            record("DILEWATI", "Halaman artis v2 dimuat dari hasil pencarian", "Waktu tunggu habis")
        shell("input keyevent KEYCODE_BACK")
        wait_for(lambda: find_interactive("Hapus pencarian", attribute="content-desc"), 5)
    else:
        screenshot("v2-92-search-artists-fallback")
        record(
            "DILEWATI",
            "Halaman artis dan album v2 dari hasil pencarian YouTube Music",
            "Kendala jaringan/katalog emulator",
        )

    if tap_desc("Hapus pencarian"):
        history_shown = bool(wait_for(lambda: ui_bounds("Tulus") and ui_bounds("Hapus riwayat"), 5))
        check(history_shown, "Riwayat pencarian menampilkan kueri 'Tulus' dalam kartu v2")
        screenshot("v2-93-search-history")
        if history_shown:
            tap_text("Hapus riwayat", scrolls=0)
            wait_for(lambda: not ui_bounds("Hapus riwayat"), 5)
    tap_text("Lagu", scrolls=0)
    scroll_to_top(2)


def check_browse_light_theme() -> None:
    """Tangkap layar tema terang untuk Cari dan Library, lalu kembalikan mode gelap."""
    try:
        shell("cmd uimode night no")
        time.sleep(2.0)
        if open_tab("Cari"):
            scroll_to_top(2)
            wait_for(lambda: ui_bounds("Cari judul atau artis"), 5)
            screenshot("v2-99c-search-light")
        if open_tab("Library"):
            scroll_to_top(2)
            wait_for(lambda: ui_bounds("Putar Favorit", attribute="content-desc"), 5)
            screenshot("v2-99d-library-light")
    finally:
        shell("cmd uimode night yes")
        time.sleep(1.0)


def min_touch_px() -> int:
    # Toleransi 1 px untuk pembulatan piksel pada kepadatan layar non-bulat (mis. 420 dpi).
    return int(48 * screen_density() / 160) - 1


def queue_menu_targets(menu_label: str) -> list[tuple[tuple[int, int, int, int], ET.Element]]:
    if not tap_desc(menu_label):
        return []
    try:
        return wait_for(lambda: find_interactive(QUEUE_ACTION), 5)
    finally:
        shell("input keyevent KEYCODE_BACK")


def check_initial_accessibility() -> None:
    root = ui_tree(save_as="01-ui.xml")
    menu_label = f"Buka menu {FIRST}"
    menu_matches = find_interactive(menu_label, attribute="content-desc", root=root)
    check(menu_matches, f"Tombol menu ⋯ punya content-desc '{menu_label}'", [box for box, _ in menu_matches])

    min_px = min_touch_px()
    targets = [
        (label, find_interactive(label, attribute="content-desc", root=root))
        for label in (menu_label, "Beranda", "Cari", "Library", "Profil")
    ]
    targets.append((QUEUE_ACTION, queue_menu_targets(menu_label)))
    missing = []
    undersized = []
    checked = 0
    for label, matches in targets:
        if not matches:
            missing.append(label)
            continue
        checked += 1
        x1, y1, x2, y2 = matches[0][0]
        if (x2 - x1) < min_px or (y2 - y1) < min_px:
            undersized.append(f"{label}={x2 - x1}x{y2 - y1}px")
    check(
        checked > 0 and not missing and not undersized,
        "Tombol utama memenuhi ukuran sentuh minimal 48 dp",
        f"min {min_px}px, dicek {checked}, hilang {missing}, kekecilan {undersized}",
    )


def check_search_keyboard() -> None:
    scroll_to_top(2)
    placeholder = "Cari judul atau artis"
    labels = ("Beranda", "Buka Sedang diputar")

    def visible_bars(root: ET.Element) -> list[str]:
        return [label for label in labels if ui_bounds(label, attribute="content-desc", root=root)]

    def keyboard_visible() -> bool:
        return "mInputShown=true" in shell("dumpsys input_method")

    initial = ui_tree()
    if not check(
        initial is not None and visible_bars(initial) == list(labels) and tap_text(placeholder, scrolls=0),
        "Kolom Cari bisa difokuskan saat navigasi dan mini player terlihat",
    ):
        return

    if not check(wait_for(keyboard_visible, 5), "Papan ketik tab Cari terbuka"):
        return
    screenshot("v2-91-search-keyboard")
    typing = ui_tree(save_as="search-keyboard-open.xml")
    visible = visible_bars(typing) if typing is not None else None
    check(visible == [], "Navigasi dan mini player tersembunyi saat papan ketik terbuka", visible)

    shell("input keyevent KEYCODE_BACK")
    closed = wait_for(lambda: not keyboard_visible(), 5)
    restored = ui_tree(save_as="search-keyboard-closed.xml")
    check(
        closed and restored is not None and visible_bars(restored) == list(labels)
        and ui_bounds(placeholder, root=restored),
        "Tombol kembali menutup papan ketik dan memulihkan kedua bilah di tab Cari",
    )


def overlay_top(root: ET.Element, height: int) -> int:
    """Batas atas mini player dan navigasi bawah yang mengambang di atas isi tab."""
    tops = [
        box[1]
        for label in ("Buka Sedang diputar", "Beranda")
        for box in ui_bounds(label, attribute="content-desc", root=root)
    ]
    return min(tops, default=height)


def collect_while_scrolling(
    targets: list[tuple[str, str]], scrolls: int = 6, *, save_first: str | None = None,
) -> dict[str, tuple[tuple[int, int, int, int], ET.Element]]:
    width, height = screen_size()
    min_px = min_touch_px()
    found: dict[str, tuple[tuple[int, int, int, int], ET.Element]] = {}
    partial: dict[str, tuple[tuple[int, int, int, int], ET.Element]] = {}
    for step in range(scrolls + 1):
        root = ui_tree(save_as=save_first if step == 0 else None)
        if root is not None:
            for label, attr in targets:
                if label in found:
                    continue
                matches = find_interactive(label, attribute=attr, root=root)
                if not matches:
                    continue
                box, node = matches[0]
                partial[label] = (box, node)
                # Tunggu sampai elemen tidak terpotong di tepi atas layar atau tertutup bilah bawah yang
                # mengambang (Compose melaporkan hanya bagian yang tidak tertutup) sebelum mengunci ukurannya.
                near_vertical_edge = box[1] <= 80 or box[3] >= overlay_top(root, height) - 80
                if not near_vertical_edge or (box[3] - box[1]) >= min_px:
                    found[label] = (box, node)
        if len(found) == len(targets) or step == scrolls:
            break
        shell(f"input swipe {width // 2} {height * 3 // 4} {width // 2} {height // 4} 300")
        time.sleep(0.6)
    for label, entry in partial.items():
        found.setdefault(label, entry)
    return found


def scroll_to_top(swipes: int = 4) -> None:
    width, height = screen_size()
    for _ in range(swipes):
        shell(f"input swipe {width // 2} {height // 4} {width // 2} {height * 3 // 4} 250")
        time.sleep(0.35)


def check_now_playing_queue() -> None:
    if not check(open_now_playing() and tap_desc("Antrean"), "Panel Antrean di Sedang diputar dapat dibuka"):
        return

    row_label = f"{SECOND}, Senada, 12 detik"
    menu_label = f"Buka menu {SECOND}"
    collect_while_scrolling([(row_label, "content-desc"), (menu_label, "content-desc")], scrolls=3)
    tree = ui_tree(save_as="11-queue-ui.xml")
    rows = find_interactive(row_label, attribute="content-desc", root=tree) if tree is not None else []
    menus = find_interactive(menu_label, attribute="content-desc", root=tree) if tree is not None else []
    actions = [f"Putar {SECOND}", f"Jeda {SECOND}", f"Hapus {SECOND} dari antrean"]
    min_px = min_touch_px()
    if not check(
        rows and rows[0][1].get("clickable") == "true" and len(menus) == 1
        and all(x2 - x1 >= min_px and y2 - y1 >= min_px for (x1, y1, x2, y2), _ in rows + menus)
        and not any(find_interactive(label, attribute="content-desc", root=tree) for label in actions),
        "Baris Antrean dan satu tombol ⋯ dapat diketuk dengan area sentuh minimal 48 dp",
    ):
        return

    x1, y1, x2, y2 = rows[0][0]
    tap(((x1 + x2) // 2, (y1 + y2) // 2))
    playing = wait_session("PLAYING", SECOND, 5)
    marked = playing and wait_for(
        lambda: find_interactive(f"{row_label}, Sedang diputar", attribute="content-desc"), 5,
    )
    check(playing and marked, "Ketukan pada baris Antrean memutar lagu dan mengumumkan 'Sedang diputar'", session())
    tap(((x1 + x2) // 2, (y1 + y2) // 2))
    check(wait_session("PAUSED", SECOND, 5), "Ketukan pada lagu aktif di Antrean menjeda lagu", session())

    first_menu = f"Buka menu {FIRST}"
    if not check(tap_desc(first_menu), "Menu ⋯ pada baris pertama Antrean dapat dibuka"):
        return
    menu = ui_tree(save_as="11-queue-menu-ui.xml")
    remove_label = "Hapus dari antrean"
    remove = find_interactive(remove_label, root=menu) if menu is not None else []
    enqueue = find_interactive(QUEUE_ACTION, root=menu) if menu is not None else []
    if not check(
        remove and enqueue and remove[0][0][1] < enqueue[0][0][1],
        "Hapus dari antrean berada di atas aksi Tambah ke antrean pada menu ⋯",
    ):
        return
    removed = tap_text(remove_label, scrolls=0) and wait_for(
        lambda: (tree := ui_tree()) is not None
        and not find_interactive(first_menu, attribute="content-desc", root=tree)
        and find_interactive(menu_label, attribute="content-desc", root=tree),
        5,
    )
    check(removed, "Hapus dari antrean hanya menghapus baris yang dipilih")
    tap_desc("Tutup antrean")


def check_large_font_accessibility() -> None:
    width, height = screen_size()
    min_px = min_touch_px()
    shell("settings put system font_scale 2.0")
    time.sleep(2)
    shell(f"am start -W -n {ACTIVITY}")
    time.sleep(1.5)
    scroll_to_top(3)
    screenshot("06-large-font")

    menu_label = f"Buka menu {FIRST}"
    open_tab("Cari")
    scroll_to_top(3)
    cari_targets = [(menu_label, "content-desc")]
    cari_found = collect_while_scrolling(cari_targets, scrolls=7, save_first="06-large-font-ui.xml")
    queue_targets = queue_menu_targets(menu_label) if menu_label in cari_found else []
    if queue_targets:
        cari_found[QUEUE_ACTION] = queue_targets[0]
    menu_targets = [(label, "content-desc") for label in ("Acak: mati", "Ulangi: mati")]
    player_targets = [
        (label, "content-desc")
        for label in ("Tutup", "Menu lainnya", "Sebelumnya", "Berikutnya", "Timer tidur")
    ]
    player_found = {}
    if open_now_playing():
        if tap_desc("Menu lainnya"):
            player_found.update(collect_while_scrolling(menu_targets, scrolls=3))
            shell("input keyevent KEYCODE_BACK")
            time.sleep(0.6)
        player_found.update(collect_while_scrolling(player_targets, scrolls=4))
    shell("input keyevent KEYCODE_BACK")
    time.sleep(1)
    found = {**cari_found, **player_found}
    targets = cari_targets + [(QUEUE_ACTION, "text")] + player_targets + menu_targets
    cari_missing = [label for label, _ in targets if label not in found]
    cari_clipped = [
        f"{label}={box}"
        for label, (box, _) in found.items()
        if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
    ]
    check(
        not cari_missing and not cari_clipped,
        "Kontrol tab Cari dan Sedang diputar tidak terpotong dan minimal 48 dp pada skala font 200%",
        f"lebar {width}px, hilang {cari_missing}, terpotong/kekecilan {cari_clipped}",
    )

    settings_opened = open_tab("Profil") and (scroll_to_top(2) or True) and tap_profile_item("Tampilan")
    if not check(settings_opened, "Tampilan dapat dibuka dari Profil pada skala font 200%"):
        return

    screenshot("07-settings-large-font")
    visible_accents = [
        f"Warna aksen {label}" for label in ("Senada", "Mint", "Indigo", "Sky")
    ]
    appearance_targets = [
        ("Gelap", "text"),
        ("Terang", "text"),
        ("Ikuti sistem", "text"),
        ("Warna mengikuti sampul", "text"),
        ("Kurangi sudut sampul", "content-desc"),
        ("Tambah sudut sampul", "content-desc"),
        ("Latar pemutar dari sampul", "text"),
        ("Efek kaca", "text"),
        ("Pelan", "text"),
        ("Normal", "text"),
        ("Cepat", "text"),
        ("Mati", "text"),
    ]
    settings_targets = [
        ("Ikuti sistem", "text"),
        ("Ekspor backup", "text"),
        ("Impor backup", "text"),
        ("Putar lokal", "text"),
        ("Putar HTTPS", "text"),
    ]
    accent_targets = [(label, "content-desc") for label in visible_accents]
    settings_found = collect_while_scrolling(
        appearance_targets + accent_targets, scrolls=9, save_first="07-settings-ui.xml",
    )
    if back_to_profile() and tap_profile_item("Backup library"):
        settings_found.update(collect_while_scrolling(settings_targets[1:3]))
    if back_to_profile() and tap_profile_item("Pengembang"):
        settings_found.update(collect_while_scrolling(settings_targets[3:]))
    back_to_profile()
    settings_missing = [label for label, _ in settings_targets if label not in settings_found]
    radio_node = settings_found["Ikuti sistem"][1] if "Ikuti sistem" in settings_found else None
    radio_ok = radio_node is not None and (
        radio_node.get("checkable") == "true" or radio_node.get("selected") == "true"
    )
    follow_switch = settings_found["Warna mengikuti sampul"][1] if "Warna mengikuti sampul" in settings_found else None
    follow_ok = follow_switch is not None and follow_switch.get("checked") == "true"
    selected_accents = [
        label for label in visible_accents if label in settings_found
        and any(settings_found[label][1].get(attr) == "true" for attr in ("checked", "selected"))
    ]
    check(
        radio_ok and follow_ok and selected_accents == ["Warna aksen Senada"],
        "Pilihan tema, warna aksen, dan sakelar Warna mengikuti sampul di Tampilan mengumumkan statusnya",
        f"radio={radio_node.attrib if radio_node is not None else None}, "
        f"follow={follow_switch.attrib if follow_switch is not None else None}, "
        f"aksen dipilih={selected_accents}",
    )
    accent_missing = [label for label in visible_accents if label not in settings_found]
    accent_clipped = [
        f"{label}={box}"
        for label, (box, _) in settings_found.items()
        if label in visible_accents
        and (box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px)
    ]
    check(
        not accent_missing and not accent_clipped,
        "Bulatan warna aksen yang terlihat memiliki label dan minimal 48 dp pada skala font 200%",
        f"lebar {width}px, hilang {accent_missing}, terpotong/kekecilan {accent_clipped}",
    )
    accent_boxes = [settings_found[label][0] for label in visible_accents if label in settings_found]
    row_aligned = bool(accent_boxes) and all(abs(box[1] - accent_boxes[0][1]) <= 2 for box in accent_boxes)
    appearance_missing = [label for label, _ in appearance_targets if label not in settings_found]
    appearance_clipped = [
        f"{label}={settings_found[label][0]}"
        for label, _ in appearance_targets
        if label in settings_found
        and (
            settings_found[label][0][0] < 0
            or settings_found[label][0][2] > width
            or (settings_found[label][0][2] - settings_found[label][0][0]) < min_px
            or (settings_found[label][0][3] - settings_found[label][0][1]) < min_px
        )
    ]
    check(
        row_aligned and not appearance_missing and not appearance_clipped,
        "Semua kontrol halaman Tampilan sejajar, tidak terpotong, dan minimal 48 dp pada skala font 200%",
        f"lebar {width}px, sejajar={row_aligned}, hilang {appearance_missing}, terpotong/kekecilan {appearance_clipped}",
    )
    backup_clipped = [
        f"{label}={settings_found[label][0]}"
        for label in ("Ekspor backup", "Impor backup", "Putar lokal", "Putar HTTPS")
        if label in settings_found
        and (
            settings_found[label][0][0] < 0
            or settings_found[label][0][2] > width
            or (settings_found[label][0][2] - settings_found[label][0][0]) < min_px
            or (settings_found[label][0][3] - settings_found[label][0][1]) < min_px
        )
    ]
    check(
        not settings_missing and not backup_clipped,
        "Tombol Ekspor/Impor backup dan uji audio tidak terpotong dan minimal 48 dp pada skala font 200%",
        f"lebar {width}px, hilang {settings_missing}, terpotong/kekecilan {backup_clipped}",
    )

    if open_tab("Beranda"):
        scroll_to_top(2)
        home_tree = ui_tree(save_as="10b-home-large-font-ui.xml")
        home_avatar = find_interactive("Buka profil", attribute="content-desc", root=home_tree) if home_tree is not None else []
        home_play = hero_play_target(home_tree)
        home_view = find_interactive("Lihat lagu yang paling sering diputar", attribute="content-desc", root=home_tree) if home_tree is not None else []
        home_targets = [
            ("Buka profil", home_avatar[0][0] if home_avatar else None),
            ("Putar hero", home_play[1] if home_play else None),
            ("Lihat hero", home_view[0][0] if home_view else None),
        ]
        home_missing = [label for label, box in home_targets if box is None]
        home_clipped = [
            f"{label}={box}"
            for label, box in home_targets
            if box is not None and (box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px)
        ]
        check(
            not home_missing and not home_clipped,
            "Kontrol bar atas dan hero Beranda tidak terpotong dan minimal 48 dp pada skala font 200%",
            f"lebar {width}px, hilang {home_missing}, terpotong/kekecilan {home_clipped}",
        )

    if open_tab("Library"):
        scroll_to_top(3)
        screenshot("10-favorites-large-font")
        fav_targets = [
            ("Putar Favorit", "content-desc"),
            ("Putar acak Favorit", "content-desc"),
            ("Buka menu Favorit", "content-desc"),
        ]
        fav_found = collect_while_scrolling(fav_targets, scrolls=4, save_first="10-favorites-ui.xml")
        fav_missing = [label for label, _ in fav_targets if label not in fav_found]
        fav_clipped = [
            f"{label}={box}"
            for label, (box, _) in fav_found.items()
            if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
        ]
        check(
            not fav_missing and not fav_clipped,
            "Header koleksi Favorit tidak terpotong dan minimal 48 dp pada skala font 200%",
            f"lebar {width}px, hilang {fav_missing}, terpotong/kekecilan {fav_clipped}",
        )

        scroll_to_top(3)
        chip_boxes = ui_bounds("Favorit")
        chip_y = (chip_boxes[0][1] + chip_boxes[0][3]) // 2 if chip_boxes else None

        def select_chip(label: str) -> bool:
            boxes = ui_bounds(label)
            if (not boxes or boxes[0][2] >= width - 4) and chip_y is not None:
                shell(f"input swipe {width * 4 // 5} {chip_y} {width // 5} {chip_y} 300")
                time.sleep(0.5)
            return tap_text(label, scrolls=0)

        if select_chip("Playlist"):
            playlist_targets = [
                ("Playlist baru", "text"),
                ("Impor playlist", "text"),
                ("Buka playlist Pagi", "content-desc"),
                ("Buka menu playlist Pagi", "content-desc"),
            ]
            playlist_found = collect_while_scrolling(
                playlist_targets, scrolls=4, save_first="11-playlists-ui.xml",
            )
            playlist_missing = [label for label, _ in playlist_targets if label not in playlist_found]
            playlist_clipped = [
                f"{label}={box}"
                for label, (box, _) in playlist_found.items()
                if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
            ]
            check(
                not playlist_missing and not playlist_clipped,
                "Daftar playlist dan aksi header tidak terpotong dan minimal 48 dp pada skala font 200%",
                f"lebar {width}px, hilang {playlist_missing}, terpotong/kekecilan {playlist_clipped}",
            )

        scroll_to_top(3)
        if select_chip("Riwayat"):
            history_targets = [
                ("Putar Riwayat", "content-desc"),
                ("Putar acak Riwayat", "content-desc"),
            ]
            history_found = collect_while_scrolling(history_targets, scrolls=3)
            history_missing = [label for label, _ in history_targets if label not in history_found]
            history_clipped = [
                f"{label}={box}"
                for label, (box, _) in history_found.items()
                if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
            ]
            check(
                not history_missing and not history_clipped,
                "Header ringkas Riwayat tidak terpotong dan minimal 48 dp pada skala font 200%",
                f"lebar {width}px, hilang {history_missing}, terpotong/kekecilan {history_clipped}",
            )

        scroll_to_top(3)
        if select_chip("File impor"):
            imported_targets = [("Impor file audio", "text")]
            imported_found = collect_while_scrolling(imported_targets, scrolls=3)
            imported_play = ui_bounds("Putar File impor", attribute="content-desc")
            imported_missing = [label for label, _ in imported_targets if label not in imported_found]
            if not imported_play:
                imported_missing.append("Putar File impor")
            imported_clipped = [
                f"{label}={box}"
                for label, (box, _) in imported_found.items()
                if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
            ] + [
                f"Putar File impor={box}"
                for box in imported_play
                if box[0] < 0 or box[2] > width or (box[2] - box[0]) < min_px or (box[3] - box[1]) < min_px
            ]
            check(
                not imported_missing and not imported_clipped,
                "Header ringkas File impor dan aksinya tidak terpotong dan minimal 48 dp pada skala font 200%",
                f"lebar {width}px, hilang {imported_missing}, terpotong/kekecilan {imported_clipped}",
            )


def scenario() -> None:
    run("adb", "install", "-r", APK)
    shell("logcat -c")
    shell("cmd uimode night yes")
    # ANR aplikasi sistem di emulator CI tidak boleh menutupi Senada; crash Senada tetap terdeteksi lewat logcat.
    shell("settings put global hide_error_dialogs 1")
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")

    def search_tab_ready():
        dismiss_system_dialogs()
        return find_interactive("Cari", attribute="content-desc")

    opened = wait_for(search_tab_ready, 30, 1)
    if not check(opened and open_tab("Cari"), "Aplikasi terbuka dan tab Cari dapat dibuka"):
        screenshot("01-app")
        return
    screenshot("01-app")
    check_initial_accessibility()

    added = all(
        tap_desc(f"Buka menu {title}") and tap_text(QUEUE_ACTION, scrolls=0)
        for title in (FIRST, SECOND)
    )
    if added:
        check_search_keyboard()
    started = added and tap_desc("Putar")
    if not check(started, "Dua lagu ditambahkan ke antrean dan diputar dari mini player"):
        screenshot("02-ui")
        return

    playing = wait_session("PLAYING", FIRST, 15)
    if not check(playing, f"Sesi media memutar '{FIRST}'", playing or session()):
        return

    # Bekukan lagu contoh yang hanya 12 detik sebelum uiautomator mencari mini player dan tombol tutup.
    ready = pause_for_setup(FIRST)
    if ready and int(ready["position"]) > 1_000:
        shell("cmd media_session dispatch previous")
        ready = wait_session("PAUSED", FIRST, 5, max_ms=1_000)
    check_now_playing_swipes()
    if not check(open_now_playing(), "Mini player membuka Sedang diputar"):
        screenshot("02-ui")
        return

    if not check(ready and tap_desc("Putar"), "Kontrol Putar/Jeda di aplikasi ditemukan saat audio dijeda", ready or session()):
        return
    pause_point = wait_for(lambda: desc_point("Jeda"), 5) if wait_session("PLAYING", FIRST, 5, min_ms=1_500) else None
    if pause_point is not None:
        tap(pause_point)
        paused = wait_session("PAUSED", FIRST, 5)
    else:
        paused = None
    if not check(paused, "Tombol 'Jeda' di aplikasi menjeda audio", paused or session()):
        return

    screenshot("02-paused")
    screenshot("v2-60-nowplaying")
    if check(tap_desc("Lirik") and wait_for(lambda: ui_bounds("Lirik tidak tersedia"), 5),
             "Tombol Lirik membuka panel lirik untuk lagu contoh tanpa lirik"):
        screenshot("v2-61-nowplaying-lyrics")
        check(tap_desc("Tutup lirik"), "Panel lirik dapat ditutup")
    if check(tap_desc("Antrean") and wait_for(lambda: desc_point("Tutup antrean"), 5),
             "Tombol Antrean membuka panel antrean"):
        screenshot("v2-62-nowplaying-queue")
        check(tap_desc("Tutup antrean"), "Panel antrean dapat ditutup")
    paused_tree = ui_tree()
    paused_readable = paused_tree is not None and any(
        "Dijeda" in (node.get("text") or "") or "Dijeda" in (node.get("content-desc") or "")
        for node in paused_tree.iter("node")
    )
    check(paused_readable, "Status pemutaran 'Dijeda' terbaca di layar")
    notification = wait_for(media_notification, 10)
    check(notification, "Notifikasi media tampil", notification)
    open_shade("03-notification")

    paused_at = int(paused["position"])
    play_point = desc_point("Putar")
    if play_point is not None:
        tap(play_point)
    resumed = play_point is not None and wait_session("PLAYING", FIRST, 5)
    if not check(
        resumed and paused_at >= 1_000 and int(resumed["position"]) >= paused_at - 300,
        "Tombol 'Putar' di aplikasi melanjutkan dari posisi terakhir",
        f"dijeda di {paused_at} ms, lanjut di {resumed['position'] if resumed else '?'} ms",
    ):
        return

    # Tata letak masih sama. Gunakan posisi tombol Jeda yang sudah ditemukan dari content-desc
    # agar lagu tidak keburu berganti selama dump uiautomator kedua.
    tap(pause_point)
    before_seek = wait_session("PAUSED", FIRST, 5)
    if before_seek:
        # Entri pertama kembali ke awal tanpa memutar audio agar jarak geser selalu cukup.
        shell("cmd media_session dispatch previous")
        before_seek = wait_session("PAUSED", FIRST, 5, max_ms=1_000)
    if check(before_seek, "Entri pertama dijeda untuk uji slider", before_seek or session()):
        check_paused_seek(before_seek)

    # Jeda selama pencarian Berikutnya agar lagu pertama tidak berpindah otomatis.
    pause_for_setup(FIRST)
    check_app_navigation()

    # Tombol kembali Android menutup Sedang diputar tanpa keluar dari aplikasi.
    shell("input keyevent KEYCODE_BACK")
    back_closed = wait_for(now_playing_closed, 5)
    check(back_closed, "Tombol kembali menutup Sedang diputar dan mini player tetap terlihat")

    shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    shell("input keyevent KEYCODE_SLEEP")
    time.sleep(1.5)
    check(wakefulness() != "Awake", "Layar mati", wakefulness())
    check(foreground_service(), "PlaybackService menjadi foreground service saat aplikasi di latar belakang")
    still = session()
    check(still and still["state"] in ("PLAYING", "BUFFERING"), "Audio tetap diputar setelah HOME dan layar mati", still)

    advanced = wait_session("PLAYING", SECOND, 25)
    check(advanced, f"Lanjut otomatis ke '{SECOND}' saat layar mati", advanced or session())

    # Tombol media sistem: jalur yang sama dengan layar kunci, notifikasi, dan headset Bluetooth.
    shell("cmd media_session dispatch previous")
    check(wait_session("PLAYING", FIRST, 8), "Tombol 'sebelumnya' di awal lagu kembali ke entri pertama", session())
    shell("cmd media_session dispatch next")
    check(wait_session("PLAYING", SECOND, 8), "Tombol 'berikutnya' pindah ke entri kedua", session())
    shell("cmd media_session dispatch pause")
    check(wait_session("PAUSED", SECOND, 5), "Tombol 'jeda' menjeda audio", session())
    shell("cmd media_session dispatch play")
    check(wait_session("PLAYING", SECOND, 5), "Tombol 'putar' melanjutkan audio", session())

    shell("input keyevent KEYCODE_WAKEUP")
    time.sleep(2.5)
    screenshot("04-lockscreen")
    open_shade("05-shade-lockscreen")

    def finished():
        current = session()
        return current if current and current["state"] == "STOPPED" and SECOND in current["description"] else None

    ended = wait_for(finished, 25)
    check(ended, "Berhenti setelah entri terakhir selesai", ended or session())
    if ended:
        check_sleep_timer()
    check_https()
    check_listening_stats()
    check_home_shelves()
    check_appearance_v2()
    check_collection_header()
    check_browse_v2()
    check_large_font_accessibility()
    check_now_playing_queue()
    check_home_hero_play()
    check_browse_light_theme()


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / "logcat-live.txt").open("w", encoding="utf-8") as live_log:
        logcat = subprocess.Popen(["adb", "logcat", "-v", "brief", "*:W"], stdout=live_log, stderr=subprocess.STDOUT)
        try:
            return run_scenario()
        finally:
            if logcat.poll() is None:
                logcat.terminate()
            logcat.wait()


def run_scenario() -> int:
    try:
        scenario()
    except subprocess.CalledProcessError as error:
        check(False, "Skenario berjalan tanpa error", f"{error} {(error.stderr or '').strip()}")
    except Exception as error:  # noqa: BLE001 - laporkan lalu tetap simpan bukti
        check(False, "Skenario berjalan tanpa error", repr(error))
    finally:
        try:
            shell("settings put system font_scale 1.0")
        except Exception:  # noqa: BLE001
            pass
        try:
            crashes = shell("logcat -d -b crash")
            check(PKG not in crashes, "Tidak ada crash aplikasi", crashes.strip()[-2000:] if PKG in crashes else "")
            save("logcat.txt", shell("logcat -d"))
            save("media_session.txt", shell("dumpsys media_session"))
            save("notification.txt", shell("dumpsys notification --noredact"))
        except subprocess.CalledProcessError as error:
            check(False, "ADB tetap terhubung setelah skenario", f"{error} {(error.stderr or '').strip()}")

    report = ["| Hasil | Pemeriksaan | Detail |", "| --- | --- | --- |"]
    labels = {"PASS": "✅ Lulus", "FAIL": "❌ Gagal", "DILEWATI": "⚠️ Dilewati"}
    report += [f"| {labels[status]} | {name} | {detail.replace('|', '/')} |" for status, name, detail in results]
    save("report.md", "\n".join(report) + "\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as file:
            file.write("## Uji pemutaran latar belakang Android\n\n" + "\n".join(report) + "\n")
    return 0 if results and all(status != "FAIL" for status, _, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())

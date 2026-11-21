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
        state = re.search(r"PlaybackState \{state=([^,]+), position=(-?\d+)", block)
        if not state:
            continue
        code = re.search(r"\d+", state.group(1))
        description = re.search(r"description=(.*)", block)
        return {
            "state": STATES.get(code.group(0) if code else "", state.group(1)),
            "position": state.group(2),
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
    for _ in range(3):
        shell("uiautomator dump /sdcard/senada-ui.xml")
        xml = shell("cat /sdcard/senada-ui.xml")
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


def tap_desc(label: str, index: int = 0) -> bool:
    matches = find_interactive(label, attribute="content-desc")
    if len(matches) <= index:
        return False
    x1, y1, x2, y2 = matches[index][0]
    tap(((x1 + x2) // 2, (y1 + y2) // 2))
    time.sleep(0.8)
    return True


def open_tab(label: str) -> bool:
    """Tab di navigasi bawah yang mengambang punya content-desc sesuai labelnya."""
    return tap_desc(label)


def open_now_playing() -> bool:
    return tap_desc("Buka Sedang diputar") and bool(wait_for(lambda: find_interactive("Tutup", attribute="content-desc"), 5))


def open_shade(name: str) -> None:
    shell("cmd statusbar expand-notifications")
    time.sleep(2)
    screenshot(name)
    shell("cmd statusbar collapse")
    time.sleep(1)


def check_paused_seek(play_pause: tuple[int, int], before: dict[str, str]) -> None:
    name = "Slider saat dijeda berpindah ke tengah, tetap diam, lalu maju setelah Putar"
    sliders = ui_bounds("Posisi pemutaran", attribute="content-desc")
    if not check(sliders, "Slider 'Posisi pemutaran' terlihat"):
        return
    x1, y1, x2, y2 = sliders[0]
    y = (y1 + y2) // 2
    before_ms = int(before["position"])
    start_x = x1 + round((x2 - x1) * max(0.04, before_ms / 12_000))
    shell(f"input swipe {start_x} {y} {(x1 + x2) // 2} {y} 400")

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

    tap(play_pause)
    resumed = wait_session("PLAYING", FIRST, 4)
    resumed_ms = int(resumed["position"]) if resumed else -1
    advanced = resumed and wait_session("PLAYING", FIRST, 3, min_ms=resumed_ms + 500)
    check(
        stayed and sought_ms - 250 <= resumed_ms <= sought_ms + 1_500 and advanced,
        name,
        f"awal {before_ms} ms, seek {sought_ms} ms, tertahan {held}, lanjut {resumed_ms} ms, maju {advanced}",
    )


def check_app_navigation() -> None:
    second = tap_text("Berikutnya", scrolls=0) and wait_session("PLAYING", SECOND, 5)
    # Bekukan posisi entri kedua sebelum pencarian tombol lewat uiautomator.
    paused = second and pause_for_setup(SECOND)
    early = paused and int(paused["position"]) < 3_000
    check(second, "Tombol 'Berikutnya' di aplikasi memutar entri kedua", second or session())

    first = early and tap_text("Sebelumnya", scrolls=0) and wait_session("PLAYING", FIRST, 5)
    check(
        first,
        "Tombol 'Sebelumnya' di aplikasi sebelum 3 detik kembali ke entri pertama",
        f"posisi entri kedua: {paused or session()}, hasil: {first}",
    )


def check_https() -> None:
    name = "Tombol 'Putar HTTPS' memutar sampel internet (PLAYING)"
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")
    prior_errors = shell("logcat -d -s ExoPlayerImplInternal:E")
    open_tab("Pengaturan")
    if not tap_text("Putar HTTPS", scrolls=8):
        check(False, name, "Tombol 'Putar HTTPS' tidak ditemukan")
        return

    # Sampel HTTPS tampil sendiri di sesi media tanpa metadata artis dari antrean.
    playing = wait_session("PLAYING", f"{FIRST}, null, null", 45)
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


def min_touch_px() -> int:
    # Toleransi 1 px untuk pembulatan piksel pada kepadatan layar non-bulat (mis. 420 dpi).
    return int(48 * screen_density() / 160) - 1


def check_initial_accessibility() -> None:
    root = ui_tree(save_as="01-ui.xml")
    menu_label = f"Buka menu {FIRST}"
    menu_matches = find_interactive(menu_label, attribute="content-desc", root=root)
    check(menu_matches, f"Tombol menu ⋯ punya content-desc '{menu_label}'", [box for box, _ in menu_matches])

    min_px = min_touch_px()
    targets = [
        ("Tambah", "text", True),
        (menu_label, "content-desc", True),
        ("Beranda", "content-desc", True),
        ("Cari", "content-desc", True),
        ("Library", "content-desc", True),
        ("Pengaturan", "content-desc", True),
    ]
    missing = []
    undersized = []
    checked = 0
    for label, attr, required in targets:
        matches = find_interactive(label, attribute=attr, root=root)
        if not matches:
            if required:
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
                # Tunggu sampai elemen tidak terpotong di tepi atas/bawah layar sebelum mengunci ukurannya.
                near_vertical_edge = box[1] <= 80 or box[3] >= height - 80
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
    cari_targets = [
        (menu_label, "content-desc"),
        ("Tambah", "text"),
    ]
    cari_found = collect_while_scrolling(cari_targets, scrolls=7, save_first="06-large-font-ui.xml")
    player_targets = [("Sebelumnya", "text"), ("Berikutnya", "text"), ("Tutup", "content-desc")]
    player_found = collect_while_scrolling(player_targets, scrolls=4) if open_now_playing() else {}
    shell("input keyevent KEYCODE_BACK")
    time.sleep(1)
    found = {**cari_found, **player_found}
    targets = cari_targets + player_targets
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

    settings_opened = open_tab("Pengaturan")
    if not check(settings_opened, "Tab Pengaturan dapat dibuka pada skala font 200%"):
        return

    screenshot("07-settings-large-font")
    settings_targets = [
        ("Ikuti sistem", "text"),
        ("Warna mengikuti sampul", "text"),
        ("Ekspor backup", "text"),
        ("Impor backup", "text"),
        ("Putar lokal", "text"),
        ("Putar HTTPS", "text"),
    ]
    settings_found = collect_while_scrolling(settings_targets, scrolls=7, save_first="07-settings-ui.xml")
    settings_missing = [label for label, _ in settings_targets if label not in settings_found]
    radio_node = settings_found["Ikuti sistem"][1] if "Ikuti sistem" in settings_found else None
    switch_node = settings_found["Warna mengikuti sampul"][1] if "Warna mengikuti sampul" in settings_found else None
    radio_ok = radio_node is not None and (
        radio_node.get("checkable") == "true" or radio_node.get("selected") == "true"
    )
    switch_ok = switch_node is not None and switch_node.get("checkable") == "true"
    check(
        radio_ok and switch_ok,
        "Pilihan radio dan sakelar di Pengaturan mengumumkan statusnya",
        f"radio={radio_node.attrib if radio_node is not None else None}, "
        f"switch={switch_node.attrib if switch_node is not None else None}",
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


def scenario() -> None:
    run("adb", "install", "-r", APK)
    shell("logcat -c")
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")

    opened = wait_for(lambda: find_interactive("Cari", attribute="content-desc"), 30, 1)
    if not check(opened and open_tab("Cari"), "Aplikasi terbuka dan tab Cari dapat dibuka"):
        screenshot("01-app")
        return
    screenshot("01-app")
    check_initial_accessibility()

    added = tap_text("Tambah", 0) and tap_text("Tambah", 1)
    started = added and tap_desc("Putar")
    if not check(started, "Dua lagu ditambahkan ke antrean dan diputar dari mini player"):
        screenshot("02-ui")
        return

    playing = wait_session("PLAYING", FIRST, 15)
    if not check(playing, f"Sesi media memutar '{FIRST}'", playing or session()):
        return

    if not check(open_now_playing(), "Mini player membuka Sedang diputar"):
        screenshot("02-ui")
        return

    # Ambil koordinat saat dijeda; kontrol Putar dan Jeda menempati tombol yang sama.
    ready = pause_for_setup(FIRST)
    buttons = ui_points("Putar") if ready else []
    if not check(buttons, "Kontrol Putar/Jeda di aplikasi ditemukan saat audio dijeda", ready or session()):
        return
    play_pause = buttons[0]
    tap(play_pause)
    if wait_session("PLAYING", FIRST, 5, min_ms=1_500):
        tap(play_pause)
        paused = wait_session("PAUSED", FIRST, 5)
    else:
        paused = None
    if not check(paused, "Tombol 'Jeda' di aplikasi menjeda audio", paused or session()):
        return

    screenshot("02-paused")
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
    tap(play_pause)
    resumed = wait_session("PLAYING", FIRST, 5)
    if not check(
        resumed and paused_at >= 1_000 and int(resumed["position"]) >= paused_at - 300,
        "Tombol 'Putar' di aplikasi melanjutkan dari posisi terakhir",
        f"dijeda di {paused_at} ms, lanjut di {resumed['position'] if resumed else '?'} ms",
    ):
        return

    tap(play_pause)
    before_seek = wait_session("PAUSED", FIRST, 5)
    if before_seek:
        # Entri pertama kembali ke awal tanpa memutar audio agar jarak geser selalu cukup.
        shell("cmd media_session dispatch previous")
        before_seek = wait_session("PAUSED", FIRST, 5, max_ms=1_000)
    if check(before_seek, "Entri pertama dijeda untuk uji slider", before_seek or session()):
        check_paused_seek(play_pause, before_seek)

    # Jeda selama pencarian Berikutnya agar lagu pertama tidak berpindah otomatis.
    pause_for_setup(FIRST)
    check_app_navigation()

    # Tombol kembali Android menutup Sedang diputar tanpa keluar dari aplikasi.
    shell("input keyevent KEYCODE_BACK")
    back_closed = wait_for(
        lambda: not find_interactive("Tutup", attribute="content-desc")
        and find_interactive("Buka Sedang diputar", attribute="content-desc"),
        5,
    )
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
    check_https()
    check_large_font_accessibility()


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        scenario()
    except Exception as error:  # noqa: BLE001 - laporkan lalu tetap simpan bukti
        check(False, "Skenario berjalan tanpa error", repr(error))
    finally:
        try:
            shell("settings put system font_scale 1.0")
        except Exception:  # noqa: BLE001
            pass
        crashes = shell("logcat -d -b crash")
        check(PKG not in crashes, "Tidak ada crash aplikasi", crashes.strip()[-2000:] if PKG in crashes else "")
        save("logcat.txt", shell("logcat -d"))
        save("media_session.txt", shell("dumpsys media_session"))
        save("notification.txt", shell("dumpsys notification --noredact"))

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

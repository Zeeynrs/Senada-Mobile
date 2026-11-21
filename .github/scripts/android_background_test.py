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


def ui_bounds(label: str, *, attribute: str = "text") -> list[tuple[int, int, int, int]]:
    for _ in range(3):
        shell("uiautomator dump /sdcard/senada-ui.xml")
        xml = shell("cat /sdcard/senada-ui.xml")
        if "<?xml" in xml:
            break
        time.sleep(1)
    else:
        return []
    root = ET.fromstring(xml[xml.index("<?xml") :])
    bounds = []
    for node in root.iter("node"):
        if node.get(attribute) == label:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            bounds.append((x1, y1, x2, y2))
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
    if not tap_text("Putar HTTPS"):
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


def scenario() -> None:
    run("adb", "install", "-r", APK)
    shell("logcat -c")
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {ACTIVITY}")

    if not check(wait_for(lambda: ui_points("Senada"), 30, 1), "Aplikasi terbuka"):
        screenshot("01-app")
        return
    screenshot("01-app")

    added = tap_text("Tambah", 0) and tap_text("Tambah", 1)
    started = added and tap_text("Putar", 0)
    if not check(started, "Dua lagu ditambahkan ke antrean dan entri pertama diputar lewat UI"):
        screenshot("02-ui")
        return

    playing = wait_session("PLAYING", FIRST, 15)
    if not check(playing, f"Sesi media memutar '{FIRST}'", playing or session()):
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


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        scenario()
    except Exception as error:  # noqa: BLE001 - laporkan lalu tetap simpan bukti
        check(False, "Skenario berjalan tanpa error", repr(error))
    finally:
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

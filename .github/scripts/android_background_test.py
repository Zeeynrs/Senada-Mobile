#!/usr/bin/env python3
"""Uji pemutaran latar belakang Senada di emulator Android lewat adb.

Memasang APK debug, mengisi antrean lewat UI, lalu memeriksa sesi media, notifikasi,
foreground service, lanjut otomatis saat layar mati, dan tombol media. Screenshot,
dumpsys dan logcat disimpan di folder keluaran.
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

results: list[tuple[bool, str, str]] = []


def run(*args: str) -> str:
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def shell(command: str) -> str:
    return run("adb", "shell", command)


def save(name: str, text: str) -> None:
    (OUT / name).write_text(text, encoding="utf-8")


def screenshot(name: str) -> None:
    png = subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    (OUT / f"{name}.png").write_bytes(png)


def check(ok: object, name: str, detail: object = "") -> bool:
    passed = bool(ok)
    results.append((passed, name, str(detail or "")))
    print(f"{'PASS' if passed else 'FAIL'}: {name}" + (f" ({detail})" if detail else ""), flush=True)
    return passed


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


def wait_session(state: str, title: str, timeout: float) -> dict[str, str] | None:
    def matches():
        current = session()
        return current if current and current["state"] == state and title in current["description"] else None

    return wait_for(matches, timeout)


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


def ui_points(text: str) -> list[tuple[int, int]]:
    for _ in range(3):
        shell("uiautomator dump /sdcard/senada-ui.xml")
        xml = shell("cat /sdcard/senada-ui.xml")
        if "<?xml" in xml:
            break
        time.sleep(1)
    else:
        return []
    root = ET.fromstring(xml[xml.index("<?xml") :])
    points = []
    for node in root.iter("node"):
        if node.get("text") == text:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            points.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return sorted(points, key=lambda point: point[1])


def tap_text(text: str, index: int = 0, scrolls: int = 4) -> bool:
    width, height = screen_size()
    for _ in range(scrolls + 1):
        points = ui_points(text)
        if len(points) > index:
            x, y = points[index]
            shell(f"input tap {x} {y}")
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
    check(playing, f"Sesi media memutar '{FIRST}'", playing or session())
    screenshot("02-playing")
    notification = wait_for(media_notification, 10)
    check(notification, "Notifikasi media tampil", notification)
    open_shade("03-notification")

    # Tombol di layar aplikasi: jeda lalu putar harus melanjutkan dari posisi yang sama, bukan dari awal.
    time.sleep(1)
    paused = tap_text("Jeda", 0) and wait_session("PAUSED", FIRST, 5)
    check(paused, "Tombol 'Jeda' di aplikasi menjeda audio", paused or session())
    paused_at = int(paused["position"]) if paused else 0
    resumed = tap_text("Putar", 0) and wait_session("PLAYING", FIRST, 5)
    check(
        resumed and paused_at >= 1_000 and int(resumed["position"]) >= paused_at - 300,
        "Tombol 'Putar' di aplikasi melanjutkan dari posisi terakhir",
        f"dijeda di {paused_at} ms, lanjut di {resumed['position'] if resumed else '?'} ms",
    )

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
        return current if current and current["state"] != "PLAYING" and SECOND in current["description"] else None

    ended = wait_for(finished, 25)
    check(ended, "Berhenti setelah entri terakhir selesai", ended or session())


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
    report += [f"| {'✅' if ok else '❌'} | {name} | {detail.replace('|', '/')} |" for ok, name, detail in results]
    save("report.md", "\n".join(report) + "\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as file:
            file.write("## Uji pemutaran latar belakang Android\n\n" + "\n".join(report) + "\n")
    return 0 if results and all(ok for ok, _, _ in results) else 1


if __name__ == "__main__":
    sys.exit(main())

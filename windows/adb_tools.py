import os
import shutil
import subprocess
import time


def _adb_path() -> str:
    adb = shutil.which("adb")
    return adb or "adb"


def _run(args: list, timeout: int = 30) -> tuple[int, str, str]:
    try:
        proc = subprocess.run(
            [_adb_path()] + args,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        return proc.returncode, proc.stdout, proc.stderr
    except Exception as e:
        return -1, "", str(e)


def _run_bytes(args: list, timeout: int = 30) -> tuple[int, bytes, bytes]:
    try:
        proc = subprocess.run(
            [_adb_path()] + args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        return proc.returncode, proc.stdout, proc.stderr
    except Exception as e:
        return -1, b"", str(e).encode("utf-8", errors="replace")


def is_available() -> bool:
    code, _, _ = _run(["version"], timeout=5)
    return code == 0


def devices() -> list[dict]:
    code, out, _ = _run(["devices", "-l"], timeout=10)
    result = []
    if code != 0:
        return result
    for line in out.splitlines():
        if not line or line.startswith("List"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial = parts[0]
        state = parts[1]
        props = {}
        for item in parts[2:]:
            if ":" in item:
                k, v = item.split(":", 1)
                props[k] = v
        result.append({"serial": serial, "state": state, "props": props})
    return result


def shell(serial: str, command: str) -> tuple[int, str, str]:
    return _run(["-s", serial, "shell", command])


def input_key(serial: str, keycode: int) -> tuple[int, str, str]:
    return shell(serial, f"input keyevent {keycode}")


def input_text(serial: str, text: str) -> tuple[int, str, str]:
    escaped = text.replace("'", "'\\''")
    return shell(serial, f"input text '{escaped}'")


def input_tap(serial: str, x: int, y: int) -> tuple[int, str, str]:
    return shell(serial, f"input tap {x} {y}")


def input_swipe(serial: str, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> tuple[int, str, str]:
    return shell(serial, f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")


def screenshot(serial: str, dest_path: str) -> tuple[int, str]:
    code, out, err = _run_bytes(["-s", serial, "exec-out", "screencap", "-p"], timeout=15)
    if code == 0 and out:
        with open(dest_path, "wb") as f:
            f.write(out)
        return 0, dest_path
    return code, err.decode("utf-8", errors="replace") if isinstance(err, bytes) else err


def get_screen_size(serial: str) -> tuple[int, int]:
    code, out, _ = shell(serial, "wm size")
    if code != 0:
        return 1080, 1920
    for line in out.splitlines():
        if "Physical size:" in line:
            size = line.split(":")[-1].strip()
            w, h = size.split("x")
            return int(w), int(h)
    return 1080, 1920


def connect_wireless(ip: str, port: int = 5555) -> tuple[int, str, str]:
    return _run(["connect", f"{ip}:{port}"], timeout=10)


KEYCODES = {
    "HOME": 3,
    "BACK": 4,
    "RECENT": 187,
    "POWER": 26,
    "VOLUME_UP": 24,
    "VOLUME_DOWN": 25,
    "ENTER": 66,
    "TAB": 61,
}

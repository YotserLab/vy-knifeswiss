import os
import shutil
import time
import urllib.request


def _safe_name(name: str) -> str:
    name = os.path.basename(name).strip()
    forbidden = '<>:"/\\|?*\x00-\x1f'
    return "".join(c for c in name if c not in forbidden) or "arquivo"


def download_file(url: str, dest_dir: str) -> str:
    os.makedirs(dest_dir, exist_ok=True)
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    temp_path = os.path.join(dest_dir, f"downloading_{timestamp}_{int(time.time() * 1000)}")
    final_name = None
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "VYChat/1.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            disposition = resp.headers.get("Content-Disposition", "")
            if "filename=" in disposition:
                final_name = disposition.split("filename=")[-1].strip('"').strip()
            with open(temp_path, "wb") as f:
                shutil.copyfileobj(resp, f)
    except Exception as e:
        os.remove(temp_path) if os.path.exists(temp_path) else None
        raise e

    if not final_name:
        final_name = os.path.basename(url.split("/")[-1].split("?")[0]) or f"recebido_{timestamp}"
    final_name = _safe_name(final_name)
    final_path = os.path.join(dest_dir, f"{timestamp}_{final_name}")
    counter = 1
    base, ext = os.path.splitext(final_path)
    while os.path.exists(final_path):
        final_path = f"{base}_{counter}{ext}"
        counter += 1
    os.rename(temp_path, final_path)
    return final_path

#!/usr/bin/env python3
"""Sign and pack a VY Mini-App package (.vyp) with full SHA-256 asset integrity."""
import argparse
import base64
import hashlib
import json
import os
import zipfile
from pathlib import Path


def compute_sha256(file_path: Path) -> str:
    h = hashlib.sha256()
    h.update(file_path.read_bytes())
    return h.hexdigest()


def sign_package(src_dir: Path, private_key_path: Path, output_path: Path):
    manifest_path = src_dir / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"manifest.json not found in {src_dir}")

    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding
    except ImportError:
        raise RuntimeError("Install 'cryptography' package: pip install cryptography")

    # 1. Read existing manifest
    manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))

    # 2. Compute SHA-256 for all files in src_dir (except manifest.json and signature.txt)
    file_hashes = {}
    for root, _, files in os.walk(src_dir):
        for file in sorted(files):
            if file in ("manifest.json", "signature.txt"):
                continue
            file_path = Path(root) / file
            rel_name = file_path.relative_to(src_dir).as_posix()
            file_hashes[rel_name] = compute_sha256(file_path)

    manifest_data["fileHashes"] = file_hashes

    # Save manifest with normalized json formatting
    manifest_bytes = json.dumps(manifest_data, indent=2, sort_keys=True, ensure_ascii=False).encode("utf-8")
    manifest_path.write_bytes(manifest_bytes)
    print(f"Updated manifest with {len(file_hashes)} file hashes.")

    # 3. RSA-SHA256 signature of manifest.json
    with open(private_key_path, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)

    signature = private_key.sign(
        manifest_bytes,
        padding.PKCS1v15(),
        hashes.SHA256(),
    )

    signature_path = src_dir / "signature.txt"
    signature_path.write_text(base64.b64encode(signature).decode("ascii"), encoding="utf-8")

    # 4. Pack into .vyp zip file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(src_dir):
            for file in sorted(files):
                file_path = Path(root) / file
                arc_name = file_path.relative_to(src_dir).as_posix()
                zf.write(file_path, arc_name)

    print(f"Signed package successfully created: {output_path}")
    print(f"Files included: {list(file_hashes.keys())}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pack and sign a VY Mini-App with maximum security")
    parser.add_argument("--src", default="src", help="Source directory containing manifest.json and assets")
    parser.add_argument("--key", default="miniapp_private.pem", help="RSA private key PEM file")
    parser.add_argument("--out", default="out/HelloMini.vyp", help="Output .vyp file")
    args = parser.parse_args()
    sign_package(Path(args.src), Path(args.key), Path(args.out))

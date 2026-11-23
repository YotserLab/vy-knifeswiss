#!/usr/bin/env python3
import os
import json
import zipfile
import hashlib
import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PLUGINS_SRC_DIR = os.path.join(SCRIPT_DIR, "plugins")
OUTPUT_JSON = os.path.join(SCRIPT_DIR, "plugins.json")
DOWNLOADS_DIR = os.path.join(SCRIPT_DIR, "downloads")
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
APK_SOURCE = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
TARGET_APK = os.path.join(DOWNLOADS_DIR, "vyknifeswiss-latest.apk")

def sha256sum(filename):
    h = hashlib.sha256()
    with open(filename, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def sync_apk():
    os.makedirs(DOWNLOADS_DIR, exist_ok=True)
    if os.path.exists(APK_SOURCE):
        print(f"Sincronizando APK de: {APK_SOURCE} -> {TARGET_APK}")
        shutil.copy2(APK_SOURCE, TARGET_APK)
        checksum = sha256sum(TARGET_APK)
        sha_file = f"{TARGET_APK}.sha256"
        with open(sha_file, "w") as f:
            f.write(f"{checksum}  vyknifeswiss-latest.apk\n")
        print(f"APK atualizado com sucesso!")
        print(f"Tamanho: {os.path.getsize(TARGET_APK) / (1024*1024):.2f} MB")
        print(f"SHA-256: {checksum}")
    elif os.path.exists(TARGET_APK):
        checksum = sha256sum(TARGET_APK)
        print(f"APK existente em {TARGET_APK}: {checksum}")
    else:
        print("Aviso: Nenhum APK encontrado para sincronizar. Execute './gradlew assembleDebug' primeiro.")

def package_plugins():
    catalog = []
    
    if not os.path.exists(PLUGINS_SRC_DIR):
        print(f"Diretório de plugins não encontrado: {PLUGINS_SRC_DIR}")
        return

    for item in sorted(os.listdir(PLUGINS_SRC_DIR)):
        item_path = os.path.join(PLUGINS_SRC_DIR, item)
        if not os.path.isdir(item_path):
            continue
            
        manifest_file = os.path.join(item_path, "plugin.json")
        if not os.path.exists(manifest_file):
            continue
            
        with open(manifest_file, "r", encoding="utf-8") as f:
            manifest = json.load(f)
            
        plugin_id = manifest.get("id", item)
        zip_filename = f"{plugin_id}.zip"
        zip_path = os.path.join(PLUGINS_SRC_DIR, zip_filename)
        
        print(f"Empacotando plugin: {manifest.get('name')} ({plugin_id}) -> {zip_filename}")
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
            for root, _, files in os.walk(item_path):
                for file in files:
                    file_path = os.path.join(root, file)
                    arcname = os.path.relpath(file_path, item_path)
                    z.write(file_path, arcname)
                    
        file_size = os.path.getsize(zip_path)
        manifest["fileSize"] = file_size
        manifest["downloadUrl"] = f"https://knifeswiss.yotserlab.com/plugins/{zip_filename}"
        
        catalog.append(manifest)
        
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        
    print(f"\n[SUCESSO] Catálogo gerado com {len(catalog)} plugins:")
    for p in catalog:
        print(f" - {p['name']} v{p['version']} ({p['fileSize']} bytes)")
    print(f"Arquivo gerado: {OUTPUT_JSON}")

if __name__ == "__main__":
    sync_apk()
    print("-" * 50)
    package_plugins()

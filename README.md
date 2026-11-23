# VYKnifeSwiss (Network Toolbox)

Welcome to **VYKnifeSwiss**! This is a Swiss-army-knife app for Android designed for local network management and fast, secure file sharing. This is an alpha project, under development — my first Android project with Kotlin and Jetpack Compose, built with AI assistance.

## 🛠 Main Features

### 💬 Local Communication
* **Offline Local Chat:** P2P chat on the same Wi-Fi network — no internet, no server. Peers are discovered via UDP broadcast (with automatic subnet sweep fallback), messages go over TCP, voice calls over UDP. History is saved locally (AES-256-GCM).
* **Mesh Networking:** Store-and-forward routing with hop TTL, coordinator election, and delivery to offline peers.
* **SMS Manager:** Full SMS/MMS client when set as the default SMS app (RoleManager).
* **Floating Chat Bubble:** Chat heads-style overlay on top of other apps.

### 📁 Files & Sharing
* **Fast File Transfer (DroidLink):** Encrypted device-to-device transfer (PBKDF2 + AES-GCM, HMAC integrity) for files up to 64 MiB per frame.
* **Advanced File Manager:** Local and remote browsing with tabs, search, filters and sort — share-menu integration.
* **Remote Protocols:** FTP, FTPS, SFTP (JSch), WebDAV and SMB clients.
* **Built-in Servers:** FTP server on :2221 (constant-time auth) and WebDAV server on :8080 (Basic auth) — turn your phone into a network drive.
* **Cloud Backup & Sync:** Google Drive via OAuth, plus manual backup/sync.
* **APK Extractor:** Pull APKs from installed apps.

### 🌐 Network Tools
* **Subnet Scanner:** Discover devices on your /24 with OUI vendor lookup.
* **Port Scanner:** Check open ports on any host.
* **Ping & Speed Test:** Latency checks and throughput measurement.
* **Wi-Fi Monitor & Network Info:** Live signal monitoring, connection details and IP info.
* **Built-in Terminal:** Run network commands in a Termux-style shell.

### 🎬 Media
* **Media Player & Manager:** Play audio/video, playlists, and a library view powered by MediaStore.
* **Cast to TV:** Stream to Chromecast, DLNA/UPnP and Roku devices with remote-control sheet (play/pause/seek/volume).
* **Recorders:** Audio recorder (m4a), screen recorder (MediaProjection) and offline speech transcription.
* **Equalizer:** Built-in audio effects for the player.

### 🔐 Security & Vault
* **Password Vault:** SQLCipher-encrypted Room database unlocked by biometrics.
* **TOTP Authenticator:** RFC 6238 codes (SHA-1/256/512, 6–8 digits) via manual `otpauth://` entry or QR scan.
* **Encrypted Backup:** Export/import the whole vault with a master PIN (AES-256).
* **Device Security Check:** Screen lock, biometrics, patch level, root and accessibility audit.

### 🔧 Device & Diagnostics
* **Device Info:** 14 telemetry tabs — live CPU per-core frequencies, sensors with real-time X/Y/Z values, battery, memory, network and more — plus a hardware test suite (touchscreen, flashlight, speakers, mic, vibration…).
* **OBD-II Scanner:** ELM327 over Bluetooth or Wi-Fi — live polling, DTC read/clear, trip computer, CSV/PDF export.
* **Sensors Toolbox:** Compass and bubble level.
* **App Tools:** Activity launcher, cache settings shortcut, anti-kill (battery optimization) guide.

### 📄 Documents & Productivity
* **Viewers & Editors:** PDF viewer, EPUB reader, text editor, whiteboard.
* **Notes & Kanban:** Notes with folders, drag-and-drop kanban board, shopping lists.
* **Calculators:** HP-48G-style RPN, price-per-kg, and calorie counter with a local food table.
* **Alarms & Calendar:** Smart alarms and calendar-based scheduling.

### 🔌 Extensibility
* **MiniApp Plugin System:** WebView-based plugins (`.vyp` packages) with a JS bridge, permissions and a bundled catalog (games, translator…).
* **QR & Barcode:** ZXing generator + ML Kit scanner.
* **Global Search:** Search across files from anywhere.
* **Deep Links:** `vyknifeswiss://tool/<id>` launches any tool; share intents route to the right screen.

### ⚙️ Platform
* **Dashboard & History:** Home dashboard with per-tool settings and usage history.
* **About & Dev Settings:** Version info, developer options, per-module toggles.

## 🌐 Localization

The app is fully internationalized (i18n) via Android resources:
* **English** — default locale (`values/strings.xml`)
* **Portuguese (Brazil)** — `values-pt-rBR/strings.xml`

The UI language follows the device locale automatically.

## 🚀 How to Build and Run

1. Clone the repository.
2. Open in **Android Studio**.
3. Sync Gradle.
4. (Optional) For a fast terminal build (Linux/Mac):
   ```bash
   bash scripts/instalar_apk.sh
   ```
   *(The automated build script increments the version and installs on the Android device connected via ADB).*

## 📌 Requirements
* Android 7.0 (API 24) or higher.
* Network and Storage permissions (requested at runtime).

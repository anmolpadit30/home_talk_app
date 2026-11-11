# HomeTalk

Android Wi‑Fi walkie-talkie for a household. Phones on the same home network talk to each other with push-to-talk.

- No internet required
- No cloud or accounts
- Audio stays on your local Wi‑Fi (Opus over UDP)
- Works in the background and on a locked screen via a foreground service

**Current version:** `0.5.0-phase5`  
**Package:** `com.hometalk.app`  
**Min SDK:** 26 · **Target / Compile SDK:** 36

Progress notes and known limits: [DEVELOPMENT.md](DEVELOPMENT.md)

---

## Features

### Core
- Push-to-talk (hold Talk in the app)
- Half-duplex channel (one speaker at a time)
- Live Opus voice between paired phones on the same LAN
- Loudspeaker playback by default
- Mute / unmute speaker in-app

### Discovery & pairing
- Automatic discovery on home Wi‑Fi (NSD / mDNS)
- Pair with PIN or QR
- Manual peer IP for tricky multi-router homes
- Device list shows **Online**, **Unreachable**, or **Offline**

### Background & notification
- Foreground service keeps listening while minimized or screen-locked
- Persistent notification with:
  - **Talk** / **Stop** (tap to start / stop transmitting)
  - **Mute** / **Unmute**
- Settings toggle: keep listening in the background

### Reliability & battery (Phase 5)
- Reconnect / peer probe burst after Wi‑Fi or local IP changes
- If Wi‑Fi is off for **10 minutes**, the listener sleeps to save battery
- When Wi‑Fi returns, listening starts again (while the process is alive, or next app open)

### Not included yet
- Quick Settings tile
- Encrypted audio
- Auto-start on phone boot without opening the app once

---

## Requirements

### On your PC
- Windows, macOS, or Linux
- **JDK 17**
- **Android SDK** (platform-tools + SDK 36 recommended)
- Android Studio **or** command-line Gradle (wrapper included)

### On phones
- Android 8.0+ (API 26+)
- Same home Wi‑Fi (or second router in **Access Point / bridge** mode)
- Microphone + notifications permissions

---

## Project setup

### 1. Clone the repo

```bash
git clone <your-repo-url> home_talky
cd home_talky
```

### 2. Point Gradle at your Android SDK

Create or edit `local.properties` in the project root (do not commit secrets):

**Windows**

```properties
sdk.dir=E\:\\android_sdks
```

**macOS / Linux**

```properties
sdk.dir=/Users/YOU/Library/Android/sdk
```

Use your real SDK path.

### 3. Open in Android Studio (optional)

1. **File → Open** → select the `home_talky` folder
2. Let Gradle sync finish
3. Select a device / emulator, press **Run**

---

## Build & run

From the project root:

### Windows (PowerShell)

```powershell
.\gradlew.bat assembleDebug
```

### macOS / Linux

```bash
./gradlew assembleDebug
```

### Install & launch on a connected device

```bash
# List devices
adb devices

# Install debug APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Or let Gradle install + launch
.\gradlew.bat installDebug
adb shell am start -n com.hometalk.app/.MainActivity
```

On macOS/Linux use forward slashes:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug
```

### Wireless debugging (optional)

On the phone: **Developer options → Wireless debugging**

1. **Pair device with pairing code** → note pairing IP:port + 6-digit code  
2. Note the **connection** IP:port on the main Wireless debugging screen  

```bash
adb pair <pairing-ip>:<pairing-port>
# enter the 6-digit code when prompted

adb connect <connection-ip>:<connection-port>
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `adb connect` with the **connection** port, not the pairing port. If you get “connection refused”, toggle Wireless debugging off/on and use the new ports.

---

## Create APK files

### Debug APK (daily testing)

```bash
./gradlew assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (unsigned / local)

```bash
./gradlew assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

For Play Store / sideload on locked-down devices you must **sign** the release APK (Android Studio **Build → Generate Signed Bundle / APK**, or `apksigner` / Gradle signingConfig). Debug APKs are fine for family testing on developer-enabled phones.

### Install the APK on every connected device

**PowerShell**

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$devices = & $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "\s+")[0] }
foreach ($d in $devices) {
  Write-Host "Installing on $d"
  & $adb -s $d install -r $apk
}
```

---

## Unit tests

```bash
./gradlew testDebugUnitTest
```

Runs JVM unit tests (protocol, arbitration, preferences codecs, defaults, etc.).

View HTML reports under:

```text
app/build/reports/tests/testDebugUnitTest/index.html
```

---

## Manual test plan (two phones)

1. Install the same APK on both phones.
2. Open HomeTalk; allow **microphone** and **notifications**.
3. Confirm both are on the **same home Wi‑Fi** (or AP/bridge second router).
4. On phone A open **Family**, start pairing (PIN/QR).
5. On phone B pair with A.
6. Hold **Talk** on A → hear audio on B; release → stop.
7. Hold **Talk** on B → hear audio on A.
8. Minimize or lock B → Talk from A should still play on B.
9. On B’s notification: tap **Talk**, speak, tap **Stop**; try **Mute** / **Unmute**.
10. Turn Wi‑Fi off on one phone → listening stops; after ~10 minutes the service should sleep; turn Wi‑Fi back on → listening resumes (or open the app once).

### Logs

```bash
adb logcat -s HomeTalk
```

---

## Network tips (multi-router homes)

HomeTalk needs phones to reach each other by local IP.

- Prefer one SSID / one subnet.
- If you use a second router, set it to **Access Point (AP)** or **bridge** mode (not a second NAT router).
- Otherwise add the other phone’s IP under **Settings → Manual peer addresses**.

---

## Project layout (high level)

```text
app/src/main/java/com/hometalk/app/
  presentation/     # Compose UI (Home, Family, Settings)
  domain/           # Models, use cases, repository interfaces
  data/             # DataStore, NSD discovery, TCP/UDP networking
  audio/            # Capture, Opus, playback, talk session
  service/          # Foreground WalkieTalkieService + notification
```

Stack: Kotlin, Jetpack Compose, Hilt, DataStore, NSD, TCP control, UDP Opus (Concentus).

---

## Troubleshooting

| Problem | What to try |
| --- | --- |
| Gradle cannot find SDK | Set `sdk.dir` in `local.properties` |
| `adb devices` empty | Enable USB/wireless debugging; authorize the PC |
| `adb connect` refused (10061) | Toggle Wireless debugging; use fresh **connection** port |
| Phones don’t discover each other | Same Wi‑Fi/subnet; disable AP isolation; try manual peer IP |
| No audio | Mic permission; both paired & online; check logcat `HomeTalk` |
| Notification controls missing | Grant notifications; open the app once so the service can start |
| Second router breaks Talk | Put 2nd router in AP/bridge mode |

---

## License / privacy

HomeTalk is designed for private home use. Voice traffic is intended to stay on your LAN only. Review local laws before recording or monitoring others.

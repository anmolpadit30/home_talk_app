# HomeTalk development log

Private household Wi-Fi walkie-talkie for Android. No internet, cloud, or accounts.

## Current milestone

**Phase 5 — Reliability + OEM device fixes**

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (`0.5.5-realme-keepalive`).

### What works now

- Phase 1–5: discovery, pairing, Opus PTT, FGS notification Talk/Stop/Mute, Wi‑Fi sleep/resume.
- **Moto E40:** pairing PIN/QR no longer crashes (API 33 `URLEncoder(Charset)` removed).
- **Moto Edge / receive audio:** larger playback buffer, AudioTrack recovery, avoid per-frame speaker re-route (was causing silence/distortion).
- **Realme / long lock screen:** CPU wake lock + Wi‑Fi lock while the FGS runs; Settings → **Allow unrestricted battery**.

### Important limits

- Open the app at least once (mic + notifications) so the typed foreground service can start.
- On aggressive OEMs, disable battery optimization for HomeTalk (Settings in-app button).
- Dual-router NAT still needs AP/bridge mode or manual peer IPs.

### What does not work yet

- Quick Settings tile (optional).
- Encrypted audio transport.
- Boot-time auto-start without opening the app.

## How to verify device fixes

1. **Moto E40:** Family → Show pairing PIN — PIN + QR should appear without closing.
2. **Moto Edge 60 Fusion:** pair, Talk from another phone — clear audio on Edge receive.
3. **Realme 13 Pro+:** allow unrestricted battery, lock screen 10+ minutes, Talk from another phone — should still hear audio.

Logs: `adb logcat -s HomeTalk`

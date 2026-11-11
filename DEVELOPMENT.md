# HomeTalk development log

Private household Wi-Fi walkie-talkie for Android. No internet, cloud, or accounts.

## Current milestone

**Phase 5 — Reliability (implemented in this repo)**

Build status: debug APK `app/build/outputs/apk/debug/app-debug.apk` (`0.5.0-phase5`).

### What works now

- Phase 1–4: UI, discovery, pairing, live Opus PTT, foreground service, notification Talk/Stop/Mute.
- Wi‑Fi loss: listening stops immediately; after **10 minutes** offline the foreground service sleeps to save battery.
- Wi‑Fi returns: discovery/audio resume and the foreground service starts again (while the app process is alive, or next time the app is opened).
- Local IP change: NSD republish + peer probe burst so audio targets fresh addresses.
- Paired phones that cannot be reached show as **Unreachable**.

### Important limits

- Open the app at least once (mic + notifications) so the typed foreground service can start.
- If Android kills the process after sleep, auto-resume needs the app opened once (or Wi‑Fi reconnect while the process is still alive).
- Dual-router NAT still requires AP/bridge mode or manual peer IPs.

### What does not work yet

- Quick Settings tile (optional).
- Encrypted audio transport.
- Boot-time auto-start without opening the app.

## How to test Phase 5

1. Install `0.5.0-phase5` and open HomeTalk on both phones.
2. Confirm Talk works on the same home Wi‑Fi.
3. Turn Wi‑Fi off on one phone: Talk should stop; notification shows sleep countdown.
4. Leave Wi‑Fi off for 10+ minutes: notification/service should stop (sleep).
5. Turn Wi‑Fi back on: listening and notification should return (or open the app once if the process was killed).
6. Optional: switch AP/SSID on one phone and confirm peers become reachable again after a short probe burst.

Logs: `adb logcat -s HomeTalk`

## Next ideas

- Quick Settings tile.
- Stronger OEM battery-optimization guidance in Settings.
- Encrypted audio / boot auto-start.

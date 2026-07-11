#### I wanted to see how Git push commands worked using Claude Code. It's cool how it can just do that. Anyways, Claude is a contributer now for that reason lol

# GridDrop

**100% offline file transfer between an Android phone and an iPhone with nothing to install on the iPhone.**

The Android phone creates a private local Wi-Fi network and serves a tiny web page, and the iPhone joins it and transfers files straight from Safari.

No internet, no accounts, no cloud, no AirDrop, no receiver app.

---

## How it works for the user

A grandparent-proof wizard with one step per screen:

1. **Choose** - "Send files" or "Receive files".
2. **Connecting** - the phone spins up a private `LocalOnlyHotspot`.
3. **Step 1: Join the Wi-Fi** - the iPhone scans a QR to join the network (name + password also shown in large text for manual entry).
4. **Step 2: Open the page** - the iPhone scans a second QR that opens `http://<gateway-ip>:8080` on the iPhone's default browser.
5. **Step 3: Transfer**
   - *Send:* pick files on Android -> they appear on the iPhone's page to download.
   - *Receive:* the iPhone picks files -> they're saved to the Android phone's Downloads folder.

There is an **"iPhone can't see the network?"** link that opens plain manual instructions (join the network by name/password by hand, and also switch the band to 5 or 2.4 GHz). The technical reason for this is that prior to Android 16, the function that starts the local grid network on the Android side -- `startLocalOnlyHotspot()` -- will default to the highest performance radio possible. If the CPU decides to choose the 6 GHz band to start the grid network, iPhones prior to the 15 Pro cannot physically see the network whatsoever. Android 16 fixes this issue by adding a `startLocalOnlyHotspotWithConfiguration()` function so the network can be configured to use 5 GHz for a wider range of compatibility. That newer API is still unstable in practice, and the in-app 5 GHz switch can fail or even crash on some current Android 16 builds, so the manual join-by-hand steps remain the dependable fallback for now (a future Android update will hopefully settle it down).

There is also a **"Stats for nerds"** panel, reachable from the home and transfer screens, that shows live hotspot and server details, running byte counters, and a timestamped event log you can copy to the clipboard. It is meant for diagnosing issues on the phone itself, without connecting it to a computer for logcat.

---

## Build

Requires **Android SDK Platform 36 (Android 16)** to compile the apk. The app targets API 36 (Android 16). However, this app is known to work (tested) on Android 9 using the compiled apk using the method below.

**There's a release in the releases tab**. Otherwise...

**Command line** (self-contained toolchain, no root. This repo was built this way):

```bash
./build.sh            # sets JAVA_HOME/ANDROID_HOME, runs assembleRelease, copies the APK out
```

`build.sh` produces `griddrop-slim.apk` in the project root: a shrunk (R8 + resource shrinking) build that installs much faster on old phones, debug-signed so it still sideloads with no keystore. Install it on a physical phone (the emulator has no Wi-Fi radio for the hotspot, obviously lol) using adb or however else you want:

```bash
adb install -r griddrop-slim.apk
```

`minSdk 26`, `targetSdk/compileSdk 36`.

---

## Architecture

### Hotspot (`hotspot/HotspotController.kt`)
When you start a transfer, the phone creates its own private Wi-Fi network for the iPhone to join. In practice this gives the two phones a direct, internet-free link that nothing else can get onto. It relies on Android's `WifiManager.startLocalOnlyHotspot()`, which has been available since Android 8.0.

By default the app uses this plain call and lets the phone pick the band, which gives the best speed. The catch is that some phones choose a 6 GHz channel that iPhones older than the 15 Pro cannot see. On Android 16 and newer, the troubleshooting screen offers an experimental one-tap "Switch to a more compatible network" button that tries to restart the hotspot pinned to 5 GHz, which those older
iPhones can join. It uses Android 16's `startLocalOnlyHotspotWithConfiguration()` together with a `SoftApConfiguration`, but that API is currently unstable and the switch can fail or crash on some devices, so treat it as a bonus rather than something to rely on. Whatever the Android version, the same screen also gives plain written steps for joining the network by hand, which is the dependable path.

Bringing a hotspot up can be touchy if someone taps quickly or backs out partway, so the controller ignores a second start while one is still pending and quietly closes any hotspot that comes up after the user has already left. This is what keeps the app from tripping Android's "caller already has an active LocalOnlyHotspot request" error.

### Server (`net/GridDropServer.kt`, Ktor CIO)
The phone runs a small web server that hosts the page the iPhone opens and moves the actual file data. This is what lets the iPhone send and receive files straight from Safari with nothing installed. It is built on a lightweight embedded Ktor server using the CIO engine.

Transfers can survive a flaky connection. If a download is interrupted, the iPhone can continue from where it stopped rather than starting over, because the server answers HTTP range requests with `206 Partial Content` and seeks to the requested byte. Uploads resume the same way through a few small endpoints that track how many bytes have safely arrived (`init`, `chunk`, `status`, `finish`). The
server lives inside a foreground service (`GridDropService`) so a transfer keeps running even if you switch away from the app.

### Storage (`net/TransferRepository.kt`)
Files the iPhone sends land directly in the phone's public Downloads folder, so they appear right away in Files and Gallery. While an upload is still coming in it is written to a private temporary file so it can be resumed safely, and it is only moved into Downloads once it is complete. On Android 10 and newer this goes through `MediaStore.Downloads`, and on older versions it falls back to a direct write
using the legacy storage permission.

### Live updates (single source of truth)
Everything stays in agreement about which files have been shared or received, so the app screen and the iPhone's page never show different lists. In practice a file the iPhone uploads shows up in the app the instant it finishes, with no need to refresh. This works because the repository publishes its file lists as observable `StateFlow`s that the app watches, while the iPhone's page re-checks the list
every few seconds.

### Browser client (`assets/web/`)
This is the page the iPhone opens in Safari, written as plain HTML and JavaScript with no dependencies. It breaks large uploads into 4 MB pieces so a dropped connection can pick up from the last confirmed byte instead of starting again. During an upload it checks in with the server once a second and reloads itself once the transfer is finished.

---

## Project layout
```
app/src/main/
├── java/com/griddrop/
│   ├── MainActivity.kt            # permissions + Compose host + file picker
│   ├── GridDropApplication.kt     # process-wide repository + hotspot controller
│   ├── Model.kt                   # Role, SERVER_PORT
│   ├── hotspot/                   # LocalOnlyHotspot lifecycle
│   ├── net/                       # Ktor server, foreground service, repository
│   ├── ui/                        # the one-step-per-screen wizard (Compose)
│   ├── viewmodel/                 # UiState + Step navigation
│   └── util/                      # QR generation, network + diagnostics helpers
└── assets/web/                    # the iPhone's Safari client (index.html, app.js, style.css)
```

---

### Author
Mayitreya Pasumarthy - Made with love, and maybe a little bit of spite too <3

Also if anyone is hiring, hit me up please, my website is [here](https://mayitreya.github.io/website)

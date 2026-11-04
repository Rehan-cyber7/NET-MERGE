# Plexo Mobile

Plexo for Android and iOS: download one file over **Wi-Fi and mobile data at the same time**.
A port of [Plexo](https://getplexo.app) (MIT), same idea and same look.

One React UI (Plexo's own colors, fonts and icon) runs in a Capacitor shell. The downloader itself is
native on each platform, because a phone has to be told to send traffic over a particular network:

| | Android | iOS |
|---|---|---|
| Pins a connection to Wi-Fi / mobile data / Ethernet | `ConnectivityManager.requestNetwork` + `Network.openConnection` | `NWConnection.requiredInterfaceType` |
| Saves to | `Downloads/Plexo` (MediaStore) | the app's Files folder (`On My iPhone › Plexo`) |
| Keeps running with the screen off | foreground service + notification | about 30 s after leaving the app; the screen stays awake while a download runs |

Both engines work like Plexo's desktop one: HEAD-less probe (`Range: bytes=0-0`), the file cut into 4 MB chunks,
4 streams per network pulling from one shared queue (so the faster network does more), per-network speed caps,
retries with back-off, a network that keeps failing is dropped while the others carry on.

## Build

Needs Node 20+, and Android Studio and/or Xcode.

```bash
npm install
npm run build
npx cap add android        # and/or
npx cap add ios && node scripts/patch-ios.mjs
npx cap sync
npx cap open android       # or: npx cap open ios
npx @capacitor/assets generate --iconBackgroundColor '#1b1e20'   # optional: app icons from resources/icon.png
```

`npm run dev` opens the UI in a browser with a simulated engine, for design work.
`plugins/plexo-engine` is a local Capacitor plugin: `cap sync` links it into both projects, adds its
permissions and the download service to the Android manifest, and registers it. No manual wiring.

## Checked, and not

- Checked here: UI types (against stubs), `format.ts` tests (`npm test`), the chunk scheduler and speed limiter
  (`npm run test:native`), file-name parsing, and that the whole Android plugin **compiles** against stubs of the Android API.
- **Not run on a device or simulator, and the Swift was not compiled** (no Android SDK, Xcode or network in the build environment).
  Expect a small fix or two on the first build, most likely in the Swift.
- Needs real hardware to confirm: that your phone really keeps mobile data up while on Wi-Fi (Android: some OEMs
  restrict it; iOS: cellular-only sockets while Wi-Fi is the default route).
- Android needs 10+ (API 29), because of how files are saved to Downloads.

## Not in v1 (Plexo desktop has these)

Torrents and magnet links (the desktop engine is Node/WebTorrent), browser extension capture (Android share-sheet intake would be the equivalent),
scheduling and data allowances, "fix link" for expired URLs, auto-adjusting stream counts and racing the last slow chunk,
pause/resume of an unfinished download across an app restart (finished downloads do persist), sign-in sessions from the browser,
iOS downloading for long periods in the background (iOS does not allow it for this kind of networking).

## Test in a phone browser (no Android Studio)

1. Install Node.js 20 or newer on your computer.
2. In this folder, run `npm install`.
3. Start Vite with `npm run dev` (the server listens on `0.0.0.0:5173`).
4. Connect your phone and computer to the same Wi-Fi network.
5. Find the computer's local IPv4 address (`ipconfig` on Windows; look for the Wi-Fi adapter's IPv4 Address).
6. On the phone, open `http://COMPUTER-IP:5173` in Chrome. Example: `http://192.168.1.5:5173`.
7. If it does not load, allow Node.js/Vite through the computer firewall on private networks and check that both devices are on the same Wi-Fi.

Browser mode uses the simulated engine from `src/mock.ts`. It is for UI and interaction testing only; it does not exercise the native Android networking/download implementation.

## Build an Android APK with GitHub Actions (no local Android Studio)

1. Push this project to a GitHub repository, including `.github/workflows/android-apk.yml`.
2. In GitHub, open **Actions** and select **Build Plexo Android APK**.
3. Select **Run workflow** (or push to `main` / `master`).
4. Open the completed workflow run and download the `plexo-android-debug-apk` artifact.
5. Extract the downloaded ZIP and install `app-debug.apk` on an Android 10+ phone. You may need to allow Chrome or Files to install unknown apps.

The workflow installs Node.js, Java 21 and the Android SDK on GitHub's hosted runner, builds the Vite app, generates the Capacitor Android project, syncs the local `plexo-engine` plugin and creates a debug APK. No Android SDK or emulator is needed on your laptop. This is a debug/test APK, not a Play Store release.

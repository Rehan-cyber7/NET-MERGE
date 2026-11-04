# PlusNet v0.4.0

This release rebrands the Android/iOS app and its native Capacitor engine as **PlusNet**. It carries forward the multi-network download improvements from the previous `0.3.x` release line.

## What's changed

- Renamed the app display name, web title, native plugin, plugin package and build workflow to PlusNet.
- Changed the Android application ID to `app.plusnet.mobile`.
- Updated Android and iOS native plugin identifiers and namespaces to PlusNet.
- Replaced the app icon with a new PlusNet mark.
- Updated the README, release notes, workflow artifact names and diagnostics.
- Preserved independent Wi-Fi/cellular probing, redirect handling, HTTP range validation, fair chunk scheduling and network fallback.

## Android build

- App version: `0.4.0`
- Android versionCode: `5`
- Application ID: `app.plusnet.mobile`
- Artifact: `PlusNet-0.4.0-debug.apk`

## Important upgrade note

Because the Android application ID changed from `app.plexo.mobile` to `app.plusnet.mobile`, Android installs PlusNet as a separate app. It does not replace an existing Plexo installation or automatically migrate its local history and preferences.

## Network compatibility

Multi-network splitting still requires a server that supports HTTP byte ranges and accepts requests over each selected network. Server, CDN and carrier restrictions cannot be bypassed by the app.

This is a debug/testing APK, not a production-signed Google Play release.

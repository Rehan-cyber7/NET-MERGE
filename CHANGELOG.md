# Changelog

## [0.4.0] - 2026-11-04

- Rebrand the app and native engine from Plexo to PlusNet.
- Change Android application ID to `app.plusnet.mobile`.
- Rename the local Capacitor plugin and its Android/iOS identifiers to PlusNet.
- Replace the app icon and update the README, release assets and GitHub Actions workflow.
- Carry forward per-network redirect handling, range validation, chunk scheduling and fallback improvements.

## [0.3.7] - 2026-11-04

- Prepare the previous app and local Capacitor plugin for the `0.3.7` release line.
- Include independent network-specific redirect handling for Wi-Fi and cellular downloads.
- Validate byte-range support per network before splitting a download.
- Improve chunk scheduling, fallback behavior and Android network diagnostics.

## [0.1.2] - 2026-11-04

- Introduce network-specific redirect handling, per-network range validation and route diagnostics.

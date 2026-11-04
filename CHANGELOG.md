# Changelog

Notable changes to Plexo are documented here.

## [0.3.7] - 2026-11-04

- Prepare the Plexo app and local Capacitor plugin for the repository's `0.3.7` release line.
- Include independent network-specific redirect handling for Wi-Fi and cellular downloads.
- Validate byte-range support per network before splitting a download.
- Keep the original input URL for the native download job.
- Improve chunk scheduling and fallback behavior for selected networks.
- Add Android network diagnostics and automatic GitHub Release packaging.

## [0.1.2] - 2026-11-04

- Introduced network-specific redirect handling for the Plexo Android engine.
- Added per-network range validation, scheduler improvements and route diagnostics.

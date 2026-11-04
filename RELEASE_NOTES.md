# Plexo v0.3.7

This release continues the repository's `0.3.x` release line and packages the working Android multi-network download implementation.

## Included

- Independent download URL probing and redirect handling on each selected Android network.
- Keep the original input URL for native network-specific requests instead of reusing a redirect URL discovered over another network.
- HTTP range validation before a network is used for split downloading.
- Fair byte-range scheduling and fallback for unclaimed ranges when a selected network becomes unavailable.
- Network diagnostics for investigating Wi-Fi and cellular route failures.
- GitHub Actions build and automatic GitHub Release packaging.

## Download

- `Plexo-0.3.7-debug.apk` — Android debug APK for testing.
- `Plexo-0.3.7-SHA256SUMS.txt` — SHA-256 checksum for the APK.

## Important notes

- The download host must support HTTP byte ranges and return a compatible `206 Partial Content` response.
- The server/CDN must accept requests from both selected networks. A server may reject a route based on IP, session or signed-URL policy.
- This is a debug/test APK, not a production-signed Play Store build.

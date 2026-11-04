# Plexo v0.1.2 – Android network-specific redirect fix

## What changed

1. The New Download screen now starts the native download with the original URL entered by the user, rather than the URL resolved by the first network during the link check.
2. The Android job probes every selected, connected network independently from that original URL.
3. Only networks that return a valid byte-range response for the same file length are included in the split download.
4. Every Android worker follows redirects from the original URL using its own `Network`, instead of using a redirect URL obtained over Wi-Fi.
5. Logcat records HTTP status, transport and hostname (but never the full URL or signed query string), to help diagnose hosts that still reject a route.

This specifically addresses download links/CDNs that issue temporary or source-IP-bound redirects. It cannot override a host/provider that intentionally returns HTTP 403 to cellular requests; in that case the log will make the rejection visible.

## Version

- App/plugin version: 0.1.2
- Android versionCode: 3

## Apply

Copy the contents of this project over the existing repository, preserving the existing `.git` directory. Then:

```bash
git add -A
git commit -m "Fix network-specific download redirects"
git push
```

Run GitHub Actions and download the `plexo-android-debug-apk` artifact.

## Diagnose if cellular still fails

Connect the Android phone with USB debugging enabled and run:

```bash
adb logcat -s PlexoNets:I
```

Look for separate lines such as `HTTP 206 via wifi` and `HTTP 206 via cellular`. If cellular shows HTTP 401/403 even when started from the original URL, the download host is refusing that cellular route; Plexo cannot bypass the host's access policy.

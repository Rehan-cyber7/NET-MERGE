# Network-specific redirect handling

The PlusNet Android engine retains the following behavior from the earlier `0.1.2` fix:

1. The New Download screen starts the native job with the original URL entered by the user, rather than a URL resolved by the first network during link checking.
2. The Android job probes each selected, connected network independently from that original URL.
3. Only networks that return a valid byte-range response for the same file length are included in split downloading.
4. Every Android worker follows redirects from the original URL using its own Android `Network`, rather than reusing a redirect URL obtained over Wi-Fi.
5. Logcat records HTTP status, transport and hostname without logging the full URL or signed query string.

This addresses hosts that issue temporary or source-IP-bound redirects. It cannot override a host or carrier that returns HTTP 401/403 to one network.

For diagnostics, connect the Android phone with USB debugging enabled and run:

```bash
adb logcat -s PlusNetNets:I
```

Look for independent responses such as `HTTP 206 via wifi` and `HTTP 206 via cellular`.

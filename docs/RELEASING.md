# Releasing Plexo

The Android workflow builds a debug APK on GitHub-hosted runners. A push of a tag matching `v*` also publishes a GitHub Release and attaches the APK plus a SHA-256 checksum.

## Current release candidate

The supplied GitHub Releases screenshot shows `net-merge 0.3.6` as the latest release. This package continues that version sequence with Plexo `0.3.7`.

- Root package version: `0.3.7`
- Local plugin version: `0.3.7`
- Android version code: `4`
- Git tag to create: `v0.3.7`
- GitHub Release title: `Plexo v0.3.7`

## Publish this version

Commit and push the release-ready files first:

```bash
git add .
git commit -m "Prepare Plexo v0.3.7 release"
git push
```

Create and push the annotated tag:

```bash
git tag -a v0.3.7 -m "Plexo v0.3.7"
git push origin v0.3.7
```

Then open **GitHub → Actions** to follow the build. On success, open **GitHub → Releases**. The release contains:

- `Plexo-0.3.7-debug.apk`
- `Plexo-0.3.7-SHA256SUMS.txt`

## Prepare the next patch release

For example, to release `0.3.8`:

1. Set `package.json` `version` to `0.3.8`.
2. Set `plugins/plexo-engine/package.json` `version` to `0.3.8`.
3. Increase `ANDROID_VERSION_CODE` from `4` to `5` in `.github/workflows/android-apk.yml`.
4. Rewrite `RELEASE_NOTES.md` for `0.3.8`.
5. Commit and push the changes.
6. Create and push tag `v0.3.8`.

```bash
git add package.json plugins/plexo-engine/package.json \
  .github/workflows/android-apk.yml RELEASE_NOTES.md
git commit -m "Prepare Plexo v0.3.8 release"
git push
git tag -a v0.3.8 -m "Plexo v0.3.8"
git push origin v0.3.8
```

Do not reuse a published tag or Android `versionCode`. A tag must match the root package version exactly: `v` + the `package.json` version. If `v0.3.7` already exists in your repository, choose the next unused patch version instead.

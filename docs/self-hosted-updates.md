# Self-hosted APK updates

The app checks this stable manifest URL by default:

```text
https://github.com/meliherenn/iMAX-Player/releases/latest/download/latest.json
```

The first APK that users install must already include this update system. After that, newer
GitHub Releases with a higher `versionCode` will be detected in-app on mobile and TV.

## One-time setup on this machine

```bash
scripts/setup_release_keystore.sh
```

Back up `keystores/imax-release.jks` somewhere safe. Every future update must be signed with
the same keystore or Android will reject it as a different app.

## Build the first install APK

```bash
./gradlew assembleRelease
```

Send this APK to users for the first installation:

```text
app/build/outputs/apk/release/app-release.apk
```

## Publish a new update

1. Commit and push the code for the new version.
2. Create an update bundle:

```bash
scripts/publish_self_hosted_update.sh 1.0.2 false notes.txt
```

Use `true` instead of `false` for a mandatory update.

3. Upload the bundle to GitHub Releases:

```bash
scripts/upload_github_update_release.sh 1.0.2 notes.txt
```

The app will read the latest release's `latest.json`, compare `versionCode`, download the APK,
verify `sha256`, and open Android's installer.

## Generated manifest shape

```json
{
  "versionCode": 3,
  "versionName": "1.0.2",
  "apkUrl": "https://github.com/meliherenn/iMAX-Player/releases/latest/download/imax-player-1.0.2.apk",
  "sha256": "...",
  "mandatory": false,
  "minSupportedVersionCode": 0,
  "releaseNotes": "..."
}
```

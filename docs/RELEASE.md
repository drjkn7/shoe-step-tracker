# Releasing a version

The signing key belongs to the product owner. It is created and kept on their computer, never
committed, never uploaded, never shared with anyone (including an AI assistant). Losing it means
future updates can't install over the old app, so keep a backup (e.g. a USB stick in a safe place)
and the password in a password manager.

## 1. Create the signing key and signed APK (Android Studio, once per release)

1. Open the project in Android Studio.
2. Menu **Build → Generate Signed App Bundle or APK…** → choose **APK** → **Next**.
3. First time only: **Create new…** next to "Key store path".
   - Key store path: a folder **outside** the project, e.g. `Documents\ShoeStepper-keys\shoestep-release.jks`.
   - Choose a strong password, alias `shoestep`, validity 30 years (enter years only), your name.
   - Later releases: pick the same file and enter the same passwords.
4. **Next** → build variant **release** → **Create**.
5. Android Studio saves `app/release/app-release.apk` (APK files are ignored by git).

## 2. Checks before publishing (the assistant runs these and reports)

```bash
apksigner verify --print-certs app/release/app-release.apk   # signed, shows the certificate SHA-256
sha256sum app/release/app-release.apk                       # published next to the download
```

- The certificate SHA-256 is written into the README so people can verify downloads.
- Install it on the phone (it replaces the debug build only after uninstalling it, because the
  signing key differs) and go through [MANUAL_CHECKS.md](MANUAL_CHECKS.md).

## 3. Publish (only after the product owner says so)

- Merge the release PR, then tag `v0.1.0` on `main`.
- GitHub Release "Shoe Step Tracker 0.1.0" with notes from [CHANGELOG.md](../CHANGELOG.md), the
  APK attached, and its SHA-256 in the notes.

## Testing the release build

The release build shrinks and renames code (R8). It is tested two ways:
- `./gradlew connectedStagingAndroidTest -PtestBuildType=staging` runs the whole on-device suite
  against "staging" (the release build signed with the debug key; CI runs it too). Staging keeps
  unused code from being removed so tests can reach it (`src/staging/keepRules`), but renames the
  app's code exactly like release.
- The real release APK is smoke-tested on an emulator: launch, add a pair, start a session, restart
  the app, Settings and Privacy, plus Android's random "monkey" tester (3,000 events).

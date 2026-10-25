# Shoe Step Tracker

An open-source Android app that counts steps **per pair of shoes**.

Stick an NFC tag on each pair. Tap the tag with your phone to start a session; the app reads
your steps from **Health Connect** and keeps lifetime totals, session history and cost per step
for every pair.

> **Status:** version 0.2.0 (Milestone 2) is being prepared; 0.1.0 is the latest release. What's in
> each: [CHANGELOG.md](CHANGELOG.md).

## Install

Download the APK from the [Releases](https://github.com/drjkn7/shoe-step-tracker/releases) page and
open it on your phone (Android asks you to allow installing from your browser or file manager).
Each release lists the file's SHA-256 checksum so you can check the download is genuine.

All releases are signed with this certificate (SHA-256), which you can compare with
`apksigner verify --print-certs`:

```
B5:82:C2:A5:E8:EE:D7:5C:5A:A8:CF:A2:11:18:8A:29:D1:D1:FB:D9:88:B6:65:3F:07:1F:70:B8:B5:7E:22:7F
```

## Privacy in one paragraph

The app has **no internet permission**. It has no accounts, no cloud, no analytics and no ads.
It reads only your step count and walking distance from Health Connect (`READ_STEPS`,
`READ_DISTANCE`), shows a notification while a session runs (`POST_NOTIFICATIONS`, can be turned
off) and stores everything in a local database on your phone, which is excluded from backups. Details: [PRIVACY.md](PRIVACY.md).

## Requirements

- Android 9 (API 28) or newer
- Health Connect (built into Android 14+; installable on Android 9–13)
- NFC is optional. Without it you can start sessions with the on-screen Start button.

## Building from source

You need Android Studio (it includes the JDK and Android SDK).

```bash
./gradlew assembleDebug        # build a debug APK
./gradlew installDebug         # build and install on a connected phone
./gradlew check                # lint + unit tests + security checks
./gradlew connectedDebugAndroidTest                               # on-device tests (phone unlocked)
./gradlew connectedStagingAndroidTest -PtestBuildType=staging     # same, against the shrunk release build
```

Releases are signed with the maintainer's own key; see [docs/RELEASE.md](docs/RELEASE.md).
Security review: [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md).

On Windows use `gradlew.bat` instead of `./gradlew`.

## Contributing

Issues and pull requests are welcome. Please read [SECURITY.md](SECURITY.md) before reporting
anything security-related; do **not** open a public issue for vulnerabilities.

## Licence

[Apache License 2.0](LICENSE).

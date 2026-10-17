# Shoe Step Tracker

An open-source Android app that counts steps **per pair of shoes**.

Stick an NFC tag on each pair. Tap the tag with your phone to start a session; the app reads
your steps from **Health Connect** and keeps lifetime totals, session history and cost per step
for every pair.

> **Status:** Milestone 1 (v0.1.0) in progress. See [docs/PROGRESS.md](docs/PROGRESS.md).

## Privacy in one paragraph

The app has **no internet permission**. It has no accounts, no cloud, no analytics and no ads.
It reads only your step count from Health Connect (`READ_STEPS`) and stores everything in a
local database on your phone, which is excluded from backups. Details: [PRIVACY.md](PRIVACY.md).

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
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Contributing

Issues and pull requests are welcome. Please read [SECURITY.md](SECURITY.md) before reporting
anything security-related; do **not** open a public issue for vulnerabilities.

## Licence

[Apache License 2.0](LICENSE).

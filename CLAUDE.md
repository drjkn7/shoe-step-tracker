# Shoe Step Tracker (Android, open source)
Tracks steps per pair of shoes via NFC tags + Health Connect. Milestone 1 only. See docs/PROGRESS.md.

## Commands
- Build: ./gradlew assembleDebug | Install: ./gradlew installDebug
- Tests: ./gradlew testDebugUnitTest | On-device: ./gradlew connectedDebugAndroidTest
- All checks: ./gradlew check (includes verifyNoInternetPermission from Phase 1)
- Windows: use gradlew.bat. java is not on PATH; set JAVA_HOME to
  "C:\Program Files\Android\Android Studio\jbr" for command-line builds.

## Fixed decisions
Kotlin, Compose + Material 3, Room, DataStore, Health Connect READ_STEPS only, manual DI,
random-UUID NDEF tags, deferred step reconciliation, NO INTERNET, NO location, minSdk 28.
Package io.github.drjkn7.shoestep. Licence Apache-2.0. Repo drjkn7/shoe-step-tracker (public).

## Rules
- Only SessionManager changes session state (transactional, idempotent).
- Totals are computed, never stored. Money = Long minor units + currency code.
- Tags are untrusted input: strict parser only. Never log health data.
- Ask before adding any dependency or permission. Never commit secrets or keystores.
- One phase at a time, plain-English explanations, branch per phase, Conventional Commits.
- GitHub Actions are pinned to full commit SHAs.
- Out of scope for M1: location, widget, notification, inactivity, photos, 3D, cloud.

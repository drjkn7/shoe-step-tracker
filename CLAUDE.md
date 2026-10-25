# Shoe Step Tracker (Android, open source)
Tracks steps and distance per pair of shoes via NFC tags + Health Connect. Milestone 1 (v0.1.0) shipped;
Milestone 2 (v0.2.0) in progress. See docs/PROGRESS.md.

## Commands
- Build: ./gradlew assembleDebug | Install: ./gradlew installDebug
- Tests: ./gradlew testDebugUnitTest | On-device: ./gradlew connectedDebugAndroidTest (phone unlocked;
  debug is a separate app "io.github.drjkn7.shoestep.debug" next to the real release-signed one, which is
  never replaced or uninstalled; UI tests use testContainer() with an in-memory DB)
- CI emulator: ./gradlew pixel2api30DebugAndroidTest
- All checks: ./gradlew check (includes verifyNoInternetPermission = merged-manifest audit)
- Windows: use gradlew.bat. java is not on PATH; set JAVA_HOME to
  "C:\Program Files\Android\Android Studio\jbr" for command-line builds.

## Fixed decisions
Kotlin, Compose + Material 3, Room 3 (androidx.room3), DataStore, Health Connect READ_STEPS + READ_DISTANCE only, manual DI,
random-UUID NDEF tags, deferred step reconciliation, NO INTERNET, NO location, minSdk 28.
Package io.github.drjkn7.shoestep. Licence Apache-2.0. Repo drjkn7/shoe-step-tracker (public).

## Rules
- Only SessionManager changes session state (transactional, idempotent).
- Totals are computed, never stored. Money = Long minor units + currency code.
- Schema change? Bump DB version, commit the new schema JSON, add a test to MigrationTest.
- Tags are untrusted input: strict parser only. Never log health data.
- Ask before adding any dependency or permission. Never commit secrets or keystores.
- One phase at a time, plain-English explanations, branch per phase, Conventional Commits.
- GitHub Actions are pinned to full commit SHAs.
- New permission or exported component? Update the allowlists in app/build.gradle.kts (needs approval).
- Debug-only code goes in src/debug with a no-op in src/release.
- Out of scope for M2: location, widget, inactivity detection and auto-pause, background Health Connect reads,
  WorkManager, photos, 3D, cloud. (The session notification is in M2, Phase 7.)

## How we work (product owner has no coding experience)
- Per phase: plain-English plan (3-6 sentences) → wait for "approved" → build → test → security
  self-review vs SEC-01..12 → report (done / files / how to test on phone / commits / decisions) → wait for "go".
- Verify versions and APIs against official sources before use; say so if unverifiable.
- Tools: gh = "C:\Program Files\GitHub CLI\gh.exe" (signed in as drjkn7); adb =
  %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe (Pixel 9 Pro). Phone must be unlocked for tests.
  In Git Bash use MSYS_NO_PATHCONV=1 only for `adb shell` paths, never for gradlew.bat.
- PR flow: push branch → `gh pr create` → bind PR → offer Auto-fix → "check CI": confirm the emulator
  job really ran all tests (download on-device-reports) → merge with `--rebase` (merge commits are
  blocked on main) → `git pull --ff-only` on main.
- Report honestly: re-check before claiming a result (e.g. that a Gradle run actually started).

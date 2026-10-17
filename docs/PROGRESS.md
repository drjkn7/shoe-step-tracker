# Progress – Milestone 1 (v0.1.0)

## Phases
- [x] Phase 0: Preflight and repository
- [x] Phase 1: Foundations
- [ ] Phase 2: Data layer
- [ ] Phase 3: Shoes UI
- [ ] Phase 4: Sessions
- [ ] Phase 5: Health Connect and offline step resolution
- [ ] Phase 6: NFC
- [ ] Phase 7: Stats, export, settings
- [ ] Phase 8: Hardening and release

## Decisions log
| Date | Decision |
|---|---|
| 2026-10-17 | App name "Shoe Step Tracker"; package `io.github.drjkn7.shoestep` (kept from project). |
| 2026-10-17 | Licence Apache-2.0. Repo `drjkn7/shoe-step-tracker`, public. |
| 2026-10-17 | Reused existing Android Studio project (AGP 9.4.1, Gradle 9.6.0, compile/target SDK 37, minSdk 28). |
| 2026-10-17 | CI runs `./gradlew check assembleDebug`. Phase 1 wires `verifyNoInternetPermission` into `check`, so CI enforces it without a workflow change. |
| 2026-10-17 | CI uses JDK 25 (Temurin), matching `gradle/gradle-daemon-jvm.properties`. |
| 2026-10-17 | GitHub Actions pinned to full commit SHAs; Dependabot updates them weekly. CodeQL uses `build-mode: manual` because Kotlin analysis requires a build. |
| 2026-10-17 | Phase 1 libraries approved (see `gradle/libs.versions.toml`). Kotlin 2.4.21 via the AGP 9 `buildscript` classpath (AGP bundles 2.2.10). compileSdk/targetSdk 37 confirmed final (installed SDK has no preview codename). |
| 2026-10-17 | Navigation uses string routes built only in `Routes` (ids validated), avoiding a serialization dependency. |
| 2026-10-17 | Icons are Material Symbols vector files (Apache-2.0) copied from google/material-design-icons, not an icon library. |
| 2026-10-17 | Manifest audit = `verify<Variant>Manifest` tasks behind `verifyNoInternetPermission`, wired into `check`. Permission allowlist: NFC, health.READ_STEPS, AndroidX internal DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Release export allowlist: MainActivity, ProfileInstallReceiver (guarded by DUMP). Exports not audited for debug (Compose tooling activities). |
| 2026-10-17 | Backups: allowBackup=false plus explicit exclude-all rules for cloud backup and device transfer (some devices ignore allowBackup for transfers). |
| 2026-10-17 | StrictMode lives in `src/debug`; `src/release` has a no-op. Release R8 strips Log.v/d/i. |

## Open items carried to next phase
- Phase 5: Health Connect rationale `activity-alias` must be exported → add it to the release export allowlist in `app/build.gradle.kts`.
- Phase 6/5: when NFC / READ_STEPS are declared, no audit change needed (already allowlisted).
- Template leftovers awaiting product-owner OK to delete: `res/values/colors.xml` (7 UnusedResources lint warnings), `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
- Not yet handled: `cross-platform-transfer` (iOS) section of data-extraction rules — review in Phase 8 audit.
- Gradle 10 deprecation warning (`Configuration.setVisible`) comes from AGP/KGP plugins, not our scripts; recheck on plugin updates.

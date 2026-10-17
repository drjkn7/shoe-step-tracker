# Progress – Milestone 1 (v0.1.0)

## Phases
- [x] Phase 0: Preflight and repository
- [ ] Phase 1: Foundations
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

## Open items carried to next phase
- Phase 1: update outdated library versions in `gradle/libs.versions.toml` (needs approval list).
- Phase 1: `allowBackup="true"` in the manifest → fix (SEC-02).
- Phase 1: confirm compileSdk/targetSdk 37 is the latest stable on developer.android.com.
- Phase 1: clear template lint warnings (GradleDependency ×6, NewerVersionAvailable ×1,
  UnusedResources ×7, RedundantLabel ×1). No lint errors at end of Phase 0.

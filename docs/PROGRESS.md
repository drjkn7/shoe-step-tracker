# Progress – Milestone 1 (v0.1.0)

## Phases
- [x] Phase 0: Preflight and repository
- [x] Phase 1: Foundations
- [x] Phase 2: Data layer
- [x] Phase 3: Shoes UI
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
| 2026-10-17 | Room 3.0.3 (`androidx.room3`) chosen over 2.8.5 (Google now labels 2.x legacy). Android SQLite driver comes transitively via sqlite-framework; KSP 2.3.12 via plugins block. |
| 2026-10-17 | `kotlinx-serialization-core` pinned to 1.11.0 (app had 1.7.3 transitively; room3-testing needs >= 1.8.1 and Android tests use app runtime versions). |
| 2026-10-17 | Schema v1: shoes (unique tag_uuid), sessions, segments (cascade deletes), active_session (PK fixed at 1 plus triggers rejecting other ids, created in onOpen). Stats computed in SQL, never stored; negative segment durations count as 0; average = total steps / ended sessions. |
| 2026-10-17 | ShoeValidator strips control and bidi-override characters, collapses whitespace, counts length in code points. CostCalculator returns major units with currency digits + 2 (per 1,000 steps) / + 5 (per step), HALF_UP. |
| 2026-10-17 | DB tests are instrumented (Room needs Android). CI runs them on Gradle Managed Device `pixel2api30` (ATD image). Local on-device runs need the phone unlocked. |
| 2026-10-17 | After adding a shoe the app opens its detail screen; Back returns to the list (product owner decision, `popUpTo(HOME)`). Edit returns to detail. || 2026-10-17 | Palette: Red, Orange, Amber, Green, Teal, Blue, Indigo, Pink (index = stored accentColor; never reorder). Selected swatch shows a tick so colour is not the only cue. || 2026-10-17 | ViewModels created via `viewModel { }` with AppContainer (no DI library). `AppNavHost(container)` lets UI tests use an in-memory database. Form state survives process death via SavedStateHandle. || 2026-10-17 | `android.injected.androidTest.leaveApksInstalledAfterRun=true` in gradle.properties: on-device tests no longer uninstall the app (which would delete real data). |

## Open items carried to next phase
- Phase 5: Health Connect rationale `activity-alias` must be exported → add it to the release export allowlist in `app/build.gradle.kts`.
- Phase 6/5: when NFC / READ_STEPS are declared, no audit change needed (already allowlisted).
- Phase 4: SessionDao/ActiveSessionDao write methods must be called only from SessionManager (convention; not enforced by visibility).
- Phase 4: enable the Start button on Shoe detail (currently disabled with a note).
- Phase 8: manual TalkBack and right-to-left check (T25); automated checks cover labels, 48dp targets and 200% font.
- Phase 8: R8 `packageScope` currently limits optimisation to androidx/kotlin/kotlinx; decide whether app code should be shrunk too. Room ships its own keep rule for generated `_Impl` classes.
- Not yet handled: `cross-platform-transfer` (iOS) section of data-extraction rules — review in Phase 8 audit.
- Gradle 10 deprecation warning (`Configuration.setVisible`) comes from AGP/KGP plugins, not our scripts; recheck on plugin updates.

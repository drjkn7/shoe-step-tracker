# Progress – Milestone 1 (v0.1.0)

## Phases
- [x] Phase 0: Preflight and repository (merged)
- [x] Phase 1: Foundations (PR #5, merged)
- [x] Phase 2: Data layer (PR #6, merged)
- [x] Phase 3: Shoes UI (PR #7, merged)
- [ ] Phase 4: Sessions ← **next** (branch `feature/phase-4-sessions` created; plan not yet approved)
- [ ] Phase 5: Health Connect and offline step resolution
- [ ] Phase 6: NFC
- [ ] Phase 7: Stats, export, settings
- [ ] Phase 8: Hardening and release

## Next up: Phase 4 hand-off (read this first after /clear)
Status: Phase 4 has **not** been planned in detail or approved yet. First action: present the Phase 4
plan (Plan Mode style: plain English, then wait for "approved").

Product owner direction (2026-10-17): manual buttons are the primary way to run sessions; NFC
(Phase 6) is only a shortcut to the same actions. Order stays 4 (buttons) → 5 (Health Connect step
reads) → 6 (NFC), so step reading can be tested without tags. Proposed (to confirm in the plan):
- Shoe detail: Start when idle; Pause + End while running; Resume + End while paused.
- Home: an "active session" card at the top with the same Pause/Resume/End buttons.
- Starting a different shoe while one is active: "Switch to this pair?" confirmation (ends current with reason SWITCH).
- End asks for a quick confirmation.
- Session history list on Shoe detail. Steps show 0 until Phase 5.

Brief's Phase 4 scope: SessionManager (the ONLY code that changes session state; each action one DB
transaction, idempotent, injected Clock), state machine Idle→Running→Paused→Ended plus Switch,
restore active session on launch (survives process death/reboot), debounce helper (2 s, used by NFC in
Phase 6), reject negative durations. Table-driven unit tests: every state × event, idempotency,
debounce, process-death restore, negative-duration guard. Acceptance: run a manual session, kill the
app mid-session, reopen, session still running (T08, T16, T17, T20).

Already built that Phase 4 reuses:
- `data/db/Daos.kt`: `SessionDao` (insert/update session and segment, open segment, history) and
  `ActiveSessionDao` (single-row upsert/clear/observe). DB triggers forbid a second active row.
- `core/Clock.kt` (+ `FakeClock` in `src/test`), `data/model/Enums.kt` (SessionState, EndReason, StepsStatus).
- `ui/shoe/ShoeDetailScreen.kt`: disabled "Start session" button waiting for Phase 4.
- `ui/home/HomeScreen.kt`: RUNNING badge already driven by `ShoeWithStats.isActive`.
- `AppContainer` (add SessionManager there), `ui/TestApp.kt` `testContainer()` for UI tests.
- Room transactions: `db.withWriteTransaction { }` (androidx.room3).

## Decisions log
| Date | Decision |
|---|---|
| 2026-10-17 | App name "Shoe Step Tracker"; package `io.github.drjkn7.shoestep` (kept from project). |
| 2026-10-17 | Licence Apache-2.0. Repo `drjkn7/shoe-step-tracker`, public. |
| 2026-10-17 | Reused existing Android Studio project (AGP 9.4.1, compile/target SDK 37, minSdk 28). Gradle wrapper 9.8.1. |
| 2026-10-17 | CI runs `./gradlew check assembleDebug`; `verifyNoInternetPermission` is wired into `check`. Second CI job runs on-device tests on emulator. |
| 2026-10-17 | CI uses JDK 25 (Temurin), matching `gradle/gradle-daemon-jvm.properties`. |
| 2026-10-17 | GitHub Actions pinned to full commit SHAs; Dependabot updates them weekly. CodeQL uses `build-mode: manual` because Kotlin analysis requires a build. |
| 2026-10-17 | Phase 1 libraries approved (see `gradle/libs.versions.toml`). Kotlin 2.4.21 via the AGP 9 `buildscript` classpath (AGP bundles 2.2.10). compileSdk/targetSdk 37 confirmed final. |
| 2026-10-17 | Navigation uses string routes built only in `Routes` (ids validated), avoiding a serialization dependency. |
| 2026-10-17 | Icons are Material Symbols vector files (Apache-2.0) copied from google/material-design-icons @ 49d4db35df87, not an icon library. |
| 2026-10-17 | Manifest audit = `verify<Variant>Manifest` tasks behind `verifyNoInternetPermission`, wired into `check`. Permission allowlist: NFC, health.READ_STEPS, AndroidX internal DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Release export allowlist: MainActivity, ProfileInstallReceiver (guarded by DUMP). Exports not audited for debug (Compose tooling activities). |
| 2026-10-17 | Backups: allowBackup=false plus explicit exclude-all rules for cloud backup and device transfer (some devices ignore allowBackup for transfers). |
| 2026-10-17 | StrictMode lives in `src/debug`; `src/release` has a no-op. Release R8 strips Log.v/d/i. |
| 2026-10-17 | Room 3.0.3 (`androidx.room3`) chosen over 2.8.5 (Google now labels 2.x legacy). Android SQLite driver comes transitively via sqlite-framework; KSP 2.3.12 via plugins block. |
| 2026-10-17 | `kotlinx-serialization-core` pinned to 1.11.0 (app had 1.7.3 transitively; room3-testing needs >= 1.8.1 and Android tests use app runtime versions). |
| 2026-10-17 | Schema v1: shoes (unique tag_uuid), sessions, segments (cascade deletes), active_session (PK fixed at 1 plus triggers rejecting other ids, created in onOpen). Stats computed in SQL, never stored; negative segment durations count as 0; average = total steps / ended sessions. |
| 2026-10-17 | ShoeValidator strips control and bidi-override characters, collapses whitespace, counts length in code points. CostCalculator returns major units with currency digits + 2 (per 1,000 steps) / + 5 (per step), HALF_UP. |
| 2026-10-17 | DB tests are instrumented (Room needs Android). CI runs them on Gradle Managed Device `pixel2api30` (ATD image, testedAbi x86); CI accepts Android SDK licences (product owner approved). |
| 2026-10-17 | After adding a shoe the app opens its detail screen; Back returns to the list (product owner decision, `popUpTo(HOME)`). Edit returns to detail. |
| 2026-10-17 | Palette: Red, Orange, Amber, Green, Teal, Blue, Indigo, Pink (index = stored accentColor; never reorder). Selected swatch shows a tick so colour is not the only cue. |
| 2026-10-17 | ViewModels created via `viewModel { }` with AppContainer (no DI library). `AppNavHost(container)` lets UI tests use an in-memory database. Form state survives process death via SavedStateHandle. |
| 2026-10-17 | `android.injected.androidTest.leaveApksInstalledAfterRun=true` in gradle.properties: on-device tests no longer uninstall the app (which would delete real data). Verified with a marker file. |
| 2026-10-17 | Save with invalid input shows a "Can't save yet" popup listing each problem with an OK button (product owner request: inline error was off-screen on a tall phone). Fields stay marked. |
| 2026-10-17 | Product owner: manual Start/Pause/Resume/End buttons are the primary session controls; NFC is a later shortcut. Phase order 4 → 5 → 6 unchanged. |

## Open items carried to later phases
- Phase 4: SessionDao/ActiveSessionDao write methods must be called only from SessionManager (convention; not enforced by visibility).
- Phase 4: enable the Start button on Shoe detail (currently disabled with a note).
- Phase 5: Health Connect rationale `activity-alias` must be exported → add it to the release export allowlist in `app/build.gradle.kts`.
- Phase 5/6: when NFC / READ_STEPS are declared, no audit change needed (already allowlisted).
- Phase 8: manual TalkBack and right-to-left check (T25); automated checks cover labels, 48dp targets and 200% font.
- Phase 8: R8 `packageScope` currently limits optimisation to androidx/kotlin/kotlinx; decide whether app code should be shrunk too. Room ships its own keep rule for generated `_Impl` classes.
- Phase 8: `cross-platform-transfer` (iOS) section of data-extraction rules not yet handled.
- After any build-tools upgrade: re-verify on-device tests keep app data (the keep-installed property is an IDE-injected one).
- Gradle 10 deprecation warning (`Configuration.setVisible`) comes from AGP/KGP plugins, not our scripts; recheck on plugin updates.

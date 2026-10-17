# Progress – Milestone 1 (v0.1.0)

## Phases
- [x] Phase 0: Preflight and repository (merged)
- [x] Phase 1: Foundations (PR #5, merged)
- [x] Phase 2: Data layer (PR #6, merged)
- [x] Phase 3: Shoes UI (PR #7, merged)
- [x] Phase 4: Sessions (PR #8, merged)
- [x] Phase 5: Health Connect and offline step resolution (PR #9, merged)
- [ ] Phase 6: NFC ← **built on branch `feature/phase-6-nfc`, awaiting product-owner "go" and PR**
- [ ] Phase 7: Stats, export, settings
- [ ] Phase 8: Hardening and release

## Next up: Phase 6 hand-off (read this first after /clear)
Status: Phase 6 plan approved (2026-10-18), built on `feature/phase-6-nfc` and confirmed with a real tag by the product
owner (who also approved the two NFC entry aliases); all local checks and on-device
tests pass. "go" given: push → PR → CI (confirm
the emulator job ran TagDbTest + TagScreensTest) → rebase-merge.

What Phase 6 added (package `nfc/`, UI in `ui/nfc/`):
- `TagFormat`: on-tag format (one MIME record `application/vnd.io.github.drjkn7.shoestep` holding a random
  v4 UUID in lowercase ASCII, plus an Android Application Record) and the strict parser (size and record
  limits first; Ours / Blank / Foreign / Malformed / Unsupported). Pure Kotlin, JVM-tested.
- `AndroidTags` / `AndroidTagHandle`: Tag/Intent → `TappedTag`; write → read back → optional lock.
  Debounce key = tag hardware id, so a just-linked tag can't start a session while the phone rests on it.
- `TagCoordinator` (one per app, in AppContainer): Debouncer → route tap → SessionManager. Prompts
  (NewTag, PickPair, RunningTap, Switch, Archived), linking state, "Tag linked" notice, navigation events.
- `RoomTagStore` + `ShoeDao.setTagUuid` (check-then-update in one write transaction; unique index is the backstop).
- MainActivity: NDEF_DISCOVERED intent (app closed, T06) and foreground dispatch while visible (T07; keeps
  tap-to-pay working, unlike reader mode). Haptic CONFIRM via View (no VIBRATE permission).
- Shoe detail "NFC tag" card: Link / Replace / Unlink; NFC off → Settings; no NFC → explained, no buttons.
- Tests: TagFormatTest, TagCoordinatorTest (JVM); TagDbTest, TagScreensTest (on-device, simulated taps).
  UI tests must not call SessionManager from inside waits (see TagScreensTest.active()): it deadlocked once.

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
| 2026-10-17 | Phase 4 plan approved: Start / Pause+End / Resume+End; Home "Current session" card; Switch asks "Switch to this pair?" (old session ends with reason SWITCH, new one starts at the same instant); End asks to confirm, Pause/Resume don't. Archived pairs have no Start; archiving a pair with a session asks first and ends it; deleting ends it first. Long-running sessions allowed (reminders out of scope for M1). |
| 2026-10-17 | SessionManager is unit-tested through a `SessionStore` interface with an in-memory fake (Room needs Android); `RoomSessionStore` is covered by on-device tests. No schema change (new DAO queries only), so DB stays at version 1. |
| 2026-10-17 | Clock going backwards: times are moved forward, never saved negative — a segment ends at its own start (0 min), resume/switch never start before the last recorded time. Launch repair never invents walking time (orphan sessions end at their last known time). |
| 2026-10-17 | Debouncer (for NFC, Phase 6): per tag, 2 s; every read restarts the timer, so a phone resting on a tag acts once; a backwards clock jump lets the read through rather than blocking the tag. |
| 2026-10-17 | Session times are shown in the time zone they were recorded in (stored `zone_id`). |
| 2026-10-17 | Phase 5 plan approved. Steps come from Health Connect (not the phone's own step sensor), so wearables count too (product owner: will use a watch, as will other users). |
| 2026-10-17 | Counts are read only while the app is open (no background-read permission, no WorkManager); steps walked while it is closed appear on the next open. |
| 2026-10-17 | A count is PROVISIONAL ("About") for one day after its segment ends, then FINAL (product owner changed 2 days → 1 day). No data after a day → UNAVAILABLE, left out of totals (a provisional count already shown is kept as FINAL instead). Read errors retry, except segments older than Health Connect's 30-day history limit, which settle. |
| 2026-10-18 | Product owner: a provisional count may go down as well as up. Health Connect's latest answer is the accurate one (e.g. after it removes duplicates), so the app shows it rather than keeping the higher number. Once FINAL, a count never changes. |
| 2026-10-17 | The running session card shows "About N steps so far" (live read, display only; shoe totals change only when a segment closes). |
| 2026-10-17 | connect-client 1.1.0 applied (latest stable on 2026-10-17; 1.2.0 is alpha only). Release export allowlist adds PermissionsRationaleActivity (static text), ViewPermissionUsageActivity (START_VIEW_PERMISSION_USAGE) and the library's HealthDataSdkService (code rejects callers other than the Health Connect package; product owner approved keeping it). |
| 2026-10-17 | Step counts are written only by StepReconciler; SessionManager creates segments PENDING and never touches steps. Not session state, so the "only SessionManager changes session state" rule is unchanged. |
| 2026-10-18 | Phase 6 plan approved. A tap on an idle pair starts it; on its running session asks Pause / End / Cancel; on a paused pair resumes straight away; on another pair asks "Switch to this pair?". Archived pair's tag explains and does nothing. |
| 2026-10-18 | Blank or unknown tag: one pop-up with "Link to an existing pair" and "Add a new pair" (product owner addition); Back or a tap outside cancels. A new pair gets the tag linked right after it is saved. |
| 2026-10-18 | Locking a tag is optional, off by default, with the permanent-lock warning always shown before the tap. A locked leftover tag of ours (pair deleted) can be re-linked using the code already on it. |
| 2026-10-18 | Tags carry a random v4 UUID (MIME record) plus an Android Application Record. Strict parser: 1 KB / 8-record limits, exactly one of our records, exact lowercase v4 UUID; anything else is ignored. Debounce key = tag hardware id. |
| 2026-10-18 | NFC permission (already allowlisted) and an NDEF_DISCOVERED filter on the exported MainActivity (product owner approved). Accepted risk: another app could send a fake tag intent; it can only start/pause/switch the user's own sessions, never read or delete data. Foreground dispatch (not reader mode) so tap-to-pay keeps working while the app is open. |
| 2026-10-18 | Tag write is read back to verify (T04); a never-formatted tag can only be checked by format() succeeding (Android can't re-read it until the next tap). |
| 2026-10-18 | Product owner (after testing a real tag): a tap always lands in the same place. Linked tag → its pair's page (session starts there; Pause/End or Switch questions are asked there). Unlinked tag → Home with the "New tag" pop-up. |
| 2026-10-18 | Android 17 (API 37, the Pixel's version): NFC intents only reach activities guarded by DISPATCH_NFC_MESSAGE, and never stopped apps (official NFC guide). Tag intents now enter via activity-alias `.NfcTagEntry` (guarded; enabled on 37+ via values-v37) or `.NfcTagEntryLegacy` (unguarded; Android 9–16, which don't define the permission). MainActivity ignores tag intents not addressed to the enabled alias, so on 17+ fake tag intents are refused; foreground dispatch targets the same alias. |

## Open items carried to later phases
- SessionDao/ActiveSessionDao write methods must be called only via SessionManager/RoomSessionStore (convention; not enforced by visibility).
- Open segments are excluded from totals (the Home card's "so far" is display-only).
- Phase 5 manual check on an Android 9–13 phone (Health Connect app path, `HealthDataSdkService`, rationale via ACTION_SHOW_PERMISSIONS_RATIONALE) was not possible on the Android 17 Pixel; do it in Phase 8 if a device is available.
- Shoe detail's "Session running" line has no "so far" count (Home card only); add in Phase 7 if wanted.
- Re-verify `HealthDataSdkService`'s caller check (bytecode) whenever connect-client is upgraded.
- Phase 7/8: Shoe detail history shows every session (no paging); revisit if lists get long. A visible live timer on the session card was left out.
- Phase 6 manual: try a real NTAG213/215/216 sticker on the Pixel (link, tap closed/open, lock on a spare tag); automated tests simulate taps.
- Phase 8: manual TalkBack and right-to-left check (T25); automated checks cover labels, 48dp targets and 200% font.
- Phase 8: R8 `packageScope` currently limits optimisation to androidx/kotlin/kotlinx; decide whether app code should be shrunk too. Room ships its own keep rule for generated `_Impl` classes.
- Phase 8: `cross-platform-transfer` (iOS) section of data-extraction rules not yet handled.
- After any build-tools upgrade: re-verify on-device tests keep app data (the keep-installed property is an IDE-injected one).
- Gradle 10 deprecation warning (`Configuration.setVisible`) comes from AGP/KGP plugins, not our scripts; recheck on plugin updates.

# Progress

## Milestone 2 (v0.2.0) – in progress (read this first after /clear)
Goal: Silver Matte restyle in a light and a night theme ([DESIGN.md](DESIGN.md), mockups in
[design/](design/)); distance from Health Connect per session, in totals and as cost per km or mile;
a session notification with a timer and Pause/Resume and End session; "Discard changes?" on the shoe
form; Settings → Appearance (Light / Dark / System); add a past session for a forgotten tap.
Out of scope (moved to M3): widget, inactivity detection and auto-pause, charger prompt, maximum session
length, background Health Connect reads, WorkManager.

- [x] Phase 0: Plan and paperwork (PR #17, merged)
- [x] Phase 1: Theme layer (PR #18, merged)
- [x] Phase 2: Components (PR #19, merged)
- [x] Phase 3: Restyle every screen + "Discard changes?" + Appearance card (PR #25, merged; night mockups still to compare)
- [x] Phase 4: Distance data (READ_DISTANCE, schema v1 → v2, reconciler reads distance, backfill) (PR #27, merged)
- [x] Phase 5: Distance in the UI, units setting, cost per km/mile, CSV distance columns (PR #31, merged)
- [ ] Phase 6: Add past session (and deleting "Added later" sessions) — built on branch `m2-phase-6`, waiting for the phone check
- [ ] Phase 7: Session notification (POST_NOTIFICATIONS)
- [ ] Phase 8: Hardening and release 0.2.0

Next: Phase 6 is built on branch `m2-phase-6` (schema v3) and waiting for the product owner's phone check and
"go", then PR and CI; after that the Phase 7 plan. 111/111 on-device tests passed on the Pixel. Still open: the 30-day check (test app → Settings → Test build → "How far back can it read?" after allowing distance
in Health Connect); the "Allow" buttons for distance open Health Connect's own screen, checked by hand on the phone;
tablet layout (content stretches the full width in landscape, left for later). Night mode uses the Balanced palette
(PR #29). On-device tests can also run on the Galaxy Tab S7+ while the Pixel is away.

Waiting on the product owner:
- 7 of the 30 mockups are not in `docs/design/` (23 files there): Add past session, Add past session
  overlap error, the five night frames (Home, pair page, tag-tap pop-up, Add past session, Settings), and
  a Settings frame with the Appearance card. The files were renumbered, so the numbers in DESIGN.md
  section 7 and in the M2 brief do not match the file names; work refers to file names. Night screens
  are needed before Phase 3 is finished, Add past session before Phase 6.
- Draft copy on the M2 screens (permission cards, notification, Add past session) is confirmed per phase
  (Phase 6 and Phase 7 ask first).

## Milestone 1 (v0.1.0)

### Phases
- [x] Phase 0: Preflight and repository (merged)
- [x] Phase 1: Foundations (PR #5, merged)
- [x] Phase 2: Data layer (PR #6, merged)
- [x] Phase 3: Shoes UI (PR #7, merged)
- [x] Phase 4: Sessions (PR #8, merged)
- [x] Phase 5: Health Connect and offline step resolution (PR #9, merged)
- [x] Phase 6: NFC (PR #10, merged; step-count decision PR #11)
- [x] Phase 7: Stats, export, settings (PR #12, merged; CSV fixes PR #13, #14)
- [x] Phase 8: Hardening and release (PR #15, merged; v0.1.0 released)

### Milestone 1 complete
v0.1.0 is published: [GitHub Release](https://github.com/drjkn7/shoe-step-tracker/releases/tag/v0.1.0), tag
`v0.1.0` on `ba7a404`, asset `shoe-step-tracker-0.1.0.apk` (SHA-256 `2e3ebf49…8d80`, also attached as a
`.sha256` file; verified by downloading it back). Signed by the product owner's own key (kept outside the
project); the product owner reported the [manual checklist](MANUAL_CHECKS.md) as passed on 2026-10-18.
Next: Milestone 2 (above). Other ideas live in [BACKLOG.md](BACKLOG.md); each needs a product decision.
For the next release follow [RELEASE.md](RELEASE.md) (same key, bump versionCode/versionName).

What Phase 8 added:
- R8 for the app's own code (release); enum names kept (stored in the DB). "staging" build type =
  release + debug key; `-PtestBuildType=staging` runs the on-device suite on it (`src/staging/keepRules`
  keep code from removal but still rename app code). CI runs both and fails if zero tests ran.
- Pair page history in pages of 50 ("Show N older sessions"); HardeningScreensTest (T26 2,000 sessions,
  RTL mirroring). "Get Health Connect" falls back to the web page without the Play Store.
- iPhone (`cross-platform-transfer`) exclude-all rules. Docs: SECURITY_REVIEW.md, MANUAL_CHECKS.md,
  RELEASE.md, CHANGELOG.md, README install section.
- Local tooling (not in the repo): Android SDK command-line tools 23.0 and the Android 13 Google APIs
  x86_64 image (AVD `ShoeStep_API33`, no Play Store) for older-Android checks.

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
| 2026-10-18 | Phase 7 plan approved. Two exports (pairs, sessions) as separate CSV files through the system file picker; the "so far" count also shows on the pair's page. DataStore 1.2.1 applied (pre-approved; latest stable re-checked). No new permission or exported component. |
| 2026-10-18 | CSV exports: header row unquoted and no UTF-8 byte order mark (product owner, after Google Sheets showed stray characters before the first header). Trade-off accepted: Excel's double-click open may garble accented letters; its Data → From Text import reads them correctly. |
| 2026-10-18 | Phase 8 plan approved: v0.1.0 as a GitHub Release with an APK signed by the product owner's own key (created in Android Studio, never committed or shared); R8 shrinks the app's own code; Android 13 emulator downloaded for older-Android checks. |
| 2026-10-18 | Release testing: full on-device suite on a "staging" R8 build (removal of unused code disabled there, renaming kept) in CI and locally, plus a smoke test of the real release APK (flows + monkey). Pair history shows 50 sessions per page. |
| 2026-10-18 | v0.1.0 signed by the product owner in Android Studio (key kept outside the project; certificate SHA-256 B5:82:…:22:7F, CN=DK, valid to 2056, APK signature scheme v2). Verified: release build, not debuggable, minSdk 28, permissions READ_STEPS + NFC only, Phase 8 content present. `app/release/` is git-ignored. |
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
| 2026-10-22 | **Milestone 2 scope approved** (see the M2 section above). Product owner approved by name: permission `android.permission.health.READ_DISTANCE`, permission `android.permission.POST_NOTIFICATIONS`, and the bundled Chakra Petch font (SIL Open Font License 1.1; bundled in the app, never downloaded). |
| 2026-10-22 | Distance comes only from Health Connect (`DistanceRecord`, aggregate `DISTANCE_TOTAL`). No estimate from steps, no GPS, no location permission. Stored in metres; units only change display; CSV stays in metres. Steps keep working if distance is refused. |
| 2026-10-22 | Units: kilometres or miles in Settings. Default from the phone's region: miles for US and GB (CLDR road-distance preference), kilometres elsewhere. Android's measurement-system data also puts Liberia and Myanmar in the US system; not used, because CLDR's road preference lists only US and GB. |
| 2026-10-22 | Home rows show total distance under total steps. Stats add total distance, average distance per session and cost per km or mile (follows the unit setting), next to cost per 1,000 steps. Pair page order: header, Stats, session controls, NFC tag, History (already the M1 order). |
| 2026-10-22 | Theme: Light / Dark / System, default System; night palette and contrast in DESIGN.md section 3 and 8. Components use tokens only. Dynamic colour off. |
| 2026-10-22 | Session notification follows design decision F2: a timer notification, no foreground service. Accepted limitation: on Android 14+ a non-foreground-service ongoing notification can be swiped away (official Android 14 behaviour change); the app posts it again on the next pause/resume or app open. After a phone restart it returns when the app is next opened (no boot receiver). |
| 2026-10-22 | Add past session: one pair, a date, start and end; not in the future, end after start, no overlap with any session of any pair (including the running one). Health Connect's default read limit is "30 days prior to when any permission was first granted" (official read-data guide); the app does not know that date, so the form allows up to 30 days back from now, which is always readable. Shown in History as "Added later"; counts go through the normal reconciler (an added session whose segment ended over a day ago settles FINAL at the first read, so it never shows "About"). |
| 2026-10-22 | Product owner: sessions marked "Added later" can be deleted (only those, with a confirmation). Normal sessions stay undeletable. Deletion goes through SessionManager. |
| 2026-10-22 | Phase 1 (theme layer): tokens in `ui/theme` (Color, Type, Shape, Depth), read as `SilverTheme.colors / type / shapes / spacing`; light and night share token names. Material's colour scheme is filled from the tokens with `primary` = accent ink (readable as text in both themes); dynamic colour removed. Depth uses Compose UI 1.12's `dropShadow` / `innerShadow` (checked in the library and the official shadows guide). |
| 2026-10-22 | Token gaps in DESIGN.md filled: secondary and icon buttons use the `sheen` gradient (night table) instead of the light-only `button-secondary`; `card-register` has no night value, so night uses `card`; `tabbar` and `sheet` are not used (no tab bar, no bottom sheet). Contrast test (ContrastTest) checks every text/surface pair in both themes: night `danger` on the lightest sheen stop is 4.3:1, so danger text never goes on secondary buttons. |
| 2026-10-22 | Theme setting stored in DataStore (`theme_mode`, unknown values → System) and applied live at the top of MainActivity and the Health Connect rationale screen; status bar icons follow the app theme. On Android 12+ the choice is also handed to `UiModeManager.setApplicationNightMode` (API 31, verified in the SDK source: persisted until changed or app data cleared; no permission) when the user changes it, so the launch window matches; tests pass a no-op so they never change the real app's setting. Launch window colour = backdrop top colour. |
| 2026-10-22 | Chakra Petch bundled as 4 static weights in `res/font` (Regular, Medium, SemiBold, Bold), OFL text in `licenses/ChakraPetch-OFL.txt` and in the in-app licences screen. Files as supplied by the product owner (docs/Chakra_Petch, not committed); SHA-256: Regular 75a36533…0333, Medium 24b98ee7…f180, SemiBold e0e25888…e113, Bold fb428d38…1798. |
| 2026-10-22 | Debug builds get application id suffix `.debug` (label "Shoe Step (test)"): the product owner's phone has the real v0.1.0 signed with the release key, which a debug-key build cannot replace, and uninstalling it would delete real data. Tests now install side by side. NFC alias names are taken from the code package (not the application id); the permission allowlist names AndroidX's receiver permission after each variant's application id. Release manifest and audit unchanged. Full on-device suite 168/168 on the Pixel 9 Pro (Android 17); real app checked unchanged afterwards (0.1.0, same signature, same install time). |
| 2026-10-22 | Phase 2 (components): Silver Matte kit in `ui/common` (Buttons, Surfaces, Fields, Dialogs, DataRows) replacing Material buttons, cards, chips, fields, dialogs, menu and swatches inside the existing composables; screen layouts follow in Phase 3. Every pop-up is `SilverDialog` (own window, tapjacking filter kept, page dimmed 42% / 60% night); two actions side by side (wrapping at large text), three or more stacked. Destructive confirmations (delete, unlink, archive) use the filled danger button. |
| 2026-10-22 | Capital-letter labels ("STATS", field labels) are drawn in capitals but read out and matched by their normal wording, and card labels are headings for TalkBack. The live count shows "About 3,214" large with "steps so far" beside it, read out as one sentence. Distance places show "–" until Phase 5. |
| 2026-10-22 | Colour swatches keep 48 dp touch targets: one row of eight when the form is wide enough (8 × 48 dp), otherwise two rows of four. The mockup's single row would need targets of about 39 dp on a phone. Shoe colours now use the DESIGN.md palette (same order, so stored pairs keep their colour name). |
| 2026-10-22 | Large text (font scale ≥ 1.5): Home rows put the totals under the name instead of beside it (at 200% the count otherwise squeezed the name to one letter per line). History rows follow PNG 08: "date, time · duration" on the left, steps and distance on the right; one test now looks for " · 0 min" instead of "0 min · " (the row's wording changed with the design, not a Material detail). Menu entries are text only (PNG 11); the unused archive/delete/unarchive icons were removed. |
| 2026-10-22 | Existing sessions get distance where Health Connect still allows the read. Whether the 30-day window is anchored to the first steps grant or the later distance grant is not stated in the docs; to be checked on the phone in Phase 4. |
| 2026-10-24 | Phase 3 plan approved: restyle every screen, "Discard changes?", Appearance card; checked on the phone in the test app with sample pairs. |
| 2026-10-24 | Phase 3 (screens): every screen uses `SilverScreen` (backdrop edge to edge, round Back/Close/action buttons, small title) and `ScreenColumn`. Home: "My shoes" heading, everything scrolls under the gear, "Show archived" is a switch card under the list, "Add shoe" floats bottom end. Pair page: Edit moved into the ⋮ menu (Edit, Archive, Delete, PNG 11); this pair's session is a card titled "Session running since …" with the live count; another pair's session shows a card with Start (which asks to switch). Form: X closes, Save in the top bar and under the fields (PNG 05). Unmocked screens (privacy, licences, currency list, date picker) use the same cards and pop-up colours. The Health Connect explanation page follows PNG 22 (text stays steps-only until Phase 4). |
| 2026-10-24 | "Discard changes?" compares the form with what it started from (the saved pair, or a blank form with the default currency), so undoing an edit by hand doesn't ask. Both snapshots survive the app being closed in the background. The phone's Back asks too; the first Back after typing only closes the keyboard (Android behaviour). |
| 2026-10-24 | Appearance card sits second in Settings (after New pairs, before Units in Phase 5): "Theme" with Light / Dark / System. Choosing the current option again does nothing. Not mocked up; follows the Units card in PNG 21. |
| 2026-10-24 | Test app only: Settings → Test build → "Add sample pairs" (src/debug, `SampleData`; release has a no-op). Adds five pairs (one archived) and past sessions through `ShoeRepository` and `SessionManager` (a second SessionManager with its clock moved back), then starts a session. Their step counts are real Health Connect reads for those times. |
| 2026-10-24 | `DataStoreSettings` takes an optional file name and lifetime so the restart test (SettingsStoreTest) uses its own file and never the app's. |
| 2026-10-24 | Phase 4 plan approved (run at high effort). Distance data only; nothing new on screen except the permission request and wording. |
| 2026-10-24 | Checked against connect-client 1.1.0 itself and the official guides: `DistanceRecord.DISTANCE_TOTAL` (aggregate, `Length` in metres), permission `android.permission.health.READ_DISTANCE`. The "Read aggregated data" guide says steps and distance totals are de-duplicated across apps by the user's priority (T36). The "Read data" guide says the 30-day limit runs from "when any permission was first granted" (whether per permission is not stated: checked on the phone with the test app's "How far back can it read?"). |
| 2026-10-24 | Schema v2: segments gain `distance_m` (REAL, metres, null = unknown), `distance_status` (same values as steps, default PENDING) and `distance_read_utc`, plus an index on `distance_status`. Migration 1 → 2 only adds columns; no destructive fallback. Existing segments start PENDING and are backfilled where Health Connect allows; older reads fail and settle UNAVAILABLE after 30 days. |
| 2026-10-24 | Distance has its own status and is read independently of steps: refusing distance never holds steps back, and distance is read even without the steps permission. One rule set (`StepReconciler.rule`) serves both. Totals: `total_distance_m` per pair and per session is SQL SUM, null when nothing is known (shown "–", never 0). |
| 2026-10-24 | Health Connect reads per run stay at 100 in total (the official rate-limit guide gives no numbers). With both allowed, steps get up to 50 and distance the rest, so a day of provisional step re-checks can't hold the distance backfill back. Health Connect errors (including rate limits) leave the value for the next run. |
| 2026-10-24 | "Allow" now asks for steps and distance together. People who already allowed steps (everyone on v0.1.0) have no in-app way to allow distance until the "Show distance" card (PNG 03) in Phase 5; meanwhile Health Connect's own app permissions screen works. Wording updated from the mockups: Allow card (PNG 02), Settings step counting (PNG 21), "Why we read your activity" (PNG 22), privacy statement and PRIVACY.md (effective 0.2.0). |
| 2026-10-24 | Test app only: Settings → Test build → "Distance per pair" (stored totals) and "How far back can it read?" (whether Health Connect answers or refuses steps and distance reads 29–40 days back; shows no values). |
| 2026-10-24 | Night mode uses the "Balanced" palette (steel navy with a darker navy tint, then 15% darker), chosen by the product owner from a side-by-side canvas of the built night mode, their navy SVG and this middle ground. Colours only: layout, accent gradient, step lavender, danger colours and shoe colours unchanged. The canvas's 8% card hairline border was left out. Values in DESIGN.md section 3. |
| 2026-10-24 | Phase 5 plan approved. Distance shows on Home rows, the running-session card ("About 2.4 km", read live like steps), the pair's stats (total, average under steps, cost per km or mile) and History rows; "–" when unknown, never 0; "About" while any part may still change. One decimal with the locale's separators. |
| 2026-10-24 | Units: Settings → Units → Distance (Kilometres / Miles). Until chosen the phone's region decides, like the currency: miles in the US, UK, Liberia and Myanmar, kilometres elsewhere. Only the display changes; distances stay stored in metres. TalkBack reads "kilometres" / "miles" in full. |
| 2026-10-24 | "Show distance" card on Home (PNG 03) when steps are allowed but distance isn't. Allow asks Health Connect for distance only; Not now hides the card for good (stored in settings); Settings → Step counting keeps an "Allow distance" button. |
| 2026-10-24 | CSV: "Total distance in metres" (pairs) and "Distance in metres" (sessions) in whole metres, always metric whatever the app shows, empty when unknown; each with a "Distance status" column (final / provisional / counted later) like steps. Header names stay plain words (the CSV header check allows only letters, digits and spaces, so not "distance_m"). |
| 2026-10-25 | Phase 6 plan and wording approved (mockup frames 12, 13, 29, read from the mockups artifact since their PNGs are not in docs/design). "+ Add past session" in the History card header opens a full-screen form: the pair (fixed), Date (last 30 days), Start and End (Material's time input), a preview of what Health Connect has for that time, and the help text. Not offered for archived pairs (they can't start sessions either). |
| 2026-10-25 | Schema v3: `sessions.added_later` (INTEGER NOT NULL DEFAULT 0); migration 2 → 3 only adds the column, so every existing session counts as recorded live. A past session is saved by `SessionManager.addPast` in one transaction: ENDED, end reason USER, one closed segment left PENDING for StepReconciler (steps and distance follow the normal rules: "About" for a day, then final). The form reconciles once right after saving so the pair's page shows the steps on return. |
| 2026-10-25 | Past session rules (checked live in the form and again in `addPast`): end after start (no sessions across midnight), not in the future, start no more than 30 days ago (`SessionManager.PAST_LIMIT_MILLIS` = the reconciler's history limit), and no shared time with any session of any pair; touching ends are allowed, and a running or paused session counts as reaching into the future. Repeating a save finds the first one as an overlap, so it is never stored twice. |
| 2026-10-25 | Deleting: only ended "added later" sessions, through `SessionManager.deleteAddedLater` (the SQL itself also requires `added_later = 1`). Their History row has a Delete button (TalkBack: "Delete the session from …") that asks "Delete this session?" first. CSV session status reads "ended (added later)"; no new column. |
| 2026-10-25 | Design review (product owner's yes/no on real phone screenshots), added to the Phase 6 branch: costs keep only the currency's decimals from one whole unit up ("£190.55"; extra decimals only below that); totals settled at zero read "No steps yet" (Home) / "No steps" (History) in muted grey; History rows drop the distance line while none is known; Stats put "Some counted later" on a note line; the running card says "No steps yet" instead of "About 0"; Add past session's help text sits on the card; a running or paused session's card comes above Stats; "pair" throughout ("Add pair", "Edit pair", page title "Pair"; Home keeps "My shoes"); card labels in sentence case. Declined: "under 0.1 mi". The suspected "Add shoe covers Show archived" bug was not one: the list's bottom space already lets the switch scroll clear. DESIGN.md updated. |
| 2026-10-25 | Draft wording added beyond the approved list, to confirm on the phone: "Pair", "Date", "Start", "End", picker titles "Date of the session" / "Start time" / "End time", preview "Checking Health Connect…", "Health Connect has no steps for this time.", "Steps are counted once Health Connect can be read.", and for a running session "This overlaps the session for … that is still going. Choose another time." |

## Open items carried to later phases
- SessionDao/ActiveSessionDao write methods must be called only via SessionManager/RoomSessionStore (convention; not enforced by visibility).
- Open segments are excluded from totals (the Home card's "so far" is display-only).
- Re-verify `HealthDataSdkService`'s caller check (bytecode) whenever connect-client is upgraded.
- After any build-tools upgrade: re-verify on-device tests keep app data (the keep-installed property is an IDE-injected one).
- Gradle 10 deprecation warning (`Configuration.setVisible`) comes from AGP/KGP plugins, not our scripts; recheck on plugin updates.
- Android 9–13 with the Health Connect app installed (needs a real phone or a Play Store emulator with a Google sign-in) was not tested; the "not installed" path was. Same for the legacy NFC entry with a real tag.
- Manual checks before each release: docs/MANUAL_CHECKS.md (TalkBack, restart, airplane mode, watch, lock, export).
- A live timer on the session card was left out (M1); see BACKLOG for further ideas.

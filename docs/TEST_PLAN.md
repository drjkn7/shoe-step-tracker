# Test plan

T01–T30 cover Milestone 1 (v0.1.0); T31–T54 are new in Milestone 2 (v0.2.0) and come after them.

Status: ☐ not run · ✅ passed · ❌ failed. Phase = when it first becomes testable.

| ID | Scenario | Expected | Phase | Status |
|---|---|---|---|---|
| T01 | First run and permission | Rationale shown, permission granted, Home appears | 5 | ◐ banner + rationale automated (StepsScreensTest); grant on phone manual |
| T02 | Permission denied or revoked | App usable; "Steps counted later" with an Allow action | 5 | ◐ automated (StepsScreensTest); revoke on phone manual |
| T03 | Add shoe validation (empty, 200 chars, negative price) | Clear messages; valid saves | 3 | ✅ (ShoeScreensTest) |
| T04 | Register a blank tag | Records written and read back; shoe linked | 6 | ✅ automated + real tag on Pixel 9 Pro (Android 17), product owner 2026-10-18 |
| T05 | Lock a tag | Warning first; overwrite fails afterwards | 6 | ◐ warning + lock request automated; real lock manual (use a spare tag) |
| T06 | Tap with app closed | Opens the right shoe; session running; haptic | 6 | ✅ automated routing + real tag on Pixel 9 Pro (Android 17), product owner 2026-10-18 |
| T07 | Tap with app open | Session starts; no duplicate screens | 6 | ✅ automated + real tag on Pixel 9 Pro (Android 17), product owner 2026-10-18 |
| T08 | Manual Start | Same state as a tag start | 4 | ✅ (SessionScreensTest, TagScreensTest) |
| T09 | End after ~200 steps | Steps close to Health Connect; totals update | 5 | ☐ |
| T10 | Pause and resume | Paused steps excluded; two segments | 5 | ☐ |
| T11 | Second tap | Pause / End / Cancel; Cancel changes nothing | 6 | ✅ (TagCoordinatorTest, TagScreensTest) |
| T12 | Tap another shoe | Switch prompt; never two active sessions | 6 | ✅ (TagCoordinatorTest, TagScreensTest) |
| T13 | Double-read within 2 s | Treated as one tap | 6 | ✅ (DebouncerTest, TagCoordinatorTest) |
| T14 | Unknown or blank tag | "New tag": link to an existing pair or add a new one; outside tap cancels | 6 | ✅ (TagCoordinatorTest, TagScreensTest) |
| T15 | Malformed payload (wrong MIME, bad UUID, oversize) | Ignored safely; no crash | 6 | ✅ (TagFormatTest, TagCoordinatorTest) |
| T16 | Kill app mid-session | Session still running | 4 | ✅ (SessionManagerDbTest close/reopen; release APK force-stop on Android 13 emulator) |
| T17 | Reboot mid-session | Session still running | 4 | ☐ |
| T18 | Airplane mode full flow | Everything works | 5 | ☐ |
| T19 | Provisional becomes final | After 1 day and app reopen; until then the count follows Health Connect and may go up or down (product owner, 2026-10-18) | 5 | ◐ rules automated (StepReconcilerTest: later counts replace earlier ones, up or down); final check on phone manual |
| T20 | Time zone / DST change | Durations and timestamps correct | 4 | ◐ automated (SessionScreensTest, CsvExporterTest); manual check in MANUAL_CHECKS.md |
| T21 | Phone + watch both write steps | Not double counted | 5 | ☐ |
| T22 | Cost per step edge cases | Dash if no price or zero steps; 0 for free shoes | 2 | ✅ (CostCalculatorTest; UI: PairStatsScreenTest) |
| T23 | CSV injection | Cell neutralised | 7 | ✅ (CsvTest, CsvExporterTest, ExportDbTest) |
| T24 | Backup exclusion | Database not in backups | 1 | ✅ (bmgr backupnow on Android 13: "Backup is not allowed"; exclude-all rules incl. iPhone transfer) |
| T25 | TalkBack, 200% font, RTL | Labelled, unclipped, mirrored | 8 | ◐ labels, 48dp, 200% font, RTL mirroring automated (HardeningScreensTest); TalkBack manual (MANUAL_CHECKS.md) |
| T26 | 40 shoes, 2,000 sessions | Smooth scrolling | 8 | ✅ (HardeningScreensTest: pair page with 2,000 sessions opens in < 3 s, history pages of 50; also on the R8 build) |
| T27 | Merged manifest audit | No INTERNET; only READ_STEPS and NFC | 1 | ✅ (automated; re-run in Phase 8) |
| T28 | Archive / unarchive / delete | History kept on archive, removed on delete | 3 | ✅ (ShoeScreensTest + DatabaseTest) |
| T29 | Same UUID on two shoes | Blocked | 6 | ✅ (DatabaseTest, TagDbTest, TagScreensTest) |
| T30 | Android 9-13 without Health Connect | Onboarding explains and links to install | 5 | ✅ (real check on Android 13 emulator: "Get Health Connect" shown; opens Play Store or, without it, the web page) |

## Milestone 2 (v0.2.0)

| ID | Scenario | Expected | Phase | Status |
|---|---|---|---|---|
| T31 | Session with distance in Health Connect | Distance per session, totals and average correct | 4–5 | ✅ DistanceScreensTest (Home row, pair stats, average, History rows, live "About" on the session card), DistanceReconcilerTest; with real Health Connect data on the phone before release |
| T32 | Switch km ↔ miles | Every screen updates; stored metres unchanged | 5 | ✅ DistanceScreensTest (Settings → Units → Miles: Home, pair page and cost per mile update; stored metres unchanged), SettingsStoreTest (kept after restart), DistanceUnitTest (region default) |
| T33 | Steps allowed, distance refused | Steps unaffected; distance "–"; "Show distance" card | 4–5 | ✅ DistanceReconcilerTest (steps unaffected, distance waits, filled once allowed); DistanceScreensTest (card shown, Not now hides it for good, Settings still offers "Allow distance"). The Allow button opens Health Connect's own screen: manual check on the phone |
| T34 | No distance data for a session | "–", not 0; left out of totals; cost per km/mile shows a dash | 4–5 | ✅ DistanceReconcilerTest, StepsDbTest (totals null, not 0); DistanceScreensTest (dashes on the pair page, never "0.0 km"); CsvExporterTest (empty cell) |
| T35 | Provisional distance | "About" for one day, may go up or down, then final | 4 | ✅ DistanceReconcilerTest (same rules as steps, shared code), StepsDbTest end to end |
| T36 | Phone + watch both write distance | Not counted twice (manual) | 4 | ◐ uses Health Connect's de-duplicated total (official aggregate guide); manual check with a watch before release |
| T37 | Database v1 → v2 | All shoes, sessions, segments and tags kept (MigrationTest + install over v0.1.0) | 4 | ◐ MigrationTest (every table and value kept, distance PENDING) and the test app upgraded on the Pixel with its data; install over v0.1.0 with the release key in Phase 8 |
| T38 | CSV export | Distance columns in metres present; injection protection intact | 5 | ✅ CsvExporterTest, ExportDbTest ("Total distance in metres" / "Distance in metres" in whole metres, always metric, empty when unknown, plus "Distance status"; formula neutralising unchanged) |
| T39 | Cost per km/mile edge cases | Dash with no price or zero distance; 0 for free shoes | 5 | ✅ CostCalculatorTest (no price, no or zero distance, negative, unknown currency, free shoes, yen, tiny distances), FormattersTest, DistanceScreensTest (€ per km and per mile on screen) |
| T40 | Notification permission (13+) | In-app ask, then system prompt; refusing leaves the app usable; no prompt below 13 | 7 | ✅ NotificationScreensTest (in-app question, Not now, nothing to ask without a session or below 13); Android's own prompt checked by hand (MANUAL_CHECKS) |
| T41 | Notification lifecycle | Appears on start (button and tag); updates on pause/resume; removed on end, switch, archive, delete | 7 | ✅ SessionNotifierTest (real notification on the tablet: start, pause/resume, end, switch, archive, delete, private on the lock screen), SessionNoticeTest (timer leaves out pauses) |
| T42 | Notification actions | Pause/Resume act at once; End opens the confirmation; stale or foreign session ids ignored | 7 | ✅ SessionNoticeTest (stale, foreign and repeated ids change nothing), NotificationScreensTest (End asks first; stale request ignored) |
| T43 | Notification setting off | No notification; turning it on shows one for a running session | 7 | ✅ SessionNotifierTest, NotificationScreensTest (Settings switch, blocked note), SettingsStoreTest |
| T44 | Restart mid-session | Notification returns when the app is opened (manual) | 7 | ☐ manual (MANUAL_CHECKS: session notification) |
| T45 | Discard changes | Asked only after an edit; Keep editing keeps the form | 3 | ✅ RestyledScreensTest (Close and Back, add and edit) |
| T46 | Restyled screens | 200% font, TalkBack, RTL and contrast pass | 3, 8 | ◐ automated: components (ComponentGalleryTest) and every screen (RestyledScreensTest: 48 dp and labels, light/night, 100% and 200%), RTL (HardeningScreensTest); TalkBack pass by hand in Phase 8 |
| T47 | Upgrade from v0.1.0 | Installs over the top with the same key; nothing lost | 8 | ✅ signed 0.2.0 installed over v0.1.0 on the Pixel, all pairs and steps kept (2026-10-26) |
| T48 | Theme setting | Light, Dark and System apply at once; System follows the phone; choice survives a restart | 1, 3 | ✅ ThemeScreensTest, ThemeModeTest, RestyledScreensTest (Settings control), SettingsStoreTest (restart) |
| T49 | Night mode legibility | Every screen readable in night; contrast per DESIGN.md section 8; no light-only colours left | 3 | ◐ token contrast (ContrastTest) and every screen in night (RestyledScreensTest); compare with the night mockups once they are in docs/design |
| T50 | Add past session | Saved for the chosen pair; "Added later" in History; steps and distance filled by the reconciler | 6 | ✅ PastSessionTest (ended, added later, one PENDING segment, nothing becomes active), PastSessionScreensTest (preview from Health Connect, Save, "Added later" row, steps counted), SessionManagerDbTest (real queries) |
| T51 | Past session overlaps | Blocked with the other pair's name and times; nothing saved | 6 | ✅ PastSessionTest (any pair, touching ends allowed, a repeated Save finds the first one), PastSessionScreensTest (message names "Daily Court" and its times, Save disabled), SessionManagerDbTest |
| T52 | Past session invalid times | Future, end before start, or older than Health Connect allows (30 days back): blocked with a clear reason | 6 | ✅ PastSessionTest (each reason and the exact limits), PastSessionScreensTest (end in the future); the date picker offers only the last 30 days |
| T53 | Past session vs running session | Cannot overlap the session that is running now | 6 | ✅ PastSessionTest (the running session reaches into the future and is left untouched), SessionManagerDbTest |
| T54 | Past session in totals and export | Counted in Stats, cost per step/km and CSV like any session; an "Added later" session can be deleted after a confirmation, normal sessions cannot | 6 | ✅ PastSessionScreensTest (totals, Delete only on the added row, confirmation), PastSessionTest and SessionManagerDbTest (live sessions refused, segments go too), CsvExporterTest ("ended (added later)"), MigrationTest (v2 → v3) |

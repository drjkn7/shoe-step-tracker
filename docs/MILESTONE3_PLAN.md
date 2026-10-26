# Milestone 3 plan (draft)

**Status:** draft, 26 Oct 2026. Nothing here is approved or being built. Scope is decided after the
product owner's few weeks of real-world testing of v0.2.0; their feedback comes first and may reorder
or drop anything below. Feature ideas already planned for M3 live in [BACKLOG.md](BACKLOG.md).

## Source
A read-only review of v0.2.0 by the four team agents (designer, developer, tester, security reviewer)
on 26 Oct 2026. No High or Critical issues were found. One developer finding (R8 off for release) was
checked and was wrong: R8 is on (`optimization { enable = true }` in app/build.gradle.kts) and CI tests
the staging (R8) build, so it is not listed.

## Top recommendations (where the agents agree)
1. Session controls: make Pause the main button and End a quieter one; show live steps in the notification.
2. Add the missing High tests: upgrade 0.1.0 → 0.2.0, and Health Connect permission removed mid-session.
3. Accessibility pass: TalkBack, 200% font size, dark mode.
4. Close the SessionManager bypass (`ShoeRepository.setArchived`).

## Work items

Priority: P1 = do first in M3, P2 = do in M3 if capacity, P3 = later.

### Design (designer)
| ID | Pri | Item | Where |
|---|---|---|---|
| D1 | P1 | Pause becomes the primary button while a session runs; End becomes secondary. | SessionControls.kt:54-55 |
| D2 | P1 | Selected colour swatch: tick and ring readable on light swatches (Amber, Teal, Green) and in the light theme. | ShoeFormScreen.kt:~340-346 |
| D3 | P1 | Loading spinner gets a spoken description ("Loading your pairs"). | HomeScreen.kt:147-151 |
| D4 | P2 | Notification: label End as "End…" (it asks first), show last-known steps, tie accent colour to the theme. | SessionNotifier.kt:88, 102, 144 |
| D5 | P2 | Errors shown next to the field, focus moves to the first error; announce changes on Home, detail and settings. | strings.xml:73-83 |
| D6 | P3 | Disabled buttons: flat grey instead of 45% fade (contrast). | Buttons.kt:171 |
| D7 | P3 | "Show archived": proper empty state; move the switch near the top. | HomeScreen.kt:152, 161 |
| D8 | P3 | Use theme text styles only (no hard-coded font sizes). | HomeScreen.kt:203, 222, 267-271 |
| D9 | P2 | Short first-run guide (NFC, Health Connect, notifications); "saved" / "session ended" confirmation; clear "no steps: permission off" state. | new |

### Engineering (developer)
| ID | Pri | Item | Where |
|---|---|---|---|
| E1 | P1 | Remove `setArchived` from ShoeRepository (or make it internal); tests and SampleData use `SessionManager.setArchived`. Add a check that only the session package writes sessions/archive. | data/ShoeRepository.kt:81 |
| E2 | P2 | Trigger the on-open step count from one place only (saves Health Connect reads). | MainActivity.kt:48-53, HomeViewModel.kt:111 |
| E3 | P2 | One app-wide coroutine scope in AppContainer for tag taps, notification actions and step counts, so a closing screen can't drop a tap. | MainActivity.kt:122, SessionActionReceiver.kt:47 |
| E4 | P3 | Split ShoeDetailScreen.kt (530 lines) and ShoeFormScreen.kt (460 lines). | ui/ |

### Testing (tester)
| ID | Pri | Item |
|---|---|---|
| T1 | P1 | MigrationTest: v1 → v3 in one go, with a realistic v1 database (several pairs, sessions, steps). |
| T2 | P1 | Health Connect permission revoked mid-session, and Health Connect missing/disabled: no crash, steps shown as unavailable. |
| T3 | P2 | Sessions across midnight and across a clock change (Europe/London, March and October): durations, CSV, stats. |
| T4 | P2 | Rotation / process death on the shoe form, add-past-session form and Discard dialog. |
| T5 | P2 | Tag edge cases: empty UUID, upper vs lower case, NUL / very long text, tag pulled away mid-read, locked tag on write. |
| T6 | P2 | Notifications: permission denied (Android 13+), Pause/End after the app was killed, notification gone after ending in-app. |
| T7 | P2 | ViewModel unit tests (few exist today). |
| T8 | P3 | CI on a newer Android version as well as API 30; tablet / landscape. |

### Security (security reviewer)
| ID | Pri | Item |
|---|---|---|
| S1 | P2 | Update SECURITY_REVIEW.md for 0.2.0 and define SEC-05 and SEC-08 (currently undefined). |
| S2 | P2 | Dependency vulnerability scan (e.g. OWASP dependency-check). Needs owner approval to add a tool. |
| S3 | P3 | Accepted risk: on Android 9–16 another app can fake a tag tap (worst case: start/pause own sessions). Optionally confirm before a tag starts a session. |
| S4 | P3 | Mention in the privacy note that the shoe name shows in the notification when unlocked. |

## Owner's phone checklist (during the testing weeks)
1. Tap a tag to start; tap again to pause/resume; end. Check totals.
2. Tap a second pair's tag mid-session; check the switch prompt.
3. Walk 10 minutes; compare steps/distance with Health Connect.
4. Turn off Health Connect permission mid-session: no crash, shows "unavailable".
5. Deny then allow notifications; use Pause and End from the shade with the app closed.
6. Force-stop the app mid-session; reopen; session survived.
7. Start before midnight, end after; check both days.
8. Change time zone; check session times.
9. Rotate the phone while editing a shoe or past session.
10. Tap an unknown, blank or someone else's NFC tag.
11. Export the CSV and open it in a spreadsheet.
12. Switch Light / Dark / System theme and units.

## Open decisions for the product owner
- Which of the BACKLOG M3 features (widget, auto-pause, charger prompt, max session length, background
  reads) to include alongside these fixes. Background reads and WorkManager need a new permission /
  dependency (approval required).
- Approve adding a dependency-scanning tool (S2).

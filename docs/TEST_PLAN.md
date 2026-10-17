# Test plan – Milestone 1

Status: ☐ not run · ✅ passed · ❌ failed. Phase = when it first becomes testable.

| ID | Scenario | Expected | Phase | Status |
|---|---|---|---|---|
| T01 | First run and permission | Rationale shown, permission granted, Home appears | 5 | ☐ |
| T02 | Permission denied or revoked | App usable; "Waiting for step data" with a fix action | 5 | ☐ |
| T03 | Add shoe validation (empty, 200 chars, negative price) | Clear messages; valid saves | 3 | ☐ |
| T04 | Register a blank tag | Records written and read back; shoe linked | 6 | ☐ |
| T05 | Lock a tag | Warning first; overwrite fails afterwards | 6 | ☐ |
| T06 | Tap with app closed | Opens the right shoe; session running; haptic | 6 | ☐ |
| T07 | Tap with app open | Session starts; no duplicate screens | 6 | ☐ |
| T08 | Manual Start | Same state as a tag start | 4 | ☐ |
| T09 | End after ~200 steps | Steps close to Health Connect; totals update | 5 | ☐ |
| T10 | Pause and resume | Paused steps excluded; two segments | 5 | ☐ |
| T11 | Second tap | Pause / End / Cancel; Cancel changes nothing | 6 | ☐ |
| T12 | Tap another shoe | Switch prompt; never two active sessions | 6 | ☐ |
| T13 | Double-read within 2 s | Treated as one tap | 6 | ☐ |
| T14 | Unknown or blank tag | "Register this shoe?" only | 6 | ☐ |
| T15 | Malformed payload (wrong MIME, bad UUID, oversize) | Ignored safely; no crash | 6 | ☐ |
| T16 | Kill app mid-session | Session still running | 4 | ☐ |
| T17 | Reboot mid-session | Session still running | 4 | ☐ |
| T18 | Airplane mode full flow | Everything works | 5 | ☐ |
| T19 | Provisional becomes final | After 30+ min and app reopen; never decreases | 5 | ☐ |
| T20 | Time zone / DST change | Durations and timestamps correct | 4 | ☐ |
| T21 | Phone + watch both write steps | Not double counted | 5 | ☐ |
| T22 | Cost per step edge cases | Dash if no price or zero steps; 0 for free shoes | 2 | ☐ |
| T23 | CSV injection | Cell neutralised | 7 | ☐ |
| T24 | Backup exclusion | Database not in backups | 1 | ☐ |
| T25 | TalkBack, 200% font, RTL | Labelled, unclipped, mirrored | 8 | ☐ |
| T26 | 40 shoes, 2,000 sessions | Smooth scrolling | 8 | ☐ |
| T27 | Merged manifest audit | No INTERNET; only READ_STEPS and NFC | 1 | ✅ (automated; re-run in Phase 8) |
| T28 | Archive / unarchive / delete | History kept on archive, removed on delete | 3 | ☐ |
| T29 | Same UUID on two shoes | Blocked | 6 | ☐ |
| T30 | Android 9-13 without Health Connect | Onboarding explains and links to install | 5 | ☐ |

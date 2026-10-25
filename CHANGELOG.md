# Changelog

## 0.2.0 – Milestone 2

A new look, walking distance and a session notification. Your pairs and sessions from 0.1.0 are kept.

- **New look**: "Silver Matte" design in a light and a night theme; Settings → Appearance chooses Light,
  Dark or the phone's setting. Bundled Chakra Petch font (never downloaded). Larger text and tablets are
  handled better (content is centred and capped in width).
- **Distance**: walking distance from Health Connect (`READ_DISTANCE`, asked for separately) per session,
  in each pair's totals and average, and as cost per km or mile. Settings → Units chooses kilometres or
  miles (the phone's region decides until then). CSV exports gain distance columns.
- **Session notification**: while a session runs, a notification shows a timer of walking time with Pause
  or Resume and End session (End asks first, in the app). Asked for once before Android 13's prompt; can
  be turned off in Settings → Notifications. Private on the lock screen ("Session running").
- **Add past session**: forgot to tap? Add a session for a past time (up to 30 days back) from the pair's
  History; it can't overlap another session. Its steps and distance are filled in from Health Connect.
  Sessions added this way are marked "Added later" and can be deleted.
- **Smaller things**: "Discard changes?" when leaving the pair form with unsaved edits; the running
  session's card comes first on its pair's page; the app says "pair" throughout.
- **Privacy and security**: still no internet permission. New permissions are only `READ_DISTANCE` and
  `POST_NOTIFICATIONS`; the notification's buttons can't be used by other apps.

Requires Android 9 or newer, as before.

## 0.1.0 – first release (Milestone 1)

Counts steps per pair of shoes. Everything stays on your phone; the app has no internet access.

- **Pairs**: add, edit, archive and delete pairs with brand, model, type, price, purchase date and colour.
- **Sessions**: Start, Pause, Resume and End on each pair's page or the Home card; switching pairs ends the
  old session and starts the new one. Sessions survive the app being closed or the phone restarting.
- **Steps from Health Connect** (read-only, steps only): filled in when the app is open; "About N steps so
  far" while a session runs; counts may still change for one day, then become final.
- **NFC tags**: link a sticker to a pair; tapping it starts the session (even with the app closed), asks
  Pause / End / Cancel on a running pair, resumes a paused one and asks before switching pairs. Optional
  permanent lock.
- **Stats**: total steps, sessions, average per session, walking time, last worn and cost per 1,000 steps.
- **Settings**: default currency, step-counting status, export pairs and sessions as CSV files to a place
  you choose, privacy statement, open-source licences.
- **Privacy and security**: no internet permission, no backups or device transfers (including to iPhone),
  only READ_STEPS and NFC permissions, strict checking of tag contents, spreadsheet-formula protection in
  exports, release build shrunk and obfuscated.

Requires Android 9 or newer. Health Connect is built into Android 14+ and installable on Android 9–13.

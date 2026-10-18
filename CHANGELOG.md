# Changelog

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

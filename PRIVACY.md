# Privacy statement

_Shoe Step Tracker, effective from version 0.2.0 (distance and the session notification added; 0.1.0
read step counts only)._

## Short version

Your data never leaves your phone. The app cannot connect to the internet.

## What the app reads

- **Step counts and walking distance from Health Connect** (permissions `READ_STEPS` and
  `READ_DISTANCE`), only for the time ranges of
  your own sessions. It does not read any other health data and never writes to Health Connect.
- **NFC tags** that you tap, to recognise which pair of shoes you are wearing. The app only
  reads a random identifier it wrote to the tag itself and ignores everything else on a tag.

## What the app stores

On your phone only, in the app's private storage:
- the shoes you add (name, brand, model, type, price, purchase date, colour),
- your sessions (start and end times, time zone, step counts and distances),
- your settings (for example default currency).

The app does **not** collect your location, contacts, photos, or any identifier of you or your
device.

## What the app shares

Nothing. The app has no internet permission, no accounts, no analytics, no advertising and no
third-party tracking libraries. Data is excluded from Android cloud backups and device transfer.

The only way data leaves the app is when **you** choose to export a CSV file, to a location you
pick in the system file picker.

While a session runs, the **session notification** (permission `POST_NOTIFICATIONS` on Android 13+)
shows the pair's name and times on your phone. The lock screen shows only "Session running". You
can turn it off in the app's Settings or in Android's notification settings.

## Deleting your data

Delete a shoe to remove it and its sessions, or uninstall the app to remove everything. You can
revoke the step and distance permissions at any time in Health Connect settings.

## Contact

Questions: open an issue on the project's GitHub repository.

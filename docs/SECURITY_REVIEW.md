# Security review – Milestone 1 (v0.1.0)

Done in Phase 8 (2026-10-18) against the merged release manifest, the code on `main` and checks on a
Pixel 9 Pro (Android 17) and an Android 13 emulator. "Audit" = `verifyReleaseManifest` (part of
`./gradlew check`, fails the build on any deviation).

The SEC numbers come from the project's planning; SEC-05 and SEC-08 are referenced in the working
agreement but not defined anywhere in the repository, so they are not claimed below. The areas they
most likely cover (health-data handling, data leaving the phone) are reviewed under "Other checks".

| ID | Topic | Result | Evidence |
|---|---|---|---|
| SEC-01 | No network access | ✅ | Audit: no `INTERNET` in any variant. Health Connect and NFC work locally. |
| SEC-02 | No backups / transfers | ✅ | `allowBackup=false`; exclude-all rules for cloud backup, device transfer and (new) iPhone transfer. `bmgr backupnow` on Android 13: "Backup is not allowed" (T24). |
| SEC-03 | Only allowlisted exported components, guarded where possible | ✅ | Release export allowlist (audit): MainActivity (launcher); NfcTagEntry (guarded by `DISPATCH_NFC_MESSAGE`, Android 17+; fake tag intent from shell refused); NfcTagEntryLegacy (Android 9–16 only, accepted risk: a fake tag intent can only start/pause/switch the user's own sessions); PermissionsRationaleActivity (static text); ViewPermissionUsageActivity (guarded by `START_VIEW_PERMISSION_USAGE`); HealthDataSdkService (library; code rejects callers other than Health Connect); ProfileInstallReceiver (guarded by `DUMP`). Not exported (M2 Phase 7): `SessionActionReceiver` (notification Pause/Resume) and the `NotificationEntry` alias (End session), reached only through the app's own immutable PendingIntents; both act only on the session that is still active. |
| SEC-04 | Only allowlisted permissions | ✅ | Audit: `health.READ_STEPS`, `health.READ_DISTANCE` (M2), `POST_NOTIFICATIONS` (M2 Phase 7, approved 2026-10-22 and 2026-10-25), `NFC`, AndroidX-internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No storage permission (export uses the system file picker). |
| SEC-06 | No debug output in release | ✅ | R8 strips `Log.v/d/i`; the app's own code has no log or print calls at all; StrictMode only in `src/debug`. Release APK is not debuggable (`dumpsys package` flags). |
| SEC-07 | Untrusted input is validated | ✅ | Shoe form (ShoeValidator: control/bidi characters stripped, lengths in code points); SQL only with bound parameters; NFC tags through the strict TagFormat parser (size/record limits, exact v4 UUID, T15); stored currency re-validated on read; navigation ids validated in `Routes`. |
| SEC-09 / SEC-12 | No secrets in the repository | ✅ | `.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `*.apk`; `git ls-files` shows none. The release key is created and kept by the product owner only ([RELEASE.md](RELEASE.md)). |
| SEC-10 | Dependencies pinned and approved | ✅ | Every version pinned in `gradle/libs.versions.toml`; each added library approved by the product owner; GitHub Actions pinned to full commit SHAs. Licences: all Apache-2.0 except Checker Framework qualifiers (MIT), both shown in-app. |
| SEC-11 | Tapjacking and irreversible actions | ✅ | `filterTouchesWhenObscured` on the main window and every dialog; delete, end session, unlink tag and switch pairs ask first; tag lock warns before the tap. |

## Other checks

- **Health data is never logged or sent anywhere**: no log calls in app code; step counts are only read
  for the user's own session times; nothing leaves the phone except a CSV the user exports to a file
  they pick.
- **Exports**: every text cell quoted, formula-like cells neutralised (T23).
- **Release build**: R8 shrinks and obfuscates app code; enum names stored in the database are kept
  by a keep rule; the full on-device suite passes on the R8 "staging" build, and the real release APK
  passed an emulator smoke test (flows + 3,000 random events, no crash).
- **Not covered here**: a third-party penetration test, and a real Android 9–13 phone with the Health
  Connect app installed (the emulator has no Play Store; the "not installed" path was checked).

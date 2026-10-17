# Security policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub:
**Security** tab of this repository → **Report a vulnerability**
(GitHub private vulnerability reporting).

Please include:
- what the problem is and its impact,
- steps to reproduce (for NFC issues, the tag contents or how to create such a tag),
- the app version and Android version.

You should receive an acknowledgement within 7 days. Once a fix is released, we will credit
you in the release notes unless you prefer not to be named.

## Scope and design

The app is designed to be safe even though all of its code is public:
- No `INTERNET` permission; the build fails if one is ever added.
- Only two permissions: `NFC` and Health Connect `READ_STEPS`.
- NFC tags are treated as untrusted input and parsed by a strict, fuzz-tested parser.
- Health data, tag contents and shoe details are never logged.
- App data is excluded from Android backups.
- Release signing keys are never stored in this repository.

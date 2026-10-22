# Backlog

Ideas recorded here are **not** being built now. Each needs a product decision before work starts.
Milestone 2 (v0.2.0) took the session notification and the "Discard unsaved changes?" prompt from this
list; see [PROGRESS.md](PROGRESS.md).

## Planned for Milestone 3

| Idea | Notes |
|---|---|
| Homescreen widget | Show the active session / quick start. |
| Inactivity detection and auto-pause | Likely needs background work. |
| Charger prompt | Details to be decided. |
| Maximum session length | Details to be decided. |
| Background Health Connect reads | Would need `READ_HEALTH_DATA_IN_BACKGROUND`; today counts are read only while the app is open. |
| Background step reconciliation (WorkManager) | Would need a new dependency. |

## Later ideas

| Idea | Notes |
|---|---|
| Shoe photos | Would need image storage and picker. |
| 3D shoe models | |
| Cloud sync / accounts | Conflicts with the no-internet design; would need a major privacy review. |
| Location / routes | Explicitly excluded; would add a sensitive permission. |
| Retirement reminder at a step threshold | e.g. "this pair has done 800 km". |
| iOS version | |
| Sort and search the shoe list | Useful once there are many pairs. |

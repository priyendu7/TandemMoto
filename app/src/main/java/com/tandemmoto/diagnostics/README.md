# diagnostics

Local diagnostic logs for debugging field tests, with no cloud service or SDK.

- `AppLog.d/i/w/e(tag, message, error?)` writes to Logcat and to `RollingFileLog` in app-private
  storage (`files/logs/`, at most 2 × 1 MB). Debug builds keep every level in the file; release and
  QA builds keep Info and above.
- Uncaught exceptions are recorded by `CrashLogger` before the app crashes normally.
- Settings → **Export diagnostic logs** builds one file (app version, Android version, device model,
  then the logs) and opens the system share sheet via `LogExporter`. Logs leave the phone only
  when the user shares them this way.

## What not to log

Logs may be shared with anyone the user chooses, so never log personal data:
- no song titles, artists or file names (log counts, sizes and durations instead)
- no phone numbers, contact names or caller details (the PRD forbids reading them anyway)
- no full MAC addresses or device names of the partner's phone (log a short hash)

Development plan: Phase 0 (#18). Each feature PR logs its own events with a short tag
(`Link`, `Player`, `Voice`, …).

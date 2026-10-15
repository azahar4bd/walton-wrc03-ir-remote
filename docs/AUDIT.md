# Audit and implementation notes — 2026-10-15

Baseline: `e47ab8ef78ebaadf6cf4a7c8080a74d2db436e07`.
Reviewed Kotlin activities, NEC encoder/controller/hardware adapter, profile data,
scan plans/engine/scheduler/storage, resources, manifest, JVM tests, Gradle and CI.

## Findings and changes

- Native Kotlin/Views app, ConsumerIrManager, no backend/network permission. Preserve
  this architecture and the three legacy Walton profile IDs/code tables.
- Scanning already existed. Add a **sourced VEON profile** rather than rebranding
  Walton's generic 21-key table; details and limitations in `VEON_CODES.md`.
- QUICK previously assumed all profiles shared that sheet. It now scans each
  profile's own documented table, selected profile first. Dedicated VEON scanning
  prioritizes the requested key and covers all 256 command bytes without duplicates.
- Preferences were keyed only by command, overwriting another TV's same button.
  Read legacy keys and write per-command/on-wire-address/carrier keys; newest
  confirmation wins only within that identity. Malformed entries are ignored.
- Changing command/preset rebuilt the engine and discarded confirmations. A separate
  draft now retains them across rebuilds and Android saved-instance restoration.
  Explicit save is still required before leaving the scanner. Sweeps never auto-resume
  after recreation or leaving the foreground.
- Common-address scans could produce codes no shipped profile could replay. Saved
  unfamiliar addresses now get scan-only profiles, with unmapped buttons left empty.
  Saving selects the last confirmed code's matching profile; selection survives restart.
- Equivalent standard/extended addresses now match by their actual wire bytes.
- Failed transmissions could be marked found. Both engine and UI reject that state.
- Volume/channel hold sent repeat frames before the full command and sent a full
  frame only on release. Send the full command on touch-down, then repeats, no extra
  command on release. Long-press scanning is only for non-repeat keys; other targets
  are selectable by distinct button labels in the scanner.
- Removed misleading "wrong codes do nothing / cannot cause harm" UI claims:
  scanning can change settings or perform an unexpected action.
- Version 3 / `0.3.0-unverified`. CI gains explicit branch workflow-dispatch release,
  version/tag validation, mandatory APK signature verification, SHA-256 attachment,
  and signing-source-specific install warnings. Existing releases are not overwritten.

## Validation / remaining limitations

- Local Python resource/binding/package consistency and NEC reference checks.
- JVM regression tests cover VEON wire vectors, unmapped keys, scan coverage/order,
  legacy migration, per-address isolation, draft serialization, discovered profiles,
  carrier separation and failure-to-confirm prevention.
- Android compilation, full JVM suite and APK signature verification run in GitHub
  Actions: this sandbox has no JDK/Android SDK and restricts dependency network access.
- No physical IR emitter/TV or Android UI instrumentation available here. Matching
  real TV actions, OEM IR timing and activity interaction require device smoke testing.
- Only NEC and the listed address blocks are searched, not every protocol/address.
  A slow TV reaction can be mistaken for the next candidate: increase the interval,
  pause promptly and recheck the saved key against its intended function.
- Existing two-full-frame scanner behavior remains; some TVs may interpret two presses.
- Unsaved drafts are not retained after intentionally closing the scanner.
- Without a repository signing secret, releases use ephemeral signing keys. Updating
  an older differently signed install requires uninstalling it (which loses app data).

## Device smoke checklist

1. Install, check no-IR error on a non-emitter device; no scan result can be confirmed.
2. On an IR phone, select VEON, restart app, verify selection; test power/mute/volume.
3. Open scanner, select volume + and volume − (distinct labels), try VEON scan/pause.
4. Confirm one correct action, change target/preset, verify draft survives, then save.
5. Verify saved key is `✓`, correct profile is active, and Walton saved keys still exist.
6. Background while scanning: no further signals. Restore activity: draft kept, no auto-run.
7. Test volume hold: initial full command followed by repeats; release stops transmission.

# Project progress

Updated: October 22, 2026. This is the entry point for current work, not a release
history or a replacement for Git. Validate it against `git status` and the current
diff before resuming; working copies can contain newer or unrelated changes.

## Product baseline

FinTrack captures authorized Android notifications and user-scoped bank email,
then provides transaction classification, cards/accounts and cashflow analysis.
See [README](../README.md), [architecture](architecture/overview.md) and
[development](development/local.md) for behavior, boundaries and execution.

| Area | Current evidence | Remaining acceptance |
| --- | --- | --- |
| Android capture | FinTrack 1.3.7 (20): release HTTPS, redirect protection and credential-backup exclusions implemented; 81 JVM tests per debug/captureTest/release variant passed and packaged policies checked. Prior reboot/Doze evidence is retained in the detailed record. | Hosted CI, full lifecycle rerun, deployed HTTPS, real backup/transfer and physical/OEM acceptance remain. See [capture progress](android/capture-progress.md). |
| Backend/dashboard | Ingestion provenance and all four persistence suites passed against isolated PostgreSQL. The local runner and initializer reproduce setup/checks. See [validation evidence](development/persistence-progress.md). | Hosted CI has not run for the current uncommitted changes. No production migration, live OAuth or end-to-end synchronization was performed. |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: Android transport and credential-backup policy

- Owner: current chat; scope: release HTTPS, redirect/token protection and backup
  exclusions without changing financial ownership or pairing storage identities.
- Status: implemented and verified for local policy/artifact scope; 1.3.7 (20).
- Changes: Android network security configuration, central request policy,
  actionable HTTPS diagnostic, backup exclusions and release checks in CI. See
  [capture progress](android/capture-progress.md).
- Checks: 81 JVM tests per debug/captureTest/release variant, including loopback
  HTTP rejection, allowed debug HTTP, untrusted TLS rejection and pending-row
  preservation. All APK variants and isolated instrumentation assembled; actual
  manifests and optimized release XML policies were inspected.
- Limitations: no APK installation, real-backend request, backup restore/transfer,
  lifecycle rerun or hosted CI execution. Signing/production deployment is separate;
  prior remote backups are not deleted by the new exclusion policy.
- Next action: user reviews/commits the focused groups, checks hosted CI and then
  performs authorized physical-device/provider and deployment acceptance.
  No agent commit or push was performed. The prior backend/setup
  evidence remains in [its record](development/persistence-progress.md).

## Next priorities

1. Complete remaining Android acceptance using the detailed capture record.
2. Review and selectively version reusable local skills; do not publish personal
   configuration or private fixtures wholesale.
3. Repeat [the five-question evaluation](development/agent-evaluation.md) in a fresh
   clone after the user commits/transfers this follow-up; the existing-checkout
   evaluation is recorded, but is not fresh-clone evidence.

## Task continuity

Follow the [checkpoint protocol](development/agent-workflow.md#checkpoints-and-concurrency).
This file records current state; keep historical evidence in the owning area record.

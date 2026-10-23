# Project progress

Updated: October 21, 2026. This is the entry point for current work, not a release
history or a replacement for Git. Validate it against `git status` and the current
diff before resuming; working copies can contain newer or unrelated changes.

## Product baseline

FinTrack captures authorized Android notifications and user-scoped bank email,
then provides transaction classification, cards/accounts and cashflow analysis.
See [README](../README.md), [architecture](architecture/overview.md) and
[development](development/local.md) for behavior, boundaries and execution.

| Area | Current evidence | Remaining acceptance |
| --- | --- | --- |
| Android capture | FinTrack 1.3.5 (18): real isolated reboot, passive automatic recovery and explicit-opening idempotency verified; 70 JVM tests per variant, two SIGKILL/force-stop regressions and three host safety tests passed. | Full nine-case lifecycle suite not rerun. Doze, physical/OEM acceptance and production transport/backup checks remain. See [capture progress](android/capture-progress.md). |
| Backend/dashboard | Ingestion provenance and all four persistence suites passed against isolated PostgreSQL. The local runner and initializer reproduce setup/checks. See [validation evidence](development/persistence-progress.md). | Hosted CI has not run for the current uncommitted changes. No production migration, live OAuth or end-to-end synchronization was performed. |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: Android reboot acceptance

- Owner: current chat; scope: isolated Android recovery after a real emulator reboot.
- Status: verified for stated emulator scope; FinTrack 1.3.5 (18).
- Changes: captureTest-only gate release on a new boot, split instrumentation
  phases, passive host Room/WAL inspection and guarded reboot runner. See
  [capture progress](android/capture-progress.md).
- Checks: 70 JVM tests per variant, APK/test compilation, real boot-ID change,
  passive automatic recovery, explicit-opening idempotency, preserved synthetic
  owner/identity and two passing SIGKILL/force-stop regressions. Three host safety
  tests passed. Full nine-case lifecycle suite was not rerun.
- Limitations: one disposable emulator and unpaired fixtures; no physical device,
  real provider/backend, Doze or universal background-recovery guarantee.
- Next action: user reviews/commits the focused groups, then proceed with isolated
  Doze acceptance. No agent commit or push was performed. The prior backend/setup
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

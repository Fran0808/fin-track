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
| Android capture | FinTrack 1.3.6 (19): forced deep Doze passed with a live process and after SIGKILL, with passive recovery and opening idempotency; 70 JVM tests per variant, two SIGKILL/force-stop regressions and six host safety tests passed. | Full nine-case suite and real reboot not repeated on this revision. Natural sleep, independent worker scheduling, physical/OEM acceptance and production transport/backup checks remain. See [capture progress](android/capture-progress.md). |
| Backend/dashboard | Ingestion provenance and all four persistence suites passed against isolated PostgreSQL. The local runner and initializer reproduce setup/checks. See [validation evidence](development/persistence-progress.md). | Hosted CI has not run for the current uncommitted changes. No production migration, live OAuth or end-to-end synchronization was performed. |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: Android deep Doze acceptance

- Owner: current chat; scope: forced deep Doze with durable pending capture,
  both retaining the process and terminating it with real SIGKILL.
- Status: verified for stated emulator scope; FinTrack 1.3.6 (19).
- Changes: fixed-time captureTest-only gate, Doze mode in the guarded host runner,
  passive Room/WAL inspection and restoration of simulated power/idle state. See
  [capture progress](android/capture-progress.md).
- Checks: 70 JVM tests per variant, APK/test compilation, two actual deep-IDLE
  scenarios with automatic processing before app opening, preserved synthetic
  owner/identity/hash and two passing SIGKILL/force-stop regressions (65.44 seconds).
  Six host safety tests passed. Full nine-case suite and reboot were not repeated.
- Limitations: one disposable emulator and unpaired fixtures; no physical device,
  real provider/backend, natural sleep, independent worker-scheduling evidence or
  universal background-recovery guarantee. Forced idle ended ACTIVE, force=false,
  charging restored and test listener access revoked.
- Next action: user reviews/commits the focused groups, then proceed with isolated
  production HTTPS/credential-backup review and physical-device acceptance.
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

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
| Android capture | FinTrack 1.3.4 (17). The detailed record reports 70 JVM tests per variant, a prior complete run of 29 instrumented tests/eight lifecycle scenarios, and a focused two-test force-stop follow-up. | The new nine-case lifecycle suite has not been rerun in full. Reboot, Doze, unattended recovery, physical-device/OEM behavior and production transport/backup checks remain. See [capture progress](android/capture-progress.md). |
| Backend/dashboard | Main flows are implemented. Pending changes add ingestion provenance and configure CI with separate context, deployment, instrument and classification databases. | New CI configuration and provenance schema/migration need disposable PostgreSQL verification. No live deployment, real OAuth success or end-to-end synchronization follows from unit tests. See [deployment](development/deployment.md). |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: repository evaluation follow-up

- Owner: current chat; scope: documentation/setup defects, documentation checker,
  CI persistence coverage and ingestion evidence ownership. Initial Git status was
  clean at `b53d137`. Earlier tooling/docs/CI groups are already in Git as
  `83fc371`, `46dd43d` and `b53d137`; the previous commit-pending checkpoint was stale.
- Status: implemented; database/schema and hosted CI verification pending.
- Changes: fix template deployment path and legacy-container instructions; document
  external setup inputs; record [the repository-only evaluation](development/agent-evaluation.md).
  Extend template reference checks/regressions and add documentation-only CI.
  Enable all four opt-in persistence suites on separate CI databases. Add owner and
  movement references to new raw evidence and owner references to new email markers,
  preserving existing message/transaction hashes and unknown historical ownership.
- Changed paths: `.env.example`, `.github/workflows/`, `api/src/main/` entities and
  ingestion services, corresponding `api/src/test/` tests, `scripts/checks/`,
  `scripts/migrations/`, `docker/README.md`, script catalogue and affected `docs/`.
- Checks: 16 script regression groups passed in PowerShell 7 and Windows
  PowerShell 5.1 using mocked services/devices and temporary files. Backend
  database-free tests passed: 236 run, zero failures/errors/skips, offline and
  outside the sandbox with context/persistence excluded, an unreachable database
  URL and email scheduling disabled. The initial sandbox run blocked Mockito
  attachment; preloading its agent then exposed 11 JDK loopback setup errors
  (225 tests passed). The unrestricted repeat passed without source changes.
  Verifier preview, documentation references and `git diff --check` passed.
- Limitations: no database migration, context/persistence execution, hosted CI,
  service startup/restart, device installation or real email ingestion. No Android
  or UI implementation changed. Fresh-clone behavior, context savings and Java
  editor refresh remain unverified. Historical application results remain in
  [the harness record](development/harness-progress.md).
- Next action: validate schema/migration and all opt-in suites in confirmed
  disposable PostgreSQL databases, then review the functional commit groups.
  The agent has not committed or pushed this follow-up. Instruction maintenance
  remains due November 20, 2026 under [the register](development/instruction-maintenance.md).

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

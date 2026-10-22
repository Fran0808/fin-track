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
| Backend/dashboard | Ingestion provenance and all four persistence suites passed against isolated PostgreSQL. The local runner and initializer reproduce setup/checks. See [validation evidence](development/persistence-progress.md). | Hosted CI has not run for the current uncommitted changes. No production migration, live OAuth or end-to-end synchronization was performed. |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: isolated persistence validation and initialization

- Owner: current chat; scope: backend persistence/schema acceptance, local setup
  script and reproducible isolated checks. Initial working tree was clean at
  `72d82c7`; the preceding provenance/tooling groups are already committed as
  `ae808dc` and `72d82c7`. Android remains deferred by the user.
- Status: verified for local backend/setup scope; hosted CI pending publication.
- Changes: add `scripts/setup/initialize-local.ps1`,
  `scripts/checks/test-persistence.ps1`, a synthetic provenance SQL regression,
  CI execution of that SQL regression, script tests and setup/verification docs.
- Checks: 246 backend tests and packaging passed with Java 21.0.10/PostgreSQL 17.4
  in PowerShell 7 and Windows PowerShell 5.1, all four opt-in suites enabled and email scheduling disabled.
  The SQL regression applied the migration twice and verified legacy preservation
  and foreign keys. Temporary cluster shutdown and directory removal were confirmed.
  18 script regression groups passed in both supported PowerShell versions; actual
  Docker/native initialization preserved existing `.env` without printing secrets.
  Documentation references passed in both shells; `git diff --check` passed and
  the Android application/delivery paths have no diff.
  See [the detailed record](development/persistence-progress.md).
- Limitations: hosted CI, production migration, real Google access/synchronization,
  Android acceptance, fresh-clone behavior and Java editor refresh remain unverified.
  No application service restart, device installation, real-data access, dependency
  installation, Android or UI implementation change was performed.
- Next action: initialization is committed as `fec0361` and `2dde5b5`; the user
  reviews/commits/pushes SQL regression, isolated runner and CI as three remaining
  atomic groups (see the detailed record), then checks
  hosted CI. The agent has not committed or pushed. Instruction maintenance remains
  due November 20, 2026 under [the register](development/instruction-maintenance.md).

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

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
| Backend/dashboard | Main user-facing flows and deployment configuration are implemented; documented CI covers module tests/builds and isolated backend context/deployment databases. | No claim of a live deployment, real OAuth success or end-to-end synchronization follows from repository tests. See [deployment](development/deployment.md). |
| Agent knowledge | Shared root/module instructions and this progress index are intended to travel with the repository. | Local skills remain ignored pending selective review. Agent restart evaluations are not automated. |

## Current checkpoint: instruction maintenance

- Owner: current chat; scope: root/module instructions and related workflow,
  maintenance and progress documentation. Earlier unrelated changes are preserved.
- Status: verified for documentation scope; no commits.
- Changes: prioritize hard constraints and routing, replace repeated continuity
  rules with a workflow link, remove temporary UI-skill status from instructions,
  and register instruction sources, applicability and review/retirement conditions.
- Earlier evidence: [harness verification](development/harness-progress.md) records
  the script/application checks and analytics diagnosis from the previous pass.
- Current checks: `scripts/checks/test-docs.ps1` passed in Windows PowerShell 5.1
  and PowerShell 7; `git diff --check` passed. Root entry size is 52 lines.
  Removed obsolete editor-guide links without recreating the absent guide.
  No application tests/builds were rerun for these documentation-only changes.
- Limitations: instruction effectiveness and context savings are not measured;
  a fresh-clone cold-start evaluation and Java editor refresh remain unverified.
- Next action: user reviews and commits the pending functional groups, then
  runs the fresh-clone evaluation. Instruction maintenance is documented in
  [the register](development/instruction-maintenance.md); next full audit is due
  November 20, 2026, at the next work session on/after that date.

## Next priorities

1. Complete remaining Android acceptance using the detailed capture record.
2. Review and selectively version reusable local skills; do not publish personal
   configuration or private fixtures wholesale.
3. Exercise a fresh-clone agent session against the five orientation questions.
   Record missing knowledge as a documentation defect.

## Task continuity

Follow the [checkpoint protocol](development/agent-workflow.md#checkpoints-and-concurrency).
This file records current state; keep historical evidence in the owning area record.

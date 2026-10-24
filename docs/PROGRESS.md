# Project progress

Updated: October 24, 2026. This is the entry point for current work, not a release
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

## Frontend review checkpoint

- Owner/scope: frontend review/fix chat; dashboard behavior, navigation and usability.
  Status: fixes implemented; automated verification passed for stated scope;
  visual acceptance pending.
  See [findings and evidence](ui/frontend-review-2026-10-23.md).
- Changes: query-scoped resources and errors, separate Home recent movements,
  preserved classification drafts, fresh category catalogs, explicit CSV failures,
  native selects/mobile menu, URL section navigation and responsive movement table.
  Updated UI README and review report; permanent regressions added under ui/tests.
- Checks: final run passed 60 UI tests across 12 files, including 14 new
  regressions, using a workspace temporary directory. Lint completed with 8
  remaining warnings (12 before the fixes); TypeScript/Vite production build,
  diff whitespace and documentation link checks passed. No financial operations.
- Limitations: integrated browser could not reach local Vite (connection timeout);
  no visual/mobile acceptance or real backend/OAuth/financial operations performed.
- Next action: perform visual/keyboard acceptance with synthetic data and review
  the proposed commit groups in the report. No commit or push.

## Frontend redesign checkpoint: stage 1

- Owner/scope: current chat; incremental redesign of Home's heading and monthly
  summary. Status: implemented; automated checks passed; visual acceptance pending.
- Changes: `ui/src/components/views/HomeView.tsx` replaces the introductory copy
  with month/year, removes the summary card and blue strip, and aligns the three
  metrics with responsive stacking. Typography scale/weight and spacing change
  locally; palette, font families, financial calculations, navigation and lower
  lists remain unchanged. The existing auth test now locates the monthly region.
- Checks: 60 tests across 12 files passed; lint completed with the same 8 warnings;
  TypeScript/Vite build and diff whitespace checks passed. No new dependency,
  financial operation or service restart. Screenshot data was not copied to files.
- Limitations: the updated running UI could not be verified over localhost:5173;
  desktop/mobile visual acceptance remains pending. No browser permission retry.
- Next action: review this first Home change in the user's running UI before
  selecting the next small adjustment. User performs the commit; no commit/push.

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

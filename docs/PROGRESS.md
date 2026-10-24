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

## Frontend visual foundations checkpoint

- Owner/scope: current chat; first incremental visual layer after restoring the
  original layout. Status: implemented; automated/delivery checks passed; user
  visual acceptance pending before further redesign.
- Changes: shared palette keeps the blue family (#2c4f99) with clearer neutral
  text; local Latin Manrope/Onest fonts replace platform-dependent families.
  Sidebar, Home and Análisis lose slogans and redundant descriptions. Card/grid
  structure, financial logic, semantic flow colors and navigation are unchanged.
  The auth test now identifies the monthly region rather than marketing copy.
- Paths: ui/src/index.css, package manifests, SidebarNavigation, HomeView,
  MyMoneyView, auth-session test and UI README. No real screenshot data copied.
- Checks: 60 UI tests across 12 files passed; lint completed with the same 8
  warnings; TypeScript/Vite build emitted both local font assets (~59 KB total).
  Text contrast checked: muted text/canvas 5.25:1 and white/brand 7.82:1. HTTP 200
  from localhost:5173 confirmed the new stylesheet and concise Home module.
- Limitations: no desktop/mobile screenshot or full visual acceptance. No backend
  changes, financial operation, service restart, agent commit or push.
- Next action: user reviews only colors, typography and copy in the running UI;
  keep structural work separate until this layer is accepted.

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

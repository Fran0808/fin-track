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

## Current checkpoint: category analysis

- Owner/scope: current chat; expense categories in Analysis, including uncategorized
  movements, with server aggregates, amounts, percentages and counts.
- Checks: 255 backend tests in disposable PostgreSQL, 69 UI tests, lint (9 existing
  warnings) and production build passed. Full desktop/mobile Edge → real packaged
  API → disposable PostgreSQL verification passed before starting the next feature.
  Temporary processes and fixtures stopped and removed; no live data accessed.
- Next: user deploys/restarts the updated API/UI. No schema migration, existing
  service restart, agent commit or push. Merchant drill-down is a separate change.

## Checkpoint: Analysis merchant ranking

- Owner/scope: current chat; correct the backend top-five merchant query to select
  total expense descending before applying its limit. Count and name resolve ties;
  the dashboard already renders the API order. No frontend or schema change.
- Checks: Java 21 offline persistence runner passed 254 backend tests, all four
  persistence suites and both SQL migration regressions in disposable PostgreSQL.
  The new analytics regression verifies a large single expense outranks frequent
  small purchases, top-five selection, tie order, decimal totals and exclusion of
  other users, income, own transfers and dates outside the selected month.
  Temporary cluster stopped/removed; diff and documentation links checked.
- Status/next: verified for isolated backend scope; restart/deploy the updated API
  with authorization to see the corrected order. No live-data changes, existing
  service restart, browser acceptance, commit or push. Recommendations for category
  breakdown, merchant drill-down and explicit period comparisons were not implemented.

## Checkpoint: retire transaction tags and notes

- Owner/scope: current chat; remove tags/notes from entity/DTO/update logic,
  contact/category search, filters, table, CSV and shared client types. Category
  editing/rules remain; Android sync is unchanged. New bootstrap schema omits both
  columns; an explicit incremental migration is prepared for existing databases.
- Status: implemented; verified with synthetic data. See
  [deployment order and migration](domain/transaction-metadata-retirement.md).
- Checks: 67 UI tests across 14 files, TypeScript/Vite build and lint (same 9
  warnings) passed. Java 21 offline persistence runner passed 253 backend tests,
  including all four persistence suites, plus provenance and new metadata SQL
  regressions in six fresh disposable databases. Metadata migration ran twice,
  preserving amounts/owners/instruments/categories and per-user uniqueness.
  Temporary PostgreSQL stopped and its fixtures were removed. All 18 script
  regression groups passed; diff and documentation links checked.
- Limits/next: no real financial database migration or existing-service restart,
  browser/mobile visual acceptance, agent commit or push. Deploy API/UI together,
  back up any historical metadata needed, then authorize/apply the migration on
  the intended database. No live data was deleted; no further feature started.
  Subsequently the user reported successful backup and migration (`COMMIT`) on
  the local database; that operational result was not independently inspected.

## Dashboard checkpoint: integrated movement review

- Owner/scope: current chat; replace Pendientes navigation with review filters in
  Movimientos and contextual rule creation. Status: implemented; automated checks
  passed, end-to-end/browser acceptance pending. See [behavior](domain/classification.md)
  and [contract](architecture/contracts.md).
- Paths: transaction controller/service/CORS and their tests; UI navigation,
  movement/date filters, finance hook, API client, rule dialog, detail and tests.
  Updated UI README. The unrelated AGENTS.md change was preserved.
- Checks: 67 UI tests across 14 files; TypeScript/Vite build passed. Lint completed
  with 9 warnings (8 pre-existing, one for guarded pagination correction after a
  server response). 241 database-free backend tests passed with email scheduling
  disabled and an unreachable database URL. HTTP 200 confirmed updated menu,
  review-filter and detail modules served by local Vite; this is not visual QA.
- Limits: no browser/mobile or live API end-to-end acceptance, persistence suite,
  financial mutations, migration, service restart, commit or push. Unit/HTTP tests
  use synthetic mocks. The API suite needed execution outside the process sandbox
  after Mockito stalled there.
- Next action: deploy/restart the updated API with authorization, then check review
  filters, CSV and rule-dialog focus/cancellation at desktop/mobile sizes. The UI
  rejects review responses from an API lacking the matching capability header.
  No additional feature work before that end-to-end verification.

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
  text. At the user's request, original Aptos/Segoe UI families, display variants
  and small-label typography are restored; Manrope/Onest packages are removed.
  Sidebar, Home and Análisis lose slogans and redundant descriptions. Card/grid
  structure, financial logic, semantic flow colors and navigation are unchanged.
  The auth test now identifies the monthly region rather than marketing copy.
- Paths: ui/src/index.css, package manifests, SidebarNavigation, HomeView,
  MyMoneyView, auth-session test and UI README. No real screenshot data copied.
- Checks after restoring fonts: 60 UI tests across 12 files passed; lint completed
  with the same 8 warnings; TypeScript/Vite build passed without bundled fonts.
  Comparing index.css against the original baseline leaves only palette changes.
  Text contrast checked: muted text/canvas 5.25:1 and white/brand 7.82:1. HTTP 200
  from localhost:5173 confirmed the restored font stacks and retained palette;
  the prior concise Home delivery check remains applicable.
- Limitations: no desktop/mobile screenshot or full visual acceptance. No backend
  changes, financial operation, service restart, agent commit or push.
- Next action: user reviews retained colors/copy with original typography in the UI;
  keep structural work separate until this layer is accepted.

## Dashboard navigation checkpoint: remove budgets

- Owner/scope: current chat; remove the unplanned budget feature. Status:
  implemented and verified for automated/delivery scope; visual acceptance pending.
- Changes: removed Presupuestos, the empty Próximamente group, its route/type,
  App branch and unused PlaceholderView/export. Desktop/mobile share the menu.
  Legacy budget URLs use the existing unsupported-view fallback to Inicio.
- Checks: 61 tests passed, including a retired-link/navigation regression; lint
  completed with the same 8 warnings; TypeScript/Vite build and diff checks passed.
  HTTP 200 from localhost:5173 confirmed the updated menu and navigation modules.
- Limits/next: no screenshot, financial changes or service restart; user reviews
  the shorter menu and commits the focused group. No agent commit or push.

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

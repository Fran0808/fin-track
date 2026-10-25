# Project progress

Updated: October 25, 2026. This is the entry point for current work, not a release
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

## Current checkpoint: movement capture origin

- Owner/scope: current chat; show first-capture origin in movement detail using
  `Correo` and `Teléfono`, registration time and an explicit operation reference
  when recorded. See [origin evidence and rollout](domain/transaction-origin.md).
- Implemented: `UNKNOWN`/`EMAIL`/`PHONE` metadata persisted at trusted ingestion
  entry points; explicit parser receipt metadata separate from legacy hash input;
  authenticated owned-only no-store detail endpoint with no raw bodies, sender
  identity, device credentials or security codes. Existing sync/list/CSV DTOs and
  financial/hash semantics remain unchanged. Retries/corrections preserve evidence.
- Schema: additive [capture-origin migration](../scripts/migrations/20261025_transaction_capture_origin.sql)
  defaults historical rows to unknown without guessing from diagnostic channels.
  Bootstrap, isolated runner, SQL regression and CI definition updated; migration
  tested on disposable databases only. No live migration or source backfill.
- UI: independent origin panel with loading/error/retry/legacy states and stale
  response protection. Registration time is distinct from operation/phone-event
  time. Reference is optional; this feature does not detect or report duplicates.
- Verification: 315 backend tests with four disposable PostgreSQL suites and four
  SQL regressions passed; 108 UI tests, lint (same 9 warnings), production build,
  18 script regression groups and documentation checks passed. Hosted CI not run.
- Runtime: final built UI → packaged API → fresh synthetic PostgreSQL passed in
  Edge at 1440px and 390px. Verified email/phone/unknown detail, registration versus
  operation dates, reference/privacy limits, missing-API retry, tenant isolation,
  anonymous/device rejection, ignored request-origin spoofing, batch/replay and
  correction preservation. Inspected six source/viewport screenshots; no horizontal
  overflow or browser errors. Mobile registration date layout checked after refinement.
- Status: verified for isolated end-to-end scope. Temporary browser, API, HTTP
  server and PostgreSQL stopped; disposable data removed. Existing evaluation,
  other progress and title changes preserved. No live financial access, migration,
  existing-service restart, staging, commit or push. Next: user confirms/backs up
  the intended database, applies the new migration, updates API and reloads web.
- Delivery: four atomic groups proposed: storage/migration checks; parser receipt
  normalization with regressions; trusted ingestion/read API with tests/contracts;
  UI panel with tests/docs and this isolated checkpoint. Existing checks cover the
  final working tree; intermediate proposed revisions were not executed separately.

## Current checkpoint: reviewed historical rule application

- Owner/scope: current chat; apply an existing enabled rule to historical
  uncategorized income/expenses through an explicit preview and confirmation in
  `Reglas`, respecting existing literal matching and priority. No bulk selection
  added to Movements and no automatic historical writes on rule creation/edit.
- Implemented: bounded 200-row all-history previews, signed ten-minute selections,
  tenant-scoped row locking, classification-preserving retries and conflict review.
  Only category assignments change. New arrivals, transfers, already classified
  rows and other users' data cannot expand or overwrite the reviewed selection.
- UI: native dialog with merchant/date/type/amount, cancel/review/confirm states,
  explicit batch/empty/error explanations, disabled paused/inactive rules and
  dashboard refresh after success. Contracts, feature guide and classification
  domain behavior updated. No schema or Android payload changes.
- Verification: 303 backend tests with all four fresh disposable PostgreSQL suites
  and three SQL regressions passed; 102 UI tests, lint (same 9 warnings) and
  production build passed. Documentation reference and diff checks passed.
- Runtime: built UI → packaged API → fresh synthetic PostgreSQL passed in Edge
  at 1440px and 390px. Verified preview/cancel/confirm, Escape/focus restoration,
  empty/paused states, priority/transfer/tenant exclusions, exact selections,
  preserved intervening classifications, new arrivals excluded, replay and expired/
  altered previews. Two simultaneous confirmations changed each row once; a
  205-match fixture applied exactly 200 and left five for another preview.
  Screenshots inspected; no horizontal overflow or browser errors. Initial focus
  regression fixed; temporary-server CORS corrected before final runtime pass.
- Status: verified for isolated end-to-end scope. Temporary browser, API, HTTP
  server and PostgreSQL stopped; disposable data removed. Existing evaluation,
  other progress checkpoints and title changes preserved. No live financial
  changes, existing-service restart, staging, commit or push. Next: user deploys/
  restarts the updated API and reloads UI, then uses `Reglas → Aplicar al historial`.
  This feature needs no additional migration; no further feature started.
- Delivery: user prefers smaller atomic commits. Proposed order: shared matching
  refactor, historical API with security/persistence tests and contracts, then UI
  with regression coverage, domain/feature docs and this isolated checkpoint.
  Existing checks cover the final working tree; intermediate commit revisions
  were not separately executed. No staging or commits performed by the agent.

## Current checkpoint: movement filter totals

- Owner/scope: current chat; resolve finding 4 of the end-user evaluation only.
  Listing adds authenticated, decimal expense/income/internal-transfer totals for
  the complete filter using its exact specification and a repeatable-read
  transaction. Pagination and existing response fields remain compatible.
- UI: separate `Total del filtro` and `Esta página`; the selected flow controls
  the displayed metrics. Empty results show zeros, loading remains distinct and
  older APIs without summary show an unavailable state. No page subtotal is
  presented as a complete-filter total.
- Changed paths: transaction summary DTO/repository fragment and listing service;
  frontend types/API/hook, transaction table/view, regression tests, UI feature
  guide and contracts. No schema or Android payload changes.
- Verification: 284 backend tests with all four disposable PostgreSQL suites and
  three SQL migration regressions passed; 95 UI tests, lint (same 9 warnings) and
  production build passed. Final UI run used `npm test -- --maxWorkers=2
  --testTimeout=15000` under concurrent machine load, including stale-summary
  regression coverage. Documentation reference and diff checks passed.
- Runtime: built UI → packaged API → fresh synthetic PostgreSQL passed in Edge
  at 1440px and 390px: paging, all-history, income/transfers, empty search,
  category/merchant/combined filters, foreign-user exclusion and anonymous/device
  rejection. Income-view screenshots inspected; no horizontal overflow.
- Status: verified for isolated end-to-end scope. Temporary browser/API/HTTP
  servers and PostgreSQL clusters stopped; temporary databases removed. Initial sandbox/temp
  and overloaded parallel-test attempts required isolated reruns; runtime startup
  needed a larger timeout and bounded JVM resources. No live financial access,
  existing-service restart, staging, commit or push. Original evaluation files,
  title edit and concurrent Android changes preserved. Next: user deploys/restarts
  API and reloads UI to see this additive change. No next feature started.

## Current checkpoint: own-card repayments

- Owner/scope: current chat; exclude confirmed BCP own-card repayments from
  expenses/income/net flow while preserving movement history and legacy hashes.
  Purchases and third-party card payments retain their existing expense flow.
- Changed: BCP parser/fixtures; authenticated per-ID repair in transaction service
  and controller; detail action and shared row/detail flow label; API/UI/domain
  documentation. No enum, schema, Android payload or existing hash migration.
- Verification: 274 backend tests, including all four isolated PostgreSQL suites
  and both migration regressions, passed. Coverage includes legacy hash replay,
  preservation of amount/date/category/product, idempotent repair, tenant isolation,
  unauthorized/device rejection and exclusion from expense metrics/CSV. 87 UI
  tests, lint (same 9 warnings) and production build passed.
- Browser verification: built UI → packaged API → fresh PostgreSQL passed in
  headless Edge at 1440px and 390px. Two synthetic legacy repayments were repaired
  explicitly; history/count/hash were preserved, totals refreshed and expense
  ranking/CSV excluded them. Anonymous, foreign-ID and ordinary-expense attempts
  were rejected. Screenshots inspected; no horizontal overflow. Temporary browser,
  API/HTTP servers and PostgreSQL stopped and removed. Doc links/diff checks passed.
- Status: verified for isolated scope. No existing service restart or live
  financial read/write performed. Next: deploy/restart the updated API, reload UI
  and use `Es un pago de mi tarjeta` on the existing payment's detail. No migration.
  Unrelated AGENTS.md and ui/index.html edits are preserved/excluded from commit
  proposals; no agent staging, commit or push. UI/domain docs reflect the feature;
  no debug code added and the standard `npm run dev` startup remains available.

## Checkpoint: four Analysis improvements

- Owner/scope: current chat; category drill-down, daily/cumulative chart, explicit
  period comparison and factual expense highlights, implemented sequentially.
- Step 1 verified: category and uncategorized drill-down preserve exact grouping,
  dates, user, expense flow, paging and CSV. 260 backend tests including all four
  persistence suites plus both migration regressions passed in disposable PostgreSQL;
  76 UI tests, lint (same 9 warnings), production build and doc/diff checks passed.
  Full built UI → packaged API → fresh PostgreSQL browser check passed in headless
  Edge at desktop/mobile sizes for both named/uncategorized groups and CSV.
  Temporary processes/fixtures stopped and removed before starting step 2.
- Step 2 verified: daily bars default with an accumulated-line toggle, zero-spend
  days and keyboard/pointer exploration. 79 UI tests, lint (same 9 warnings),
  production build and doc/diff checks passed. Full desktop/mobile Edge → real
  API → disposable PostgreSQL check passed for both chart modes and category
  drill-down. Temporary processes/fixtures stopped and removed before step 3.
- Step 3 verified: server-provided current/prior dates, decimal expense difference
  and amounts shown explicitly without percentages or division by zero. 262 backend
  tests with disposable persistence/migrations, 82 UI tests, lint (same 9 warnings),
  production build and doc/diff checks passed. Full desktop/mobile browser check
  verified dates, current/prior amounts, difference and exclusion of another user's
  prior expenses. Temporary processes/fixtures stopped and removed before step 4.
- Step 4 verified: largest owned individual expense (merchant/date/amount) and peak
  expense day (date/sum), with deterministic ties and empty/future states. 263
  backend tests with all four persistence suites and both migration regressions,
  84 UI tests across 18 files, lint (same 9 warnings), production build and doc/diff
  checks passed. Final full Edge desktop/mobile → packaged API → fresh PostgreSQL
  scenario passed all four features together, including period/user/flow exclusions,
  keyboard chart exploration and CSV. Screenshots inspected; no horizontal overflow.
  Temporary browsers, API/HTTP servers and databases stopped and removed.
- Status: all four suggestions implemented and verified sequentially for isolated
  end-to-end scope. User deploys/restarts the updated API/UI; no migration required.
  No live financial access, existing-service restart, agent commit or push.
  The unrelated ui/index.html title change is preserved and excluded from staging patches.

## Checkpoint: compact native combo boxes

- Owner/scope: current chat; shared styled native pickers for CustomSelect and the
  two visible rule-category selects. Existing colors/type retained; selection uses
  a soft brand tint/checkmark, bounded 18rem scrolling and viewport-aware placement.
  Closed controls have stable height. CSS feature detection preserves the ordinary
  native picker on unsupported browsers; hidden period fallbacks are unchanged.
- Checks: 73 UI tests across 15 files, lint (same 9 warnings), TypeScript/Vite build,
  diff and documentation links passed. Final built UI verified in headless Edge
  at 1440×1000 and 390×844 against an isolated API/PostgreSQL fixture with 32
  synthetic categories and a product. Verified actual 288px picker height,
  viewport width, modal layering, keyboard End/Enter selection, Escape closing
  only the picker, preserved category drafts, required form validation and native
  fallback after disabling the CSS enhancement. Desktop/mobile screenshots inspected.
  Temporary browser, API, HTTP server and database were stopped and removed.
- Status/next: verified for stated browser scope. User reloads the frontend and
  commits the focused change. Other browser engines were not run; unsupported
  engines retain platform menus. No backend changes, live data operations, existing
  service restart, commit or push. The unrelated ui/index.html title edit was preserved.

## Checkpoint: consistent mouse cursors

- Owner/scope: current chat; shared `cursor: pointer` styles for enabled actions,
  links, selects, checkbox/radio controls and labels; disabled controls retain
  unavailable/busy/default cursors. Text fields keep their editing cursor, with
  a hand only on the date picker icon. Removed redundant native-button utilities
  and misleading hand cursors from hover-only charts. No icons or layout changes.
- Checks: 73 UI tests, lint (same 9 warnings), TypeScript/Vite build, diff and
  documentation checks passed. AST audit found click handlers only on native
  buttons/dialogs. Built UI checked in headless Edge across seven views, movement/
  rule dialogs and mobile navigation, using a temporary API and synthetic database.
  Verified native disabled fieldsets, checkbox labels, wait overrides, links,
  summaries, text inputs and the calendar indicator's native computed cursor.
  Ad hoc browser harness selector/pseudo-style issues were corrected; final run
  passed. All temporary processes and database fixtures stopped and removed.
- Status/next: verified for isolated browser scope; user reloads the web and
  commits the focused UI/doc change. No backend changes, existing-service restart,
  live financial access, agent commit or push.

## Checkpoint: category analysis and merchant drill-down

- Owner/scope: current chat; add period expense category aggregates and a visible
  category breakdown, followed by exact merchant drill-down into Movements.
  No schema, Android sync or visual foundation changes.
- Sequence: category breakdown first passed 255 backend tests, 69 UI tests and a
  full desktop/mobile browser → real API → disposable PostgreSQL check. Only then
  was merchant drill-down implemented, retaining server-provided analysis bounds,
  clearing conflicting filters and preserving scope in paging/CSV.
- Final checks: 257 backend tests with all four persistence suites and both SQL
  regressions passed in disposable PostgreSQL; 73 UI tests across 15 files, lint
  (same 9 warnings), TypeScript/Vite build and documentation/diff checks passed.
  Isolated headless Edge ran the built frontend against the packaged API and a
  fresh synthetic database at 1440×1100 and 390×844: categories/uncategorized totals,
  exact merchant selection, date bounds, exclusion of similar names/other users/
  income/transfers, CSV and removable filtering passed. Screenshots inspected;
  no horizontal overflow. Temporary API, browser, database and HTTP server stopped
  and removed. Browser fixture/scenario were ad hoc; permanent regressions are
  in the UI tests and ClassificationPersistenceTest.
- Status/next: verified for isolated end-to-end scope. User restarts/deploys the
  updated API/UI to see the additions; no migration required. No existing service
  restart, real financial access, live mutation, agent commit or push.

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

# Instruction maintenance

This policy applies when adding/changing repository instructions, when their
source changes, or when a scheduled review becomes due. It is not required context
for unrelated implementation. It implements the user's October 21, 2026 request
for a small router, thematic guidance and explicit instruction lifecycle.

## Placement and admission

Before adding an instruction, identify the problem and its source. Put financial
meaning in `docs/domain/`, architectural details in `docs/architecture/`, commands
and workflow in `docs/development/`, and temporary status in
[progress](../PROGRESS.md). Module `AGENTS.md` files contain only applicable local
constraints and pointers. The root entry contains summary, hard constraints,
routing and closure expectations; aim for 50-200 lines without padding.

Place hard constraints near the start and verification/closure near the end.
Do not claim that placement alone proves better agent performance. Prefer one
owning document and links; merge or remove redundant instructions. A one-off
failure is not enough reason to add a permanent rule: first consider a code fix,
regression test or a clarification in the relevant thematic reference.

Every instruction must be covered by a register entry specifying its source,
applicability and review/retirement trigger. Group instructions only when these
properties are shared. Split an entry if a new rule has a different lifecycle.
The register explains existing authority; it does not create exceptions to it.

## Instruction register

The user's working agreements supplied on October 21, 2026 are the source for
the existing root/module policies. The linked references supply technical context;
they do not independently authorize changing financial/security constraints.

| Coverage | Source / rationale | Applies when | Review or retirement condition |
| --- | --- | --- | --- |
| Root: cashflow, precision and per-user idempotency | User agreements; [cashflow](../domain/cashflow.md) | Interpreting or persisting money | Review if financial model, currency or identity semantics change; retain until an authorized policy replaces it. |
| Root: provider-independent financial meaning and UI | User's October 25, 2026 request to prioritize adding banks/wallets; [provider extension](../architecture/overview.md#provider-extension) | Adding integrations, operation types or provider-dependent behavior | Review when shared financial contracts or integration boundaries change; retain until an explicit user policy replaces it. |
| Root: authenticated-user scope, credentials and untrusted input | User agreements; [contracts](../architecture/contracts.md) | Handling financial reads/writes, ingestion, sensitive output or external content | Review on authentication, tenant or trust-boundary changes; no automatic expiry. |
| Root: authorized notification packages | User agreements; [Android recovery](../android/notification-recovery.md) | Notification capture or parser changes | Review when a provider/package is explicitly authorized or removed; no automatic expiry. |
| Root: no agent commits/pushes and operational authorization | User agreements | Git publication, live financial writes, credential rotation, installations or restarts | Only an explicit user revision can replace these authority constraints. |
| Root: preserve unrelated work and explain intended edits | User agreements | Editing shared files or changing task scope | Review when the collaboration agreement changes; retain otherwise. |
| Root: routing and selective context | User's instruction-design request; [documentation map](../README.md) | Starting work or selecting module context | Update on moved references or module boundaries; remove stale routes immediately. |
| Root: success, regression coverage, isolation, runtime verification and honest reporting | User agreements; [development](local.md), [scripts](../../scripts/README.md) | Selecting checks and closing work | Review when test isolation, delivery or documented commands change; remove obsolete precautions only after replacement is verified. |
| Workflow: communication and scope | User agreements | Implementation and user handoff | Review when user collaboration preferences change; do not duplicate in module files. |
| Workflow: English code, emoji restriction, commit format/groups | User agreements | Writing code/docs/logs or proposing commits | Review on an explicit coding/versioning policy change; task-specific UI content permission remains scoped to that task. |
| Workflow: checkpoints and concurrency | User's continuity/ACID request; [progress](../PROGRESS.md) | Starting, interrupting, completing or coordinating work | Review when state storage or concurrency workflow changes; archive superseded task evidence rather than retain it as an instruction. |
| API: boundaries and current conventions | User backend agreements; [architecture](../architecture/overview.md), [pom.xml](../../api/pom.xml) | Backend code/configuration changes | Review on package, dependency, authentication, persistence or DTO convention changes; read exact versions from manifests. |
| API: parsing and contracts | User backend agreements; [cashflow](../domain/cashflow.md), [contracts](../architecture/contracts.md) | Parsing bank messages or changing shared payloads | Review with provider format, financial meaning or client contract changes; retire format-specific guidance with the parser it protects. |
| API: verification | User backend agreements; [backend checks](local.md#backend-verification) | Backend tests or runtime delivery | Review when opt-in suites, database isolation or build/deployment behavior changes. |
| Android: boundaries | User Android agreements; [recovery](../android/notification-recovery.md), [contracts](../architecture/contracts.md), [Gradle](../../android/app/build.gradle.kts) | Android capture, persistence, pairing or synchronization | Review when provider authorization, ownership, synchronization or storage contracts change. |
| Android: version/delivery | User Android agreements | Delivering changed application contents or diagnosing an installed build | Review on delivery/version policy changes; no version bump for unchanged application contents or docs-only work. |
| Android: verification | User Android agreements; [capture evidence](../android/capture-progress.md) | Android changes and claims about capture/sync | Review when validation tooling or supported platform/source changes; remove limitations only with executed evidence. |
| UI: boundaries | User dashboard agreements; [contracts](../architecture/contracts.md), [cashflow](../domain/cashflow.md), [manifest](../../ui/package.json) | Dashboard data/session/API changes | Review with authentication, API client, domain or build changes; exact versions remain in manifests. |
| UI: design/interaction | User dashboard agreements; existing UI conventions | Changing visible behavior or layout | Review on an approved design/accessibility/product-language change; apply visual checks only when the task affects visuals. |
| UI: verification | User dashboard agreements; [development](local.md#dashboard-and-android-verification) | Dashboard changes and delivery | Review when test/build tooling or supported UI behavior changes. |
| This policy: placement, register and audits | User's October 21 instruction-maintenance request | Changing instructions or conducting a due audit | Review if instruction-loading behavior, repository organization or the user's maintenance policy changes. |

## Review cadence and evidence

The maintainer or agent handling an instruction change reviews affected entries in
the same change. Also review at the next repository work session if 30 days have
elapsed since the last complete audit. This is an on-work review convention, not
an automation, reminder or background job.

During review, verify sources and applicability, examine contradictory/duplicate
rules, move thematic/status content out of entry files, retire resolved temporary
guidance and run documentation checks. Record the date, scope, result, unresolved
debt and next due date here. Hard financial/security/authority constraints never
expire merely because a review date passes.

- Last complete audit: October 21, 2026; root and three module instruction files,
  workflow and documentation routing reviewed during this task.
- Result: hard constraints placed first; continuity details consolidated; redundant
  backend version/package lists reduced; temporary UI-skill note moved to progress.
- Verification: documentation links and rooted script references passed in both
  Windows PowerShell 5.1 and PowerShell 7; `git diff --check` passed. The root
  router is 52 lines. No application build was needed for this documentation pass.
- Remaining debt: reusable local skills remain ignored; cold-start behavior and
  any context-budget savings have not been measured. These are tracked in progress.
- Next complete audit due: November 20, 2026, at the next work session on/after
  that date; source changes trigger an earlier review of affected entries.
- Targeted review: October 25, 2026; admitted the user's provider scalability
  requirement as one root constraint, with architecture-owned details and its own
  register entry. Existing parser-boundary guidance remains complementary; no
  unrelated instruction or authority constraint was replaced.

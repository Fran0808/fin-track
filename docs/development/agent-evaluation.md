# Repository-only agent evaluation

Evaluated October 21, 2026 using repository files and Git in the existing checkout.
This was not a fresh clone or an automated agent benchmark.

## Observed evidence

The root/module instructions and documentation index answered the five orientation
questions: product/rules, module routing, local execution, verification/isolation,
and current acceptance. Git was initially clean at `b53d137`; the preceding tooling
and documentation groups were already committed (`83fc371`, `46dd43d`).
Only `scripts/checks/verify-all.ps1 -Plan` and `scripts/checks/test-docs.ps1` were
executed during that evaluation; both returned zero. No application tests, builds,
services, installations or financial data operations were run.

## Findings and follow-up

- Correct the stale deployment path in `.env.example`, and cover template paths
  in the documentation checker and its disposable regression fixture.
- Use discovered container names for legacy volume inspection.
- Replace the stale global checkpoint; keep earlier evidence explicitly historical.
- Enable instruments/classification persistence suites in CI with separate databases.
- Document external setup inputs without inventing credentials or installed tools.
- Add explicit ownership/provenance for new ingestion evidence, preserving unknown
  historical ownership. See [deployment preparation](deployment.md#database-preparation).

Follow-up implementation and actual checks are recorded in [progress](../PROGRESS.md).
Physical-device acceptance, real OAuth, end-to-end sync and production migration
remain unverified until performed in their authorized environments.

## Repeating the evaluation

Start a new agent session with only a fresh clone and the five orientation questions.
Read `AGENTS.md`, progress, the documentation index and affected module guides;
compare documented status with Git. Run only the verifier preview and documentation
reference checker. Record evidence, broken references, contradictions and missing
external inputs, distinguishing inspected source from executed checks and historical
claims. Do not start services, access live data, install software or infer secrets.

The reference checker validates file existence, not web availability or heading
fragments. Source/configuration consistency still requires review. A successful
evaluation does not measure context savings or prove application/runtime behavior.

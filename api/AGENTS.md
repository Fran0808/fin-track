# Backend instructions

These instructions apply to backend changes and supplement [the root agreements](../AGENTS.md).
Their sources and review triggers are in the [instruction register](../docs/development/instruction-maintenance.md#instruction-register).

## Boundaries

- Resolve Java targets, dependency versions and annotation processors from [pom.xml](pom.xml).
- Controllers handle HTTP and validation; services own use cases and transactional boundaries; repositories handle persistence.
- Keep tests aligned with production packages; follow [package organization](../docs/architecture/overview.md#backend-package-organization) for layer and feature boundaries.
- Keep bank-specific interpretation in `service/email/parser`. The dispatcher selects a parser; `EmailIngestionService` orchestrates ingestion.
- Use `UserContext.requireCurrentUser()` for protected financial operations. Keep tenant predicates explicit in repository queries.
- Web requests use JWTs; device credentials have limited endpoint access. Check [the contract](../docs/architecture/contracts.md) and `JwtAuthFilter` before changing authentication.
- Use `BigDecimal` and the existing `NUMERIC(10,2)` amount column. Avoid implicit currency conversion or changes to hash inputs.
- Use Bean Validation on request DTOs and `@Transactional` / `@Transactional(readOnly = true)` at the use-case boundary.
- DTOs currently use mutable Lombok classes. Follow the established pattern; an immutability migration is a separate change, not a prerequisite for routine work.
- Configure CORS in `CorsConfig`, not separately in each controller.

## Parsing and contracts

- Require a recognized transaction confirmation and its operation amount. Promotions, surveys and administrative emails must not become financial entries.
- Protect incoming payments and internal transfers when changing parsing rules.
- Preserve per-user hash deduplication. Email and Android hashes use different inputs; do not assume cross-source matching.
- Add anonymized positive and negative fixtures for parser changes. Never copy personal emails, real tokens or real notification security codes into tests.
- Update consuming Android DTOs and frontend types when the API schema changes; see [contracts](../docs/architecture/contracts.md).

## Verification

- Use `scripts/checks/verify-all.ps1 -SkipUi -SkipAndroid` from the root for ordinary database-free tests, or the equivalent Maven wrapper command in the development guide.
- Security and ingestion changes require tests covering authentication, tenant isolation and deduplication, as applicable.
- `AppTests` and persistence suites are opt-in and require their documented disposable database URLs. `AppTests` uses `create-drop`; never point it at financial data.
- Follow [development verification precautions](../docs/development/local.md#backend-verification), including the separate credentials and schema settings for each suite.
- Verify runtime behavior if the task includes starting or restarting the API. A Docker image must be rebuilt after source changes.

# Pending movements and classification rules

The dashboard's `Pendientes` view covers the authenticated user's entire history,
independent of the global month selector. A movement needs review when:

- Its category is null or blank, unless it is an `INTERNAL_TRANSFER`.
- Its financial instrument is unassigned, including internal transfers.

The inbox supports all pending movements, missing category, or missing instrument.
Each movement appears once even when both fields are missing. Complete it through
the existing detail dialog; the inbox reloads after a saved classification,
instrument assignment or successful manual sync from the header. A movement disappears when it no longer meets the selected
filter. Archived assigned instruments still count as assigned. No review flag or
dismissal state is stored.

## Rules

Rules belong to one user and match a literal substring in `contactName`, ignoring
case. The dashboard supports creation, editing, pause/reactivation and deletion.
Each rule references an owned category by ID and has an explicit priority (0–9999).
Lower values run first; ties use the oldest rule ID. The first enabled matching
rule with an active category assigns the category name.

The shared ingestion service applies rules to new email and Android movements
before persistence. Existing hashes return the existing movement without reapplying
rules. A rule never changes amounts, direction, instruments, tags, notes or hashes,
never overwrites a nonblank category, and never classifies internal transfers.
It can classify either income or expenses. Editing a rule does not update history.

Renaming a category affects subsequent rule assignments. Deactivating it makes its
rules ineligible; the next matching eligible rule may apply. A category referenced
by any rule cannot be deleted until those rules are changed or deleted. An existing
rule with an inactive category can be paused, deleted or retargeted to an active
category; it cannot be reactivated with an inactive target.

## Schema and verification

Deploy the backend and dashboard together. The Android sync payload is unchanged.
Hibernate's current `ddl-auto=update` configuration creates the new rule table when
the updated API starts. For environments using schema validation, apply
[the migration](../../scripts/migrations/20261008_classification_rules.sql) to the
confirmed database after a backup. It adds a table and index without reclassifying
historical movements; the existing category migration remains a prerequisite.
The migration is repeatable but does not repair an incompatible existing rule table.

Unit and HTTP contract tests mock persistence. Run them without connecting to the
financial database:

```powershell
cd api
.\mvnw.cmd test '-Dtest=!*AppTests,!*PersistenceTest'
```

[ClassificationPersistenceTest](../../api/src/test/java/com/store/api/service/category/ClassificationPersistenceTest.java)
is opt-in. Create a disposable PostgreSQL database named
`fintrack_classification_eval_<suffix>` on `127.0.0.1`; never reuse financial data.
Set `CLASSIFICATION_TEST_DATABASE_URL` to its full JDBC URL, and set
`CLASSIFICATION_TEST_DATABASE_USER` and `CLASSIFICATION_TEST_DATABASE_PASSWORD`
for that disposable instance. The test explicitly disables email scheduling and
uses `ddl-auto=update` only on this restricted test database. It rolls back synthetic
fixtures and verifies pending filters, ownership, rule ordering, retries and
category lifecycle. Run it from `api` with:

```powershell
.\mvnw.cmd test '-Dtest=ClassificationPersistenceTest'
```

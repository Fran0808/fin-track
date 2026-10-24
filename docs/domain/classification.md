# Pending movements and classification rules

The dashboard integrates review into `Movimientos` through the advanced `Revisión`
filter. There is no separate `Pendientes` navigation entry. A movement needs review when:

- Its category is null or blank, unless it is an `INTERNAL_TRANSFER`.
- Its financial instrument is unassigned, including internal transfers.

The filter supports missing category, missing instrument, or either condition.
Each movement appears once even when both fields are missing. Complete it through
the existing detail dialog; the listing reloads after a saved classification,
instrument assignment or successful manual sync from the header. A movement disappears
when it no longer meets the selected filter; an empty final page returns to a valid page.
Archived assigned instruments still count as assigned. No review flag or
dismissal state is stored.

Selecting a review reason activates the explicitly labeled `Todo el historial`
date scope and clears custom dates. Search, flow, tags, channel and amount filters
remain combinable, including CSV export. Missing-category review clears and disables
the contradictory category selector. The date controls can restrict review to the
selected month, one day or a range; clearing all filters restores the selected month.
The header month selector is hidden while an all-history/custom-date search applies
in Movements. Home and analysis continue using their own selected period.
Legacy `?vista=pendientes` links initially open Movements with either review condition
across all history. Other filters remain in memory and are reset on reload.

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

`Crear regla para este comercio` in a non-transfer movement's detail opens a native
dialog with its contact text and currently selected category. The user can edit
the text, choose an active category, set priority and review before explicitly
saving. Opening or cancelling it creates nothing; the movement's unsaved category,
tags and notes remain intact. Creating a rule does not classify that historical
movement; save its classification separately. `Reglas` retains full rule management.

Renaming a category affects subsequent rule assignments. Deactivating it makes its
rules ineligible; the next matching eligible rule may apply. A category referenced
by any rule cannot be deleted until those rules are changed or deleted. An existing
rule with an inactive category can be paused, deleted or retargeted to an active
category; it cannot be reactivated with an inactive target.

## Schema and verification

Deploy the backend and dashboard together. The Android sync payload is unchanged.
The integrated review filters require the updated API but no additional migration.
The UI checks the review-filter response header before displaying/exporting filtered
results, and reports an outdated API instead of showing an unfiltered response.
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
.\mvnw.cmd test '-Dtest=*Test,!*PersistenceTest,!AppTests' '-Dmail.sync.enabled=false' '-Dspring.jpa.hibernate.ddl-auto=validate' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/fintrack_review_no_database'
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

# Retire transaction tags and personal notes

The dashboard and API no longer support transaction tags or personal notes.
The category remains editable and classification rules remain available. Search
matches contact/category only; CSV exports nine columns as specified in the
[contract](../architecture/contracts.md). Android ingestion does not use these fields.

## Existing installations

1. Review and back up historical `transactions.tags` and `transactions.notes` if
   their values need to be retained. Dropping the columns permanently deletes them.
2. Deploy the updated API and dashboard together, and retire all older API
   instances before changing the schema. The updated entity does not map these
   columns and works while they still exist. Hibernate update/validate does not
   perform this column removal automatically.
3. With authorization for the intended database, apply
   [20261024_remove_transaction_metadata.sql](../../scripts/migrations/20261024_remove_transaction_metadata.sql).
   Use PostgreSQL `psql` with `ON_ERROR_STOP=1` and credentials supplied privately.
   The script runs in a transaction, uses bounded lock/statement timeouts and
   drops only those columns on `public.transactions`. Repeating it is safe.
4. Verify listing, contact/category search, category editing, review filters and
   CSV export. Amounts, ownership, instruments, categories and idempotency keys
   must remain unchanged. No movement is deleted by this migration.

Rollback to the old API after column removal requires restoring its schema and
any required historical values from backup. Recreating empty columns alone does
not recover tags/notes. The change prepares a migration; it does not execute it
against a live financial database.

New installations use the updated
[empty-database schema](../../scripts/migrations/20261014_empty_database.sql),
which omits both columns.

## Isolated verification

[test_remove_transaction_metadata.sql](../../scripts/migrations/test_remove_transaction_metadata.sql)
requires a fresh `fintrack_metadata_eval_<suffix>` database. It creates synthetic
fixtures, applies the migration twice and checks row values, tenant references,
per-user hash uniqueness and column removal. It intentionally refuses other
database names. Never use it against an application database.

Run [the persistence runner](../../scripts/checks/test-persistence.ps1) with Java 21
and PostgreSQL tools available. It provisions its own temporary cluster, runs
both migration regressions and all four persistence suites with email scheduling
disabled, then stops only its temporary process and removes the synthetic data.

October 24, 2026 verification: 253 backend tests passed with all four persistence
suites enabled, both SQL regressions passed and the temporary cluster was stopped
and removed. UI: 67 tests, lint (9 existing warnings) and production build passed.
All 18 script regression groups passed. No live schema migration or existing
service restart was performed; browser/mobile visual acceptance remains pending.

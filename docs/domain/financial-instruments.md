# Cards and accounts retirement

The manual `Tarjetas y cuentas` (`FinancialInstrument`) catalog and movement-assignment
module has been retired across the dashboard, backend API and database schema.

Automatic payment-channel and card-suffix detection (`channel` and `cardLast4` on
each movement) remains active and continues powering `Medios de pago` in `Inicio`
and `Análisis`, without requiring manual product registration or movement linking.

## Schema preparation and verification

New empty databases initialized with
[20261014_empty_database.sql](../../scripts/migrations/20261014_empty_database.sql)
no longer create `financial_instruments`, `transactions.financial_instrument_id`
or `idx_tx_user_instrument_date`.

For an existing database that previously had `financial_instruments`, deploy the
updated API first, take a verified backup of the confirmed target database, and
then apply
[20261028_remove_financial_instruments.sql](../../scripts/migrations/20261028_remove_financial_instruments.sql):

```powershell
psql -X -v ON_ERROR_STOP=1 -d finance_db -f scripts/migrations/20261028_remove_financial_instruments.sql
```

The migration runs transactionally with bounded lock and statement timeouts, drops
`idx_tx_user_instrument_date`, `transactions.financial_instrument_id` and
`public.financial_instruments` repeatable-safely (`IF EXISTS`), and preserves all
stored movements, amounts, categories, channels, card suffixes, hashes and user
ownership constraints.

The disposable SQL regression test
[test_remove_financial_instruments.sql](../../scripts/migrations/test_remove_financial_instruments.sql)
requires an isolated database matching `fintrack_remove_instruments_eval_<suffix>`
and is executed automatically by
[test-persistence.ps1](../../scripts/checks/test-persistence.ps1) and CI.

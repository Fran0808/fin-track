# Cards and accounts

Financial instruments are user-owned bank accounts, debit cards and credit cards.
This version supports soles (`PEN`) only. Registration does not establish
balances, debt, interest or available credit.

## Registration and lifecycle

- Required: type, alias (up to 100 characters), bank. Active defaults to true.
- Banks: BCP, Interbank, BBVA and Other. Other requires an institution name.
- Optional: exactly four numeric suffix digits. Full account/card numbers, CVV,
  passwords and bank credentials are not supported fields.
- A debit card may reference an owned active account from the same bank. For
  Other, the trimmed institution names must match.
- Archive preserves existing links and history. Archived products cannot receive
  assignments and are excluded from suggestions. They can be reactivated.
- Archiving an account does not archive its debit cards. Existing links remain.
  A card linked to an archived account must unlink or choose an active account
  before reactivation.
- Unlink debit cards before changing their account's type or bank. Editing
  aliases or suffixes never rewrites confirmed movement assignments.

## Movement assignment

A movement has at most one direct product association. Users can assign, replace
or remove it in the movement detail. A debit card's movement is not also assigned
to its linked account. Product history filters only direct associations.

Suggestions are calculated on demand for old and new movements. They require
an exact four-digit suffix plus a supported bank and product type:

| Captured channel | Suggested product |
| --- | --- |
| `TARJETA_DEBITO_<bank>` | Debit card from BCP, Interbank or BBVA |
| `TARJETA_CREDITO_<bank>` | Credit card from BCP, Interbank or BBVA |
| `BCP_TRANSFERENCIA` | BCP account, only when a suffix was captured |

Yape, Plin, unknown channels, missing suffixes and Other-bank products do not
produce suggestions. Registration does not add parser support for a bank.
All matches require explicit confirmation, even a single match. Manual assignment
is available without a suggestion.

Assignments never change amounts, flow types, captured channels, timestamps or
hashes. Retried ingestion preserves the association, including archived products.
Product histories show the selected period and do not represent balances.
Existing [cashflow rules](cashflow.md) still govern analytics.

## API

All routes require web JWT authentication; device credentials are denied.

| Route | Behavior |
| --- | --- |
| `GET /api/v1/financial-instruments?active=true` | Owned active products; omit filter for all, false for archived |
| `POST /api/v1/financial-instruments` | Create; returns 201 |
| `PUT /api/v1/financial-instruments/{id}` | Replace editable fields including active status; returns 200 |
| `PATCH /api/v1/transactions/{id}/financial-instrument` | Assign with `{"financialInstrumentId":123}`; null unlinks |
| `GET /api/v1/transactions/{id}/financial-instrument-suggestions` | Candidate products; empty array for insufficient evidence |
| `GET /api/v1/transactions?financialInstrumentId=123` | Existing pagination/date/flow/search filters plus direct product filter |

Request fields: `type` (`BANK_ACCOUNT`, `DEBIT_CARD`, `CREDIT_CARD`), `alias`,
`bank` (`BCP`, `INTERBANK`, `BBVA`, `OTHER`), `institutionName`, `lastFour`,
`active`, `linkedAccountId`. Send absent optional fields as null, not empty suffixes.
Responses add `id` and fixed `currency: "PEN"`. Transaction responses add nullable
`financialInstrument` with this product summary. Ingestion requests are unchanged;
Android accepts the additive response.

Foreign/missing identifiers return 404. Invalid fields, incompatible links and
archived assignment targets return 400. No deletion endpoint exists.

## Schema preparation and verification

Current Hibernate `ddl-auto=update` creates `financial_instruments` with user and
linked-account foreign keys/indexes, plus nullable
`transactions.financial_instrument_id` and a user/product/date index.
Existing movements stay unassigned; there is no automatic backfill.

Back up the local financial database and verify the target before updating it.
An authorized backend restart is needed to load the new endpoints and schema.

The PostgreSQL integration test accepts only an explicitly selected localhost
database named `fintrack_instruments_eval_<suffix>`. Create a separate disposable
database first. Set `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` to its credentials without committing them.
In a dedicated terminal, run from the repository root:

```powershell
$env:INSTRUMENT_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/fintrack_instruments_eval_local'
$env:SPRING_DATASOURCE_URL = $env:INSTRUMENT_TEST_DATABASE_URL
$env:GMAIL_SYNC_ENABLED = 'false'
Push-Location api
try {
    .\mvnw.cmd test
} finally {
    Pop-Location
    Remove-Item Env:INSTRUMENT_TEST_DATABASE_URL
    Remove-Item Env:SPRING_DATASOURCE_URL
    Remove-Item Env:GMAIL_SYNC_ENABLED
}
```

The new persistence test is skipped without the opt-in variable. Existing
`AppTests` still needs an isolated target; see
[development precautions](../development.md#backend-verification).
Afterward, remove only the disposable database you created.

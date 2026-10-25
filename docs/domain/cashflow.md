# Cashflow rules

## What the product measures

The product measures captured money movements, not available balances in bank
accounts. A receipt or push notification cannot establish a live account balance.

| Flow type | Meaning | Effect on expense metrics |
| --- | --- | --- |
| `EXPENSE` | Money paid out in a recognized operation | Included |
| `INCOME` | Money received in a recognized operation | Excluded |
| `INTERNAL_TRANSFER` | Movement between the user's own accounts | Excluded |

For a selected period, net flow is incoming money minus expenses. The legacy
summary field `netBalance` is this computed flow, not a bank balance. Internal
transfers are reported separately. Period and all-history totals must be labeled
according to their scope; do not combine them as if they cover the same dates.

An explicitly confirmed payment of the user's own credit card is stored as
`INTERNAL_TRANSFER`: purchases were already expenses when captured. Repayment
retains its positive amount and history but contributes to neither income nor
expenses/net flow. This is not a live card balance or debt calculation. Card
purchases and payments of third-party cards remain expenses. BCP recognition
requires explicit own-card operation evidence, not just the word "card".
Own-card BCP email hashes retain their legacy pre-fix flow input so reprocessing
cannot create a second movement.

Analysis ranks its top five merchants by total recorded expense descending,
then by operation count descending and merchant name ascending for ties. The
ranking is selected after aggregating all expenses in the authenticated user's
selected period. Income and internal transfers never enter this ranking.

Analysis also groups every expense by trimmed category name, merging null/blank
names into the uncategorized group. All groups, including historical categories,
contribute to period expenses; their percentages use that same expense total.
Displayed percentages are rounded to one decimal and may not sum to exactly 100.

## Evidence and amount

- A bank name or currency amount alone is insufficient evidence of a transaction.
- Require a supported source and an identifiable confirmation of a financial operation.
- Read the operation amount, not rewards, limits, advertising figures or unrelated amounts.
- Preserve positive amounts and represent direction with `flowType`.
- Reject conflicting evidence rather than silently assuming an expense.
- Java and Kotlin calculations use `BigDecimal`; persistence currently uses `NUMERIC(10,2)`.

The request and stored transaction currently have no currency field. Some email
parsers identify a currency internally, but that value is not carried into the
persisted transaction contract. Multi-currency accounting is therefore not an
implemented capability; fixing transport and display requires an explicit contract
change. Do not add currency conversion or claim currency-aware totals incidentally.

[Cards and accounts](financial-instruments.md) are registered in soles only.
Assigning a movement to a product organizes its history without changing amounts,
flows, identity or aggregate totals.

## Identity, tenancy and deduplication

Every financial record must belong to the authenticated user. The database's
unique `(transaction_hash, user_id)` index protects against inserting the same
hash twice for that user; matching hashes for different users are separate records.

Current hash inputs differ:

- Android Yape notifications (1.0.2+): Android event metadata and a fingerprint of flow, normalized amount, contact and any security code. Older rows keep their original capture-minute hashes. See [recovery and compatibility](../android/notification-recovery.md).
- BCP/Yape emails: flow, amount representation, normalized merchant/contact, operation identifier (or minute fallback) and date.

The same payment arriving through email and a notification may have different
hashes. The current index does not guarantee deduplication across these sources.
Treat changes to normalization, time inputs or scale as identity changes and test
their effect on retries and already-stored transactions.

## Dates and regression examples

Transaction timestamps currently use local date-times without offsets in the
API. Gmail converts its message time to `America/Lima`; Android uses the device's
local time. These are current implementation choices, not a timezone-neutral contract.

Useful anonymized regression cases include a confirmed payment, incoming yapeo,
internal transfer, repeated sync, unsupported package, promotional reward,
administrative limit change and ambiguous operation amount. Each case should
assert the financial result, not merely that parsing returned an object.

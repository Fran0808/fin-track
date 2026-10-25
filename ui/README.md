# FinTrack dashboard

The dashboard displays personal cashflow, transactions and Android pairing.
It uses React, TypeScript, Vite and Tailwind; exact versions live in
[package.json](package.json) and its lockfile.

## Setup and verification

Follow the [local development guide](../docs/development/local.md) for prerequisites,
backend configuration, startup commands and module checks. The development server
runs on port 5173 and proxies `/api` to `localhost:8080` through
[vite.config.ts](vite.config.ts). Keep the proxy and OAuth URLs aligned when changing ports.

## Searching movements by date

In Movements, use the visible date filter to choose the selected month, a specific
day, or an inclusive date range. Choose the dates and press `Buscar por fecha`.
Ranges can span multiple months. The final date includes the entire day.
The date filter combines with text, movement type and advanced filters; pagination
and CSV export retain the same applied range. Invalid or incomplete ranges cannot
be applied. `Limpiar filtros` or `Mes seleccionado` restores the selected month;
changing the global month also clears custom dates. Dashboard analytics continue
to use the globally selected month.

## Entry points

### Visual foundations

The existing layout uses a blue primary (`#2c4f99`) with neutral backgrounds and
stronger secondary-text contrast. Shared typography retains the original Aptos /
Segoe UI system families and display variants, without additional font packages. The
dashboard keeps functional instructions and financial definitions while removing
redundant descriptions and slogans. Tokens and font stacks live in
[index.css](src/index.css).

### Navigation and behavior

Confirmed own-card repayments use normalized `operationType=OWN_CARD_PAYMENT`
and the existing internal-transfer flow, independently of provider/contact text, and
display `Pago de tarjeta propia` in transaction rows/detail, without a plus/minus
sign. They remain in history but do not contribute to expenses, income or net
flow. In the detail of an expense or transfer with operation type `UNKNOWN`,
click `Es un pago de mi tarjeta`, check the ownership explanation and confirm (or
cancel). This is available across providers, not only for old BCP records. The action calls the
authenticated PATCH endpoint and refreshes all dashboard resources after success;
failure leaves the expense unchanged and permits retry. No automatic repair runs
on opening the UI. An older API without operation metadata does not expose the
action; the PATCH response must confirm both operation and neutral flow. Apply the
[operation migration](../scripts/migrations/20261025_transaction_operation_type.sql),
then deploy/restart the updated API before using this UI.

Analysis includes all expense categories for the selected period, with amounts,
shares and counts ordered by spending. Null/blank categories appear together as
`Sin categoría`. It uses the server aggregate, not the current transaction page;
income and own transfers are excluded. Loading, empty and older-API states are
explicit. Deploy the API with `categoryBreakdown` before using the updated UI.

Click a merchant in Analysis to open its expense movements. The exact contact name
and server-provided analysis dates are applied, previous filters are cleared and
pagination starts at zero. The visible `Comercio` filter can be removed independently;
clearing all filters also restores the selected month. Paging and CSV retain the
merchant/date scope. An older API lacking date bounds disables this shortcut.

Category rows in Analysis also open their expenses, including `Sin categoría`.
They retain the server's analysis dates, reset conflicting filters and start at
page zero. The visible category-group filter can be removed; paging/CSV keep it.
The advanced category selector is disabled while that group is active, preventing
contradictory category selections. The API must support `categoryGroup`.

The existing expense chart starts with daily bars, including zero-spend days,
and can switch to the cumulative line without another card. The scale follows
the chosen metric; pointer/touch and left/right keys expose both daily and
cumulative amounts for the selected day. Empty periods retain an explicit message.

Analysis comparison shows both server-provided intervals, their recorded expense
amounts and the difference in soles. It does not divide by the previous amount or
replace unavailable/future comparisons with zero. Short-month and year-boundary
dates are shown literally; no timezone conversion is applied to these local dates.

Two compact factual highlights distinguish the largest individual expense
(merchant, amount and timestamp) from the highest daily expense sum (date and
amount). Both use the authenticated user's analysis interval and exclude income
and own transfers. No predictive/judgmental copy is generated. Missing, loading
and empty-period states do not invent a zero-valued maximum.

The selected section is kept in the `vista` URL parameter, for example
`?vista=movimientos` or `?vista=mi-dinero` (shown as `Análisis`). Reloading keeps
that section, and browser Back/Forward follows section changes. Filters and the
selected month remain in memory while navigating between sections; reloading
resets them. Home's recent movements always use the global month independently
of the filters, custom dates and page selected in Movements.

Only available features appear in the sidebar. The retired `presupuestos` view
and its placeholder screen are removed; old `?vista=presupuestos` links fall back
to Inicio, like any unsupported view. Desktop and mobile share the same menu.

CSV export downloads the complete server result for the applied filters. A failed
export shows an error and can be retried; it never silently exports just one page.
The detail dialog edits only the category. Tags and personal notes are retired
from the API, shared types, table, filters and CSV. Search covers contact/category.
It preserves unsaved category edits when assigning a product
and reloads its category catalog each time it opens. Catalog failures provide a
retry action rather than invented fallback options.

Native selects provide keyboard selection.

Visible selects share the `fintrack-select` styling, including classification-rule
forms. Where the browser supports customizable native selects (`base-select` and
`::picker(select)`), options use app typography/colors, a soft selected state and
a scrollable picker capped at 18rem and the viewport height. The native control
retains labels, typed values, required/disabled behavior, keyboard selection and
automatic popup placement above dialogs. Unsupported browsers retain the ordinary
native picker; the enhancement uses CSS feature detection and no new dependency.
The period selector's hidden native fallbacks are excluded. See
[the browser documentation](https://developer.chrome.com/blog/a-customizable-select).

Enabled buttons, links, selects and checkbox/radio controls use a hand cursor;
disabled controls use an unavailable cursor, with explicit busy/default overrides
retained where defined. Checkbox/radio labels follow the input's enabled state,
including disabled fieldsets. Text inputs retain their editing cursor; only the
calendar icon receives a hand cursor. Charts with hover-only detail keep their
normal cursor instead of suggesting a click action.

On small screens the navigation opens
as a native modal dialog; Escape closes it. The sidebar scrolls on short screens,
and the movement table prioritizes contact, amount and detail below 640 px; date
and payment method remain available in the detail dialog.

`Movimientos` includes advanced review filters for missing category, missing assigned
card/account, or either condition. Selecting one searches `Todo el historial`;
date controls can narrow the scope again. Search and other filters apply to both
listing and full CSV. Old `?vista=pendientes` links open this review in Movements;
the separate view and menu entry are removed.
`Reglas` manages automatic category assignments for new movements and does not use
the global month selector. A movement's detail offers `Crear regla para este comercio`
with editable text/category/priority and explicit save; cancelling preserves its
unsaved classification. Creating a rule does not reclassify historical movements.
Deploy the updated API for review filters; an incompatible API produces an error
instead of an unfiltered result. See [pending movements and rules](../docs/domain/classification.md)
for matching, priority and category lifecycle behavior.

| Area | Source |
| --- | --- |
| Session lifecycle | [AuthContext](src/contexts/AuthContext.tsx) |
| HTTP requests | [API service](src/services/api.ts) |
| Response types | [Shared types](src/types/index.ts) |
| Views | [View components](src/components/views) |
| Financial formatting | [Formatters](src/utils/formatters.ts) |
| Tests | [UI tests](tests) |

Read [dashboard instructions](AGENTS.md) before changes. Use the
[cashflow rules](../docs/domain/cashflow.md) for financial interpretation and
[contracts](../docs/architecture/contracts.md) for changes involving backend responses.

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
The detail dialog preserves unsaved classification edits when assigning a product
and reloads its category catalog each time it opens. Catalog failures provide a
retry action rather than invented fallback options.

Native selects provide keyboard selection. On small screens the navigation opens
as a native modal dialog; Escape closes it. The sidebar scrolls on short screens,
and the movement table prioritizes contact, amount and detail below 640 px; date
and payment method remain available in the detail dialog.

`Pendientes` shows the entire history's movements missing a category or assigned
instrument, with reason filters and the existing detail dialog for completion.
`Reglas` manages automatic category assignments for new movements. These screens
do not use the global month selector. See [pending movements and rules](../docs/domain/classification.md)
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

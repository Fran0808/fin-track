import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Search, SlidersHorizontal, X, ChevronDown, ChevronUp, CalendarDays, Filter } from 'lucide-react';
import type { FlowType, PendingReason } from '../../types';
import { STANDARD_CATEGORIES } from '../../types';
import { useCategories } from '../../hooks';
import type { TransactionFilterParams } from '../../services';
import { CustomSelect } from '../common';
import { TransactionDateFilter } from './TransactionDateFilter';
import { getChannelLabel } from '../../utils';

interface TransactionFiltersProps {
  renderHeader?: (search: ReactNode) => ReactNode;
  dateScope?: string;
  categoryGroup?: string;
  merchantName?: string;
  search: string;
  flowType: FlowType | '';
  category?: string;
  channel?: string;
  minAmount?: number;
  maxAmount?: number;
  startDate?: string;
  endDate?: string;
  allHistory?: boolean;
  pendingReason?: PendingReason | '';
  onFilterChange: (filters: Partial<TransactionFilterParams>) => void;
  onReset: () => void;
}

const FLOW_OPTIONS: { value: FlowType | ''; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'EXPENSE', label: 'Gastos' },
  { value: 'INCOME', label: 'Entradas' },
  { value: 'INTERNAL_TRANSFER', label: 'Transferencias' },
];

const CHANNEL_OPTIONS = [
  { value: '', label: 'Todos los medios' },
  { value: 'CASH', label: 'Efectivo' },
  { value: 'CARD', label: 'Tarjeta' },
  { value: 'BANK_TRANSFER', label: 'Transferencia' },
  { value: 'WALLET', label: 'Billetera' },
  { value: 'OTHER', label: 'Otro' },
  { value: 'YAPE', label: 'Yape' },
  { value: 'PLIN', label: 'Plin' },
  { value: 'TARJETA_DEBITO_BCP', label: 'Tarjeta Débito BCP' },
  { value: 'TARJETA_CREDITO_BCP', label: 'Tarjeta Crédito BCP' },
  { value: 'BCP_TRANSFERENCIA', label: 'Transferencia BCP' },
];

export function TransactionFilters({
  renderHeader,
  dateScope,
  categoryGroup,
  merchantName,
  search,
  flowType,
  category = '',
  channel = '',
  minAmount,
  maxAmount,
  startDate,
  endDate,
  allHistory = false,
  pendingReason = '',
  onFilterChange,
  onReset,
}: TransactionFiltersProps) {
  const dateMenu = useRef<HTMLDetailsElement>(null);
  const [localSearch, setLocalSearch] = useState(search);
  const [isAdvancedOpen, setIsAdvancedOpen] = useState(Boolean(pendingReason));
  const { options: categoryOptions, error: categoryError, refresh: refreshCategories } = useCategories(true);

  useEffect(() => {
    setLocalSearch(search);
  }, [search]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (localSearch !== search) onFilterChange({ search: localSearch });
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch, search, onFilterChange]);

  const activeAdvancedCount = [
    pendingReason,
    category,
    channel,
    minAmount !== undefined && minAmount !== null && !isNaN(minAmount) ? minAmount : '',
    maxAmount !== undefined && maxAmount !== null && !isNaN(maxAmount) ? maxAmount : '',
  ].filter((value) => value !== '' && value !== undefined && value !== null).length;

  const hasAnyFilter = Boolean(
    categoryGroup !== undefined ||
      merchantName !== undefined ||
      localSearch.trim() ||
      flowType ||
      startDate ||
      endDate ||
      allHistory ||
      activeAdvancedCount > 0
  );

  const appliedDateScope =
    dateScope ||
    (allHistory
      ? 'Todo el historial'
      : startDate && endDate
      ? `${startDate.slice(0, 10).split('-').reverse().join('/')} al ${endDate.slice(0, 10).split('-').reverse().join('/')}`
      : 'Mes seleccionado');

  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (dateMenu.current && !dateMenu.current.contains(event.target as Node)) {
        dateMenu.current.open = false;
      }
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, []);

  const searchField = (
    <div className="relative w-full sm:w-72 lg:w-80">
      <label htmlFor="transaction-search" className="sr-only">
        Buscar por comercio o categoría
      </label>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
      <input
        id="transaction-search"
        type="search"
        value={localSearch}
        onChange={(event) => setLocalSearch(event.target.value)}
        placeholder="Buscar comercio o categoría..."
        className="w-full rounded-xl border border-line bg-canvas py-2.5 pl-10 pr-10 text-sm text-ink placeholder:text-muted transition-colors focus:border-brand focus:bg-white focus:outline-none"
      />
      {localSearch && (
        <button
          type="button"
          onClick={() => {
            setLocalSearch('');
            onFilterChange({ search: '' });
          }}
          aria-label="Limpiar búsqueda"
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1 text-muted hover:text-ink"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );

  // Active filter chips list
  const activeChips: { id: string; label?: string; prefix?: string; value?: string; onRemove: () => void; ariaLabel: string }[] = [];

  if (categoryGroup !== undefined) {
    const catLabel = categoryGroup
      ? STANDARD_CATEGORIES.find((item) => item.id === categoryGroup)?.label ?? categoryGroup
      : 'Sin categoría';
    activeChips.push({
      id: 'categoryGroup',
      prefix: 'Categoría:',
      value: catLabel,
      onRemove: () => onFilterChange({ categoryGroup: undefined }),
      ariaLabel: 'Quitar filtro de categoría',
    });
  }

  if (merchantName !== undefined) {
    activeChips.push({
      id: 'merchantName',
      prefix: 'Comercio:',
      value: merchantName || 'Sin nombre',
      onRemove: () => onFilterChange({ merchantName: undefined }),
      ariaLabel: 'Quitar filtro de comercio',
    });
  }

  if (pendingReason) {
    activeChips.push({
      id: 'pendingReason',
      label: pendingReason === 'CATEGORY' ? 'Pendiente: Sin categoría' : 'Revisión pendiente',
      onRemove: () => onFilterChange({ pendingReason: '' }),
      ariaLabel: 'Quitar filtro de revisión',
    });
  }

  if (category && categoryGroup === undefined) {
    const catObj = categoryOptions.find((o) => o.id === category);
    activeChips.push({
      id: 'category',
      label: `Categoría: ${catObj ? catObj.label : category}`,
      onRemove: () => onFilterChange({ category: '' }),
      ariaLabel: 'Quitar filtro de categoría específica',
    });
  }

  if (channel) {
    activeChips.push({
      id: 'channel',
      label: `Medio: ${getChannelLabel(channel)}`,
      onRemove: () => onFilterChange({ channel: '' }),
      ariaLabel: 'Quitar filtro de medio de pago',
    });
  }

  if (minAmount !== undefined || maxAmount !== undefined) {
    let amountStr = 'Importe: ';
    if (minAmount !== undefined && maxAmount !== undefined) amountStr += `S/ ${minAmount} – S/ ${maxAmount}`;
    else if (minAmount !== undefined) amountStr += `≥ S/ ${minAmount}`;
    else amountStr += `≤ S/ ${maxAmount}`;

    activeChips.push({
      id: 'amount',
      label: amountStr,
      onRemove: () => onFilterChange({ minAmount: undefined, maxAmount: undefined }),
      ariaLabel: 'Quitar filtro de importe',
    });
  }

  return (
    <div className="space-y-3.5">
      {renderHeader?.(searchField)}

      {/* Primary Toolbar: Segmented flow control + Date dropdown + Filter button */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Segmented Flow Filter */}
          <div
            className="inline-flex rounded-xl border border-line bg-canvas p-1 shadow-2xs"
            role="group"
            aria-label="Filtrar por tipo de movimiento"
          >
            {FLOW_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => onFilterChange({ flowType: option.value })}
                aria-pressed={flowType === option.value}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
                  flowType === option.value
                    ? 'bg-white text-ink shadow-2xs'
                    : 'text-muted hover:text-ink'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>

          {/* Date Selector Popover */}
          <details
            ref={dateMenu}
            className="relative"
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                event.preventDefault();
                dateMenu.current!.open = false;
                dateMenu.current?.querySelector('summary')?.focus();
              }
            }}
          >
            <summary
              className="flex max-w-full items-center gap-2 rounded-xl border border-line bg-white px-3 py-1.5 text-xs font-semibold text-ink shadow-2xs transition hover:border-brand/40 hover:bg-canvas focus-visible:outline-2 focus-visible:outline-brand"
              title={appliedDateScope}
            >
              <CalendarDays className="h-4 w-4 shrink-0 text-brand" />
              <span>Fechas</span>
              <span className="max-w-44 truncate font-normal text-muted">{appliedDateScope}</span>
              <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted" />
            </summary>
            <div className="absolute left-0 top-full z-20 mt-2 w-[min(34rem,calc(100vw-4rem))] rounded-2xl border border-line bg-white p-4 shadow-xl sm:left-auto sm:right-0">
              <TransactionDateFilter
                key={`${allHistory}:${startDate || ''}:${endDate || ''}`}
                startDate={startDate}
                endDate={endDate}
                allHistory={allHistory}
                onFilterChange={(value) => {
                  onFilterChange(value);
                  if (dateMenu.current) dateMenu.current.open = false;
                }}
              />
            </div>
          </details>

          {/* Advanced Filters Toggle Button */}
          <button
            type="button"
            onClick={() => setIsAdvancedOpen((prev) => !prev)}
            aria-expanded={isAdvancedOpen}
            aria-label="Filtros avanzados"
            title="Filtros avanzados"
            className={`inline-flex items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-semibold shadow-2xs transition-all ${
              isAdvancedOpen || activeAdvancedCount > 0
                ? 'border-brand/40 bg-brand/8 text-brand'
                : 'border-line bg-white text-ink hover:border-brand/30 hover:bg-canvas'
            }`}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            <span>Filtros</span>
            {activeAdvancedCount > 0 && (
              <span className="flex h-4.5 w-4.5 items-center justify-center rounded-full bg-brand text-[10px] font-bold text-white">
                {activeAdvancedCount}
              </span>
            )}
            {isAdvancedOpen ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          </button>
        </div>

        {hasAnyFilter && (
          <button
            type="button"
            onClick={() => {
              setLocalSearch('');
              onReset();
            }}
            className="inline-flex items-center gap-1 text-xs font-medium text-muted transition hover:text-negative animate-pop"
          >
            <X className="h-3.5 w-3.5" />
            Limpiar filtros
          </button>
        )}
      </div>

      {/* Active Filter Chips Bar */}
      {activeChips.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 pt-1" role="region" aria-label="Filtros aplicados">
          <span className="inline-flex items-center gap-1 text-xs font-medium text-muted">
            <Filter className="h-3 w-3" />
            Filtros activos:
          </span>
          {activeChips.map((chip) => (
            <span
              key={chip.id}
              className="inline-flex items-center gap-1.5 rounded-lg border border-brand/25 bg-brand/8 px-2.5 py-1 text-xs font-medium text-ink shadow-2xs transition"
            >
              <span>{chip.prefix ? <>{chip.prefix} <strong className="font-semibold text-ink">{chip.value}</strong></> : chip.label}</span>
              <button
                type="button"
                onClick={chip.onRemove}
                aria-label={chip.ariaLabel}
                className="rounded p-0.5 text-muted hover:bg-brand/15 hover:text-ink focus:outline-none"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </span>
          ))}
        </div>
      )}

      {/* Advanced Filters Expandable Drawer */}
      <div
        className={`grid transition-[grid-template-rows,opacity] duration-250 ease-out ${
          isAdvancedOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0 pointer-events-none'
        }`}
      >
        <div className="overflow-hidden">
          <div className="rounded-2xl border border-line bg-canvas/70 p-4 shadow-2xs sm:p-5 mt-1">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label htmlFor="filter-review" className="block text-xs font-semibold text-muted">
                Revisión
              </label>
              <div className="mt-1.5">
                <CustomSelect<PendingReason | ''>
                  id="filter-review"
                  value={pendingReason}
                  onChange={(value) =>
                    onFilterChange({
                      pendingReason: value,
                      ...(value ? { allHistory: true, startDate: undefined, endDate: undefined } : {}),
                      ...(value === 'CATEGORY' || value === 'ALL' ? { category: '' } : {}),
                    })
                  }
                  options={[
                    { value: '', label: 'Todos los movimientos' },
                    { value: 'CATEGORY', label: 'Sin categoría' },
                  ]}
                />
              </div>
              {pendingReason && (
                <p className="mt-2 text-xs text-muted">Puedes limitar la búsqueda con las opciones de fecha.</p>
              )}
            </div>

            <div>
              <label htmlFor="filter-category" className="block text-xs font-semibold text-muted">
                Categoría
              </label>
              <div className="mt-1.5">
                <CustomSelect
                  id="filter-category"
                  disabled={pendingReason === 'CATEGORY' || pendingReason === 'ALL' || categoryGroup !== undefined}
                  value={category}
                  onChange={(val) => onFilterChange({ category: val })}
                  options={[
                    { value: '', label: 'Todas las categorías' },
                    ...(category && !categoryOptions.some((o) => o.id === category)
                      ? [{ value: category, label: category }]
                      : []),
                    ...categoryOptions.map((cat) => ({ value: cat.id, label: cat.label })),
                  ]}
                  className="text-xs py-1.5"
                />
              </div>
              {categoryError && (
                <p role="alert" className="mt-2 text-xs text-negative">
                  {categoryError}{' '}
                  <button type="button" onClick={() => void refreshCategories()} className="underline">
                    Reintentar categorías
                  </button>
                </p>
              )}
            </div>

            <div>
              <label htmlFor="filter-channel" className="block text-xs font-semibold text-muted">
                Medio de pago
              </label>
              <div className="mt-1.5">
                <CustomSelect
                  id="filter-channel"
                  value={channel}
                  onChange={(val) => onFilterChange({ channel: val })}
                  options={CHANNEL_OPTIONS}
                  className="text-xs py-1.5"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted">Rango de importe (S/)</label>
              <div className="mt-1.5 flex items-center gap-2">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={minAmount !== undefined ? minAmount : ''}
                  onChange={(e) =>
                    onFilterChange({
                      minAmount: e.target.value ? Number(e.target.value) : undefined,
                    })
                  }
                  placeholder="Mín."
                  aria-label="Importe mínimo en soles"
                  className="w-full rounded-xl border border-line bg-white px-2.5 py-2 text-xs font-medium text-ink placeholder:text-muted focus:border-brand focus:outline-none"
                />
                <span className="text-muted">–</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={maxAmount !== undefined ? maxAmount : ''}
                  onChange={(e) =>
                    onFilterChange({
                      maxAmount: e.target.value ? Number(e.target.value) : undefined,
                    })
                  }
                  placeholder="Máx."
                  aria-label="Importe máximo en soles"
                  className="w-full rounded-xl border border-line bg-white px-2.5 py-2 text-xs font-medium text-ink placeholder:text-muted focus:border-brand focus:outline-none"
                />
              </div>
            </div>
          </div>

          {activeAdvancedCount > 0 && (
            <div className="mt-4 flex justify-end">
              <button
                type="button"
                onClick={() =>
                  onFilterChange({
                    pendingReason: '',
                    category: '',
                    channel: '',
                    minAmount: undefined,
                    maxAmount: undefined,
                  })
                }
                className="text-xs font-semibold text-brand hover:underline"
              >
                Limpiar solo filtros avanzados
              </button>
            </div>
          )}
          </div>
        </div>
      </div>
    </div>
  );
}

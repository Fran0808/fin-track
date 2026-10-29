import { useEffect, useState } from 'react';
import { Search, SlidersHorizontal, X, ChevronDown, ChevronUp } from 'lucide-react';
import type { FlowType, PendingReason } from '../../types';
import { STANDARD_CATEGORIES } from '../../types';
import { useCategories } from '../../hooks';
import type { TransactionFilterParams } from '../../services';
import { CustomSelect } from '../common';
import { TransactionDateFilter } from './TransactionDateFilter';

interface TransactionFiltersProps {
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
  ].filter(value => value !== '' && value !== undefined && value !== null).length;

  const hasAnyFilter = Boolean(categoryGroup !== undefined || merchantName !== undefined || localSearch.trim() || flowType || startDate || endDate || allHistory || activeAdvancedCount > 0);

  return (
    <div className="space-y-4">
      {categoryGroup !== undefined && <div className="flex items-center justify-between gap-3 rounded-xl border border-brand/20 bg-brand/5 px-3 py-2 text-sm">
        <span className="min-w-0 break-words">Categoría: <strong>{categoryGroup ? STANDARD_CATEGORIES.find(item => item.id === categoryGroup)?.label ?? categoryGroup : 'Sin categoría'}</strong></span>
        <button type="button" onClick={() => onFilterChange({ categoryGroup: undefined })} aria-label="Quitar filtro de categoría" className="shrink-0 rounded p-1 text-brand hover:bg-brand/10"><X className="h-4 w-4" /></button>
      </div>}
      {merchantName !== undefined && <div className="flex items-center justify-between gap-3 rounded-xl border border-brand/20 bg-brand/5 px-3 py-2 text-sm">
        <span className="min-w-0 break-words">Comercio: <strong>{merchantName || 'Sin nombre'}</strong></span>
        <button type="button" onClick={() => onFilterChange({ merchantName: undefined })} aria-label="Quitar filtro de comercio" className="shrink-0 rounded p-1 text-brand hover:bg-brand/10"><X className="h-4 w-4" /></button>
      </div>}
      <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <div className="relative w-full xl:max-w-sm">
          <label htmlFor="transaction-search" className="sr-only">Buscar por comercio o categoría</label>
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
          <input
            id="transaction-search"
            type="search"
            value={localSearch}
            onChange={(event) => setLocalSearch(event.target.value)}
            placeholder="Buscar comercio o categoría..."
            className="w-full rounded-xl border border-line bg-canvas py-2.5 pl-10 pr-10 text-sm text-ink placeholder:text-muted focus:border-brand focus:bg-white focus:outline-none"
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

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex flex-wrap items-center gap-1 rounded-xl border border-line bg-canvas p-1" aria-label="Filtrar por tipo de movimiento">
            {FLOW_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => onFilterChange({ flowType: option.value })}
                aria-pressed={flowType === option.value}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                  flowType === option.value ? 'bg-white text-ink shadow-sm' : 'text-muted hover:text-ink'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setIsAdvancedOpen((prev) => !prev)}
            aria-expanded={isAdvancedOpen}
            className={`inline-flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-semibold transition-colors ${
              isAdvancedOpen || activeAdvancedCount > 0
                ? 'border-brand bg-brand/5 text-brand'
                : 'border-line bg-white text-muted hover:border-ink hover:text-ink'
            }`}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            Filtros avanzados
            {activeAdvancedCount > 0 && (
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-brand text-[10px] font-bold text-white">
                {activeAdvancedCount}
              </span>
            )}
            {isAdvancedOpen ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          </button>

          {hasAnyFilter && (
            <button
              type="button"
              onClick={() => {
                setLocalSearch('');
                onReset();
              }}
              className="rounded-xl px-2.5 py-2 text-xs font-medium text-muted hover:text-negative"
            >
              Limpiar filtros
            </button>
          )}
        </div>
      </div>

      <TransactionDateFilter key={`${allHistory}:${startDate || ''}:${endDate || ''}`} startDate={startDate} endDate={endDate} allHistory={allHistory} onFilterChange={onFilterChange} />

      {isAdvancedOpen && (
        <div className="rounded-2xl border border-line bg-canvas/60 p-4 transition-all sm:p-5">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <label htmlFor="filter-review" className="block text-xs font-semibold text-muted">Revisión</label>
              <div className="mt-1.5">
                <CustomSelect<PendingReason | ''> id="filter-review" value={pendingReason}
                  onChange={value => onFilterChange({ pendingReason: value,
                    ...(value ? { allHistory: true, startDate: undefined, endDate: undefined } : {}),
                    ...(value === 'CATEGORY' || value === 'ALL' ? { category: '' } : {}),
                  })}
                  options={[
                    { value: '', label: 'Todos los movimientos' },
                    { value: 'CATEGORY', label: 'Sin categoría' },
                  ]} />
              </div>
              {pendingReason && <p className="mt-2 text-xs text-muted">Puedes limitar la búsqueda con las opciones de fecha.</p>}
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
              {categoryError && <p role="alert" className="mt-2 text-xs text-negative">{categoryError} <button type="button" onClick={() => void refreshCategories()} className="underline">Reintentar categorías</button></p>}
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
      )}
    </div>
  );
}

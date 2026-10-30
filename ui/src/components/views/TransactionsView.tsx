import { useState } from 'react';
import { Download, Loader2, Plus } from 'lucide-react';
import type { TransactionPageResponse, Transaction } from '../../types';
import type { TransactionFilterParams } from '../../services';
import { api } from '../../services/api';
import { ManualTransactionModal } from '../transactions/ManualTransactionModal';
import { TransactionTable, TransactionFilters } from '../transactions';

interface TransactionsViewProps {
  pageData: TransactionPageResponse | null;
  loading: boolean;
  filters: TransactionFilterParams;
  exportFilters?: TransactionFilterParams;
  onFilterChange: (filters: Partial<TransactionFilterParams>) => void;
  onPageChange: (newPage: number) => void;
  onSelectTransaction: (transaction: Transaction) => void;
  periodName?: string;
  onCreated?: (transaction: Transaction) => void;
}

export function TransactionsView({
  pageData,
  loading,
  filters,
  exportFilters = filters,
  onFilterChange,
  onPageChange,
  onSelectTransaction,
  periodName,
  onCreated,
}: TransactionsViewProps) {
  const [manualOpen, setManualOpen] = useState(false);
  const [created, setCreated] = useState<Transaction | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const month = periodName?.match(/^(\d{1,2})\/(\d{4})$/);
  const selectedMonth = month ? new Date(Number(month[2]), Number(month[1]) - 1, 1).toLocaleDateString('es-PE', { month: 'long', year: 'numeric' }) : periodName || 'Mes seleccionado';
  const start = filters.startDate?.slice(0, 10), end = filters.endDate?.slice(0, 10);
  const dateScope = filters.allHistory ? 'Todo el historial' : start && end
    ? start === end ? start.split('-').reverse().join('/') : `${start.split('-').reverse().join('/')} al ${end.split('-').reverse().join('/')}`
    : selectedMonth;

  const handleExportCSV = async () => {
    setIsExporting(true);
    setExportError(null);
    try {
      const blob = await api.exportTransactionsCsv(exportFilters);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `fintrack_movimientos_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch {
      setExportError('No se pudo exportar el historial completo. Vuelve a pulsar Exportar CSV para reintentar.');
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="space-y-6">


      {manualOpen && <ManualTransactionModal onClose={() => setManualOpen(false)} onCreated={transaction => { setManualOpen(false); setCreated(transaction); onCreated?.(transaction); }} />}
      {created && <p role="status" className="rounded-xl border border-positive/20 bg-positive/5 p-4 text-sm text-ink">Registro manual guardado. Si no aparece, revisa los filtros y la fecha. <button type="button" className="font-semibold text-brand underline" onClick={() => onSelectTransaction(created)}>Ver registro creado</button></p>}
      {exportError && <p role="alert" className="rounded-xl border border-negative/20 bg-negative/5 p-4 text-sm text-negative">{exportError}</p>}
          <TransactionFilters
            renderHeader={searchField => (
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-semibold tracking-tight sm:text-4xl">Movimientos</h1>
        </div>
        <div className="flex w-full flex-wrap items-center gap-3 lg:w-auto">
          {searchField}
        <button type="button" onClick={() => setManualOpen(true)} aria-label="Agregar movimiento" title="Agregar movimiento" className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white"><Plus className="h-4 w-4" />Agregar movimiento</button>
        <button
          type="button"
          onClick={handleExportCSV}
          disabled={isExporting || loading || !pageData?.totalElements}
          className="inline-flex items-center gap-2 rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-semibold text-ink transition-colors hover:border-brand hover:text-brand disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isExporting ? <Loader2 className="h-4 w-4 animate-spin text-brand" /> : <Download className="h-4 w-4" />}
          {isExporting ? 'Exportando...' : 'Exportar CSV'}
        </button>
        </div>
      </div>
            )}
            dateScope={dateScope}
            categoryGroup={filters.categoryGroup}
            merchantName={filters.merchantName}
            search={filters.search || ''}
            flowType={filters.flowType || ''}
            category={filters.category || ''}
            channel={filters.channel || ''}
            minAmount={filters.minAmount}
            maxAmount={filters.maxAmount}
            startDate={filters.startDate}
            endDate={filters.endDate}
            allHistory={filters.allHistory}
            pendingReason={filters.pendingReason}
            onFilterChange={onFilterChange}
            onReset={() =>
              onFilterChange({
                merchantName: undefined,
                categoryGroup: undefined,
                search: '',
                flowType: '',
                category: '',
                channel: '',
                minAmount: undefined,
                maxAmount: undefined,
                startDate: undefined,
                endDate: undefined,
                allHistory: false,
                pendingReason: '',
              })
            }
          />
      <section className="surface overflow-hidden" aria-label="Listado de movimientos">
        <TransactionTable
          pageData={pageData}
          flowType={filters.flowType}
          loading={loading}
          periodName={dateScope}
          onPageChange={onPageChange}
          onSelectTransaction={onSelectTransaction}
        />
      </section>
    </div>
  );
}

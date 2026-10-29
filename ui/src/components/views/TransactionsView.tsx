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
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">{filters.allHistory ? 'Historial completo' : 'Historial del período'}</p>
          <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Movimientos</h1>
          <p className="mt-2 text-sm text-muted">Consulta, filtra y clasifica las entradas, gastos y transferencias registradas.</p>
        </div>
        <div className="flex flex-wrap gap-3">
        <button type="button" onClick={() => setManualOpen(true)} className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white"><Plus className="h-4 w-4" />Registrar manualmente</button>
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

      {manualOpen && <ManualTransactionModal onClose={() => setManualOpen(false)} onCreated={transaction => { setManualOpen(false); setCreated(transaction); onCreated?.(transaction); }} />}
      {created && <p role="status" className="rounded-xl border border-positive/20 bg-positive/5 p-4 text-sm text-ink">Registro manual guardado. Si no aparece, revisa los filtros y la fecha. <button type="button" className="font-semibold text-brand underline" onClick={() => onSelectTransaction(created)}>Ver registro creado</button></p>}
      {exportError && <p role="alert" className="rounded-xl border border-negative/20 bg-negative/5 p-4 text-sm text-negative">{exportError}</p>}
      <section className="surface overflow-hidden" aria-label="Listado de movimientos">
        <div className="border-b border-line px-5 py-5 sm:px-6">
          <TransactionFilters
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
        </div>
        <TransactionTable
          pageData={pageData}
          flowType={filters.flowType}
          loading={loading}
          periodName={filters.allHistory ? 'Todo el historial' : filters.startDate && filters.endDate
            ? `${filters.startDate.slice(0, 10).split('-').reverse().join('/')} al ${filters.endDate.slice(0, 10).split('-').reverse().join('/')}`
            : periodName}
          onPageChange={onPageChange}
          onSelectTransaction={onSelectTransaction}
        />
      </section>
    </div>
  );
}

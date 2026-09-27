import { Download } from 'lucide-react';
import type { PageResponse, Transaction } from '../../types';
import type { TransactionFilterParams } from '../../services';
import { TransactionTable, TransactionFilters } from '../transactions';

interface TransactionsViewProps {
  pageData: PageResponse<Transaction> | null;
  loading: boolean;
  filters: TransactionFilterParams;
  onFilterChange: (filters: Partial<TransactionFilterParams>) => void;
  onPageChange: (newPage: number) => void;
  onSelectTransaction: (transaction: Transaction) => void;
  periodName?: string;
}

export function TransactionsView({
  pageData,
  loading,
  filters,
  onFilterChange,
  onPageChange,
  onSelectTransaction,
  periodName,
}: TransactionsViewProps) {
  const exportToCSV = () => {
    if (!pageData?.content.length) return;
    const headers = ['ID', 'Fecha', 'Comercio', 'Monto', 'Tipo', 'Canal', 'Tarjeta', 'Hash'];
    const rows = pageData.content.map((transaction) => [
      transaction.id,
      transaction.transactionDate,
      transaction.contactName,
      transaction.amount,
      transaction.flowType,
      transaction.channel,
      transaction.cardLast4 || '',
      transaction.transactionHash,
    ]);
    const escapeCell = (value: string | number) => `"${String(value).replace(/"/g, '""')}"`;
    const csvContent = [headers, ...rows].map((row) => row.map(escapeCell).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF', csvContent], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `fintrack_movimientos_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Historial del período</p>
          <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Movimientos</h1>
          <p className="mt-2 text-sm text-muted">Consulta las entradas, los gastos y las transferencias registradas.</p>
        </div>
        <button
          type="button"
          onClick={exportToCSV}
          disabled={!pageData?.content.length}
          className="inline-flex items-center gap-2 rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-semibold text-ink transition-colors hover:border-brand hover:text-brand disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Download className="h-4 w-4" /> Exportar página CSV
        </button>
      </div>

      <section className="surface overflow-hidden" aria-label="Listado de movimientos">
        <div className="border-b border-line px-5 py-5 sm:px-6">
          <TransactionFilters
            search={filters.search || ''}
            flowType={filters.flowType || ''}
            onSearchChange={(search) => onFilterChange({ search })}
            onFlowTypeChange={(flowType) => onFilterChange({ flowType })}
            onReset={() => onFilterChange({ search: '', flowType: '' })}
          />
        </div>
        <TransactionTable
          pageData={pageData}
          loading={loading}
          periodName={periodName}
          onPageChange={onPageChange}
          onSelectTransaction={onSelectTransaction}
        />
      </section>
    </div>
  );
}

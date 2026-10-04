import { useEffect, useRef, useState } from 'react';
import { Check, Copy, X } from 'lucide-react';
import type { Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel } from '../../utils';
import { InstrumentAssignment } from '../instruments/InstrumentAssignment';

interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
  onUpdated?: (transaction: Transaction) => void;
}

export function TransactionDetailModal({ transaction, onClose, onUpdated }: TransactionDetailModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (transaction && !dialog.open) dialog.showModal();
    if (!transaction && dialog.open) dialog.close();
  }, [transaction]);

  const copyHash = async () => {
    if (!transaction?.transactionHash) return;
    await navigator.clipboard.writeText(transaction.transactionHash);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2000);
  };

  if (!transaction) return null;

  const isIncome = transaction.flowType === 'INCOME';
  const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
  const flowLabel = isIncome ? 'Entrada' : isTransfer ? 'Transferencia propia' : 'Gasto';

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={(event) => { if (event.target === dialogRef.current) onClose(); }}
      aria-labelledby="transaction-dialog-title"
      className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-0 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4 border-b border-line px-6 py-5">
        <div>
          <p className="eyebrow">{flowLabel}</p>
          <h2 id="transaction-dialog-title" className="font-display mt-1 text-xl font-semibold">Detalle del movimiento</h2>
        </div>
        <button type="button" onClick={onClose} aria-label="Cerrar detalle" className="rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink"><X className="h-5 w-5" /></button>
      </div>

      <div className="px-6 py-6">
        <p className={`font-display font-num text-4xl font-semibold tracking-tight ${isIncome ? 'text-positive' : isTransfer ? 'text-brand' : 'text-ink'}`}>
          {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
        </p>
        <p className="mt-2 text-base font-medium">{transaction.contactName || 'Movimiento'}</p>

        <dl className="mt-7 divide-y divide-line border-y border-line text-sm">
          <div className="flex justify-between gap-4 py-3.5"><dt className="text-muted">Tipo</dt><dd className="text-right font-medium">{flowLabel}</dd></div>
          <div className="flex justify-between gap-4 py-3.5"><dt className="text-muted">Medio</dt><dd className="text-right font-medium">{getChannelLabel(transaction.channel)}{transaction.cardLast4 ? ` ··${transaction.cardLast4}` : ''}</dd></div>
          <div className="flex justify-between gap-4 py-3.5"><dt className="text-muted">Fecha y hora</dt><dd className="text-right font-medium">{formatDate(transaction.transactionDate)}</dd></div>
        </dl>

        {onUpdated && <InstrumentAssignment key={transaction.id} transaction={transaction} onUpdated={onUpdated} />}

        <details className="mt-5 text-sm">
          <summary className="cursor-pointer font-medium text-brand">Información técnica</summary>
          <p className="mt-3 text-xs leading-relaxed text-muted">Este identificador ayuda a evitar movimientos duplicados.</p>
          <div className="mt-2 flex items-start gap-2 rounded-xl bg-canvas p-3">
            <code className="min-w-0 flex-1 break-all text-xs text-muted">{transaction.transactionHash}</code>
            <button type="button" onClick={copyHash} aria-label="Copiar identificador" className="shrink-0 rounded-lg p-1 text-brand hover:bg-brand/10">
              {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            </button>
          </div>
        </details>
      </div>
    </dialog>
  );
}

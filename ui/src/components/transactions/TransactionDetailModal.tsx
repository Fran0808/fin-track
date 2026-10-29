import { useEffect, useRef, useState } from 'react';
import { Check, X, Loader2 } from 'lucide-react';
import type { ClassificationRuleDraft, Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel, getTransactionFlowLabel, isOwnCardPayment } from '../../utils';
import { api } from '../../services/api';
import { useCategories } from '../../hooks';
import { CustomSelect } from '../common';
import { TransactionOriginPanel } from './TransactionOriginPanel';
import { ManualReconciliationPanel } from './ManualReconciliationPanel';

interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
  onUpdated?: (transaction: Transaction) => void;
  onCreateRule?: (draft: ClassificationRuleDraft) => void;
}

export function TransactionDetailModal(props: TransactionDetailModalProps) {
  return props.transaction ? <TransactionDetailContent key={props.transaction.id} {...props} transaction={props.transaction} /> : null;
}

function TransactionDetailContent({ transaction, onClose, onUpdated, onCreateRule }: TransactionDetailModalProps & { transaction: Transaction }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { options: categoryOptions, loading: loadingCategories, error: categoryError, refresh: refreshCategories } = useCategories(true);

  // Classification state
  const [category, setCategory] = useState<string>(transaction.category || '');
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [reconciling, setReconciling] = useState(false);
  const busy = isSaving || reconciling;
  const [archived, setArchived] = useState(transaction.reconciledToId != null);
  const readOnly = transaction.reconciledToId != null || archived;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (transaction && !dialog.open) dialog.showModal();
    if (!transaction && dialog.open) dialog.close();
  }, [transaction]);

  const handleSaveClassification = async () => {
    if (!transaction) return;
    setIsSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const updated = await api.updateTransactionClassification(transaction.id, {
        category,
      });
      setSaveSuccess(true);
      window.setTimeout(() => setSaveSuccess(false), 2500);
      if (onUpdated) onUpdated(updated);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Error al guardar cambios');
    } finally {
      setIsSaving(false);
    }
  };

  if (!transaction) return null;

  const isIncome = transaction.flowType === 'INCOME';
  const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
  const flowLabel = getTransactionFlowLabel(transaction);
  const ownCardPayment = isOwnCardPayment(transaction);

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onCancel={event => { if (busy) event.preventDefault(); }}
      onClick={(event) => { if (!busy && event.target === dialogRef.current) onClose(); }}
      aria-labelledby="transaction-dialog-title"
      className="m-auto max-h-[92dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-0 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
        <div>
          <p className="eyebrow">{flowLabel}</p>
          <h2 id="transaction-dialog-title" className="font-display mt-0.5 text-lg font-semibold">Detalle del movimiento</h2>
        </div>
        <button type="button" onClick={onClose} disabled={busy} aria-label="Cerrar detalle" className="rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink"><X className="h-5 w-5" /></button>
      </div>

      <div className="px-6 py-5">
        <p className={`font-display font-num text-3xl font-semibold tracking-tight ${isIncome ? 'text-positive' : isTransfer ? 'text-brand' : 'text-ink'}`}>
          {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
        </p>
        <p className="mt-1 text-sm font-medium">{transaction.contactName || 'Movimiento'}</p>
        {ownCardPayment && isTransfer && <p role="status" className="mt-1.5 text-xs text-muted">No suma gastos ni entradas y no afecta el flujo neto.</p>}

        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 border-y border-line py-3.5 text-sm">
          <div>
            <dt className="text-xs text-muted">Medio</dt>
            <dd className="mt-0.5 font-medium">{getChannelLabel(transaction.channel)}{transaction.cardLast4 ? ` ··${transaction.cardLast4}` : ''}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Fecha y hora</dt>
            <dd className="mt-0.5 font-medium">{formatDate(transaction.transactionDate)}</dd>
          </div>
          <TransactionOriginPanel transactionId={transaction.id} />
        </dl>

        {onUpdated && transaction.captureOrigin && ['MANUAL','EMAIL','PHONE'].includes(transaction.captureOrigin) && <ManualReconciliationPanel transaction={transaction} onUpdated={onUpdated} onBusyChange={setReconciling} onArchivedChange={setArchived} />}
        {readOnly && <p role="status" className="mt-3 text-xs text-muted">Este registro manual se conserva como antecedente y ya no suma en los totales.</p>}

        <fieldset disabled={busy || readOnly} className="mt-4 rounded-xl border border-line bg-canvas/40 p-3.5">
          <p className="eyebrow text-ink!">Clasificación</p>

          <div className="mt-2.5 space-y-3">
            <div>
              <label htmlFor="modal-category" className="block text-xs font-semibold text-muted">
                Categoría
              </label>
              <div className="mt-1">
                <CustomSelect
                  id="modal-category"
                  disabled={loadingCategories || !!categoryError || busy}
                  value={category}
                  onChange={(val) => setCategory(val)}
                  options={[
                    { value: '', label: 'Sin categorizar' },
                    ...(category && !categoryOptions.some((o) => o.id === category)
                      ? [{ value: category, label: category }]
                      : []),
                    ...categoryOptions.map((cat) => ({ value: cat.id, label: cat.label })),
                  ]}
                />
              </div>
              {categoryError && <p role="alert" className="mt-1.5 text-xs text-negative">{categoryError} <button type="button" onClick={() => void refreshCategories()} className="underline">Reintentar categorías</button></p>}
            </div>

            {saveError && (
              <p role="alert" className="text-xs font-medium text-negative">
                {saveError}
              </p>
            )}

            <div className="flex items-center justify-between pt-0.5">
              <span className="text-xs text-muted">
                {saveSuccess ? (
                  <span className="inline-flex items-center gap-1 font-semibold text-positive">
                    <Check className="h-3.5 w-3.5" /> Cambios guardados
                  </span>
                ) : (
                  'Guarda para actualizar la clasificación'
                )}
              </span>
              <button
                type="button"
                onClick={handleSaveClassification}
                disabled={busy}
                className="inline-flex items-center gap-1.5 rounded-xl bg-brand px-3.5 py-1.5 text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                {isSaving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                Guardar clasificación
              </button>
            </div>
          </div>
        </fieldset>

        {!readOnly && !isTransfer && transaction.contactName?.trim() && onCreateRule && <button type="button" disabled={busy}
          onClick={() => onCreateRule({ matchText: transaction.contactName.trim(), categoryName: category })}
          className="mt-3 text-xs font-semibold text-brand hover:underline disabled:opacity-50">Crear regla para este comercio</button>}

      </div>
    </dialog>
  );
}

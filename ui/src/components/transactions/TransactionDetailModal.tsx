import { useEffect, useRef, useState } from 'react';
import { Check, X, Loader2 } from 'lucide-react';
import type { ClassificationRuleDraft, Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel } from '../../utils';
import { api } from '../../services/api';
import { useCategories } from '../../hooks';
import { InstrumentAssignment } from '../instruments/InstrumentAssignment';
import { CustomSelect } from '../common';

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

        <div className="mt-6 rounded-2xl border border-line bg-canvas/40 p-4">
          <p className="eyebrow text-ink!">Clasificación</p>
          
          <div className="mt-3.5 space-y-4">
            <div>
              <label htmlFor="modal-category" className="block text-xs font-semibold text-muted">
                Categoría
              </label>
              <div className="mt-1.5">
                <CustomSelect
                  id="modal-category"
                  disabled={loadingCategories || !!categoryError || isSaving}
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
              {categoryError && <p role="alert" className="mt-2 text-xs text-negative">{categoryError} <button type="button" onClick={() => void refreshCategories()} className="underline">Reintentar categorías</button></p>}
            </div>

            {saveError && (
              <p role="alert" className="text-xs font-medium text-negative">
                {saveError}
              </p>
            )}

            <div className="flex items-center justify-between pt-1">
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
                disabled={isSaving}
                className="inline-flex items-center gap-1.5 rounded-xl bg-brand px-3.5 py-1.5 text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                {isSaving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                Guardar clasificación
              </button>
            </div>
          </div>
        </div>

        {!isTransfer && transaction.contactName?.trim() && onCreateRule && <button type="button" disabled={isSaving}
          onClick={() => onCreateRule({ matchText: transaction.contactName.trim(), categoryName: category })}
          className="mt-4 text-sm font-semibold text-brand hover:underline disabled:opacity-50">Crear regla para este comercio</button>}

        {onUpdated && <InstrumentAssignment key={transaction.id} transaction={transaction} onUpdated={onUpdated} />}

      </div>
    </dialog>
  );
}

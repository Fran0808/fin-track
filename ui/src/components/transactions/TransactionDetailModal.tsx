import { useEffect, useRef, useState } from 'react';
import { Check, Copy, X, Plus, Tag, Loader2 } from 'lucide-react';
import type { Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel } from '../../utils';
import { api } from '../../services/api';
import { useCategories } from '../../hooks';
import { InstrumentAssignment } from '../instruments/InstrumentAssignment';

interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
  onUpdated?: (transaction: Transaction) => void;
}

export function TransactionDetailModal({ transaction, onClose, onUpdated }: TransactionDetailModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [copied, setCopied] = useState(false);
  const { options: categoryOptions } = useCategories(true);

  // Classification state
  const [category, setCategory] = useState<string>('');
  const [tags, setTags] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (transaction && !dialog.open) dialog.showModal();
    if (!transaction && dialog.open) dialog.close();
  }, [transaction]);

  useEffect(() => {
    if (transaction) {
      setCategory(transaction.category || '');
      setTags(transaction.tags || []);
      setNotes(transaction.notes || '');
      setNewTagInput('');
      setSaveSuccess(false);
      setSaveError(null);
    }
  }, [transaction]);

  const copyHash = async () => {
    if (!transaction?.transactionHash) return;
    await navigator.clipboard.writeText(transaction.transactionHash);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2000);
  };

  const handleAddTag = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const clean = newTagInput.trim().replace(/^#+/, '').toLowerCase();
    if (!clean) return;
    if (!tags.includes(clean)) {
      setTags([...tags, clean]);
    }
    setNewTagInput('');
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((t) => t !== tagToRemove));
  };

  const handleSaveClassification = async () => {
    if (!transaction) return;
    setIsSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const updated = await api.updateTransactionClassification(transaction.id, {
        category: category || null,
        tags: tags.length > 0 ? tags : null,
        notes: notes.trim() || null,
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

        {/* Clasificación y Organización */}
        <div className="mt-6 rounded-2xl border border-line bg-canvas/40 p-4">
          <p className="eyebrow !text-ink">Categoría y etiquetas</p>
          
          <div className="mt-3.5 space-y-4">
            <div>
              <label htmlFor="modal-category" className="block text-xs font-semibold text-muted">
                Categoría
              </label>
              <select
                id="modal-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2 text-sm font-medium text-ink focus:border-brand focus:outline-none"
              >
                <option value="">Sin categorizar</option>
                {category && !categoryOptions.some((o) => o.id === category) && (
                  <option value={category}>{category}</option>
                )}
                {categoryOptions.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="modal-tag-input" className="block text-xs font-semibold text-muted">
                Etiquetas (#tags)
              </label>
              <div className="mt-1.5 flex gap-2">
                <div className="relative flex-1">
                  <Tag className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted" />
                  <input
                    id="modal-tag-input"
                    type="text"
                    value={newTagInput}
                    onChange={(e) => setNewTagInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddTag();
                      }
                    }}
                    placeholder="ej. almuerzo, trabajo, viaje"
                    className="w-full rounded-xl border border-line bg-white py-1.5 pl-8 pr-3 text-xs font-medium text-ink placeholder:text-muted focus:border-brand focus:outline-none"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => handleAddTag()}
                  className="inline-flex items-center gap-1 rounded-xl border border-line bg-white px-3 py-1.5 text-xs font-semibold text-ink hover:border-brand hover:text-brand"
                >
                  <Plus className="h-3.5 w-3.5" /> Agregar
                </button>
              </div>

              {tags.length > 0 && (
                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  {tags.map((t) => (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1 rounded-lg border border-line bg-white px-2 py-0.5 text-xs font-medium text-ink"
                    >
                      #{t}
                      <button
                        type="button"
                        onClick={() => handleRemoveTag(t)}
                        className="text-muted hover:text-negative"
                        aria-label={`Quitar etiqueta ${t}`}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div>
              <label htmlFor="modal-notes" className="block text-xs font-semibold text-muted">
                Notas personales
              </label>
              <textarea
                id="modal-notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={2}
                placeholder="Añade un apunte o recordatorio sobre este movimiento..."
                className="mt-1.5 w-full resize-none rounded-xl border border-line bg-white p-2.5 text-xs font-medium text-ink placeholder:text-muted focus:border-brand focus:outline-none"
              />
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

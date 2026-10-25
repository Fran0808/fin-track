import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import type { ClassificationRule, RuleHistoryPreview, RuleHistoryApplyResponse } from '../../types';
import { api } from '../../services/api';
import { formatCurrency, formatDate } from '../../utils';

interface Props {
  rule: ClassificationRule;
  onClose: () => void;
  onApplied: (result: RuleHistoryApplyResponse) => void;
}

export function RuleHistoryModal({ rule, onClose, onApplied }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [returnFocus] = useState(() => document.activeElement instanceof HTMLElement ? document.activeElement : null);
  const [preview, setPreview] = useState<RuleHistoryPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => {
      if (dialog?.open) dialog.close();
      if (returnFocus?.isConnected) returnFocus.focus();
    };
  }, [returnFocus]);

  useEffect(() => {
    let active = true;
    api.previewRuleHistory(rule.id).then(value => { if (active) setPreview(value); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'No se pudo cargar la vista previa.'); });
    return () => { active = false; };
  }, [rule.id, attempt]);

  const confirm = async () => {
    if (!preview || saving || preview.transactions.length === 0) return;
    setSaving(true); setError(null);
    try {
      const result = await api.applyRuleHistory(preview);
      onApplied(result);
    } catch (reason) {
      setPreview(null);
      setError(reason instanceof Error ? reason.message : 'No se pudo aplicar la regla. Revisa de nuevo antes de confirmar.');
      setSaving(false);
    }
  };

  return <dialog ref={dialogRef} aria-labelledby="rule-history-title" aria-describedby="rule-history-description"
    onCancel={event => { event.preventDefault(); if (!saving) onClose(); }}
    className="fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-2xl border border-line bg-white p-0 text-ink shadow-2xl backdrop:bg-ink/40">
    <div className="flex items-start justify-between gap-3 border-b border-line p-5 sm:p-6">
      <div>
        <h2 id="rule-history-title" className="font-display text-xl font-semibold">Aplicar a movimientos anteriores</h2>
        <p id="rule-history-description" className="mt-2 text-sm text-muted">Revisa las entradas y gastos sin categoría que recibirán esta regla. Se respeta el orden de prioridad; las transferencias quedan excluidas.</p>
      </div>
      <button type="button" disabled={saving} onClick={onClose} aria-label="Cerrar vista previa" className="shrink-0 rounded-lg p-2 text-muted hover:bg-canvas disabled:opacity-40"><X className="h-5 w-5" /></button>
    </div>
    <div className="space-y-4 p-5 sm:p-6">
      {!preview && !error && <p role="status" className="text-sm text-muted">Buscando movimientos sin categoría...</p>}
      {error && <div><p role="alert" className="text-sm text-negative">{error}</p>
        <button type="button" onClick={() => { setError(null); setPreview(null); setAttempt(value => value + 1); }} className="mt-3 text-sm font-semibold text-brand">Revisar de nuevo</button></div>}
      {preview && <>
        <p className="break-words text-sm">Contiene <strong>«{preview.matchText}»</strong> → <strong className="text-brand">{preview.categoryName}</strong></p>
        <p className="text-sm font-semibold">{preview.transactions.length} movimientos en esta vista previa · Todo el historial</p>
        {preview.hasMore && <p role="status" className="text-sm text-muted">Hay más coincidencias. Este lote incluye solo los primeros {preview.limit} movimientos. Después de aplicarlo, puedes revisar otro lote.</p>}
        {preview.transactions.length === 0 ? <p role="status" className="text-sm text-muted">No hay movimientos anteriores sin categoría para esta regla. Otra regla con mayor prioridad puede cubrir las coincidencias.</p> : <>
          <div className="max-h-[45dvh] overflow-auto rounded-xl border border-line">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Movimientos que recibirán la categoría {preview.categoryName}</caption>
              <thead className="sticky top-0 bg-canvas text-xs text-muted"><tr><th className="p-3">Comercio y fecha</th><th className="p-3">Tipo</th><th className="p-3 text-right">Importe</th></tr></thead>
              <tbody className="divide-y divide-line">{preview.transactions.map(transaction => <tr key={transaction.id}>
                <td className="max-w-48 break-words p-3"><p>{transaction.contactName}</p><p className="mt-1 text-xs text-muted">{formatDate(transaction.transactionDate)}</p></td>
                <td className="p-3 text-xs text-muted">{transaction.flowType === 'INCOME' ? 'Entrada' : 'Gasto'}</td>
                <td className="font-num whitespace-nowrap p-3 text-right">{formatCurrency(transaction.amount)}</td>
              </tr>)}</tbody>
            </table>
          </div>
          <p className="text-xs text-muted">Solo se guardará la categoría de los movimientos mostrados. Las categorías ya asignadas se conservarán. La vista previa vence en 10 minutos.</p>
        </>}
      </>}
    </div>
    <div className="flex flex-wrap justify-end gap-3 border-t border-line p-5 sm:px-6">
      <button type="button" autoFocus disabled={saving} onClick={onClose} className="rounded-xl border border-line px-4 py-2.5 text-sm font-semibold disabled:opacity-40">Cancelar</button>
      <button type="button" disabled={saving || !preview?.transactions.length} onClick={() => void confirm()}
        className="rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-40">{saving ? 'Aplicando...' : `Confirmar clasificación${preview ? ` (${preview.transactions.length})` : ''}`}</button>
    </div>
  </dialog>;
}

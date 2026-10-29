import { useCallback, useEffect, useState } from 'react';
import { api } from '../../services/api';
import { useQueryResource } from '../../hooks/useQueryResource';
import type { ManualReconciliationPreview, Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel, getTransactionFlowLabel } from '../../utils';

function Evidence({ transaction }: { transaction: Transaction }) {
  return <dl className="mt-2 space-y-1 text-sm">
    <div><dt className="inline text-muted">Concepto: </dt><dd className="inline break-words">{transaction.contactName}</dd></div>
    <div><dt className="inline text-muted">Movimiento: </dt><dd className="inline">{getTransactionFlowLabel(transaction)} · {formatCurrency(transaction.amount)}</dd></div>
    <div><dt className="inline text-muted">Fecha: </dt><dd className="inline">{formatDate(transaction.transactionDate)}</dd></div>
    <div><dt className="inline text-muted">Medio: </dt><dd className="inline">{getChannelLabel(transaction.channel)}</dd></div>
    <div><dt className="inline text-muted">Categoría: </dt><dd className="inline">{transaction.category || 'Sin categoría'}</dd></div>
  </dl>;
}

export function ManualReconciliationPanel({ transaction, onUpdated, onBusyChange, onArchivedChange }: {
  transaction: Transaction; onUpdated: (transaction: Transaction) => void; onBusyChange: (busy: boolean) => void; onArchivedChange?: (archived: boolean) => void;
}) {
  const load = useCallback(() => api.getManualReconciliation(transaction.id), [transaction.id]);
  const status = useQueryResource(load, 'No se pudo consultar la conciliación.');
  const [preview, setPreview] = useState<ManualReconciliationPreview | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [undoing, setUndoing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const manual = status.data?.manual;
  const captured = status.data?.captured;
  useEffect(() => {
    if (manual?.id === transaction.id) onArchivedChange?.(manual.reconciledToId != null);
  }, [manual, transaction.id, onArchivedChange]);
  const candidate = preview?.candidates.find(item => item.transaction.id === selected);
  const search = async () => {
    setLoading(true); setError(null); setSelected(null);
    try { setPreview(await api.previewManualReconciliation(transaction.id)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'No se pudieron consultar las capturas.'); }
    finally { setLoading(false); }
  };
  const apply = async (undo: boolean) => {
    if (busy) return;
    setBusy(true); onBusyChange(true); setError(null);
    try {
      const result = undo && manual && captured
        ? await api.undoManualReconciliation(manual.id, captured.id, status.data!.revision)
        : preview && candidate ? await api.reconcileManual(preview.manual.id, candidate.transaction.id, preview.expiresAt, candidate.previewToken) : null;
      if (!result) return;
      setPreview(null); setSelected(null); setUndoing(false);
      setFeedback(undo ? 'Conciliación deshecha. El registro manual vuelve a contar en sus totales.' : 'Movimiento conciliado. Se cuenta una sola vez.');
      await status.refresh(); onUpdated(result);
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'No se pudo completar la conciliación.'); }
    finally { setBusy(false); onBusyChange(false); }
  };
  if (!status.loading && !status.error && !manual && transaction.captureOrigin !== 'MANUAL' && !feedback) return null;
  return <section aria-labelledby="manual-reconciliation-title" className="mt-6 rounded-2xl border border-line p-4">
    <h3 id="manual-reconciliation-title" className="text-sm font-semibold">Conciliación del registro manual</h3>
    {!status.loading && <button type="button" disabled={busy} onClick={() => void status.refresh()} className="mt-2 text-xs font-semibold text-brand underline disabled:opacity-50">Actualizar estado de conciliación</button>}
    {status.loading && <p role="status" className="mt-2 text-sm text-muted">Consultando conciliación...</p>}
    {status.error && <p role="status" className="mt-2 text-sm text-muted">No se pudo consultar la conciliación. <button type="button" onClick={() => void status.refresh()} className="font-semibold text-brand underline">Reintentar conciliación</button></p>}
    {feedback && <p role="status" className="mt-2 text-sm text-positive">{feedback}</p>}
    {!status.loading && !status.error && manual && !captured && <>
      <p className="mt-2 text-sm leading-6 text-muted">Si esta misma operación ya llegó por correo o teléfono, revisa ambas entradas y vincúlalas para contarla una sola vez. No se unirán automáticamente.</p>
      <button type="button" disabled={loading || busy} onClick={() => void search()} className="mt-3 text-sm font-semibold text-brand underline disabled:opacity-50">{loading ? 'Buscando capturas...' : preview ? 'Actualizar coincidencias' : 'Buscar capturas para conciliar'}</button>
    </>}
    {preview && !captured && <div className="mt-4 space-y-4">
      <div className="rounded-xl bg-canvas p-3"><h4 className="text-sm font-semibold">Registro manual que dejará de contar</h4><Evidence transaction={preview.manual} /></div>
      <p className="text-xs leading-5 text-muted">Se proponen capturas con igual importe y tipo, hasta tres días antes o después. La coincidencia no demuestra que sean la misma operación. Se muestran hasta {preview.limit} opciones.</p>
      {!preview.candidates.length ? <p className="text-sm text-muted">No hay capturas compatibles para revisar. Puedes volver a buscar cuando llegue el comprobante.</p> : <fieldset disabled={busy} className="space-y-3"><legend className="mb-2 text-sm font-semibold">Captura que se conservará</legend>
        {preview.candidates.map(item => <label key={item.transaction.id} className="block rounded-xl border border-line p-3">
          <span className="flex items-start gap-2"><input type="radio" name="manual-capture" value={item.transaction.id} checked={selected === item.transaction.id} onChange={() => setSelected(item.transaction.id)} className="mt-1" />
            <span className="text-sm font-semibold">{item.transaction.captureOrigin === 'EMAIL' ? 'Correo' : 'Teléfono'} · {item.transaction.contactName}</span></span>
          <Evidence transaction={item.transaction} />
        </label>)}
      </fieldset>}
      {preview.hasMore && <p className="text-sm text-muted">Hay más capturas compatibles fuera de estas {preview.limit} opciones. No confirmes si la tuya no está en la revisión.</p>}
      {candidate && <p className="rounded-xl bg-brand/5 p-3 text-sm leading-6">El registro manual de {formatCurrency(preview.manual.amount)} del {formatDate(preview.manual.transactionDate)} dejará de sumar en esa fecha. Se conservará la captura del {formatDate(candidate.transaction.transactionDate)}, con su categoría actual. Esto puede cambiar los totales de un mes si las fechas pertenecen a meses distintos. Ambos registros se conservarán como evidencia.</p>}
      <div className="flex flex-wrap gap-3"><button type="button" disabled={!candidate || busy} onClick={() => void apply(false)} className="rounded-xl bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{busy ? 'Conciliando...' : 'Confirmar conciliación'}</button>
        <button type="button" disabled={busy} onClick={() => { setPreview(null); setSelected(null); setError(null); }} className="rounded-xl border border-line px-3 py-2 text-sm">Cancelar revisión</button></div>
    </div>}
    {manual && captured && <div className="mt-3 space-y-3">
      <p className="text-sm leading-6 text-muted">El registro manual está conciliado con la captura #{captured.id}. Solo la captura cuenta en los totales. El antecedente manual conserva sus datos y no se edita mientras esté vinculado.</p>
      {transaction.id !== captured.id && <button type="button" disabled={busy} onClick={() => onUpdated(captured)} className="text-sm font-semibold text-brand underline">Ver captura conservada</button>}
      <div className="rounded-xl bg-canvas p-3"><h4 className="text-sm font-semibold">Antecedente manual #{manual.id}</h4><Evidence transaction={manual} /></div>
      {status.data?.reconciledAt && <p className="text-xs text-muted">Conciliado: {formatDate(status.data.reconciledAt)}</p>}
      {!undoing ? <button type="button" disabled={busy} onClick={() => setUndoing(true)} className="text-sm font-semibold text-brand underline">Deshacer conciliación</button> : <>
        <p className="text-sm leading-6">El manual de {formatCurrency(manual.amount)} volverá a sumar en su fecha original. La captura seguirá contando; si son la misma operación, volverás a contarla dos veces.</p>
        <div className="flex flex-wrap gap-3"><button type="button" disabled={busy} onClick={() => void apply(true)} className="text-sm font-semibold text-brand underline">{busy ? 'Deshaciendo...' : 'Confirmar deshacer conciliación'}</button><button type="button" disabled={busy} onClick={() => setUndoing(false)} className="text-sm underline">Cancelar</button></div>
      </>}
    </div>}
    {error && <p role="alert" className="mt-3 text-sm leading-6 text-negative">{error}</p>}
  </section>;
}

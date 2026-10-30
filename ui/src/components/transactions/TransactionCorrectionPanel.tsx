import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import type { FlowType, Transaction, TransactionCorrectionHistory, TransactionCorrectionRequest } from '../../types';
import { api } from '../../services/api';
import { formatCurrency, formatDate } from '../../utils';
import { createRequestId } from '../../utils/requestId';
import { CustomSelect } from '../common';

const labels: Record<FlowType, string> = { EXPENSE: 'Gasto', INCOME: 'Entrada', INTERNAL_TRANSFER: 'Transferencia propia' };
const fields = 'mt-1 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink';

export function TransactionCorrectionPanel({ transactionId, mode, onBack, onSaved, onBusyChange }: {
  transactionId: number; mode: 'edit' | 'history'; onBack?: () => void;
  onSaved: (transaction: Transaction) => void; onBusyChange: (busy: boolean) => void;
}) {
  const [history, setHistory] = useState<TransactionCorrectionHistory | null>(null);
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    api.getTransactionCorrections(transactionId, page).then(result => { if (active) setHistory(result); })
      .catch(failure => { if (active) setError(failure instanceof Error ? failure.message : 'No se pudo consultar el movimiento.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [transactionId, page, attempt]);
  const reload = (next = page) => { setHistory(null); setError(null); setLoading(true); setPage(next); setAttempt(value => value + 1); };
  return <section className="space-y-4" aria-label={mode === 'edit' ? 'Corrección del movimiento' : 'Historial de correcciones'}>
    {onBack && <button type="button" onClick={onBack} aria-label="Volver al detalle" title="Volver al detalle" className="rounded-lg p-2 text-brand hover:bg-canvas focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"><ArrowLeft className="h-5 w-5" /></button>}
    <h3 className="font-display text-lg font-semibold">{mode === 'edit' ? 'Corregir datos' : 'Historial de correcciones'}</h3>
    {loading && <p role="status" className="text-sm text-muted">Consultando datos actuales...</p>}
    {error && <p role="alert" className="text-sm text-negative">{error} <button type="button" onClick={() => reload()} className="underline">Reintentar</button></p>}
    {!loading && history && (mode === 'edit'
      ? history.editable ? <CorrectionForm snapshot={history} onSaved={onSaved} onBusyChange={onBusyChange} />
        : <p className="text-sm text-muted">Deshaz la conciliación antes de corregir este movimiento. Sus datos ya forman parte de una pareja revisada.</p>
      : <>
        <p className="text-sm text-muted">Cada cambio conserva los datos anteriores. Los totales usan los datos actuales del movimiento.</p>
        {!history.entries.length && <p className="text-sm text-muted">Este movimiento no tiene correcciones.</p>}
        {history.entries.map(entry => <article key={entry.id} className="rounded-xl border border-line p-4 text-sm">
          <p className="font-semibold">{formatDate(entry.createdAt)}</p><p className="mt-1 break-words text-muted">{entry.reason}</p>
          <dl className="mt-3 space-y-2">
            <div><dt className="text-xs text-muted">Importe</dt><dd>{formatCurrency(entry.beforeAmount)} → {formatCurrency(entry.afterAmount)}</dd></div>
            <div><dt className="text-xs text-muted">Fecha y hora</dt><dd>{formatDate(entry.beforeDate)} → {formatDate(entry.afterDate)}</dd></div>
            <div><dt className="text-xs text-muted">Tipo</dt><dd>{labels[entry.beforeFlow]} → {labels[entry.afterFlow]}</dd></div>
            {entry.beforeOperation === 'OWN_CARD_PAYMENT' && entry.afterOperation !== entry.beforeOperation && <div><dt className="text-xs text-muted">Interpretación anterior</dt><dd>Pago de tarjeta propia</dd></div>}
          </dl>
        </article>)}
        {history.totalPages > 1 && <div className="flex items-center justify-between text-sm"><button type="button" disabled={page === 0} onClick={() => reload(page - 1)} className="text-brand disabled:opacity-50">Anterior</button><span>{page + 1} / {history.totalPages}</span><button type="button" disabled={page + 1 >= history.totalPages} onClick={() => reload(page + 1)} className="text-brand disabled:opacity-50">Siguiente</button></div>}
      </>)}
  </section>;
}

function CorrectionForm({ snapshot, onSaved, onBusyChange }: { snapshot: TransactionCorrectionHistory; onSaved: (transaction: Transaction) => void; onBusyChange: (busy: boolean) => void }) {
  const original = snapshot.transaction;
  const visibleDate = original.transactionDate.slice(0, 23);
  const [amount, setAmount] = useState(String(original.amount));
  const [date, setDate] = useState(visibleDate);
  const [flow, setFlow] = useState(original.flowType);
  const [reason, setReason] = useState('');
  const [requestId] = useState(createRequestId);
  const [review, setReview] = useState<TransactionCorrectionRequest | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => () => onBusyChange(false), [onBusyChange]);
  const prepare = (event: React.FormEvent) => {
    event.preventDefault(); setError(null);
    const normalized = amount.trim().replace(',', '.');
    if (!/^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) { setError('Escribe un importe positivo con hasta dos decimales.'); return; }
    if (!date || Number.isNaN(Date.parse(date)) || !reason.trim()) { setError('Completa una fecha válida y el motivo.'); return; }
    const transactionDate = date === visibleDate ? original.transactionDate : date.length === 16 ? `${date}:00` : date;
    if (Number(normalized) === original.amount && transactionDate === original.transactionDate && flow === original.flowType) { setError('Cambia al menos un dato antes de confirmar.'); return; }
    setReview({ requestId, expectedRevision: snapshot.revision, amount: normalized, transactionDate, flowType: flow, reason: reason.trim() });
  };
  const save = async () => {
    if (!review || saving) return;
    setSaving(true); onBusyChange(true); setError(null);
    try { onSaved(await api.correctTransaction(original.id, review)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'No se pudo guardar. Reintenta con los mismos datos.'); }
    finally { setSaving(false); onBusyChange(false); }
  };
  return <>
    {!review ? <form onSubmit={prepare} className="space-y-4">
      <p className="text-sm text-muted">Corrige lo ocurrido. Se conservarán el registro original y el motivo del cambio.</p>
      <label className="block text-sm font-medium">Importe (S/)<input autoFocus className={fields} required inputMode="decimal" maxLength={11} value={amount} onChange={event => setAmount(event.target.value)} /></label>
      <label className="block text-sm font-medium">Fecha y hora del movimiento<input type="datetime-local" step="any" className={fields} required value={date} onChange={event => setDate(event.target.value)} /></label>
      <div><label htmlFor="correction-flow" className="text-sm font-medium">Tipo</label><div className="mt-1"><CustomSelect<FlowType> id="correction-flow" value={flow} onChange={setFlow} options={Object.entries(labels).map(([value, label]) => ({ value: value as FlowType, label }))} /></div></div>
      <p className="text-xs leading-5 text-muted">Una transferencia entre tus cuentas no suma gastos ni entradas. La fecha determina en qué período se contabiliza.</p>
      <label className="block text-sm font-medium">Motivo de la corrección<textarea className={fields} required maxLength={300} rows={3} value={reason} onChange={event => setReason(event.target.value)} /></label>
      <button type="submit" className="w-full rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white">Revisar corrección</button>
    </form> : <div className="space-y-4 text-sm">
      <h4 className="font-semibold">Antes de confirmar</h4>
      <dl className="space-y-3 rounded-xl border border-line p-4">
        <div><dt className="text-muted">Importe</dt><dd>{formatCurrency(original.amount)} → {formatCurrency(Number(review.amount))}</dd></div>
        <div><dt className="text-muted">Fecha y hora</dt><dd>{formatDate(original.transactionDate)} → {formatDate(review.transactionDate)}</dd></div>
        <div><dt className="text-muted">Tipo</dt><dd>{labels[original.flowType]} → {labels[review.flowType]}</dd></div>
        <div><dt className="text-muted">Motivo</dt><dd className="break-words">{review.reason}</dd></div>
      </dl>
      <div className="rounded-xl bg-canvas p-4 leading-6"><p className="font-semibold">Efecto en los totales</p>
        <p>Se retiran {formatCurrency(original.amount)} de {labels[original.flowType].toLowerCase()} del {formatDate(original.transactionDate)}.</p>
        <p>Se contabilizan {formatCurrency(Number(review.amount))} como {labels[review.flowType].toLowerCase()} el {formatDate(review.transactionDate)}.</p>
        <p>El flujo neto se calcula como entradas menos gastos. Las transferencias propias se muestran por separado.</p>
      </div>
      {original.operationType === 'OWN_CARD_PAYMENT' && review.flowType !== original.flowType && <p className="text-muted">Este cambio sustituye la interpretación de pago de tarjeta propia. La interpretación anterior quedará en el historial.</p>}
      <p className="text-muted">La fecha o el tipo nuevos pueden dejar el movimiento fuera del filtro actual. Se mantienen el origen, la referencia, el medio y la categoría.</p>
      <div className="flex flex-wrap gap-3"><button type="button" disabled={saving} onClick={() => { setReview(null); setError(null); }} className="rounded-xl border border-line px-4 py-2.5 font-semibold disabled:opacity-50">Volver y ajustar</button><button type="button" disabled={saving} onClick={() => void save()} className="rounded-xl bg-brand px-4 py-2.5 font-semibold text-white disabled:opacity-50">{saving ? 'Guardando...' : 'Confirmar corrección'}</button></div>
    </div>}
    {error && <p role="alert" className="text-sm text-negative">{error}</p>}
  </>;
}

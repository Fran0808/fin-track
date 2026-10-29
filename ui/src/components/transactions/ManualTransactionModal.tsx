import { useCallback, useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { api } from '../../services/api';
import { useCategories } from '../../hooks';
import { useQueryResource } from '../../hooks/useQueryResource';
import type { ManualTransactionRequest, Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel } from '../../utils';

function localNow() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function createRequestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function ManualTransactionModal({ onClose, onCreated }: { onClose: () => void; onCreated: (transaction: Transaction) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [previousFocus] = useState(() => document.activeElement as HTMLElement | null);
  const [requestId] = useState(createRequestId);
  const [amount, setAmount] = useState('');
  const [flow, setFlow] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [contact, setContact] = useState('');
  const [date, setDate] = useState(localNow);
  const [method, setMethod] = useState<ManualTransactionRequest['paymentMethod']>('CASH');
  const [categoryId, setCategoryId] = useState('');
  const [productId, setProductId] = useState('');
  const [review, setReview] = useState<ManualTransactionRequest | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const categories = useCategories(true);
  const productQuery = useCallback(() => api.getFinancialInstruments(true), []);
  const products = useQueryResource(productQuery, 'No se pudieron cargar las tarjetas y cuentas.');
  const categoryItems = categories.categories.flatMap(parent => [parent, ...(parent.subcategories ?? []).filter(item => item.active)]);
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => { element?.close(); previousFocus?.focus(); };
  }, [previousFocus]);
  const prepare = (event: React.FormEvent) => {
    event.preventDefault(); setError(null);
    const normalized = amount.trim().replace(',', '.');
    if (!/^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(normalized) || Number(normalized) <= 0) {
      setError('Escribe un importe entre S/ 0.01 y S/ 99,999,999.99, con hasta dos decimales.'); return;
    }
    if (!contact.trim() || contact.trim().length > 150 || !date || Number.isNaN(Date.parse(date))) {
      setError('Completa el comercio o concepto y una fecha válida.'); return;
    }
    setReview({ requestId, amount: normalized, flowType: flow, contactName: contact.trim(),
      transactionDate: date.length === 16 ? `${date}:00` : date, paymentMethod: method,
      categoryId: categoryId ? Number(categoryId) : null, financialInstrumentId: productId ? Number(productId) : null });
  };
  const save = async () => {
    if (!review || saving) return;
    setSaving(true); setError(null);
    try { onCreated(await api.createManualTransaction(review)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'No se pudo guardar. Reintenta este registro con los mismos datos.'); }
    finally { setSaving(false); }
  };
  const input = 'mt-1 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink';
  return <dialog ref={dialog} aria-labelledby="manual-entry-title" onCancel={event => { if (saving) event.preventDefault(); }}
    onClose={() => { if (!saving) onClose(); }}
    className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-0 text-ink shadow-xl backdrop:bg-ink/40">
    <header className="flex items-start justify-between gap-4 border-b border-line p-5 sm:px-6">
      <div><p className="eyebrow">Origen manual</p><h2 id="manual-entry-title" className="font-display mt-1 text-xl font-semibold">{review ? 'Revisar registro manual' : 'Registro manual'}</h2></div>
      <button type="button" disabled={saving} onClick={onClose} aria-label="Cerrar registro manual" className="rounded-lg p-1.5 text-muted hover:bg-canvas disabled:opacity-50"><X className="h-5 w-5" /></button>
    </header>
    <div className="p-5 sm:p-6">
      {!review ? <form onSubmit={prepare} className="space-y-4">
        <p className="text-sm leading-6 text-muted">Completa un movimiento realizado que falta en FinTrack. Los traspasos entre tus cuentas no son gastos ni entradas.</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-medium">Tipo<select className={input} value={flow} onChange={event => setFlow(event.target.value as typeof flow)}><option value="EXPENSE">Gasto</option><option value="INCOME">Entrada</option></select></label>
          <label className="text-sm font-medium">Importe (S/)<input autoFocus className={input} inputMode="decimal" required value={amount} maxLength={11} onChange={event => setAmount(event.target.value)} placeholder="0.00" /></label>
        </div>
        <label className="block text-sm font-medium">Comercio o concepto<input className={input} required maxLength={150} value={contact} onChange={event => setContact(event.target.value)} /></label>
        <label className="block text-sm font-medium">Fecha y hora del movimiento<input className={input} type="datetime-local" required value={date} onChange={event => setDate(event.target.value)} /></label>
        <label className="block text-sm font-medium">Medio de pago<select className={input} value={method} onChange={event => setMethod(event.target.value as typeof method)}>
          <option value="CASH">Efectivo</option><option value="CARD">Tarjeta</option><option value="BANK_TRANSFER">Transferencia</option><option value="WALLET">Billetera</option><option value="OTHER">Otro</option>
        </select></label>
        <div><label className="block text-sm font-medium">Categoría (opcional)<select className={input} value={categoryId} disabled={categories.loading || !!categories.error} onChange={event => setCategoryId(event.target.value)}>
          <option value="">Sin categoría</option>{categoryItems.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select></label>{categories.error && <p className="mt-2 text-sm text-negative">{categories.error} <button type="button" onClick={() => void categories.refresh()} className="underline">Reintentar categorías</button></p>}</div>
        <div><label className="block text-sm font-medium">Tarjeta o cuenta (opcional)<select className={input} value={productId} disabled={products.loading || !!products.error} onChange={event => setProductId(event.target.value)}>
          <option value="">Sin asignar</option>{products.data?.map(item => <option key={item.id} value={item.id}>{item.alias}</option>)}
        </select></label>{products.error && <p className="mt-2 text-sm text-negative">{products.error} <button type="button" onClick={() => void products.refresh()} className="underline">Reintentar productos</button></p>}</div>
        <button type="submit" className="w-full rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white">Revisar movimiento</button>
      </form> : <div className="space-y-4">
        <p className="font-display text-3xl font-semibold">{review.flowType === 'INCOME' ? '+ ' : '− '}{formatCurrency(Number(review.amount))}</p>
        <dl className="divide-y divide-line text-sm">
          {[
            ['Tipo', review.flowType === 'INCOME' ? 'Entrada' : 'Gasto'], ['Comercio o concepto', review.contactName],
            ['Fecha y hora', formatDate(review.transactionDate)], ['Medio', getChannelLabel(review.paymentMethod)],
            ['Categoría', categoryItems.find(item => item.id === review.categoryId)?.name ?? 'Sin categoría'],
            ['Tarjeta o cuenta', products.data?.find(item => item.id === review.financialInstrumentId)?.alias ?? 'Sin asignar'],
          ].map(([label, value]) => <div key={label} className="py-2.5 sm:flex sm:justify-between sm:gap-4"><dt className="text-muted">{label}</dt><dd className="mt-1 break-words font-medium sm:mt-0 sm:text-right">{value}</dd></div>)}
        </dl>
        <p className="text-sm leading-6 text-muted">Este registro sumará a tus movimientos. Si ya existe o después llega la misma operación por correo o teléfono, abre su registro manual y concílialo con la captura para contarlo una sola vez.</p>
        <div className="flex flex-wrap gap-3"><button type="button" disabled={saving} onClick={() => { setReview(null); setError(null); }} className="rounded-xl border border-line px-4 py-2.5 text-sm font-semibold disabled:opacity-50">Volver y corregir</button>
          <button type="button" disabled={saving} onClick={() => void save()} className="rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">{saving ? 'Guardando...' : 'Confirmar registro'}</button></div>
      </div>}
      {error && <p role="alert" className="mt-4 text-sm leading-6 text-negative">{error}</p>}
    </div>
  </dialog>;
}

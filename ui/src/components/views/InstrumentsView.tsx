import { useEffect, useRef, useState } from 'react';
import type { FinancialInstrument, PageResponse, Transaction } from '../../types';
import { api } from '../../services/api';
import { instrumentBank, instrumentLabels, instrumentRequest } from '../../utils/instruments';
import { InstrumentForm } from '../instruments/InstrumentForm';
import { TransactionTable } from '../transactions/TransactionTable';
import { CustomSelect } from '../common';

interface Props {
  year: number;
  month: number;
  revision: number;
  onSelectTransaction: (transaction: Transaction) => void;
  onProductsChanged?: () => void;
}
export function InstrumentsView({ year, month, revision, onSelectTransaction, onProductsChanged }: Props) {
  const [items, setItems] = useState<FinancialInstrument[]>([]);
  const [status, setStatus] = useState('active');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loadedRefresh, setLoadedRefresh] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<{ item: FinancialInstrument | null } | null>(null);
  const [archive, setArchive] = useState<FinancialInstrument | null>(null);
  const [saving, setSaving] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [pagination, setPagination] = useState({ year, month, page: 0 });
  const page = pagination.year === year && pagination.month === month ? pagination.page : 0;
  const setPage = (next: number) => setPagination({ year, month, page: next });
  const movementKey = [selectedId, year, month, page, revision, refresh].join(':');
  const [movementResult, setMovementResult] = useState<{ key: string; data: PageResponse<Transaction> | null; error: string | null } | null>(null);
  const loading = loadedRefresh !== refresh;
  const loadingMovements = movementResult?.key !== movementKey;
  const movements = loadingMovements ? null : movementResult?.data || null;
  const movementError = loadingMovements ? null : movementResult?.error;
  const archiveRef = useRef<HTMLDialogElement>(null);
  const selected = items.find(item => item.id === selectedId);
  const visible = items.filter(item => status === 'all' || item.active === (status === 'active'));

  useEffect(() => {
    let cancelled = false;
    api.getFinancialInstruments().then(result => { if (!cancelled) { setItems(result); setError(null); } })
      .catch(() => { if (!cancelled) setError('No se pudieron cargar tus tarjetas y cuentas.'); })
      .finally(() => { if (!cancelled) setLoadedRefresh(refresh); });
    return () => { cancelled = true; };
  }, [refresh]);

  useEffect(() => {
    if (!archiveRef.current) return;
    if (archive && !archiveRef.current.open) archiveRef.current.showModal();
    if (!archive && archiveRef.current.open) archiveRef.current.close();
  }, [archive]);

  useEffect(() => {
    if (selectedId === null) return;
    let cancelled = false;
    const pad = (n: number) => String(n).padStart(2, '0');
    api.getTransactions({
      financialInstrumentId: selectedId, page, size: 10,
      startDate: `${year}-${pad(month)}-01T00:00:00`,
      endDate: `${year}-${pad(month)}-${pad(new Date(year, month, 0).getDate())}T23:59:59.999999999`,
    }).then(result => { if (!cancelled) setMovementResult({ key: movementKey, data: result, error: null }); })
      .catch(() => { if (!cancelled) setMovementResult({ key: movementKey, data: null, error: 'No se pudieron cargar los movimientos del producto.' }); });
    return () => { cancelled = true; };
  }, [selectedId, year, month, page, revision, refresh, movementKey]);

  const toggleActive = async (item: FinancialInstrument) => {
    setSaving(true); setError(null);
    try {
      await api.saveFinancialInstrument({ ...instrumentRequest(item), active: !item.active }, item.id);
      setArchive(null); setRefresh(value => value + 1);
      onProductsChanged?.();
    } catch { setError('No se pudo actualizar el producto. Si estás reactivando una tarjeta, revisa su cuenta vinculada.'); }
    finally { setSaving(false); }
  };
  const saved = (item: FinancialInstrument) => {
    setForm(null); setSelectedId(item.id); setPage(0); setStatus(item.active ? 'active' : 'archived'); setRefresh(value => value + 1);
    onProductsChanged?.();
  };
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="eyebrow">Tus productos</p><h1 className="font-display mt-2 text-3xl font-semibold">Tarjetas y cuentas</h1>
          <p className="mt-2 text-sm text-muted">Organiza tus productos en soles y consulta sus movimientos registrados.</p></div>
        <button onClick={() => setForm({ item: null })} className="rounded-lg bg-brand px-4 py-2.5 text-sm font-medium text-white">Agregar producto</button>
      </div>
      <div className="flex items-center gap-3">
        <label htmlFor="instruments-status" className="text-sm font-semibold text-muted">
          Mostrar productos
        </label>
        <div className="w-44">
          <CustomSelect
            id="instruments-status"
            value={status}
            onChange={(val) => {
              setStatus(val);
              setSelectedId(null);
              setPage(0);
            }}
            options={[
              { value: 'active', label: 'Activos' },
              { value: 'archived', label: 'Archivados' },
              { value: 'all', label: 'Todos' },
            ]}
          />
        </div>
      </div>
      {error && <div role="alert" className="text-sm text-negative">{error}<button className="ml-3 underline" onClick={() => setRefresh(value => value + 1)}>Reintentar</button></div>}
      {loading ? <p role="status" className="text-sm text-muted">Cargando productos...</p> : !visible.length ? <div className="surface p-8 text-sm text-muted">No tienes productos {status === 'archived' ? 'archivados' : 'registrados en esta vista'}. Agrega una cuenta o tarjeta para comenzar.</div> :
        <div className="grid gap-6 lg:grid-cols-2">
          {(['accounts', 'cards'] as const).map(group => <section key={group} aria-label={group === 'accounts' ? 'Cuentas' : 'Tarjetas'} className="space-y-3">
            <h2 className="font-display text-xl font-semibold">{group === 'accounts' ? 'Cuentas' : 'Tarjetas'}</h2>
            {visible.filter(item => (item.type === 'BANK_ACCOUNT') === (group === 'accounts')).map(item => <article key={item.id} className={`surface p-5 ${selectedId === item.id ? 'ring-2 ring-brand' : ''}`}>
              <h3 className="font-semibold">{item.alias}</h3>
              <p className="mt-1 text-sm text-muted">{instrumentLabels[item.type]} · {instrumentBank(item)}{item.lastFour ? ` ··${item.lastFour}` : ''}</p>
              <p className="mt-2 text-xs text-muted">Soles · {item.active ? 'Activo' : 'Archivado'}{item.linkedAccountId ? ` · Cuenta: ${items.find(account => account.id === item.linkedAccountId)?.alias || 'vinculada'}` : ''}</p>
              <div className="mt-4 flex flex-wrap gap-3 text-sm">
                <button onClick={() => { setSelectedId(item.id); setPage(0); }} className="font-medium text-brand" aria-label={`Ver movimientos de ${item.alias}`}>Ver movimientos</button>
                <button onClick={() => setForm({ item })} className="text-brand" aria-label={`Editar ${item.alias}`}>Editar</button>
                <button disabled={saving} onClick={() => item.active ? setArchive(item) : void toggleActive(item)} className="text-muted" aria-label={`${item.active ? 'Archivar' : 'Reactivar'} ${item.alias}`}>{item.active ? 'Archivar' : 'Reactivar'}</button>
              </div>
            </article>)}
          </section>)}
        </div>}
      {selected && <section className="surface overflow-hidden" aria-label={`Movimientos de ${selected.alias}`}>
        <div className="border-b border-line px-6 py-5"><h2 className="font-display text-xl font-semibold">{selected.alias}</h2>
          <p className="mt-1 text-sm text-muted">{instrumentLabels[selected.type]} · {instrumentBank(selected)} · Soles{selected.lastFour ? ` ··${selected.lastFour}` : ''}{!selected.active ? ' · Archivado' : ''}</p>
          <p className="mt-2 text-xs text-muted">Solo movimientos asignados directamente a este producto. No representa su saldo bancario.</p></div>
        {movementError ? <p role="alert" className="p-6 text-sm text-negative">{movementError}<button className="ml-3 underline" onClick={() => setRefresh(value => value + 1)}>Reintentar</button></p> :
          <TransactionTable pageData={movements} loading={loadingMovements} onPageChange={setPage} onSelectTransaction={onSelectTransaction} periodName={`${month.toString().padStart(2, '0')}/${year}`} />}
      </section>}
      {form && <InstrumentForm item={form.item} instruments={items} onClose={() => setForm(null)} onSaved={saved} />}
      <dialog ref={archiveRef} onClose={() => setArchive(null)} onCancel={event => { if (saving) event.preventDefault(); }} aria-labelledby="archive-title" className="m-auto w-[calc(100%-2rem)] max-w-md rounded-[20px] border border-line bg-white p-6 text-ink backdrop:bg-ink/40">
        <h2 id="archive-title" className="font-display text-xl font-semibold">Archivar producto</h2>
        <p className="mt-3 text-sm text-muted">¿Archivar {archive?.alias}? Sus movimientos y vínculos se conservarán. No recibirá nuevas asignaciones ni sugerencias.</p>
        {error && <p role="alert" className="mt-3 text-sm text-negative">{error}</p>}
        <div className="mt-5 flex justify-end gap-3"><button disabled={saving} onClick={() => setArchive(null)} className="rounded-lg border border-line px-4 py-2">Cancelar</button>
          <button disabled={saving || !archive} onClick={() => { if (archive) void toggleActive(archive); }} className="rounded-lg bg-brand px-4 py-2 text-white">{saving ? 'Archivando...' : 'Confirmar archivo'}</button></div>
      </dialog>
    </div>
  );
}

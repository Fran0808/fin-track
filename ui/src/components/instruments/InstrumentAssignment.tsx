import { useEffect, useState } from 'react';
import type { FinancialInstrument, Transaction } from '../../types';
import { api } from '../../services/api';
import { instrumentBank, instrumentLabels } from '../../utils/instruments';
import { CustomSelect } from '../common';

export function InstrumentAssignment({ transaction, onUpdated }: { transaction: Transaction; onUpdated: (transaction: Transaction) => void }) {
  const [resource, setResource] = useState<{ key: string; items: FinancialInstrument[]; suggestions: FinancialInstrument[]; error: string | null } | null>(null);
  const [saving, setSaving] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const currentId = transaction.financialInstrument?.id || null;
  const [choice, setChoice] = useState({ baseId: currentId, value: currentId?.toString() || '' });
  const value = choice.baseId === currentId ? choice.value : currentId?.toString() || '';
  const key = transaction.id + ':' + retry;
  const loading = resource?.key !== key;
  const items = loading ? [] : resource?.items || [];
  const suggestions = loading ? [] : resource?.suggestions || [];
  const loadError = loading ? null : resource?.error;
  const error = mutationError || loadError;
  useEffect(() => {
    let cancelled = false;
    Promise.all([api.getFinancialInstruments(true), api.getInstrumentSuggestions(transaction.id)])
      .then(([products, proposed]) => { if (!cancelled) setResource({ key, items: products, suggestions: proposed, error: null }); })
      .catch(() => { if (!cancelled) setResource({ key, items: [], suggestions: [], error: 'No se pudieron cargar los productos y las sugerencias.' }); });
    return () => { cancelled = true; };
  }, [transaction.id, retry, key]);
  const assign = async (id: number | null) => {
    setSaving(true); setMutationError(null);
    try {
      const updated = await api.assignFinancialInstrument(transaction.id, id);
      setChoice({ baseId: updated.financialInstrument?.id || null, value: updated.financialInstrument?.id.toString() || '' });
      onUpdated(updated);
    }
    catch { setMutationError('No se pudo guardar la asignación. Comprueba que el producto siga activo e inténtalo de nuevo.'); }
    finally { setSaving(false); }
  };
  const current = transaction.financialInstrument;
  const unchanged = value === (current?.id.toString() || '');
  return (
    <section className="mt-6 border-b border-line pb-5" aria-labelledby="assignment-title">
      <h3 id="assignment-title" className="text-sm font-semibold">Tarjeta o cuenta asignada</h3>
      <p className="mt-2 text-sm text-muted">{current ? `${current.alias}${current.active ? '' : ' (archivado)'}` : 'Sin asignar'}</p>
      {loading ? <p role="status" className="mt-3 text-sm text-muted">Buscando productos y sugerencias...</p> : !loadError && <>
        <div>
          <label htmlFor="assignment-product" className="block text-sm font-medium">Seleccionar producto</label>
          <div className="mt-1">
            <CustomSelect
              id="assignment-product"
              disabled={saving}
              value={value}
              onChange={val => setChoice({ baseId: currentId, value: val })}
              options={[
                { value: '', label: 'Sin asignar' },
                ...(current && !items.some(item => item.id === current.id)
                  ? [{ value: current.id.toString(), label: `${current.alias} (archivado)` }]
                  : []),
                ...items.map(item => ({
                  value: item.id.toString(),
                  label: `${item.alias} · ${instrumentLabels[item.type]} · ${instrumentBank(item)}${item.lastFour ? ` ··${item.lastFour}` : ''}`,
                })),
              ]}
            />
          </div>
        </div>
        <button disabled={saving || unchanged} onClick={() => { void assign(value ? Number(value) : null); }} className="mt-3 rounded-lg bg-brand px-3 py-2 text-sm text-white disabled:opacity-50">{saving ? 'Guardando...' : 'Guardar asignación'}</button>
        <h4 className="mt-4 text-sm font-medium">Sugerencias</h4>
        {!suggestions.length ? <p className="mt-1 text-xs text-muted">Sin sugerencias</p> : <div className="mt-2 space-y-2">
          <p className="text-xs text-muted">Coinciden banco, tipo y últimos cuatro dígitos. Confirma el producto que corresponde.</p>
          {suggestions.map(item => <button key={item.id} disabled={saving || current?.id === item.id} onClick={() => { void assign(item.id); }} className="block rounded-lg border border-line px-3 py-2 text-left text-sm text-brand disabled:opacity-50">Confirmar {item.alias} · {instrumentBank(item)} ··{item.lastFour}</button>)}
        </div>}
      </>}
      {error && <p role="alert" className="mt-3 text-sm text-negative">{error}<button onClick={() => { setMutationError(null); setRetry(n => n + 1); }} className="ml-2 underline" disabled={saving}>Reintentar</button></p>}
    </section>
  );
}

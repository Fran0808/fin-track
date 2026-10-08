import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { Bank, FinancialInstrument, FinancialInstrumentRequest, InstrumentType } from '../../types';
import { api } from '../../services/api';
import { instrumentLabels, instrumentRequest } from '../../utils/instruments';
import { CustomSelect } from '../common';

interface Props {
  item: FinancialInstrument | null;
  instruments: FinancialInstrument[];
  onClose: () => void;
  onSaved: (item: FinancialInstrument) => void;
}

const initial: FinancialInstrumentRequest = {
  alias: '', type: 'BANK_ACCOUNT', bank: 'BCP', institutionName: null,
  lastFour: null, active: true, linkedAccountId: null,
};

export function InstrumentForm({ item, instruments, onClose, onSaved }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [data, setData] = useState(() => item ? instrumentRequest(item) : initial);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  const accounts = instruments.filter(account => account.type === 'BANK_ACCOUNT' && account.active
    && account.bank === data.bank && (data.bank !== 'OTHER' || account.institutionName?.trim() === data.institutionName?.trim()));
  const existingAccount = instruments.find(account => account.id === data.linkedAccountId);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true); setError(null);
    try {
      const saved = await api.saveFinancialInstrument({
        ...data, alias: data.alias.trim(), institutionName: data.bank === 'OTHER' ? data.institutionName?.trim() || null : null,
        lastFour: data.lastFour || null, linkedAccountId: data.type === 'DEBIT_CARD' ? data.linkedAccountId : null,
      }, item?.id);
      onSaved(saved);
    } catch { setError('No se pudo guardar. Revisa los datos y los vínculos con otras tarjetas o cuentas.'); }
    finally { setSaving(false); }
  };
  const fieldClass = 'mt-1 block w-full rounded-lg border border-line bg-white px-3 py-2 text-ink focus-visible:outline-brand';
  return (
    <dialog ref={ref} onClose={onClose} onCancel={event => { if (saving) event.preventDefault(); }}
      aria-labelledby="instrument-form-title" className="m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-6 text-ink backdrop:bg-ink/40">
      <h2 id="instrument-form-title" className="font-display text-xl font-semibold">{item ? 'Editar producto' : 'Agregar tarjeta o cuenta'}</h2>
      <p className="mt-2 text-sm text-muted">Solo soles. Guarda un alias y, si lo necesitas, los últimos cuatro dígitos.</p>
      <form onSubmit={event => { void submit(event); }} className="mt-5 space-y-4">
        <fieldset disabled={saving} className="space-y-4">
          <div>
            <label htmlFor="instrument-type" className="block text-sm font-medium">Tipo de producto</label>
            <div className="mt-1">
              <CustomSelect
                id="instrument-type"
                value={data.type}
                onChange={val => setData({ ...data, type: val as InstrumentType, linkedAccountId: null })}
                options={Object.entries(instrumentLabels).map(([value, label]) => ({ value: value as InstrumentType, label }))}
              />
            </div>
          </div>
          <label className="block text-sm font-medium">Alias
            <input required maxLength={100} className={fieldClass} value={data.alias} onChange={e => setData({ ...data, alias: e.target.value })} placeholder="Ejemplo: Cuenta principal" />
          </label>
          <div>
            <label htmlFor="instrument-bank" className="block text-sm font-medium">Banco</label>
            <div className="mt-1">
              <CustomSelect
                id="instrument-bank"
                value={data.bank}
                onChange={val => setData({ ...data, bank: val as Bank, linkedAccountId: null })}
                options={[
                  { value: 'BCP' as Bank, label: 'BCP' },
                  { value: 'INTERBANK' as Bank, label: 'Interbank' },
                  { value: 'BBVA' as Bank, label: 'BBVA' },
                  { value: 'OTHER' as Bank, label: 'Otro' },
                ]}
              />
            </div>
          </div>
          {data.bank === 'OTHER' && <label className="block text-sm font-medium">Nombre de la institución
            <input required maxLength={100} className={fieldClass} value={data.institutionName || ''} onChange={e => setData({ ...data, institutionName: e.target.value, linkedAccountId: null })} />
          </label>}
          <label className="block text-sm font-medium">Últimos cuatro dígitos (opcional)
            <input inputMode="numeric" pattern="[0-9]{4}" maxLength={4} className={fieldClass} value={data.lastFour || ''} onChange={e => setData({ ...data, lastFour: e.target.value || null })} />
          </label>
          {data.type === 'DEBIT_CARD' && (
            <div>
              <label htmlFor="instrument-linked" className="block text-sm font-medium">Cuenta vinculada (opcional)</label>
              <div className="mt-1">
                <CustomSelect<string | number>
                  id="instrument-linked"
                  value={data.linkedAccountId ?? ''}
                  onChange={val => setData({ ...data, linkedAccountId: val === '' ? null : Number(val) })}
                  options={[
                    { value: '', label: 'Sin cuenta vinculada' },
                    ...(existingAccount && !accounts.some(account => account.id === existingAccount.id)
                      ? [{ value: existingAccount.id, label: `${existingAccount.alias} (archivada)` }]
                      : []),
                    ...accounts.map(account => ({ value: account.id, label: account.alias })),
                  ]}
                />
              </div>
            </div>
          )}
        </fieldset>
        {error && <p role="alert" className="text-sm text-negative">{error}</p>}
        <div className="flex justify-end gap-3">
          <button type="button" disabled={saving} onClick={onClose} className="rounded-lg border border-line px-4 py-2 text-sm">Cancelar</button>
          <button disabled={saving} className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white">{saving ? 'Guardando...' : 'Guardar producto'}</button>
        </div>
      </form>
    </dialog>
  );
}

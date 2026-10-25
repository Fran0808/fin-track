import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import type { ClassificationRuleDraft } from '../../types';
import { useCategories } from '../../hooks';
import { api } from '../../services/api';

const fieldClass = 'mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink focus:border-brand focus:outline-none';

export function CreateClassificationRuleModal({ draft, onClose }: { draft: ClassificationRuleDraft; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { categories, loading, error, refresh } = useCategories(true);
  const activeCategories = categories.flatMap(category => [category, ...(category.subcategories || [])]).filter(category => category.active);
  const suggestedId = activeCategories.find(category => category.name.toLocaleLowerCase() === draft.categoryName.toLocaleLowerCase())?.id;
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const selectedId = categoryId ?? (suggestedId === undefined ? '' : String(suggestedId));
  const [matchText, setMatchText] = useState(draft.matchText);
  const [priority, setPriority] = useState('100');
  const [enabled, setEnabled] = useState(true);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  return <dialog ref={dialogRef} aria-labelledby="create-rule-title" onClose={onClose}
    onCancel={event => { if (busy) event.preventDefault(); }}
    className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40">
    <div className="flex items-start justify-between gap-3">
      <h2 id="create-rule-title" className="font-display text-xl font-semibold">Crear regla para este comercio</h2>
      <button type="button" disabled={busy} aria-label="Cerrar nueva regla" onClick={onClose} className="rounded-lg p-1.5 text-muted hover:bg-canvas disabled:opacity-50"><X className="h-5 w-5" /></button>
    </div>
    {saved ? <div className="mt-5">
      <p role="status" className="text-sm text-positive">Regla creada. Se aplicará a los nuevos movimientos.</p>
      <button type="button" onClick={onClose} className="mt-5 rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white">Listo</button>
    </div> : <>
      <p className="mt-3 text-sm text-muted">Revisa el texto y la categoría. La regla se aplicará a nuevos movimientos sin categoría; este movimiento conserva su clasificación.</p>
      {loading && <p role="status" className="mt-4 text-sm text-muted">Cargando categorías...</p>}
      {error && <p role="alert" className="mt-4 text-sm text-negative">{error} <button type="button" onClick={() => void refresh()} className="underline">Reintentar categorías</button></p>}
      <form className="mt-5" onSubmit={async event => {
        event.preventDefault();
        if (busy || loading || error || !matchText.trim() || !activeCategories.some(category => String(category.id) === selectedId)) return;
        setBusy(true); setSaveError(null);
        try {
          await api.saveClassificationRule({ matchText: matchText.trim(), categoryId: Number(selectedId), priority: Number(priority), enabled });
          setSaved(true);
        } catch (failure) { setSaveError(failure instanceof Error ? failure.message : 'No se pudo crear la regla.'); }
        finally { setBusy(false); }
      }}>
        <fieldset disabled={busy || loading || !!error} className="space-y-4 disabled:opacity-50">
          <div><label htmlFor="quick-rule-text" className="text-sm font-medium">El comercio o contacto contiene</label>
            <input id="quick-rule-text" required maxLength={150} value={matchText} onChange={event => setMatchText(event.target.value)} className={fieldClass} />
            <p className="mt-1.5 text-xs text-muted">Coincidencia literal, sin distinguir mayúsculas.</p>
          </div>
          <div><label htmlFor="quick-rule-category" className="text-sm font-medium">Asignar categoría</label>
            <select id="quick-rule-category" required value={selectedId} onChange={event => setCategoryId(event.target.value)} className={`fintrack-select ${fieldClass}`}>
              <option value="">Selecciona una categoría</option>
              {activeCategories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </div>
          <div><label htmlFor="quick-rule-priority" className="text-sm font-medium">Prioridad</label>
            <input id="quick-rule-priority" type="number" required min={0} max={9999} step={1} value={priority} onChange={event => setPriority(event.target.value)} className={fieldClass} />
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} className="accent-brand" />Regla activa</label>
          <div className="flex justify-end gap-4">
            <button type="button" onClick={onClose} className="text-sm text-muted">Cancelar</button>
            <button type="submit" disabled={!selectedId || !activeCategories.length} className="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{busy ? 'Guardando...' : 'Crear regla'}</button>
          </div>
        </fieldset>
        {saveError && <p role="alert" className="mt-3 text-sm text-negative">{saveError}</p>}
      </form>
    </>}
  </dialog>;
}

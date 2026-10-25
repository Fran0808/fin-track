import { useEffect, useState } from 'react';
import { ArrowRight, Pencil, Plus, Trash2 } from 'lucide-react';
import type { Category, ClassificationRule } from '../../types';
import { api } from '../../services/api';
import { RuleHistoryModal } from '../transactions/RuleHistoryModal';

const fieldClass = 'mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink focus:border-brand focus:outline-none';

export function ClassificationRulesView({ onHistoryApplied }: { onHistoryApplied?: () => void }) {
  const [revision, setRevision] = useState(0);
  const [resource, setResource] = useState<{ revision: number; rules: ClassificationRule[]; categories: Category[]; error: string | null } | null>(null);
  const [editing, setEditing] = useState<ClassificationRule | null>(null);
  const [matchText, setMatchText] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [priority, setPriority] = useState('100');
  const [enabled, setEnabled] = useState(true);
  const [busy, setBusy] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [historyRule, setHistoryRule] = useState<ClassificationRule | null>(null);
  const loading = resource?.revision !== revision;
  const rules = loading ? [] : resource?.rules || [];
  const categories = loading ? [] : resource?.categories || [];
  const activeCategories = categories.flatMap(category => [category, ...(category.subcategories || [])]).filter(category => category.active);
  const loadError = loading ? null : resource?.error;

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.getClassificationRules(), api.getCategories()]).then(([loadedRules, loadedCategories]) => {
      if (!cancelled) setResource({ revision, rules: loadedRules, categories: loadedCategories, error: null });
    }).catch(() => {
      if (!cancelled) setResource({ revision, rules: [], categories: [], error: 'No se pudieron cargar las reglas y las categorías. Inténtalo de nuevo.' });
    });
    return () => { cancelled = true; };
  }, [revision]);

  const resetForm = () => {
    setEditing(null); setMatchText(''); setCategoryId(''); setPriority('100'); setEnabled(true);
  };
  const edit = (rule: ClassificationRule) => {
    setEditing(rule); setMatchText(rule.matchText); setCategoryId(String(rule.categoryId));
    setPriority(String(rule.priority)); setEnabled(rule.enabled); setMutationError(null); setMessage(null);
  };
  const mutate = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true); setMutationError(null); setMessage(null);
    try {
      await action();
      resetForm(); setDeleteId(null); setMessage(success); setRevision(value => value + 1);
    } catch (error) {
      setMutationError(error instanceof Error ? error.message : 'No se pudo guardar el cambio. Inténtalo de nuevo.');
    } finally { setBusy(false); }
  };

  return (
    <div className="space-y-6">
      <div>
        <p className="eyebrow">Clasificación automática</p>
        <h1 className="font-display mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Reglas</h1>
        <p className="mt-2 max-w-3xl text-sm text-muted">Asigna una categoría cuando el comercio o contacto contenga el texto que elijas. Se aplican a nuevas entradas y gastos. Para los anteriores sin categoría, revisa y confirma una aplicación al historial.</p>
      </div>
      {message && <p role="status" className="text-sm font-medium text-positive">{message}</p>}
      {mutationError && <p role="alert" className="text-sm text-negative">{mutationError}</p>}
      {loadError && <div className="surface p-5"><p role="alert" className="text-sm text-negative">{loadError}</p><button type="button" onClick={() => setRevision(value => value + 1)} className="mt-3 text-sm font-semibold text-brand">Reintentar</button></div>}
      {loading && <p role="status" className="text-sm text-muted">Cargando reglas...</p>}

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="surface overflow-hidden" aria-labelledby="rules-heading">
          <div className="border-b border-line p-5 sm:px-6">
            <h2 id="rules-heading" className="font-display text-lg font-semibold">Orden de aplicación</h2>
            <p className="mt-1 text-sm text-muted">La prioridad menor va primero. Si empatan, se usa la más antigua. Solo se aplica la primera coincidencia activa.</p>
          </div>
          {!loading && !loadError && rules.length === 0 && <div className="p-6 text-sm text-muted">Aún no tienes reglas. Crea una con un comercio que se repita, por ejemplo CINEMARK.</div>}
          <ol className="divide-y divide-line">
            {rules.map(rule => <li key={rule.id} className="px-5 py-5 sm:px-6">
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted">
                <span>Prioridad {rule.priority}</span>
                <span>{!rule.enabled ? 'Pausada' : rule.categoryActive ? 'Activa' : 'Sin aplicar: categoría inactiva'}</span>
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
                <span>Contiene <strong className="break-all">«{rule.matchText}»</strong></span>
                <ArrowRight className="h-4 w-4 text-brand" aria-hidden="true" />
                <span className="break-words font-semibold text-brand">{rule.categoryName}</span>
              </div>
              <div className="mt-4 flex flex-wrap gap-3 text-sm font-semibold text-brand">
                <button type="button" disabled={busy || loading || !rule.enabled || !rule.categoryActive}
                  onClick={() => { setMessage(null); setHistoryRule(rule); }} aria-label={`Aplicar regla ${rule.matchText} al historial`}
                  className="disabled:opacity-40">Aplicar al historial</button>
                <button type="button" disabled={busy || loading} onClick={() => edit(rule)} aria-label={`Editar regla ${rule.matchText}`} className="inline-flex items-center gap-1.5 disabled:opacity-40"><Pencil className="h-3.5 w-3.5" />Editar</button>
                <button type="button" disabled={busy || loading || (!rule.enabled && !rule.categoryActive)}
                  onClick={() => { void mutate(() => api.saveClassificationRule({ matchText: rule.matchText, categoryId: rule.categoryId, priority: rule.priority, enabled: !rule.enabled }, rule.id), rule.enabled ? 'Regla pausada.' : 'Regla activada.'); }}
                  aria-label={`${rule.enabled ? 'Pausar' : 'Activar'} regla ${rule.matchText}`} className="disabled:opacity-40">{rule.enabled ? 'Pausar' : 'Activar'}</button>
                <button type="button" disabled={busy || loading} onClick={() => setDeleteId(rule.id)} aria-label={`Eliminar regla ${rule.matchText}`} className="inline-flex items-center gap-1.5 text-negative disabled:opacity-40"><Trash2 className="h-3.5 w-3.5" />Eliminar</button>
              </div>
              {deleteId === rule.id && <div className="mt-3 rounded-xl bg-canvas p-3 text-sm">
                <p>¿Eliminar esta regla? Los movimientos ya clasificados conservarán su categoría.</p>
                <div className="mt-2 flex gap-4">
                  <button type="button" disabled={busy} onClick={() => { void mutate(() => api.deleteClassificationRule(rule.id), 'Regla eliminada.'); }} className="font-semibold text-negative">Confirmar eliminación</button>
                  <button type="button" disabled={busy} onClick={() => setDeleteId(null)} className="text-muted">Cancelar</button>
                </div>
              </div>}
            </li>)}
          </ol>
        </section>

        <section className="surface p-5 sm:p-6" aria-labelledby="rule-form-heading">
          <div className="flex items-center justify-between gap-3">
            <h2 id="rule-form-heading" className="font-display text-lg font-semibold">{editing ? 'Editar regla' : 'Nueva regla'}</h2>
            {editing && <button type="button" disabled={busy} onClick={resetForm} className="text-sm font-semibold text-brand">Cancelar</button>}
          </div>
          <form className="mt-5 space-y-4" onSubmit={event => {
            event.preventDefault();
            if (!matchText.trim() || !categoryId || busy || loading || loadError) return;
            void mutate(() => api.saveClassificationRule({ matchText: matchText.trim(), categoryId: Number(categoryId), priority: Number(priority), enabled }, editing?.id), editing ? 'Regla actualizada.' : 'Regla creada. Se aplicará a los nuevos movimientos.');
          }}>
            <fieldset disabled={busy || loading || !!loadError} className="space-y-4 disabled:opacity-50">
              <div><label htmlFor="rule-text" className="text-sm font-medium">El comercio o contacto contiene</label>
                <input id="rule-text" required maxLength={150} value={matchText} onChange={event => setMatchText(event.target.value)} placeholder="Ej. CINEMARK" className={fieldClass} />
                <p className="mt-1.5 text-xs text-muted">No distingue mayúsculas. El texto se busca literalmente, sin comodines.</p>
              </div>
              <div><label htmlFor="rule-category" className="text-sm font-medium">Asignar categoría</label>
                <select id="rule-category" required value={categoryId} onChange={event => setCategoryId(event.target.value)} className={`fintrack-select ${fieldClass}`}>
                  <option value="">Selecciona una categoría</option>
                  {editing && !activeCategories.some(category => category.id === editing.categoryId) && <option value={editing.categoryId}>{editing.categoryName} (inactiva)</option>}
                  {activeCategories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
                </select>
              </div>
              <div><label htmlFor="rule-priority" className="text-sm font-medium">Prioridad</label>
                <input id="rule-priority" type="number" required min={0} max={9999} step={1} value={priority} onChange={event => setPriority(event.target.value)} className={fieldClass} />
              </div>
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={enabled} onChange={event => setEnabled(event.target.checked)} className="accent-brand" />Regla activa</label>
              <button type="submit" disabled={!activeCategories.length && !editing} className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-40"><Plus className="h-4 w-4" />{busy ? 'Guardando...' : editing ? 'Guardar cambios' : 'Crear regla'}</button>
            </fieldset>
          </form>
          <p className="mt-5 border-t border-line pt-4 text-xs leading-relaxed text-muted">Las transferencias propias se mantienen fuera de estas reglas. Puedes corregir cualquier categoría desde el detalle del movimiento.</p>
        </section>
      </div>
      {historyRule && <RuleHistoryModal key={historyRule.id} rule={historyRule} onClose={() => setHistoryRule(null)} onApplied={result => {
        setHistoryRule(null);
        setMessage(`Se clasificaron ${result.appliedCount} movimientos. Se conservaron ${result.skippedCount} que ya tenían categoría.`);
        onHistoryApplied?.();
      }} />}
    </div>
  );
}

import { useEffect, useState } from 'react';
import { AlertCircle, ArrowRight, CheckCircle2, History, Pause, Pencil, Play, Plus, Trash2 } from 'lucide-react';
import type { Category, ClassificationRule } from '../../types';
import { api } from '../../services/api';
import { getCategoryColor } from '../../utils/categoryColors';
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
  const activeCategories = categories.filter(category => category.active);
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
        <h1 className="font-display mt-1.5 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">Reglas</h1>
        <p className="mt-1.5 max-w-3xl text-sm leading-6 text-muted">Asigna una categoría cuando el comercio o contacto contenga el texto que elijas. Se aplican a nuevas entradas y gastos. Para los anteriores sin categoría, revisa y confirma una aplicación al historial.</p>
      </div>
      {message && (
        <div role="status" className="flex items-start gap-2.5 rounded-xl border border-positive/20 bg-positive/5 px-4 py-3 text-sm font-medium text-positive">
          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{message}</span>
        </div>
      )}
      {mutationError && (
        <div role="alert" className="flex items-start gap-2.5 rounded-xl border border-negative/20 bg-negative/5 px-4 py-3 text-sm text-negative">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{mutationError}</span>
        </div>
      )}
      {loadError && (
        <div className="surface p-5">
          <p role="alert" className="text-sm text-negative">{loadError}</p>
          <button type="button" onClick={() => setRevision(value => value + 1)} className="mt-3 rounded-lg border border-line bg-white px-3 py-1.5 text-sm font-semibold text-brand hover:bg-canvas">Reintentar</button>
        </div>
      )}
      {loading && <p role="status" className="text-sm text-muted">Cargando reglas...</p>}

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="surface overflow-hidden" aria-labelledby="rules-heading">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5 sm:px-6">
            <div>
              <h2 id="rules-heading" className="font-display text-lg font-semibold text-ink">Orden de aplicación</h2>
              <p className="mt-1 text-sm text-muted">La prioridad menor va primero. Si empatan, se usa la más antigua. Solo se aplica la primera coincidencia activa.</p>
            </div>
            {!loading && !loadError && (
              <span className="font-num rounded-full bg-brand/8 px-2.5 py-1 text-xs font-semibold text-brand">
                {rules.length} {rules.length === 1 ? 'regla' : 'reglas'}
              </span>
            )}
          </div>
          {!loading && !loadError && rules.length === 0 && (
            <div className="p-6 text-sm text-muted">Aún no tienes reglas. Crea una con un comercio que se repita, por ejemplo CINEMARK.</div>
          )}
          <ol className="divide-y divide-line">
            {rules.map(rule => {
              const catColor = getCategoryColor(rule.categoryName);
              const statusTone = !rule.enabled
                ? 'bg-canvas text-muted'
                : rule.categoryActive
                  ? 'bg-positive/10 text-positive'
                  : 'bg-negative/8 text-negative';
              const statusLabel = !rule.enabled
                ? 'Pausada'
                : rule.categoryActive
                  ? 'Activa'
                  : 'Sin aplicar: categoría inactiva';

              return (
                <li key={rule.id} className="px-5 py-5 transition-colors hover:bg-canvas/40 sm:px-6">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-num rounded-md border border-line bg-canvas/70 px-2 py-0.5 text-xs font-medium text-muted">
                      Prioridad {rule.priority}
                    </span>
                    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusTone}`}>
                      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
                      {statusLabel}
                    </span>
                  </div>

                  <div className="mt-3.5 flex flex-wrap items-center gap-2.5 text-sm">
                    <span className="text-muted">Contiene</span>
                    <strong className="break-all rounded-lg border border-line bg-white px-2.5 py-1 font-semibold text-ink">
                      «{rule.matchText}»
                    </strong>
                    <ArrowRight className="h-4 w-4 shrink-0 text-muted" aria-hidden="true" />
                    <span
                      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
                      style={{
                        backgroundColor: `${catColor}18`,
                        color: catColor,
                      }}
                    >
                      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: catColor }} aria-hidden="true" />
                      <span className="break-words">{rule.categoryName}</span>
                    </span>
                  </div>

                  <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-line/60 pt-3.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        disabled={busy || loading || !rule.enabled || !rule.categoryActive}
                        onClick={() => { setMessage(null); setHistoryRule(rule); }}
                        aria-label={`Aplicar regla ${rule.matchText} al historial`}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-brand/8 px-3 py-1.5 text-xs font-semibold text-brand transition-colors hover:bg-brand/15 disabled:opacity-40"
                      >
                        <History className="h-3.5 w-3.5" aria-hidden="true" />
                        Aplicar al historial
                      </button>
                      <button
                        type="button"
                        disabled={busy || loading}
                        onClick={() => edit(rule)}
                        aria-label={`Editar regla ${rule.matchText}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-1.5 text-xs font-semibold text-ink transition-colors hover:bg-canvas disabled:opacity-40"
                      >
                        <Pencil className="h-3.5 w-3.5 text-muted" aria-hidden="true" />
                        Editar
                      </button>
                      <button
                        type="button"
                        disabled={busy || loading || (!rule.enabled && !rule.categoryActive)}
                        onClick={() => { void mutate(() => api.saveClassificationRule({ matchText: rule.matchText, categoryId: rule.categoryId, priority: rule.priority, enabled: !rule.enabled }, rule.id), rule.enabled ? 'Regla pausada.' : 'Regla activada.'); }}
                        aria-label={`${rule.enabled ? 'Pausar' : 'Activar'} regla ${rule.matchText}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-1.5 text-xs font-semibold text-muted transition-colors hover:bg-canvas hover:text-ink disabled:opacity-40"
                      >
                        {rule.enabled ? <Pause className="h-3.5 w-3.5" aria-hidden="true" /> : <Play className="h-3.5 w-3.5" aria-hidden="true" />}
                        {rule.enabled ? 'Pausar' : 'Activar'}
                      </button>
                    </div>
                    <button
                      type="button"
                      disabled={busy || loading}
                      onClick={() => setDeleteId(rule.id)}
                      aria-label={`Eliminar regla ${rule.matchText}`}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-white px-3 py-1.5 text-xs font-semibold text-negative transition-colors hover:border-negative/30 hover:bg-negative/5 disabled:opacity-40"
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                      Eliminar
                    </button>
                  </div>

                  {deleteId === rule.id && (
                    <div className="mt-3 rounded-xl border border-negative/20 bg-negative/5 p-3.5 text-sm">
                      <p className="text-ink">¿Eliminar esta regla? Los movimientos ya clasificados conservarán su categoría.</p>
                      <div className="mt-2.5 flex flex-wrap items-center gap-3">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => { void mutate(() => api.deleteClassificationRule(rule.id), 'Regla eliminada.'); }}
                          className="rounded-lg bg-negative px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-negative/90 disabled:opacity-50"
                        >
                          Confirmar eliminación
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setDeleteId(null)}
                          className="rounded-lg border border-line bg-white px-3 py-1.5 text-xs font-semibold text-muted hover:text-ink"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
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
              <details className="group rounded-xl border border-line/60 bg-canvas/40 p-3">
                <summary className="flex cursor-pointer list-none items-center justify-between text-xs font-medium text-muted hover:text-ink">
                  <span>Opciones avanzadas</span>
                  <span className="text-[10px] text-muted transition-transform group-open:rotate-180">▼</span>
                </summary>
                <div className="mt-3 border-t border-line/40 pt-3">
                  <label htmlFor="rule-priority" className="text-sm font-medium">Prioridad</label>
                  <input id="rule-priority" type="number" required min={0} max={9999} step={1} value={priority} onChange={event => setPriority(event.target.value)} className={fieldClass} />
                  <p className="mt-1.5 text-xs text-muted">Menor número se evalúa primero cuando coinciden varias reglas.</p>
                </div>
              </details>
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

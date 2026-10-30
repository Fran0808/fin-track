import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import {
  Tags,
  Search,
  Plus,
  Loader2,
  Trash2,
  Edit2,
  AlertCircle,
  CheckCircle2,
  X,
} from 'lucide-react';
import type { Category, CategoryRequest } from '../../types';
import { api } from '../../services/api';
import { CategoryIcon } from '../categories/CategoryIcon';
import { CategoryModal } from '../categories/CategoryModal';
import { getCategoryColor } from '../../utils/categoryColors';

export function CategoriesView() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notification, setNotification] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Search & Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [systemFilter, setSystemFilter] = useState<'all' | 'active' | 'inactive'>('all');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);

  // Deletion confirm modal
  const [categoryToDelete, setCategoryToDelete] = useState<Category | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const deleteDialogRef = useRef<HTMLDialogElement>(null);

  const showNotification = (message: string, type: 'success' | 'error' = 'success') => {
    setNotification({ message, type });
    setTimeout(() => setNotification(null), 4000);
  };

  const loadCategories = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await api.getCategories();
      setCategories(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error al cargar las categorías');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  // Separate user and system categories
  const { userCategories, systemCategories } = useMemo(() => {
    const user: Category[] = [];
    const system: Category[] = [];

    for (const cat of categories) {
      if (cat.system) {
        system.push(cat);
      } else {
        user.push(cat);
      }
    }

    return { userCategories: user, systemCategories: system };
  }, [categories]);

  // Filtered system categories
  const filteredSystemCategories = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    return systemCategories.filter((cat) => {
      const matchesSearch = !q || cat.name.toLowerCase().includes(q);
      const matchesFilter =
        systemFilter === 'all'
          ? true
          : systemFilter === 'active'
          ? cat.active
          : !cat.active;
      return matchesSearch && matchesFilter;
    });
  }, [systemCategories, searchQuery, systemFilter]);

  // Filtered user categories
  const filteredUserCategories = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    if (!q) return userCategories;

    return userCategories.filter((cat) => cat.name.toLowerCase().includes(q));
  }, [userCategories, searchQuery]);

  const activeSystemCount = useMemo(() => {
    return systemCategories.filter((c) => c.active).length;
  }, [systemCategories]);

  // Toggle active state
  const handleToggle = async (cat: Category) => {
    const previousState = cat.active;
    // Optimistic update
    setCategories((prev) =>
      prev.map((item) => (item.id === cat.id ? { ...item, active: !previousState } : item))
    );

    try {
      await api.toggleCategory(cat.id);
      showNotification(
        `Categoría "${cat.name}" ${!previousState ? 'activada' : 'desactivada'} correctamente.`
      );
    } catch (err: unknown) {
      // Rollback
      setCategories((prev) =>
        prev.map((item) => (item.id === cat.id ? { ...item, active: previousState } : item))
      );
      showNotification(
        err instanceof Error ? err.message : 'Error al cambiar estado de la categoría',
        'error'
      );
    }
  };

  // Save new or edited category
  const handleSaveCategory = async (data: CategoryRequest) => {
    if (editingCategory) {
      await api.updateCategory(editingCategory.id, data);
      showNotification('Categoría actualizada con éxito.');
    } else {
      await api.createCategory(data);
      showNotification('Categoría creada con éxito.');
    }
    await loadCategories();
  };

  useEffect(() => {
    const dialog = deleteDialogRef.current;
    if (!dialog) return;
    if (categoryToDelete && !dialog.open) dialog.showModal();
    if (!categoryToDelete && dialog.open) dialog.close();
  }, [categoryToDelete]);

  // Delete category
  const handleDeleteCategory = async () => {
    if (!categoryToDelete) return;
    try {
      setIsDeleting(true);
      setDeleteError(null);
      await api.deleteCategory(categoryToDelete.id);
      showNotification(`Categoría "${categoryToDelete.name}" eliminada.`);
      setCategoryToDelete(null);
      await loadCategories();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al eliminar la categoría';
      setDeleteError(msg);
      showNotification(msg, 'error');
    } finally {
      setIsDeleting(false);
    }
  };

  const openCreateModal = () => {
    setEditingCategory(null);
    setModalOpen(true);
  };

  const openEditModal = (cat: Category) => {
    setEditingCategory(cat);
    setModalOpen(true);
  };

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {notification && (
        <div
          role="status"
          className={`flex items-center gap-2.5 rounded-xl border px-4 py-3 text-sm shadow-md transition-all ${
            notification.type === 'success'
              ? 'border-positive/20 bg-positive/10 text-positive'
              : 'border-negative/20 bg-negative/10 text-negative'
          }`}
        >
          {notification.type === 'success' ? (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          ) : (
            <AlertCircle className="h-4 w-4 shrink-0" />
          )}
          <span>{notification.message}</span>
        </div>
      )}

      {/* Header bar with FinTrack Identity */}
      <section className="surface p-6 sm:p-7 shadow-xs">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand/10 text-brand shrink-0 shadow-2xs">
              <Tags className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="font-display text-2xl font-bold tracking-tight text-ink sm:text-3xl">Categorías</h1>
                <span className="rounded-full bg-brand/10 px-2.5 py-0.5 text-xs font-semibold text-brand">
                  {categories.length} en total
                </span>
              </div>
              <p className="mt-1 text-sm text-muted">
                Personaliza tus categorías y decide cuáles del sistema deseas tener activas para clasificar tus movimientos.
              </p>
            </div>
          </div>

          {/* Controls: Search and Actions */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative min-w-[240px] flex-1 sm:w-64">
              <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Buscar categoría..."
                aria-label="Buscar categorías"
                className="w-full rounded-xl border border-line bg-canvas/40 py-2.5 pl-9 pr-8 text-sm text-ink outline-none transition focus:border-brand focus:bg-white focus:ring-2 focus:ring-brand/20"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  aria-label="Limpiar búsqueda"
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-1 text-muted hover:text-ink"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>

            <button
              type="button"
              onClick={openCreateModal}
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition hover:bg-brand/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            >
              <Plus className="h-4 w-4" />
              <span>Nueva categoría</span>
            </button>
          </div>
        </div>
      </section>

      {error && (
        <div className="rounded-xl border border-negative/20 bg-negative/5 px-4 py-3 text-sm text-negative">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex h-64 items-center justify-center gap-3 text-muted">
          <Loader2 className="h-6 w-6 animate-spin text-brand" />
          <p className="text-sm">Cargando catálogo de categorías...</p>
        </div>
      ) : (
        /* Two Column Layout */
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          {/* Column 1: Mis Categorías (Personalizadas) */}
          <div className="lg:col-span-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="font-display text-base font-bold text-ink">Mis Categorías</h2>
                  <span className="rounded-full bg-brand/10 px-2 py-0.5 text-xs font-bold text-brand">
                    {userCategories.length}
                  </span>
                </div>
                <p className="text-xs text-muted">Categorías propias para personalizar tus finanzas.</p>
              </div>
            </div>

            {filteredUserCategories.length === 0 ? (
              <div className="surface border-2 border-dashed border-line bg-canvas/40 p-8 text-center">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-muted shadow-xs">
                  <Tags className="h-6 w-6 text-brand" />
                </div>
                <h3 className="mt-3.5 text-sm font-bold text-ink">
                  {searchQuery ? 'Sin resultados para la búsqueda' : 'Aún no tienes categorías propias'}
                </h3>
                <p className="mt-1 text-xs text-muted leading-relaxed">
                  {searchQuery
                    ? 'Prueba con otro término de búsqueda o limpia el filtro.'
                    : 'Crea categorías personalizadas para adaptar FinTrack al detalle de tus gastos.'}
                </p>
                {!searchQuery && (
                  <button
                    type="button"
                    onClick={openCreateModal}
                    className="mt-4 inline-flex items-center gap-2 rounded-xl bg-white border border-line px-3.5 py-2 text-xs font-semibold text-ink shadow-xs hover:bg-canvas"
                  >
                    <Plus className="h-3.5 w-3.5 text-brand" />
                    Crear primera categoría
                  </button>
                )}
              </div>
            ) : (
              <div className="space-y-3">
                {filteredUserCategories.map((cat) => {
                  const resolvedColor = cat.color || getCategoryColor(cat.name);

                  return (
                    <div
                      key={cat.id}
                      className="surface p-4 shadow-2xs transition hover:border-brand/40"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-3 min-w-0">
                          <div
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl shadow-2xs"
                            style={{ backgroundColor: `${resolvedColor}18`, color: resolvedColor }}
                          >
                            <CategoryIcon name={cat.icon} className="h-4.5 w-4.5" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-bold text-ink">{cat.name}</p>
                            <p className="text-[11px] text-muted">
                              {cat.active ? 'Activa' : 'Desactivada'}
                            </p>
                          </div>
                        </div>

                        {/* Controls */}
                        <div className="flex items-center gap-2 shrink-0">
                          {/* Toggle switch */}
                          <button
                            type="button"
                            role="switch"
                            aria-checked={cat.active}
                            aria-label={`Alternar categoría ${cat.name}`}
                            onClick={() => handleToggle(cat)}
                            className={`relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-brand/20 ${
                              cat.active ? 'bg-brand' : 'bg-slate-300'
                            }`}
                          >
                            <span
                              className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-xs transition-transform ${
                                cat.active ? 'translate-x-4.5 mt-0.5' : 'translate-x-0.5 mt-0.5'
                              }`}
                            />
                          </button>

                          {/* Action buttons */}
                          <button
                            type="button"
                            onClick={() => openEditModal(cat)}
                            aria-label={`Editar categoría ${cat.name}`}
                            className="rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink"
                          >
                            <Edit2 className="h-3.5 w-3.5" />
                          </button>

                          <button
                            type="button"
                            onClick={() => setCategoryToDelete(cat)}
                            aria-label={`Eliminar categoría ${cat.name}`}
                            className="rounded-lg p-1.5 text-muted hover:bg-negative/10 hover:text-negative"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Column 2: Categorías del Sistema */}
          <div className="lg:col-span-7 space-y-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="font-display text-base font-bold text-ink">Categorías del Sistema</h2>
                  <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-700">
                    {activeSystemCount} de {systemCategories.length} activas
                  </span>
                </div>
                <p className="text-xs text-muted">
                  Las que desactives dejan de usarse al sugerir y clasificar transacciones.
                </p>
              </div>

              {/* Status Filter Tabs */}
              <div className="flex items-center rounded-xl bg-canvas p-1 border border-line/80 shadow-2xs self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setSystemFilter('all')}
                  className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    systemFilter === 'all'
                      ? 'bg-white text-ink shadow-2xs'
                      : 'text-muted hover:text-ink'
                  }`}
                >
                  Todas
                </button>
                <button
                  type="button"
                  onClick={() => setSystemFilter('active')}
                  className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    systemFilter === 'active'
                      ? 'bg-white text-ink shadow-2xs'
                      : 'text-muted hover:text-ink'
                  }`}
                >
                  Activas
                </button>
                <button
                  type="button"
                  onClick={() => setSystemFilter('inactive')}
                  className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    systemFilter === 'inactive'
                      ? 'bg-white text-ink shadow-2xs'
                      : 'text-muted hover:text-ink'
                  }`}
                >
                  Apagadas
                </button>
              </div>
            </div>

            {filteredSystemCategories.length === 0 ? (
              <div className="surface p-8 text-center text-muted">
                <p className="text-sm">No se encontraron categorías del sistema con los filtros actuales.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {filteredSystemCategories.map((cat) => {
                  const resolvedColor = cat.color || getCategoryColor(cat.name);

                  return (
                    <div
                      key={cat.id}
                      className={`surface flex items-center justify-between p-3.5 shadow-2xs transition ${
                        cat.active
                          ? 'hover:border-brand/40'
                          : 'opacity-65 bg-canvas/60'
                      }`}
                    >
                      <div className="flex items-center gap-3 min-w-0 pr-2">
                        <div
                          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl shadow-2xs"
                          style={{
                            backgroundColor: `${resolvedColor}18`,
                            color: resolvedColor,
                          }}
                        >
                          <CategoryIcon name={cat.icon} className="h-4 w-4" />
                        </div>
                        <div className="min-w-0">
                          <p className={`truncate text-sm font-semibold ${cat.active ? 'text-ink' : 'text-muted line-through'}`}>
                            {cat.name}
                          </p>
                          <p className="text-[11px] text-muted">
                            {cat.active ? 'Activa para sugerencias' : 'Apagada'}
                          </p>
                        </div>
                      </div>

                      {/* Toggle Switch */}
                      <button
                        type="button"
                        role="switch"
                        aria-checked={cat.active}
                        aria-label={`Alternar categoría del sistema ${cat.name}`}
                        onClick={() => handleToggle(cat)}
                        className={`relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-brand/20 ${
                          cat.active ? 'bg-brand' : 'bg-slate-300'
                        }`}
                      >
                        <span
                          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-xs transition-transform ${
                            cat.active ? 'translate-x-4.5 mt-0.5' : 'translate-x-0.5 mt-0.5'
                          }`}
                        />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Category Creation / Edit Modal */}
      {modalOpen && (
        <CategoryModal
          isOpen={modalOpen}
          initialCategory={editingCategory}
          onClose={() => setModalOpen(false)}
          onSave={handleSaveCategory}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <dialog
        ref={deleteDialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-dialog-title"
        onCancel={() => setCategoryToDelete(null)}
        className="fixed inset-0 z-50 m-auto w-full max-w-md rounded-2xl border border-line bg-white p-6 shadow-2xl backdrop:bg-ink/40"
      >
        <div className="flex items-start gap-4">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-negative/10 text-negative">
            <Trash2 className="h-5 w-5" />
          </div>
          <div>
            <h2 id="delete-dialog-title" className="font-display text-lg font-bold text-ink">
              ¿Eliminar categoría?
            </h2>
            <p className="mt-1.5 text-sm text-muted">
              ¿Estás seguro de que deseas eliminar la categoría{' '}
              <strong className="text-ink">{categoryToDelete?.name}</strong>? Los movimientos asociados conservarán
              el nombre asignado, pero ya no aparecerá como opción en el catálogo.
            </p>
          </div>
        </div>

        {deleteError && (
          <div role="alert" className="mt-4 rounded-xl border border-negative/20 bg-negative/5 p-3 text-xs text-negative">
            {deleteError}
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={() => setCategoryToDelete(null)}
            disabled={isDeleting}
            className="rounded-xl border border-line px-4 py-2 text-sm font-semibold text-ink hover:bg-canvas disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleDeleteCategory}
            disabled={isDeleting}
            className="inline-flex items-center gap-2 rounded-xl bg-negative px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-negative/90 disabled:opacity-50"
          >
            {isDeleting && <Loader2 className="h-4 w-4 animate-spin" />}
            <span>Eliminar</span>
          </button>
        </div>
      </dialog>
    </div>
  );
}

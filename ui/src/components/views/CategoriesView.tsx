import { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Tags,
  Search,
  Plus,
  FolderPlus,
  Loader2,
  Trash2,
  Edit2,
  AlertCircle,
  CheckCircle2,
  X,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import type { Category, CategoryRequest } from '../../types';
import { api } from '../../services/api';
import { CategoryIcon } from '../categories/CategoryIcon';
import { CategoryModal } from '../categories/CategoryModal';

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
  const [defaultParentId, setDefaultParentId] = useState<number | null>(null);

  // Deletion confirm modal
  const [categoryToDelete, setCategoryToDelete] = useState<Category | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Expanded subcategories in UI
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

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

    return userCategories.filter((cat) => {
      const matchesName = cat.name.toLowerCase().includes(q);
      const matchesSub = cat.subcategories?.some((s) => s.name.toLowerCase().includes(q));
      return matchesName || matchesSub;
    });
  }, [userCategories, searchQuery]);

  const activeSystemCount = useMemo(() => {
    return systemCategories.filter((c) => c.active).length;
  }, [systemCategories]);

  const toggleExpand = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // Toggle active state
  const handleToggle = async (cat: Category) => {
    const previousState = cat.active;
    // Optimistic update
    setCategories((prev) =>
      prev.map((item) => {
        if (item.id === cat.id) return { ...item, active: !previousState };
        if (item.subcategories) {
          return {
            ...item,
            subcategories: item.subcategories.map((sub) =>
              sub.id === cat.id ? { ...sub, active: !previousState } : sub
            ),
          };
        }
        return item;
      })
    );

    try {
      await api.toggleCategory(cat.id);
      showNotification(
        `Categoría "${cat.name}" ${!previousState ? 'activada' : 'desactivada'} correctamente.`
      );
    } catch (err: unknown) {
      // Rollback
      setCategories((prev) =>
        prev.map((item) => {
          if (item.id === cat.id) return { ...item, active: previousState };
          if (item.subcategories) {
            return {
              ...item,
              subcategories: item.subcategories.map((sub) =>
                sub.id === cat.id ? { ...sub, active: previousState } : sub
              ),
            };
          }
          return item;
        })
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

  // Delete category
  const handleDeleteCategory = async () => {
    if (!categoryToDelete) return;
    try {
      setIsDeleting(true);
      await api.deleteCategory(categoryToDelete.id);
      showNotification(`Categoría "${categoryToDelete.name}" eliminada.`);
      setCategoryToDelete(null);
      await loadCategories();
    } catch (err: unknown) {
      showNotification(
        err instanceof Error ? err.message : 'Error al eliminar la categoría',
        'error'
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const openCreateModal = (parentId: number | null = null) => {
    setEditingCategory(null);
    setDefaultParentId(parentId);
    setModalOpen(true);
  };

  const openEditModal = (cat: Category) => {
    setEditingCategory(cat);
    setDefaultParentId(null);
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
      <section className="rounded-2xl border border-line bg-white p-6 shadow-xs">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-3.5">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand/10 text-brand shrink-0">
              <Tags className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="font-display text-2xl font-bold tracking-tight text-ink">Categorías</h1>
                <span className="rounded-full bg-brand/10 px-2.5 py-0.5 text-xs font-semibold text-brand">
                  {categories.length} en total
                </span>
              </div>
              <p className="mt-1 text-sm text-muted">
                Personaliza tus categorías y decide cuáles del sistema deseas tener activas para la clasificación de tus movimientos.
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
              onClick={() => openCreateModal(null)}
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-dark focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            >
              <Plus className="h-4 w-4" />
              <span>Nueva categoría</span>
            </button>

            <button
              type="button"
              onClick={() => openCreateModal(userCategories.length > 0 ? userCategories[0].id : null)}
              className="inline-flex items-center gap-2 rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-semibold text-ink shadow-xs transition hover:bg-canvas focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            >
              <FolderPlus className="h-4 w-4 text-brand" />
              <span>Subcategoría</span>
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
              <div className="rounded-2xl border-2 border-dashed border-line bg-canvas/40 p-8 text-center">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-muted shadow-xs">
                  <FolderPlus className="h-6 w-6 text-brand" />
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
                    onClick={() => openCreateModal(null)}
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
                  const hasSub = cat.subcategories && cat.subcategories.length > 0;
                  const isExpanded = expandedIds.has(cat.id);

                  return (
                    <div
                      key={cat.id}
                      className="group rounded-2xl border border-line bg-white p-4 shadow-xs transition hover:border-brand/30"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-3 min-w-0">
                          <div
                            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
                            style={{ backgroundColor: `${cat.color || '#10B981'}15`, color: cat.color || '#10B981' }}
                          >
                            <CategoryIcon name={cat.icon} className="h-4 w-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-bold text-ink">{cat.name}</p>
                            {hasSub && (
                              <button
                                type="button"
                                onClick={() => toggleExpand(cat.id)}
                                className="flex items-center gap-1 text-[11px] font-medium text-brand hover:underline"
                              >
                                {isExpanded ? (
                                  <ChevronDown className="h-3 w-3" />
                                ) : (
                                  <ChevronRight className="h-3 w-3" />
                                )}
                                <span>{cat.subcategories?.length} subcategorías</span>
                              </button>
                            )}
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
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-brand/20 ${
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

                      {/* Subcategories Tree */}
                      {hasSub && isExpanded && (
                        <div className="mt-3.5 border-t border-line/60 pt-3 pl-6 space-y-2">
                          {cat.subcategories?.map((sub) => (
                            <div
                              key={sub.id}
                              className="flex items-center justify-between text-xs py-1"
                            >
                              <div className="flex items-center gap-2">
                                <span className="h-1.5 w-1.5 rounded-full bg-brand" />
                                <span className="font-medium text-ink">{sub.name}</span>
                              </div>
                              <div className="flex items-center gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => openEditModal(sub)}
                                  aria-label={`Editar subcategoría ${sub.name}`}
                                  className="rounded p-1 text-muted hover:text-ink"
                                >
                                  <Edit2 className="h-3 w-3" />
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setCategoryToDelete(sub)}
                                  aria-label={`Eliminar subcategoría ${sub.name}`}
                                  className="rounded p-1 text-muted hover:text-negative"
                                >
                                  <Trash2 className="h-3 w-3" />
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
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
                  Las que apagues dejan de usarse al sugerir y clasificar transacciones.
                </p>
              </div>

              {/* Status Filter Tabs */}
              <div className="flex items-center rounded-xl bg-canvas p-1 border border-line/80 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setSystemFilter('all')}
                  className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    systemFilter === 'all'
                      ? 'bg-white text-ink shadow-xs'
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
                      ? 'bg-white text-ink shadow-xs'
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
                      ? 'bg-white text-ink shadow-xs'
                      : 'text-muted hover:text-ink'
                  }`}
                >
                  Apagadas
                </button>
              </div>
            </div>

            {filteredSystemCategories.length === 0 ? (
              <div className="rounded-2xl border border-line bg-white p-8 text-center text-muted">
                <p className="text-sm">No se encontraron categorías del sistema con los filtros actuales.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                {filteredSystemCategories.map((cat) => (
                  <div
                    key={cat.id}
                    className={`flex items-center justify-between rounded-xl border p-3 shadow-2xs transition ${
                      cat.active
                        ? 'border-line bg-white hover:border-brand/30'
                        : 'border-line/60 bg-canvas/50 opacity-75'
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0 pr-2">
                      <div
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
                        style={{
                          backgroundColor: `${cat.color || '#64748B'}15`,
                          color: cat.color || '#64748B',
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
                      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-brand/20 ${
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
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Category Create/Edit Modal */}
      <CategoryModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onSave={handleSaveCategory}
        parentOptions={userCategories}
        initialCategory={editingCategory}
        defaultParentId={defaultParentId}
      />

      {/* Delete Confirmation Modal */}
      {categoryToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="fixed inset-0 bg-ink/40 backdrop-blur-xs"
            onClick={() => setCategoryToDelete(null)}
            aria-hidden="true"
          />
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-dialog-title"
            className="relative z-10 w-full max-w-sm rounded-2xl border border-line bg-white p-6 shadow-xl"
          >
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-negative/10 text-negative">
              <Trash2 className="h-5 w-5" />
            </div>
            <h3 id="delete-dialog-title" className="mt-4 font-display text-base font-bold text-ink">
              ¿Eliminar categoría?
            </h3>
            <p className="mt-1 text-xs text-muted leading-relaxed">
              Estás a punto de eliminar la categoría <strong>{categoryToDelete.name}</strong>. Esta acción no se puede deshacer.
            </p>
            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setCategoryToDelete(null)}
                className="rounded-xl border border-line px-3.5 py-2 text-xs font-semibold text-muted hover:bg-canvas hover:text-ink"
              >
                Cancelar
              </button>
              <button
                type="button"
                disabled={isDeleting}
                onClick={handleDeleteCategory}
                className="inline-flex items-center gap-1.5 rounded-xl bg-negative px-4 py-2 text-xs font-semibold text-white hover:bg-negative/90 disabled:opacity-50"
              >
                {isDeleting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                Eliminar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

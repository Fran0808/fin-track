import { useState, useEffect } from 'react';
import { X, Loader2 } from 'lucide-react';
import type { Category, CategoryRequest } from '../../types';
import { AVAILABLE_ICONS, CategoryIcon } from './CategoryIcon';

interface CategoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (data: CategoryRequest) => Promise<void>;
  parentOptions: Category[];
  initialCategory?: Category | null;
  defaultParentId?: number | null;
}

const PRESET_COLORS = [
  { hex: '#10B981', name: 'Esmeralda' },
  { hex: '#3B82F6', name: 'Azul' },
  { hex: '#F97316', name: 'Naranja' },
  { hex: '#A855F7', name: 'Púrpura' },
  { hex: '#EC4899', name: 'Rosa' },
  { hex: '#EF4444', name: 'Rojo' },
  { hex: '#F59E0B', name: 'Ámbar' },
  { hex: '#6366F1', name: 'Índigo' },
  { hex: '#06B6D4', name: 'Cian' },
  { hex: '#64748B', name: 'Pizarra' },
];

export function CategoryModal({
  isOpen,
  onClose,
  onSave,
  parentOptions,
  initialCategory,
  defaultParentId = null,
}: CategoryModalProps) {
  const [name, setName] = useState('');
  const [color, setColor] = useState('#10B981');
  const [icon, setIcon] = useState('tag');
  const [parentId, setParentId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (initialCategory) {
      setName(initialCategory.name);
      setColor(initialCategory.color || '#10B981');
      setIcon(initialCategory.icon || 'tag');
      setParentId(initialCategory.parentId || null);
    } else {
      setName('');
      setColor('#10B981');
      setIcon('tag');
      setParentId(defaultParentId);
    }
    setError(null);
  }, [initialCategory, defaultParentId, isOpen]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const isSubcategoryMode = defaultParentId !== null || parentId !== null;
  const title = initialCategory
    ? 'Editar categoría'
    : isSubcategoryMode
    ? 'Nueva subcategoría'
    : 'Nueva categoría';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('El nombre de la categoría es obligatorio');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await onSave({
        name: name.trim(),
        color,
        icon,
        parentId: parentId || undefined,
      });
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Error al guardar la categoría');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-ink/40 backdrop-blur-xs transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Modal Dialog */}
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="category-modal-title"
        className="relative z-10 w-full max-w-lg rounded-2xl border border-line bg-white p-6 shadow-xl sm:p-7"
      >
        <div className="flex items-center justify-between border-b border-line pb-4">
          <div className="flex items-center gap-3">
            <div
              className="flex h-10 w-10 items-center justify-center rounded-xl"
              style={{ backgroundColor: `${color}18`, color }}
            >
              <CategoryIcon name={icon} className="h-5 w-5" />
            </div>
            <div>
              <h2 id="category-modal-title" className="font-display text-lg font-bold text-ink">
                {title}
              </h2>
              <p className="text-xs text-muted">
                {isSubcategoryMode
                  ? 'Organiza tus gastos como parte de una categoría principal.'
                  : 'Crea una categoría personalizada para tus movimientos.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar modal"
            className="rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="mt-4 rounded-xl border border-negative/20 bg-negative/5 px-4 py-2.5 text-xs text-negative">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <div>
            <label htmlFor="cat-name" className="block text-xs font-semibold text-muted">
              Nombre de la categoría
            </label>
            <input
              id="cat-name"
              type="text"
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ej. Cursos online, Cafetería, Gimnasio..."
              maxLength={100}
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3.5 py-2.5 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/20"
            />
          </div>

          <div>
            <label htmlFor="cat-parent" className="block text-xs font-semibold text-muted">
              Categoría principal (opcional para subcategorías)
            </label>
            <select
              id="cat-parent"
              value={parentId || ''}
              onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3.5 py-2.5 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/20"
            >
              <option value="">Ninguna (es categoría principal)</option>
              {parentOptions
                .filter((p) => !initialCategory || p.id !== initialCategory.id)
                .map((parent) => (
                  <option key={parent.id} value={parent.id}>
                    {parent.name}
                  </option>
                ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted">Color identificador</label>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {PRESET_COLORS.map((preset) => {
                const selected = color.toLowerCase() === preset.hex.toLowerCase();
                return (
                  <button
                    key={preset.hex}
                    type="button"
                    title={preset.name}
                    aria-label={`Seleccionar color ${preset.name}`}
                    onClick={() => setColor(preset.hex)}
                    style={{ backgroundColor: preset.hex }}
                    className={`h-7 w-7 rounded-full transition-transform ${
                      selected ? 'scale-115 ring-3 ring-ink/20 shadow-xs' : 'hover:scale-105'
                    }`}
                  />
                );
              })}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted">Ícono representativo</label>
            <div className="mt-2 grid grid-cols-5 gap-2 sm:grid-cols-7 max-h-36 overflow-y-auto p-1 border border-line/60 rounded-xl bg-canvas/30">
              {AVAILABLE_ICONS.map((item) => {
                const selected = icon === item.id;
                const IconComponent = item.component;
                return (
                  <button
                    key={item.id}
                    type="button"
                    title={item.label}
                    aria-label={`Seleccionar ícono ${item.label}`}
                    onClick={() => setIcon(item.id)}
                    className={`flex flex-col items-center justify-center gap-1 rounded-xl p-2 text-xs transition ${
                      selected
                        ? 'bg-brand/12 text-brand font-semibold ring-2 ring-brand'
                        : 'text-muted hover:bg-white hover:text-ink'
                    }`}
                  >
                    <IconComponent className="h-4 w-4" />
                  </button>
                );
              })}
            </div>
          </div>

          <div className="mt-6 flex items-center justify-end gap-3 pt-3 border-t border-line">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-line px-4 py-2.5 text-sm font-semibold text-muted hover:bg-canvas hover:text-ink"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-brand-dark disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
              {initialCategory ? 'Guardar cambios' : 'Crear categoría'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

import { useState, type KeyboardEvent } from 'react';
import type { CategoryBreakdown } from '../../types';
import { formatCurrency, getCategoryColor, getCategoryLabel } from '../../utils';

export interface CategoryDonutChartProps {
  categories: CategoryBreakdown[];
  totalAmount: number;
  loading?: boolean;
  onSelectCategory?: (category: string | null) => void;
}

export function CategoryDonutChart({
  categories,
  totalAmount,
  loading = false,
  onSelectCategory,
}: CategoryDonutChartProps) {
  const [hoveredCategory, setHoveredCategory] = useState<string | null | undefined>(undefined);

  const size = 190;
  const strokeWidth = 24;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  const total = totalAmount > 0 ? totalAmount : categories.reduce((sum, c) => sum + c.amount, 0);

  // Calcular offsets y detalles de forma inmutable mediante reduce
  const itemsWithDetails = categories.reduce<
    Array<
      CategoryBreakdown & {
        label: string;
        color: string;
        dashLength: number;
        offset: number;
      }
    >
  >((acc, cat, idx) => {
    const share = total > 0 ? cat.amount / total : cat.percentage / 100;
    const dashLength = share * circumference;
    const prevItem = acc[acc.length - 1];
    const offset = prevItem ? prevItem.offset + prevItem.dashLength : 0;
    acc.push({
      ...cat,
      label: getCategoryLabel(cat.category),
      color: getCategoryColor(cat.category, idx),
      dashLength,
      offset,
    });
    return acc;
  }, []);

  const activeItem = hoveredCategory !== undefined
    ? itemsWithDetails.find((c) => c.category === hoveredCategory)
    : null;

  if (loading) {
    return <p role="status" className="py-10 text-sm text-muted">Calculando distribución...</p>;
  }

  if (categories.length === 0) {
    return <p className="py-10 text-sm text-muted">No hay gastos registrados en este período.</p>;
  }

  const handleKeyDown = (event: KeyboardEvent<SVGCircleElement>, category: string | null) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onSelectCategory?.(category);
    }
  };

  return (
    <div className="flex flex-col items-center gap-6 pt-2 sm:flex-row sm:items-start">
      {/* 1. Gráfico SVG Donut */}
      <div className="relative shrink-0 flex items-center justify-center">
        <svg
          width={size}
          height={size}
          className="overflow-visible"
          role="group"
          aria-label="Distribución por categorías"
        >
          {/* Pista de fondo */}
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="var(--color-canvas)"
            strokeWidth={strokeWidth}
            fill="none"
          />

          {/* Segmentos de categorías */}
          {itemsWithDetails.map((item) => {
            const isHovered = hoveredCategory !== undefined && hoveredCategory === item.category;
            const isDimmed = hoveredCategory !== undefined && hoveredCategory !== item.category;
            const gap = categories.length > 1 ? 2 : 0;
            const arcLength = Math.max(0, item.dashLength - gap);

            return (
              <circle
                key={JSON.stringify(item.category)}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={item.color}
                strokeWidth={isHovered ? strokeWidth + 4 : strokeWidth}
                strokeDasharray={`${arcLength} ${circumference}`}
                strokeDashoffset={-item.offset}
                transform={`rotate(-90 ${size / 2} ${size / 2})`}
                className={`transition-all duration-200 cursor-pointer ${isDimmed ? 'opacity-40' : 'opacity-100'}`}
                tabIndex={0}
                role="button"
                aria-label={`Ver movimientos de la categoría ${item.label}: ${formatCurrency(item.amount)} (${item.percentage.toFixed(1)}%)`}
                onMouseEnter={() => setHoveredCategory(item.category)}
                onMouseLeave={() => setHoveredCategory(undefined)}
                onFocus={() => setHoveredCategory(item.category)}
                onBlur={() => setHoveredCategory(undefined)}
                onClick={() => onSelectCategory?.(item.category)}
                onKeyDown={(e) => handleKeyDown(e, item.category)}
              />
            );
          })}
        </svg>

        {/* Centro interactivo del anillo */}
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center px-3 text-center">
          <span className="max-w-[110px] truncate text-[11px] font-semibold uppercase tracking-wider text-muted">
            {activeItem ? activeItem.label : 'Total'}
          </span>
          <span className="font-display font-num mt-0.5 text-base font-bold text-ink sm:text-lg">
            {activeItem ? formatCurrency(activeItem.amount) : formatCurrency(total)}
          </span>
          <span
            className="font-num mt-0.5 text-xs font-bold"
            style={{ color: activeItem ? activeItem.color : 'var(--color-brand)' }}
          >
            {activeItem ? `${activeItem.percentage.toFixed(1)}%` : `${categories.length} ${categories.length === 1 ? 'categoría' : 'categorías'}`}
          </span>
        </div>
      </div>

      {/* 2. Lista interactiva de categorías */}
      <div className="w-full flex-1 space-y-1.5 max-h-[280px] overflow-y-auto pr-1">
        {itemsWithDetails.map((item) => {
          const isHovered = hoveredCategory !== undefined && hoveredCategory === item.category;

          return (
            <button
              type="button"
              key={JSON.stringify(item.category)}
              disabled={!onSelectCategory}
              onClick={() => onSelectCategory?.(item.category)}
              onMouseEnter={() => setHoveredCategory(item.category)}
              onMouseLeave={() => setHoveredCategory(undefined)}
              onFocus={() => setHoveredCategory(item.category)}
              onBlur={() => setHoveredCategory(undefined)}
              aria-label={`Ver movimientos de la categoría ${item.label}`}
              className={`group flex w-full items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-left transition-colors focus-visible:outline-2 focus-visible:outline-brand disabled:cursor-default ${
                isHovered ? 'bg-canvas shadow-2xs' : 'hover:bg-canvas/60'
              }`}
            >
              <div className="flex min-w-0 items-center gap-2.5">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: item.color }}
                  aria-hidden="true"
                />
                <div className="min-w-0">
                  <p className="truncate text-xs font-semibold text-ink group-hover:text-brand">
                    {item.label}
                  </p>
                  <p className="font-num text-[11px] text-muted">
                    <span>{item.count} {item.count === 1 ? 'gasto' : 'gastos'}</span>
                    <span> · </span>
                    <span>{item.percentage.toFixed(1)}% del gasto</span>
                  </p>
                </div>
              </div>
              <span className="font-num shrink-0 text-xs font-semibold text-ink">
                {formatCurrency(item.amount)}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import { PieChart } from 'lucide-react';
import { formatCurrency } from '../../utils';

export interface CategoryItem {
  id: string;
  label: string;
  amount: number;
  percentage: number;
  color: string;
  strokeColor: string;
  icon: React.ComponentType<{ className?: string }>;
  merchants: string;
}

interface CategoryDonutChartProps {
  categories: CategoryItem[];
  totalAmount: number;
  loading?: boolean;
}

export const CategoryDonutChart: React.FC<CategoryDonutChartProps> = ({
  categories,
  totalAmount,
  loading = false,
}) => {
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  const activeCategory = hoveredId
    ? categories.find((c) => c.id === hoveredId)
    : null;

  // SVG Donut geometry
  const size = 180;
  const strokeWidth = 22;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  let cumulativePercent = 0;

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 p-6 flex flex-col justify-between">
      {/* 1. Header */}
      <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-5">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-700">
            <PieChart className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">Distribución por Categorías</h3>
            <p className="text-[11px] text-slate-500 font-medium">Desglose porcentual de salidas en el mes</p>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-slate-400 animate-pulse">
          Calculando distribución...
        </div>
      ) : categories.length === 0 ? (
        <div className="py-12 text-center text-xs text-slate-400">
          Sin gastos categorizados en este período.
        </div>
      ) : (
        <div className="flex flex-col sm:flex-row items-center gap-6">
          {/* 2. SVG Donut Chart */}
          <div className="relative shrink-0 flex items-center justify-center">
            <svg width={size} height={size} className="transform -rotate-90">
              {/* Background Track */}
              <circle
                cx={size / 2}
                cy={size / 2}
                r={radius}
                className="stroke-slate-100"
                strokeWidth={strokeWidth}
                fill="none"
              />

              {/* Segment Arcs */}
              {categories.map((cat) => {
                const strokeDasharray = `${(cat.percentage / 100) * circumference} ${circumference}`;
                const strokeDashoffset = -((cumulativePercent / 100) * circumference);
                cumulativePercent += cat.percentage;

                const isHovered = hoveredId === cat.id;

                return (
                  <circle
                    key={cat.id}
                    cx={size / 2}
                    cy={size / 2}
                    r={radius}
                    stroke={cat.strokeColor}
                    strokeWidth={isHovered ? strokeWidth + 3 : strokeWidth}
                    strokeDasharray={strokeDasharray}
                    strokeDashoffset={strokeDashoffset}
                    fill="none"
                    strokeLinecap="round"
                    className="transition-all duration-300"
                    onMouseEnter={() => setHoveredId(cat.id)}
                    onMouseLeave={() => setHoveredId(null)}
                  />
                );
              })}
            </svg>

            {/* Donut Center Display */}
            <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none px-2">
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider truncate max-w-[100px]">
                {activeCategory ? activeCategory.label : 'Total Gastado'}
              </span>
              <span className="text-base font-extrabold font-num text-slate-900 tabular-nums">
                {activeCategory
                  ? formatCurrency(activeCategory.amount)
                  : formatCurrency(totalAmount)}
              </span>
              <span className="text-[10px] font-bold text-blue-600">
                {activeCategory ? `${activeCategory.percentage.toFixed(1)}%` : '100%'}
              </span>
            </div>
          </div>

          {/* 3. Interactive Category Legend */}
          <div className="flex-1 w-full space-y-2.5">
            {categories.map((cat) => {
              const Icon = cat.icon;
              const isHovered = hoveredId === cat.id;

              return (
                <div
                  key={cat.id}
                  onMouseEnter={() => setHoveredId(cat.id)}
                  onMouseLeave={() => setHoveredId(null)}
                  className={`p-2.5 rounded-xl border transition-all ${
                    isHovered
                      ? 'bg-slate-50 border-slate-300 shadow-xs'
                      : 'bg-white border-slate-100 hover:bg-slate-50/60'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 min-w-0">
                      <div
                        className="w-2.5 h-2.5 rounded-full shrink-0"
                        style={{ backgroundColor: cat.strokeColor }}
                      />
                      <Icon className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                      <span className="text-xs font-bold text-slate-800 truncate">
                        {cat.label}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <span className="text-xs font-extrabold font-num text-slate-900">
                        {formatCurrency(cat.amount)}
                      </span>
                      <span className="text-[10px] font-bold text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                        {cat.percentage.toFixed(1)}%
                      </span>
                    </div>
                  </div>

                  <p className="text-[10px] text-slate-400 mt-1 truncate pl-4.5">
                    {cat.merchants}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

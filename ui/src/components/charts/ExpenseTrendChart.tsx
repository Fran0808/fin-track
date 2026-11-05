import { useState, type PointerEvent, type KeyboardEvent } from 'react';
import type { DailyExpense } from '../../types';
import { formatCurrency } from '../../utils';

export interface WeeklyExpense {
  weekNumber: number;
  startDay: number;
  endDay: number;
  label: string;
  dateRangeLabel: string;
  amount: number;
  percentage: number;
}

interface ExpenseTrendChartProps {
  points: DailyExpense[];
  monthLabel: string;
  onSelectDay?: (day: number) => void;
  onSelectWeekRange?: (startDay: number, endDay: number) => void;
}

export function ExpenseTrendChart({ points, monthLabel, onSelectDay, onSelectWeekRange }: ExpenseTrendChartProps) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const [mode, setMode] = useState<'daily' | 'weekly'>('daily');
  const total = points.at(-1)?.cumulativeAmount ?? 0;

  if (points.length === 0 || total === 0) {
    return <p className="py-12 text-sm text-muted">Aún no hay gastos para mostrar en este período.</p>;
  }

  // Derive weekly breakdown from points
  const maxDay = points.length > 0 ? Math.max(...points.map((p) => p.day)) : 31;
  const rawWeeks = [
    { weekNumber: 1, startDay: 1, endDay: Math.min(7, maxDay) },
    { weekNumber: 2, startDay: 8, endDay: Math.min(14, maxDay) },
    { weekNumber: 3, startDay: 15, endDay: Math.min(21, maxDay) },
    { weekNumber: 4, startDay: 22, endDay: Math.min(28, maxDay) },
    ...(maxDay >= 29 ? [{ weekNumber: 5, startDay: 29, endDay: maxDay }] : []),
  ].filter((w) => w.startDay <= maxDay);

  const weeks: WeeklyExpense[] = rawWeeks.map((w) => {
    const weekPoints = points.filter((p) => p.day >= w.startDay && p.day <= w.endDay);
    const amountCents = weekPoints.reduce((sum, p) => sum + Math.round(p.amount * 100), 0);
    const amount = amountCents / 100;
    const percentage = total > 0 ? (amount / total) * 100 : 0;
    return {
      weekNumber: w.weekNumber,
      startDay: w.startDay,
      endDay: w.endDay,
      label: `Sem ${w.weekNumber}`,
      dateRangeLabel: `${w.startDay}–${w.endDay}`,
      amount,
      percentage,
    };
  });

  // Daily chart geometry (improved with top spacing so bars never collide with Y-axis label)
  const left = 14;
  const right = 706;
  const top = 28;
  const bottom = 170;

  const maxDailyValue = Math.max(1, ...points.map((p) => p.amount));
  const maxWeeklyValue = Math.max(1, ...weeks.map((w) => w.amount));
  const weeklyAverageCents = weeks.length > 0 ? Math.round((total * 100) / weeks.length) : 0;
  const weeklyAverage = weeklyAverageCents / 100;

  const xDaily = (index: number) => points.length === 1 ? (left + right) / 2 : left + (index / (points.length - 1)) * (right - left);
  const yDaily = (amount: number) => bottom - (amount / maxDailyValue) * (bottom - top);

  const activePoint = mode === 'daily' && activeIndex !== null ? points[activeIndex] ?? null : null;
  const activeWeek = mode === 'weekly' && activeIndex !== null ? weeks[activeIndex] ?? null : null;

  const canOpenActiveDay = Boolean(onSelectDay && activePoint && activePoint.amount > 0);
  const canOpenActiveWeek = Boolean(onSelectWeekRange && activeWeek && activeWeek.amount > 0);

  const getAxisTicks = () => {
    if (points.length <= 6) {
      return points.map((p, idx) => ({ index: idx, day: p.day }));
    }
    const ticks: { index: number; day: number }[] = [];
    ticks.push({ index: 0, day: points[0].day });

    for (let i = 1; i < points.length - 1; i++) {
      const p = points[i];
      if (p.day % 5 === 0) {
        ticks.push({ index: i, day: p.day });
      }
    }

    const lastIdx = points.length - 1;
    const lastPoint = points[lastIdx];
    const prevTick = ticks[ticks.length - 1];

    if (prevTick && lastPoint.day - prevTick.day < 2) {
      ticks[ticks.length - 1] = { index: lastIdx, day: lastPoint.day };
    } else {
      ticks.push({ index: lastIdx, day: lastPoint.day });
    }

    return ticks;
  };

  const axisTicks = getAxisTicks();

  const handleDailyPointerMove = (event: PointerEvent<SVGSVGElement>) => {
    const svg = event.currentTarget;
    const bounds = svg.getBoundingClientRect();
    const svgX = ((event.clientX - bounds.left) / bounds.width) * 720;
    const nearestIndex = Math.round(((svgX - left) / (right - left)) * (points.length - 1));
    setActiveIndex(Math.max(0, Math.min(points.length - 1, nearestIndex)));
  };

  const handleDailyKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    if ((event.key === 'Enter' || event.key === ' ') && canOpenActiveDay && activePoint) {
      event.preventDefault();
      onSelectDay?.(activePoint.day);
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const direction = event.key === 'ArrowRight' ? 1 : -1;
    setActiveIndex((current) => Math.max(0, Math.min(points.length - 1, (current ?? points.length - 1) + direction)));
  };

  const handleWeeklyKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if ((event.key === 'Enter' || event.key === ' ') && canOpenActiveWeek && activeWeek) {
      event.preventDefault();
      onSelectWeekRange?.(activeWeek.startDay, activeWeek.endDay);
      return;
    }
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const direction = event.key === 'ArrowRight' ? 1 : -1;
    setActiveIndex((current) => Math.max(0, Math.min(weeks.length - 1, (current ?? weeks.length - 1) + direction)));
  };

  return (
    <div>
      {/* Header bar */}
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted">
            {mode === 'daily' ? 'Total del período' : 'Gasto mensual consolidado'}
          </p>
          <p className="font-display font-num mt-1 text-2xl font-semibold sm:text-3xl">
            {formatCurrency(total)}
          </p>
        </div>
        <div role="group" aria-label="Vista del gasto" className="inline-flex rounded-xl border border-line bg-canvas p-1">
          {([['daily', 'Diario'], ['weekly', 'Semanal']] as const).map(([value, label]) => (
            <button
              type="button"
              key={value}
              aria-pressed={mode === value}
              onClick={() => {
                setMode(value);
                setActiveIndex(null);
              }}
              className={`rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
                mode === value ? 'bg-white text-ink shadow-xs' : 'text-muted hover:text-ink'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {mode === 'daily' ? (
        /* Daily Continuous Chart */
        <div className="relative">
          <svg
            className={`h-auto w-full rounded-xl ${canOpenActiveDay ? 'cursor-pointer' : ''}`}
            viewBox="0 0 720 210"
            role="group"
            tabIndex={0}
            aria-label={`Gasto diario en ${monthLabel}: ${formatCurrency(total)} registrados. Usa las flechas izquierda y derecha para explorar los días.`}
            onFocus={() => setActiveIndex(points.length - 1)}
            onBlur={() => setActiveIndex(null)}
            onKeyDown={handleDailyKeyDown}
            onPointerMove={handleDailyPointerMove}
            onPointerDown={handleDailyPointerMove}
            onPointerLeave={() => setActiveIndex(null)}
            onClick={() => {
              if (canOpenActiveDay && activePoint) onSelectDay?.(activePoint.day);
            }}
          >
            <line x1={left} y1={bottom} x2={right} y2={bottom} stroke="var(--color-line)" strokeWidth="1" />
            <line x1={left} y1={yDaily(maxDailyValue / 2)} x2={right} y2={yDaily(maxDailyValue / 2)} stroke="var(--color-line)" strokeDasharray="3 5" />

            {/* Daily Bars */}
            {points.map((point, index) => {
              const width = Math.min(32, ((right - left) / points.length) * 0.66);
              const barY = yDaily(point.amount);
              const barHeight = Math.max(point.amount > 0 ? 2 : 0, bottom - barY);
              const isSelected = activeIndex === index;

              return (
                <rect
                  key={point.day}
                  x={xDaily(index) - width / 2}
                  y={barY}
                  width={width}
                  height={barHeight}
                  rx="3"
                  fill="var(--color-brand)"
                  fillOpacity={isSelected ? 1 : 0.6}
                  className={onSelectDay && point.amount > 0 ? 'cursor-pointer transition-opacity' : 'transition-opacity'}
                  onClick={(event) => {
                    if (onSelectDay && point.amount > 0) {
                      event.stopPropagation();
                      onSelectDay(point.day);
                    }
                  }}
                />
              );
            })}

            {/* Active Highlight Line */}
            {activePoint && activeIndex !== null && (
              <g pointerEvents="none">
                <line x1={xDaily(activeIndex)} y1={top} x2={xDaily(activeIndex)} y2={bottom} stroke="var(--color-brand)" strokeDasharray="4 5" opacity="0.45" />
                <circle cx={xDaily(activeIndex)} cy={yDaily(activePoint.amount)} r="5" fill="var(--color-brand)" stroke="white" strokeWidth="2" />
              </g>
            )}

            {/* Axis Labels */}
            <g fill="var(--color-muted)" fontSize="11" fontFamily="inherit" pointerEvents="none">
              <text x={left} y="14" fontWeight="500">{formatCurrency(maxDailyValue)}</text>
              {axisTicks.map((tick) => {
                const posX = xDaily(tick.index);
                return (
                  <g key={tick.day}>
                    <line x1={posX} y1={bottom} x2={posX} y2={bottom + 4} stroke="var(--color-line)" strokeWidth="1.5" />
                    <text
                      x={posX}
                      y={bottom + 18}
                      textAnchor={tick.index === 0 ? 'start' : tick.index === points.length - 1 ? 'end' : 'middle'}
                    >
                      {tick.day}
                    </text>
                  </g>
                );
              })}
            </g>
          </svg>

          {/* Daily Hover/Focus Tooltip */}
          {activePoint && activeIndex !== null && (
            <div
              role="status"
              aria-live="polite"
              className="pointer-events-none absolute z-10 min-w-44 rounded-xl border border-line bg-white px-3 py-2.5 text-xs shadow-lg"
              style={{
                left: `clamp(7rem, ${(xDaily(activeIndex) / 720) * 100}%, calc(100% - 7rem))`,
                top: `${(yDaily(activePoint.amount) / 210) * 100}%`,
                transform: yDaily(activePoint.amount) < 65
                  ? 'translate(-50%, 14px)'
                  : 'translate(-50%, calc(-100% - 14px))',
              }}
            >
              <p className="font-semibold text-ink">{activePoint.day} de {monthLabel}</p>
              <p className="font-num mt-1 text-muted">Gasto del día: <span className="font-semibold text-ink">{formatCurrency(activePoint.amount)}</span></p>
              <p className="font-num mt-0.5 text-muted">Porcentaje del mes: <span className="font-semibold text-brand">{(total > 0 ? (activePoint.amount / total) * 100 : 0).toFixed(1)}%</span></p>
              {canOpenActiveDay && <p className="mt-1.5 font-semibold text-brand">Clic para ver movimientos →</p>}
            </div>
          )}
        </div>
      ) : (
        /* Redesigned Weekly Horizontal Pacing List */
        <div>
          <div
            role="group"
            tabIndex={0}
            aria-label={`Gasto semanal en ${monthLabel}: ${formatCurrency(total)} registrados. Usa las flechas izquierda y derecha para explorar las semanas.`}
            onFocus={() => setActiveIndex(weeks.length - 1)}
            onBlur={() => setActiveIndex(null)}
            onKeyDown={handleWeeklyKeyDown}
            className="space-y-2.5 focus-visible:outline-none"
          >
            {weeks.map((week, index) => {
              const isHighest = week.amount > 0 && week.amount === maxWeeklyValue;
              const isSelected = activeIndex === index;
              const isClickable = Boolean(onSelectWeekRange && week.amount > 0);
              const fillPct = maxWeeklyValue > 0 ? (week.amount / maxWeeklyValue) * 100 : 0;

              return (
                <div
                  key={week.weekNumber}
                  role="button"
                  tabIndex={isClickable ? 0 : -1}
                  aria-label={`Semana ${week.weekNumber} (${week.dateRangeLabel} de ${monthLabel}): ${formatCurrency(week.amount)}`}
                  onClick={() => {
                    if (isClickable) onSelectWeekRange?.(week.startDay, week.endDay);
                  }}
                  onKeyDown={(event) => {
                    if ((event.key === 'Enter' || event.key === ' ') && isClickable) {
                      event.preventDefault();
                      onSelectWeekRange?.(week.startDay, week.endDay);
                    }
                  }}
                  onMouseEnter={() => setActiveIndex(index)}
                  onMouseLeave={() => setActiveIndex(null)}
                  className={`group relative flex flex-col gap-2 rounded-xl border p-3.5 sm:px-4 sm:py-3 transition-all ${
                    isSelected
                      ? 'border-brand/40 bg-brand/5 shadow-xs'
                      : 'border-line/70 bg-white hover:border-brand/30 hover:bg-canvas/40'
                  } ${isClickable ? 'cursor-pointer' : 'cursor-default'}`}
                >
                  {/* Row Top: Week label + dates, amount + percentage */}
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-ink">Sem {week.weekNumber}</span>
                      <span className="text-xs text-muted">
                        ({week.dateRangeLabel} {monthLabel.split(' ')[0]})
                      </span>
                      {isHighest && (
                        <span className="rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-bold text-brand">
                          Mayor
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2.5">
                      <span className="font-display font-num text-sm sm:text-base font-bold text-ink">
                        {formatCurrency(week.amount)}
                      </span>
                      <span className="font-num text-xs text-muted">
                        {week.percentage.toFixed(1)}%
                      </span>
                      {isClickable && (
                        <span className="hidden text-xs font-semibold text-brand sm:inline-block opacity-0 transition-opacity group-hover:opacity-100">
                          Ver →
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Row Bottom: Horizontal gauge bar with weekly average reference marker */}
                  <div className="relative w-full">
                    <div className="h-3 w-full overflow-hidden rounded-full bg-canvas border border-line/40">
                      <svg className="h-full w-full" viewBox="0 0 100 12" preserveAspectRatio="none">
                        <rect
                          x="0"
                          y="0"
                          width={Math.max(week.amount > 0 ? 2 : 0, fillPct)}
                          height="12"
                          rx="6"
                          fill="var(--color-brand)"
                          fillOpacity={isSelected ? 1 : 0.75}
                          className="transition-all duration-300"
                          onClick={(event) => {
                            if (isClickable) {
                              event.stopPropagation();
                              onSelectWeekRange?.(week.startDay, week.endDay);
                            }
                          }}
                        />
                      </svg>
                    </div>
                    {weeklyAverage > 0 && maxWeeklyValue > 0 && (
                      <div
                        className="pointer-events-none absolute top-0 bottom-0 w-0.5 border-l-2 border-dashed border-ink/40 z-10"
                        style={{ left: `${Math.min(100, (weeklyAverage / maxWeeklyValue) * 100)}%` }}
                        aria-hidden="true"
                      />
                    )}
                  </div>
                </div>
              );
            })}

            {/* Active Screen Reader Status */}
            {activeWeek && activeIndex !== null && (
              <div role="status" aria-live="polite" className="sr-only">
                <p>Semana {activeWeek.weekNumber} ({activeWeek.dateRangeLabel} de {monthLabel})</p>
                <p>{formatCurrency(activeWeek.amount)}</p>
                <p>{activeWeek.percentage.toFixed(1)}% del mes</p>
                {canOpenActiveWeek && <p>Clic para ver movimientos de la semana →</p>}
              </div>
            )}
          </div>

          {/* Benchmark Legend & Summary Footer */}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-line/60 pt-3 text-xs text-muted">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-brand" />
                <span>Gasto semanal</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-3 w-0.5 border-l-2 border-dashed border-ink/40" />
                <span>
                  Promedio semanal: <strong className="font-semibold text-ink">{formatCurrency(weeklyAverage)}</strong>
                </span>
              </div>
            </div>
            {onSelectWeekRange && (
              <span className="text-[11px] text-muted">
                Haz clic en una semana para ver sus movimientos
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

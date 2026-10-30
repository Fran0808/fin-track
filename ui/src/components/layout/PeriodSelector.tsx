import { useState, useRef, useEffect } from 'react';
import { Calendar, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';

interface PeriodSelectorProps {
  year: number;
  month: number;
  onChange: (year: number, month: number) => void;
}

const MONTHS = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Setiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

const SHORT_MONTHS = [
  'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
  'Jul', 'Ago', 'Set', 'Oct', 'Nov', 'Dic',
];

export function PeriodSelector({ year, month, onChange }: PeriodSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [activeYear, setActiveYear] = useState(year);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setActiveYear(year);
  }, [year]);

  // Click outside and escape handler
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (e: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setActiveYear(year);
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
        setActiveYear(year);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('touchstart', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('touchstart', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, year]);

  const minYear = 2024;
  const maxYear = Math.max(new Date().getFullYear() + 2, year + 1);

  const canGoPrevMonth = year > minYear || month > 1;
  const canGoNextMonth = year < maxYear || month < 12;

  const handlePrevMonth = () => {
    if (!canGoPrevMonth) return;
    if (month === 1) {
      onChange(year - 1, 12);
    } else {
      onChange(year, month - 1);
    }
  };

  const handleNextMonth = () => {
    if (!canGoNextMonth) return;
    if (month === 12) {
      onChange(year + 1, 1);
    } else {
      onChange(year, month + 1);
    }
  };

  const handleSelectMonth = (mIndex: number) => {
    onChange(activeYear, mIndex + 1);
    setIsOpen(false);
  };

  const handlePrevYear = () => {
    if (activeYear > minYear) setActiveYear((prev) => prev - 1);
  };

  const handleNextYear = () => {
    if (activeYear < maxYear) setActiveYear((prev) => prev + 1);
  };

  const currentMonthName = MONTHS[month - 1] || 'Mes';

  return (
    <div ref={containerRef} className="relative inline-block text-left" aria-label="Período seleccionado">
      {/* Hidden native selects for backward compatibility and accessibility tools */}
      <select
        id="period-month"
        value={month}
        onChange={(event) => onChange(year, Number(event.target.value))}
        tabIndex={-1}
        aria-hidden="true"
        className="sr-only pointer-events-none"
      >
        {MONTHS.map((name, index) => (
          <option key={name} value={index + 1}>
            {name}
          </option>
        ))}
      </select>
      <select
        id="period-year"
        value={year}
        onChange={(event) => onChange(Number(event.target.value), month)}
        tabIndex={-1}
        aria-hidden="true"
        className="sr-only pointer-events-none"
      >
        {Array.from({ length: maxYear - minYear + 1 }, (_, i) => minYear + i).map((y) => (
          <option key={y} value={y}>
            {y}
          </option>
        ))}
      </select>

      {/* Unified Period Selector with quick prev/next month steppers */}
      <div className="inline-flex items-center rounded-xl border border-line bg-white shadow-2xs">
        <button
          type="button"
          onClick={handlePrevMonth}
          disabled={!canGoPrevMonth}
          aria-label="Mes anterior"
          title="Mes anterior"
          className="flex h-9 w-8 items-center justify-center rounded-l-xl border-r border-line/70 text-muted transition hover:bg-canvas hover:text-ink disabled:opacity-30"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </button>

        <button
          type="button"
          onClick={() => {
            setIsOpen((prev) => {
              if (!prev) setActiveYear(year);
              return !prev;
            });
          }}
          aria-haspopup="dialog"
          aria-expanded={isOpen}
          className="inline-flex h-9 items-center gap-2 px-3 text-sm font-semibold text-ink transition hover:bg-canvas"
        >
          <Calendar className="h-4 w-4 text-brand" aria-hidden="true" />
          <span key={`${year}-${month}`} className="period-animate inline-block">
            {currentMonthName} <span className="font-normal text-muted">{year}</span>
          </span>
          <ChevronDown
            className={`h-3.5 w-3.5 text-muted transition-transform duration-200 ${
              isOpen ? 'rotate-180 text-brand' : ''
            }`}
            aria-hidden="true"
          />
        </button>

        <button
          type="button"
          onClick={handleNextMonth}
          disabled={!canGoNextMonth}
          aria-label="Mes siguiente"
          title="Mes siguiente"
          className="flex h-9 w-8 items-center justify-center rounded-r-xl border-l border-line/70 text-muted transition hover:bg-canvas hover:text-ink disabled:opacity-30"
        >
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      {/* Modern Floating Period Picker Popover */}
      {isOpen && (
        <div
          role="dialog"
          aria-label="Seleccionar período"
          className="absolute right-0 z-50 mt-2 w-72 rounded-2xl border border-line bg-white p-4 shadow-xl animate-in fade-in zoom-in-95 duration-100"
        >
          {/* Year Navigation Bar */}
          <div className="flex items-center justify-between border-b border-line pb-3">
            <button
              type="button"
              onClick={handlePrevYear}
              disabled={activeYear <= minYear}
              aria-label="Año anterior"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition hover:bg-canvas hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>

            <span className="font-display text-base font-bold text-ink">{activeYear}</span>

            <button
              type="button"
              onClick={handleNextYear}
              disabled={activeYear >= maxYear}
              aria-label="Año siguiente"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition hover:bg-canvas hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>

          {/* Month Grid (3 columns x 4 rows) */}
          <div className="mt-3 grid grid-cols-3 gap-1.5">
            {MONTHS.map((name, index) => {
              const isSelected = activeYear === year && index + 1 === month;
              const shortName = SHORT_MONTHS[index];
              return (
                <button
                  key={name}
                  type="button"
                  onClick={() => handleSelectMonth(index)}
                  title={name}
                  className={`rounded-xl py-2.5 text-xs font-semibold transition ${
                    isSelected
                      ? 'bg-brand text-white shadow-xs'
                      : 'text-ink hover:bg-brand/10 hover:text-brand'
                  }`}
                >
                  {shortName}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

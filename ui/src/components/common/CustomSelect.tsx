import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Check } from 'lucide-react';

export interface SelectOption<T = string | number> {
  value: T;
  label: string;
  icon?: React.ReactNode;
}

interface CustomSelectProps<T extends string | number = string> {
  id?: string;
  value: T;
  onChange: (value: T) => void;
  options: SelectOption<T>[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  menuClassName?: string;
  ariaLabel?: string;
}

export function CustomSelect<T extends string | number = string>({
  id,
  value,
  onChange,
  options,
  placeholder = 'Seleccionar...',
  disabled = false,
  className = '',
  menuClassName = '',
  ariaLabel,
}: CustomSelectProps<T>) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);

  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (e: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
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
  }, [isOpen]);

  return (
    <div ref={containerRef} className="relative w-full">
      {/* Hidden native select for form accessibility and tests */}
      <select
        id={id}
        value={String(value)}
        onChange={(e) => {
          const found = options.find((o) => String(o.value) === e.target.value);
          if (found) onChange(found.value);
        }}
        tabIndex={-1}
        className="sr-only"
      >
        {options.map((opt) => (
          <option key={String(opt.value)} value={String(opt.value)}>
            {opt.label}
          </option>
        ))}
      </select>

      {/* Styled interactive button */}
      <button
        type="button"
        id={id ? `${id}-button` : undefined}
        onClick={() => !disabled && setIsOpen((prev) => !prev)}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-label={ariaLabel || (selectedOption ? selectedOption.label : placeholder)}
        className={`flex w-full items-center justify-between gap-2 rounded-xl border border-line bg-white px-3 py-2 text-sm font-medium text-ink transition-all hover:border-brand/40 focus:outline-none focus:ring-2 focus:ring-brand/20 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      >
        <span className="flex items-center gap-2 truncate">
          {selectedOption?.icon}
          <span className={selectedOption ? 'text-ink' : 'text-muted'}>
            {selectedOption ? selectedOption.label : placeholder}
          </span>
        </span>
        <ChevronDown
          className={`h-4 w-4 shrink-0 text-muted transition-transform duration-200 ${
            isOpen ? 'rotate-180 text-brand' : ''
          }`}
        />
      </button>

      {/* Floating options dropdown */}
      {isOpen && (
        <ul
          role="listbox"
          aria-activedescendant={selectedOption ? `option-${selectedOption.value}` : undefined}
          className={`absolute z-50 mt-1.5 max-h-60 w-full overflow-y-auto rounded-xl border border-line bg-white p-1 shadow-xl focus:outline-none ${menuClassName}`}
        >
          {options.map((opt) => {
            const isSelected = opt.value === value;
            return (
              <li
                key={String(opt.value)}
                id={`option-${opt.value}`}
                role="option"
                aria-selected={isSelected}
                onClick={() => {
                  onChange(opt.value);
                  setIsOpen(false);
                }}
                className={`flex cursor-pointer items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-sm transition ${
                  isSelected
                    ? 'bg-brand/10 font-semibold text-brand'
                    : 'text-ink hover:bg-canvas hover:text-brand'
                }`}
              >
                <span className="flex items-center gap-2 truncate">
                  {opt.icon}
                  <span>{opt.label}</span>
                </span>
                {isSelected && <Check className="h-4 w-4 shrink-0 text-brand" />}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

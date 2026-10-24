import { useId } from 'react';
import { ChevronDown } from 'lucide-react';

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
  id, value, onChange, options, placeholder = 'Seleccionar...',
  disabled = false, className = '', ariaLabel,
}: CustomSelectProps<T>) {
  const generatedId = useId();
  const hasValue = options.some(option => option.value === value);
  return (
    <div className="relative w-full">
      <select
        id={id ?? generatedId}
        disabled={disabled}
        value={hasValue ? String(value) : ''}
        aria-label={ariaLabel || (!id ? placeholder : undefined)}
        onChange={event => {
          const option = options.find(item => String(item.value) === event.target.value);
          if (option) onChange(option.value);
        }}
        className={`block w-full min-w-0 appearance-none rounded-xl border border-line bg-white py-2 pl-3 pr-9 text-sm font-medium text-ink hover:border-brand/40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      >
        {!hasValue && <option value="" disabled>{placeholder}</option>}
        {options.map(option => <option key={String(option.value)} value={String(option.value)}>{option.label}</option>)}
      </select>
      <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden="true" />
    </div>
  );
}

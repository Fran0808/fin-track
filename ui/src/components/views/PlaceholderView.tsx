import { ArrowLeft } from 'lucide-react';
import type { NavView } from '../layout';

interface PlaceholderViewProps {
  view: NavView;
  onNavigateHome: () => void;
}

const VIEW_COPY: Partial<Record<NavView, { title: string; description: string }>> = {
  tarjetas: {
    title: 'Tarjetas y cuentas',
    description: 'Este espacio reunirá los medios vinculados a tus movimientos.',
  },
  presupuestos: {
    title: 'Presupuestos',
    description: 'Aquí podrás planificar límites y comparar tus gastos con ellos.',
  },
};

export function PlaceholderView({ view, onNavigateHome }: PlaceholderViewProps) {
  const copy = VIEW_COPY[view] ?? { title: 'Próximamente', description: 'Estamos preparando este espacio.' };
  return (
    <div className="mx-auto max-w-2xl py-8 sm:py-16">
      <p className="eyebrow">Próximamente</p>
      <h1 className="font-display mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">{copy.title}</h1>
      <p className="mt-3 text-base leading-relaxed text-muted">{copy.description}</p>
      <button type="button" onClick={onNavigateHome} className="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-brand hover:underline">
        <ArrowLeft className="h-4 w-4" /> Volver a Inicio
      </button>
    </div>
  );
}

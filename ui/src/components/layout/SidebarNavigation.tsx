import type { ComponentType } from 'react';
import { ArrowLeftRight, ChartNoAxesCombined, CreditCard, LayoutDashboard, Settings2, Target } from 'lucide-react';

export type NavView = 'inicio' | 'mi-dinero' | 'movimientos' | 'tarjetas' | 'presupuestos' | 'configuracion';

interface SidebarNavigationProps {
  activeView: NavView;
  onViewChange: (view: NavView) => void;
}

interface NavItem {
  id: NavView;
  label: string;
  icon: ComponentType<{ className?: string }>;
  upcoming?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'inicio', label: 'Inicio', icon: LayoutDashboard },
  { id: 'mi-dinero', label: 'Mi dinero', icon: ChartNoAxesCombined },
  { id: 'movimientos', label: 'Movimientos', icon: ArrowLeftRight },
  { id: 'tarjetas', label: 'Tarjetas y cuentas', icon: CreditCard, upcoming: true },
  { id: 'presupuestos', label: 'Presupuestos', icon: Target, upcoming: true },
  { id: 'configuracion', label: 'Configuración', icon: Settings2, upcoming: true },
];

export function SidebarNavigation({ activeView, onViewChange }: SidebarNavigationProps) {
  return (
    <aside className="flex h-full w-60 flex-col border-r border-line bg-white px-4 py-7 sm:w-64">
      <div className="px-3">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand text-lg font-bold text-white" aria-hidden="true">W</span>
          <div>
            <p className="font-display text-lg font-bold leading-tight tracking-tight text-ink">FinTrack</p>
            <p className="text-xs text-muted">Tu dinero en movimiento</p>
          </div>
        </div>
      </div>

      <nav className="mt-12" aria-label="Navegación principal">
        <p className="eyebrow px-3">Principal</p>
        <div className="mt-3 space-y-1">
          {NAV_ITEMS.filter((item) => !item.upcoming).map((item) => {
            const Icon = item.icon;
            const active = activeView === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onViewChange(item.id)}
                aria-current={active ? 'page' : undefined}
                className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-medium transition-colors ${active ? 'bg-brand/8 text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}
              >
                <Icon className="h-[18px] w-[18px] shrink-0" />
                {item.label}
              </button>
            );
          })}
        </div>

        <p className="eyebrow mt-9 px-3">Próximamente</p>
        <div className="mt-3 space-y-1">
          {NAV_ITEMS.filter((item) => item.upcoming).map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onViewChange(item.id)}
                className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors ${activeView === item.id ? 'bg-brand/8 font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}
              >
                <Icon className="h-[18px] w-[18px] shrink-0" />
                {item.label}
              </button>
            );
          })}
        </div>
      </nav>

      <p className="mt-auto px-3 text-xs leading-relaxed text-muted">Un registro de tus entradas y gastos, basado en movimientos confirmados.</p>
    </aside>
  );
}

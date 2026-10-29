import type { ComponentType } from 'react';
import { BrandMark } from '../common/BrandMark';
import { ArrowLeftRight, ChartNoAxesCombined, LayoutDashboard, ListFilter, Settings2, Tags } from 'lucide-react';

export type NavView = 'inicio' | 'mi-dinero' | 'movimientos' | 'reglas' | 'categorias' | 'configuracion';

interface SidebarNavigationProps {
  activeView: NavView;
  onViewChange: (view: NavView) => void;
}

interface NavItem {
  id: NavView;
  label: string;
  icon: ComponentType<{ className?: string }>;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'inicio', label: 'Inicio', icon: LayoutDashboard },
  { id: 'mi-dinero', label: 'Análisis', icon: ChartNoAxesCombined },
  { id: 'movimientos', label: 'Movimientos', icon: ArrowLeftRight },
  { id: 'reglas', label: 'Reglas', icon: ListFilter },
  { id: 'categorias', label: 'Categorías', icon: Tags },
  { id: 'configuracion', label: 'Configuración', icon: Settings2 },
];

export function SidebarNavigation({ activeView, onViewChange }: SidebarNavigationProps) {
  return (
    <aside className="flex h-full w-60 flex-col overflow-y-auto border-r border-line bg-white px-4 py-7 sm:w-64">
      <div className="px-3">
        <div className="flex items-center gap-3">
          <BrandMark />
          <div>
            <p className="font-display text-lg font-bold leading-tight tracking-tight text-ink">FinTrack</p>
          </div>
        </div>
      </div>

      <nav className="mt-12" aria-label="Navegación principal">
        <p className="eyebrow px-3">Principal</p>
        <div className="mt-3 space-y-1">
          {NAV_ITEMS.map((item) => {
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
                <Icon className="h-4.5 w-4.5 shrink-0" />
                {item.label}
              </button>
            );
          })}
        </div>
      </nav>

    </aside>
  );
}

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

interface NavGroup {
  title: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Seguimiento',
    items: [
      { id: 'inicio', label: 'Inicio', icon: LayoutDashboard },
      { id: 'mi-dinero', label: 'Análisis', icon: ChartNoAxesCombined },
      { id: 'movimientos', label: 'Movimientos', icon: ArrowLeftRight },
    ],
  },
  {
    title: 'Gestión',
    items: [
      { id: 'categorias', label: 'Categorías', icon: Tags },
      { id: 'reglas', label: 'Reglas', icon: ListFilter },
      { id: 'configuracion', label: 'Configuración', icon: Settings2 },
    ],
  },
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

      <nav className="mt-10 space-y-7" aria-label="Navegación principal">
        {NAV_GROUPS.map((group) => (
          <div key={group.title}>
            <p className="eyebrow px-3">{group.title}</p>
            <div className="mt-2.5 space-y-1">
              {group.items.map((item) => {
                const Icon = item.icon;
                const active = activeView === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onViewChange(item.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`relative flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors ${
                      active
                        ? 'bg-brand/10 font-semibold text-brand'
                        : 'font-medium text-muted hover:bg-canvas hover:text-ink'
                    }`}
                  >
                    {active && (
                      <span
                        className="absolute left-0 top-2 bottom-2 w-1 rounded-r-full bg-brand"
                        aria-hidden="true"
                      />
                    )}
                    <Icon className="h-4.5 w-4.5 shrink-0" />
                    {item.label}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
}

import { useCallback, useEffect, useState } from 'react';
import type { NavView } from '../components/layout/SidebarNavigation';

const views: NavView[] = ['inicio', 'mi-dinero', 'movimientos', 'pendientes', 'reglas', 'tarjetas', 'categorias', 'configuracion'];

function currentView(): NavView {
  const value = new URLSearchParams(window.location.search).get('vista');
  return views.includes(value as NavView) ? value as NavView : 'inicio';
}

export function useDashboardNavigation() {
  const [activeView, setActiveView] = useState(currentView);
  useEffect(() => {
    const restore = () => setActiveView(currentView());
    window.addEventListener('popstate', restore);
    return () => window.removeEventListener('popstate', restore);
  }, []);
  const navigate = useCallback((view: NavView) => {
    if (currentView() !== view) {
      const url = new URL(window.location.href);
      url.searchParams.set('vista', view);
      window.history.pushState({}, '', url);
    }
    setActiveView(view);
  }, []);
  return [activeView, navigate] as const;
}

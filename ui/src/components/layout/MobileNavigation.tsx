import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { SidebarNavigation } from './SidebarNavigation';
import type { NavView } from './SidebarNavigation';

export function MobileNavigation({ open, onClose, activeView, onViewChange }: {
  open: boolean;
  onClose: () => void;
  activeView: NavView;
  onViewChange: (view: NavView) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    if (open && !element?.open) element?.showModal();
    if (!open && element?.open) element.close();
  }, [open]);
  useEffect(() => {
    if (!window.matchMedia) return;
    const desktop = window.matchMedia('(min-width: 1024px)');
    const handleResize = () => { if (desktop.matches) onClose(); };
    desktop.addEventListener('change', handleResize);
    return () => desktop.removeEventListener('change', handleResize);
  }, [onClose]);
  return (
    <dialog ref={dialog} aria-label="Menú principal" onClose={onClose}
      onClick={event => { if (event.target === event.currentTarget) onClose(); }}
      className="m-0 h-dvh max-h-dvh w-72 max-w-[90vw] border-0 bg-white p-0 text-ink backdrop:bg-ink/40 lg:hidden">
      <div className="flex h-full min-h-0 flex-col">
        <button type="button" onClick={onClose} aria-label="Cerrar menú" className="m-3 ml-auto rounded-lg p-2 text-muted"><X className="h-5 w-5" /></button>
        <div className="min-h-0 flex-1">
          <SidebarNavigation activeView={activeView} onViewChange={view => { onViewChange(view); onClose(); }} />
        </div>
      </div>
    </dialog>
  );
}

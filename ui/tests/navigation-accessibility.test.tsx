// @vitest-environment jsdom
import { useState } from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CustomSelect } from '../src/components/common/CustomSelect';
import { MobileNavigation } from '../src/components/layout/MobileNavigation';
import { SidebarNavigation } from '../src/components/layout/SidebarNavigation';
import { useDashboardNavigation } from '../src/hooks/useDashboardNavigation';

beforeEach(() => {
  window.history.replaceState({}, '', '/');
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new Event('close')); };
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('provides a visible labeled native select with numeric values and normal keyboard focus', () => {
  const changed = vi.fn();
  render(<><label htmlFor="product">Producto</label><CustomSelect id="product" value={1} onChange={changed} options={[{ value: 1, label: 'First' }, { value: 2, label: 'Second' }]} /></>);
  const select = screen.getByRole('combobox', { name: 'Producto' }) as HTMLSelectElement;
  expect(select.tagName).toBe('SELECT');
  expect(select.tabIndex).toBe(0);
  select.focus();
  expect(document.activeElement).toBe(select);
  fireEvent.change(select, { target: { value: '2' } });
  expect(changed).toHaveBeenCalledWith(2);
  expect(screen.queryByRole('button')).toBeNull();
});

it('restores a deep link on mount and follows browser back and forward', async () => {
  window.history.replaceState({}, '', '/?vista=reglas');
  const { result } = renderHook(() => useDashboardNavigation());
  expect(result.current[0]).toBe('reglas');
  act(() => result.current[1]('movimientos'));
  expect(new URLSearchParams(window.location.search).get('vista')).toBe('movimientos');
  act(() => window.history.back());
  await waitFor(() => expect(result.current[0]).toBe('reglas'));
  act(() => window.history.forward());
  await waitFor(() => expect(result.current[0]).toBe('movimientos'));
});

it('opens Home for a retired budget link and keeps the remaining navigation usable', () => {
  window.history.replaceState({}, '', '/?vista=presupuestos');
  function Navigation() {
    const [view, navigate] = useDashboardNavigation();
    return <SidebarNavigation activeView={view} onViewChange={navigate} />;
  }
  render(<Navigation />);
  expect(screen.getByRole('button', { name: 'Inicio' }).getAttribute('aria-current')).toBe('page');
  expect(screen.queryByRole('button', { name: 'Presupuestos' })).toBeNull();
  expect(screen.queryByText('Próximamente')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Configuración' }));
  expect(screen.getByRole('button', { name: 'Configuración' }).getAttribute('aria-current')).toBe('page');
  expect(new URLSearchParams(window.location.search).get('vista')).toBe('configuracion');
});

it('opens mobile navigation as a native modal and closes it after navigation', async () => {
  const navigate = vi.fn();
  function Menu() {
    const [open, setOpen] = useState(false);
    return <><button onClick={() => setOpen(true)}>Open</button><MobileNavigation open={open} onClose={() => setOpen(false)} activeView="inicio" onViewChange={navigate} /></>;
  }
  render(<Menu />);
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.queryByRole('navigation')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Open' }));
  expect(screen.getByRole('dialog', { name: 'Menú principal' })).toHaveProperty('open', true);
  fireEvent.click(screen.getByRole('button', { name: 'Movimientos' }));
  expect(navigate).toHaveBeenCalledWith('movimientos');
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
});

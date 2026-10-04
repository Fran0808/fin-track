// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TransactionDetailModal } from '../src/components/transactions/TransactionDetailModal';
import { TransactionFilters } from '../src/components/transactions/TransactionFilters';
import { TransactionTable } from '../src/components/transactions/TransactionTable';
import { api } from '../src/services/api';
import type { PageResponse, Transaction } from '../src/types';

const sampleTx: Transaction = {
  id: 10,
  amount: 45.0,
  flowType: 'EXPENSE',
  contactName: 'Supermercado Metro',
  channel: 'TARJETA_DEBITO_BCP',
  cardLast4: '5566',
  category: 'ALIMENTACION',
  tags: ['super', 'almuerzo'],
  notes: 'Compras de despensa',
  transactionDate: '2026-10-04T12:00:00',
  transactionHash: 'hash-sample-10',
  createdAt: '2026-10-04T12:00:00',
};

beforeEach(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('renders category and tags in TransactionTable and shows dynamic summary', () => {
  const pageData: PageResponse<Transaction> = {
    content: [sampleTx],
    totalElements: 1,
    totalPages: 1,
    size: 10,
    number: 0,
    first: true,
    last: true,
    empty: false,
  };

  render(
    <TransactionTable
      pageData={pageData}
      loading={false}
      periodName="10/2026"
      onPageChange={vi.fn()}
      onSelectTransaction={vi.fn()}
    />
  );

  expect(screen.getByText('Supermercado Metro')).toBeDefined();
  expect(screen.getByText('Alimentación y bebidas')).toBeDefined();
  expect(screen.getByText('#super')).toBeDefined();
  expect(screen.getByText('#almuerzo')).toBeDefined();
  expect(screen.getByText('1 movimientos encontrados')).toBeDefined();
});

it('allows editing category, adding tags, and saving classification in TransactionDetailModal', async () => {
  const updateSpy = vi.spyOn(api, 'updateTransactionClassification').mockResolvedValue({
    ...sampleTx,
    category: 'COMPRAS',
    tags: ['super', 'almuerzo', 'despensa'],
    notes: 'Nueva nota actualizada',
  });

  const onUpdated = vi.fn();
  render(<TransactionDetailModal transaction={sampleTx} onClose={vi.fn()} onUpdated={onUpdated} />);

  // Change category
  const select = screen.getByLabelText('Categoría') as HTMLSelectElement;
  fireEvent.change(select, { target: { value: 'COMPRAS' } });

  // Add tag
  const tagInput = screen.getByPlaceholderText('ej. almuerzo, trabajo, viaje');
  fireEvent.change(tagInput, { target: { value: '#despensa' } });
  fireEvent.click(screen.getByRole('button', { name: /Agregar/ }));

  expect(screen.getByText('#despensa')).toBeDefined();

  // Change notes
  const notesArea = screen.getByPlaceholderText('Añade un apunte o recordatorio sobre este movimiento...');
  fireEvent.change(notesArea, { target: { value: 'Nueva nota actualizada' } });

  // Save
  fireEvent.click(screen.getByRole('button', { name: 'Guardar clasificación' }));

  await waitFor(() => {
    expect(updateSpy).toHaveBeenCalledWith(10, {
      category: 'COMPRAS',
      tags: ['super', 'almuerzo', 'despensa'],
      notes: 'Nueva nota actualizada',
    });
  });

  await screen.findByText('Cambios guardados');
  expect(onUpdated).toHaveBeenCalled();
});

it('toggles advanced filters panel and emits multi-criteria filter updates', () => {
  const onFilterChange = vi.fn();
  const onReset = vi.fn();

  render(
    <TransactionFilters
      search=""
      flowType=""
      onFilterChange={onFilterChange}
      onReset={onReset}
    />
  );

  // Click on "Filtros avanzados"
  fireEvent.click(screen.getByRole('button', { name: /Filtros avanzados/ }));

  // Category select is now visible
  const catSelect = screen.getByLabelText('Categoría');
  fireEvent.change(catSelect, { target: { value: 'TRANSPORTE' } });
  expect(onFilterChange).toHaveBeenCalledWith({ category: 'TRANSPORTE' });

  // Channel select
  const channelSelect = screen.getByLabelText('Medio de pago');
  fireEvent.change(channelSelect, { target: { value: 'YAPE' } });
  expect(onFilterChange).toHaveBeenCalledWith({ channel: 'YAPE' });

  // Min amount
  const minInput = screen.getByPlaceholderText('Mín.');
  fireEvent.change(minInput, { target: { value: '25' } });
  expect(onFilterChange).toHaveBeenCalledWith({ minAmount: 25 });
});

// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { CategoriesView } from '../src/components/views/CategoriesView';
import { api } from '../src/services/api';
import type { Category } from '../src/types';

vi.mock('../src/services/api', () => ({
  api: {
    getCategories: vi.fn(),
    createCategory: vi.fn(),
    updateCategory: vi.fn(),
    toggleCategory: vi.fn(),
    deleteCategory: vi.fn(),
  },
}));

const mockCategories: Category[] = [
  {
    id: 1,
    name: 'Ahorro e inversión',
    icon: 'piggy-bank',
    color: '#10B981',
    system: true,
    active: true,
  },
  {
    id: 2,
    name: 'Comida y bebidas',
    icon: 'utensils',
    color: '#F97316',
    system: true,
    active: true,
  },
  {
    id: 3,
    name: 'Delivery',
    icon: 'bike',
    color: '#FB923C',
    system: true,
    active: false,
  },
  {
    id: 10,
    name: 'Freelance & Consultoría',
    icon: 'briefcase',
    color: '#3B82F6',
    system: false,
    active: true,
    subcategories: [
      {
        id: 11,
        name: 'Diseño Web',
        icon: 'laptop',
        color: '#3B82F6',
        system: false,
        active: true,
        parentId: 10,
      },
    ],
  },
];

describe('CategoriesView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getCategories).mockResolvedValue(mockCategories);
  });

  afterEach(() => {
    cleanup();
  });

  it('renders header, user categories, and system categories columns', async () => {
    render(<CategoriesView />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'Categorías' })).toBeDefined();
    });

    expect(screen.getByRole('heading', { level: 2, name: 'Mis Categorías' })).toBeDefined();
    expect(screen.getByRole('heading', { level: 2, name: 'Categorías del Sistema' })).toBeDefined();

    // Check custom category
    expect(screen.getByText('Freelance & Consultoría')).toBeDefined();

    // Check system categories
    expect(screen.getByText('Ahorro e inversión')).toBeDefined();
    expect(screen.getByText('Comida y bebidas')).toBeDefined();
  });

  it('filters categories via search input in real time', async () => {
    render(<CategoriesView />);

    await waitFor(() => {
      expect(screen.getByText('Comida y bebidas')).toBeDefined();
    });

    const searchInput = screen.getByPlaceholderText('Buscar categoría...');
    fireEvent.change(searchInput, { target: { value: 'Comida' } });

    expect(screen.getByText('Comida y bebidas')).toBeDefined();
    expect(screen.queryByText('Ahorro e inversión')).toBeNull();
    expect(screen.queryByText('Freelance & Consultoría')).toBeNull();
  });

  it('toggles category active state optimistically and calls api.toggleCategory', async () => {
    vi.mocked(api.toggleCategory).mockResolvedValue({
      id: 2,
      name: 'Comida y bebidas',
      system: true,
      active: false,
    });

    render(<CategoriesView />);

    await waitFor(() => {
      expect(screen.getByText('Comida y bebidas')).toBeDefined();
    });

    const toggleBtn = screen.getByLabelText('Alternar categoría del sistema Comida y bebidas');
    expect(toggleBtn.getAttribute('aria-checked')).toBe('true');

    fireEvent.click(toggleBtn);

    expect(vi.mocked(api.toggleCategory)).toHaveBeenCalledWith(2);
    expect(toggleBtn.getAttribute('aria-checked')).toBe('false');
  });

  it('opens modal to create a new category and calls api.createCategory on submit', async () => {
    vi.mocked(api.createCategory).mockResolvedValue({
      id: 50,
      name: 'Gimnasio & Salud',
      icon: 'dumbbell',
      color: '#10B981',
      system: false,
      active: true,
    });

    render(<CategoriesView />);

    await waitFor(() => {
      expect(screen.getByText('Nueva categoría')).toBeDefined();
    });

    fireEvent.click(screen.getByRole('button', { name: /Nueva categoría/i }));

    expect(screen.getByRole('dialog')).toBeDefined();
    expect(screen.getByLabelText('Nombre de la categoría')).toBeDefined();

    fireEvent.change(screen.getByLabelText('Nombre de la categoría'), {
      target: { value: 'Gimnasio & Salud' },
    });

    fireEvent.click(screen.getByRole('button', { name: /Crear categoría/i }));

    await waitFor(() => {
      expect(vi.mocked(api.createCategory)).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Gimnasio & Salud',
        })
      );
    });
  });

  it('allows deleting a custom category after confirmation', async () => {
    vi.mocked(api.deleteCategory).mockResolvedValue();

    render(<CategoriesView />);

    await waitFor(() => {
      expect(screen.getByText('Freelance & Consultoría')).toBeDefined();
    });

    const deleteBtn = screen.getByLabelText('Eliminar categoría Freelance & Consultoría');
    fireEvent.click(deleteBtn);

    expect(screen.getByRole('alertdialog')).toBeDefined();
    expect(screen.getByText(/¿Eliminar categoría?/i)).toBeDefined();

    const confirmBtn = screen.getByRole('button', { name: 'Eliminar' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(vi.mocked(api.deleteCategory)).toHaveBeenCalledWith(10);
    });
  });
});

import { useCallback } from 'react';
import type { Category } from '../types';
import { api } from '../services/api';
import { useQueryResource } from './useQueryResource';

export interface CategoryOption {
  id: string;
  label: string;
}

export function useCategories(activeOnly: boolean = true) {
  const query = useCallback(() => api.getCategories(activeOnly ? true : undefined), [activeOnly]);
  const { data, loading, error, refresh } = useQueryResource<Category[]>(query, 'No se pudieron cargar las categorías.');
  const categories = data ?? [];

  // Active options for dropdowns
  const options: CategoryOption[] = categories.map((cat) => ({ id: cat.name, label: cat.name }));

  return {
    categories,
    options,
    loading,
    error,
    refresh,
  };
}

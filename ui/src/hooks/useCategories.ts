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

  // Flattened active options for dropdowns
  const options: CategoryOption[] = categories.flatMap((cat) => {
    const items: CategoryOption[] = [{ id: cat.name, label: cat.name }];
    for (const sub of cat.subcategories ?? []) {
      if (!activeOnly || sub.active) items.push({ id: sub.name, label: `${cat.name} > ${sub.name}` });
    }
    return items;
  });

  return {
    categories,
    options,
    loading,
    error,
    refresh,
  };
}

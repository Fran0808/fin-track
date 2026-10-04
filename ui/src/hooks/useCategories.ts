import { useState, useEffect, useCallback } from 'react';
import type { Category } from '../types';
import { STANDARD_CATEGORIES } from '../types';
import { api } from '../services/api';

export interface CategoryOption {
  id: string;
  label: string;
}

export function useCategories(activeOnly: boolean = true) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.getCategories(activeOnly ? true : undefined);
      setCategories(data);
    } catch {
      // Fallback silently if offline or unauthenticated
      setCategories([]);
    } finally {
      setLoading(false);
    }
  }, [activeOnly]);

  useEffect(() => {
    void fetchCategories();
  }, [fetchCategories]);

  // Flattened active options for dropdowns
  const options: CategoryOption[] = categories.length > 0
    ? categories.flatMap((cat) => {
        const items: CategoryOption[] = [{ id: cat.name, label: cat.name }];
        if (cat.subcategories) {
          cat.subcategories.forEach((sub) => {
            items.push({ id: sub.name, label: `${cat.name} > ${sub.name}` });
          });
        }
        return items;
      })
    : STANDARD_CATEGORIES.map((cat) => ({ id: cat.id, label: cat.label }));

  return {
    categories,
    options,
    loading,
    refresh: fetchCategories,
  };
}

import { STANDARD_CATEGORIES } from '../types';

export const CATEGORY_PALETTE: Record<string, string> = {
  ALIMENTACION: '#0d9488', // Teal
  TRANSPORTE: '#0284c7',   // Sky blue
  SERVICIOS: '#6366f1',    // Indigo
  SALIDAS: '#8b5cf6',      // Violet
  EDUCACION: '#f59e0b',    // Amber
  SALUD: '#e11d48',        // Rose
  COMPRAS: '#ec4899',      // Pink
  FINANZAS: '#2c4f99',     // Brand navy
  OTROS: '#64748b',        // Slate
};

export const FALLBACK_CATEGORY_COLORS = [
  '#2c4f99',
  '#0d9488',
  '#7754a8',
  '#f59e0b',
  '#0284c7',
  '#e11d48',
  '#10b981',
  '#ec4899',
  '#64748b',
];

export function getCategoryLabel(category: string | null): string {
  if (!category) return 'Sin categoría';
  return STANDARD_CATEGORIES.find((c) => c.id === category)?.label ?? category;
}

export function getCategoryColor(category: string | null, index: number = 0): string {
  if (category === null) return '#94a3b8'; // Slate para Sin categoría
  if (CATEGORY_PALETTE[category]) return CATEGORY_PALETTE[category];
  return FALLBACK_CATEGORY_COLORS[index % FALLBACK_CATEGORY_COLORS.length];
}

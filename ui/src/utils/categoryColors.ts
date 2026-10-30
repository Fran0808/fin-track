import { STANDARD_CATEGORIES } from '../types';

export const CATEGORY_PALETTE: Record<string, string> = {
  ALIMENTACION: '#0d9488', // Teal
  'ALIMENTACIÓN Y BEBIDAS': '#0d9488',
  'COMIDA Y BEBIDAS': '#0d9488',
  TRANSPORTE: '#0284c7',   // Sky blue
  'TRANSPORTE Y MOVILIDAD': '#0284c7',
  SERVICIOS: '#6366f1',    // Indigo
  'SERVICIOS Y SUSCRIPCIONES': '#6366f1',
  SALIDAS: '#8b5cf6',      // Violet
  'SALIDAS Y ENTRETENIMIENTO': '#8b5cf6',
  ENTRETENIMIENTO: '#8b5cf6',
  EDUCACION: '#f59e0b',    // Amber
  EDUCACIÓN: '#f59e0b',
  SALUD: '#e11d48',        // Rose
  'SALUD Y FARMACIA': '#e11d48',
  COMPRAS: '#ec4899',      // Pink
  'COMPRAS Y HOGAR': '#ec4899',
  HOGAR: '#ec4899',
  FINANZAS: '#2c4f99',     // Brand navy
  'TRANSFERENCIAS Y FINANZAS': '#2c4f99',
  OTROS: '#64748b',        // Slate
  'OTROS GASTOS': '#64748b',
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

export function getCategoryLabel(category: string | null | undefined): string {
  if (!category || !category.trim()) return 'Sin categoría';
  const trimmed = category.trim();
  return STANDARD_CATEGORIES.find((c) => c.id === trimmed)?.label ?? trimmed;
}

function hashCategoryName(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  return hash;
}

export function getCategoryColor(category: string | null | undefined, index?: number): string {
  if (!category || !category.trim()) return '#94a3b8'; // Slate para Sin categoría
  const normalized = category.trim().toUpperCase();
  if (CATEGORY_PALETTE[normalized]) return CATEGORY_PALETTE[normalized];
  const fallbackIdx = index !== undefined ? index : hashCategoryName(normalized);
  return FALLBACK_CATEGORY_COLORS[fallbackIdx % FALLBACK_CATEGORY_COLORS.length];
}

export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('es-PE', {
    style: 'currency',
    currency: 'PEN',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDate(isoString: string): string {
  if (!isoString) return 'Fecha no disponible';
  try {
    const date = new Date(isoString);
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }).format(date);
  } catch {
    return isoString;
  }
}

export function formatRelativeDate(isoString: string): string {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    const now = new Date();
    if (Number.isNaN(date.getTime())) return isoString;
    // Compare local calendar dates without daylight-saving or time-of-day offsets.
    const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
    const transactionDay = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
    const diffDays = (today - transactionDay) / (1000 * 60 * 60 * 24);
    if (diffDays === 0) return 'Hoy';
    if (diffDays === 1) return 'Ayer';
    if (diffDays > 1 && diffDays < 7) return `Hace ${diffDays} días`;
    return formatDate(isoString);
  } catch {
    return isoString;
  }
}

export function formatTransactionDateParts(isoString: string): { date: string; time: string } {
  const value = new Date(isoString);
  if (!isoString || Number.isNaN(value.getTime())) return { date: isoString || 'Fecha no disponible', time: '' };
  return {
    date: new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(value),
    time: new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: true }).format(value),
  };
}

export function getChannelLabel(channel: string): string {
  switch (channel) {
    case 'CASH': return 'Efectivo';
    case 'CARD': return 'Tarjeta';
    case 'BANK_TRANSFER': return 'Transferencia';
    case 'WALLET': return 'Billetera';
    case 'OTHER': return 'Otro';
    case 'TARJETA_CREDITO_BCP':
      return 'Crédito BCP';
    case 'TARJETA_DEBITO_BCP':
      return 'Débito BCP';
    case 'YAPE':
      return 'Yape';
    case 'BCP_TRANSFERENCIA':
      return 'Transferencia BCP';
    case 'PLIN':
      return 'Plin';
    default:
      return channel.replace(/_/g, ' ');
  }
}

const MONTH_NAMES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre',
];

export interface ExportFilenameFilters {
  merchantName?: string;
  categoryGroup?: string;
  flowType?: string;
  allHistory?: boolean;
  startDate?: string;
  endDate?: string;
}

export function formatExportFilename(
  filters: ExportFilenameFilters = {},
  periodName?: string,
  now = new Date()
): string {
  const parts: string[] = ['fintrack_movimientos'];

  if (filters.merchantName) {
    const slug = filters.merchantName
      .trim()
      .toLowerCase()
      .replace(/[^\w-]/g, '_')
      .replace(/_+/g, '_')
      .slice(0, 30);
    if (slug) parts.push(slug);
  }

  if (filters.categoryGroup) {
    const slug = filters.categoryGroup
      .trim()
      .toLowerCase()
      .replace(/[^\w-]/g, '_')
      .replace(/_+/g, '_')
      .slice(0, 25);
    if (slug) parts.push(slug);
  }

  if (filters.flowType) {
    if (filters.flowType === 'EXPENSE') parts.push('gastos');
    else if (filters.flowType === 'INCOME') parts.push('entradas');
    else if (filters.flowType === 'INTERNAL_TRANSFER') parts.push('transferencias');
  }

  if (filters.allHistory) {
    parts.push('historico');
    parts.push(now.toISOString().slice(0, 10));
  } else if (filters.startDate && filters.endDate) {
    const start = filters.startDate.slice(0, 10);
    const end = filters.endDate.slice(0, 10);
    if (start === end) {
      parts.push(start);
    } else {
      parts.push(`${start}_al_${end}`);
    }
  } else if (periodName) {
    const match = periodName.match(/^(\d{1,2})\/(\d{4})$/);
    if (match) {
      const monthNum = Number(match[1]);
      const year = match[2];
      const monthName = MONTH_NAMES[monthNum - 1] ?? match[1].padStart(2, '0');
      parts.push(`${monthName}_${year}`);
    } else {
      parts.push(now.toISOString().slice(0, 10));
    }
  } else {
    parts.push(now.toISOString().slice(0, 10));
  }

  return `${parts.join('_')}.csv`;
}

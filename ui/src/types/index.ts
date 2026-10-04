export type FlowType = 'INCOME' | 'EXPENSE' | 'INTERNAL_TRANSFER';

export type ChannelType =
  | 'TARJETA_CREDITO_BCP'
  | 'TARJETA_DEBITO_BCP'
  | 'YAPE'
  | 'BCP_TRANSFERENCIA'
  | 'PLIN'
  | 'UNKNOWN';

export interface Transaction {
  id: number;
  financialInstrument?: FinancialInstrument | null;
  amount: number;
  flowType: FlowType;
  contactName: string;
  channel: ChannelType | string;
  cardLast4?: string;
  category?: string | null;
  tags?: string[] | null;
  notes?: string | null;
  transactionDate: string;
  transactionHash: string;
  createdAt: string;
}

export interface TransactionClassificationRequest {
  category?: string | null;
  tags?: string[] | null;
  notes?: string | null;
}

export const STANDARD_CATEGORIES = [
  { id: 'ALIMENTACION', label: 'Alimentación y bebidas' },
  { id: 'TRANSPORTE', label: 'Transporte y movilidad' },
  { id: 'SERVICIOS', label: 'Servicios y suscripciones' },
  { id: 'SALIDAS', label: 'Salidas y entretenimiento' },
  { id: 'EDUCACION', label: 'Educación' },
  { id: 'SALUD', label: 'Salud y farmacia' },
  { id: 'COMPRAS', label: 'Compras y hogar' },
  { id: 'FINANZAS', label: 'Transferencias y finanzas' },
  { id: 'OTROS', label: 'Otros gastos' },
] as const;

export type InstrumentType = 'BANK_ACCOUNT' | 'DEBIT_CARD' | 'CREDIT_CARD';
export type Bank = 'BCP' | 'INTERBANK' | 'BBVA' | 'OTHER';

export interface FinancialInstrument {
  id: number;
  type: InstrumentType;
  alias: string;
  bank: Bank;
  institutionName?: string | null;
  lastFour?: string | null;
  currency: 'PEN';
  active: boolean;
  linkedAccountId?: number | null;
}

export interface FinancialInstrumentRequest {
  type: InstrumentType;
  alias: string;
  bank: Bank;
  institutionName: string | null;
  lastFour: string | null;
  active: boolean;
  linkedAccountId: number | null;
}

export interface FinancialSummary {
  netBalance: number;
  totalExpense: number;
  totalIncome: number;
  totalTransactions: number;
}

export interface ChannelBreakdown {
  channel: string;
  cardLast4?: string;
  displayName?: string;
  amount: number;
  percentage: number;
  count: number;
}

export interface TopMerchant {
  merchantName: string;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
}

export interface PeriodAnalytics {
  periodName: string;
  monthlyExpense: number;
  monthlyIncome: number;
  internalTransfersAmount: number;
  previousPeriodExpense: number | null;
  comparisonThroughDay: number | null;
  dailyExpenses: DailyExpense[];
  totalMovements: number;
  lastExpenseMerchant: string;
  lastExpenseAmount: number;
  lastExpenseDate: string | null;
  topChannel: string;
  topChannelAmount: number;
  topChannelPercentage: number;
  channelBreakdown: ChannelBreakdown[];
  topMerchants: TopMerchant[];
}

export interface DailyExpense {
  day: number;
  amount: number;
  cumulativeAmount: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface EmailSyncResponse {
  status: string;
  scannedCount: number;
  processedInBatch: number;
  savedCount: number;
  transactions: Transaction[];
  message: string;
}

export interface EmailConnectionStatus {
  status: string;
  connectedUser: string;
  totalInboxMessages: number;
  unreadMessages: number;
  matchedBcpEmailsCount: number;
  message?: string;
}

export interface GoogleAuthStatus {
  connected: boolean;
  email: string | null;
  lastSuccessfulSyncAt: string | null;
  lastSyncFailed: boolean;
}

export interface UserProfile {
  id: number;
  email: string;
  fullName: string | null;
  pictureUrl: string | null;
  createdAt: string;
  lastLoginAt: string;
}

export interface DevicePairingInfo {
  userId: number;
  userEmail: string;
  pairingToken: string;
  serverUrl: string;
  qrPayload: string;
}



export type FlowType = 'INCOME' | 'EXPENSE' | 'INTERNAL_TRANSFER';
export type OperationType = 'UNKNOWN' | 'OWN_CARD_PAYMENT';

export interface TransactionOrigin {
  origin: 'EMAIL' | 'PHONE' | 'MANUAL' | 'UNKNOWN';
  registeredAt: string | null;
  operationReference: string | null;
}

export type ChannelType =
  | 'TARJETA_CREDITO_BCP'
  | 'TARJETA_DEBITO_BCP'
  | 'YAPE'
  | 'BCP_TRANSFERENCIA'
  | 'PLIN'
  | 'UNKNOWN';

export interface Transaction {
  id: number;
  captureOrigin?: TransactionOrigin['origin'];
  reconciledToId?: number | null;
  amount: number;
  flowType: FlowType;
  operationType?: OperationType | null;
  contactName: string;
  channel: ChannelType | string;
  cardLast4?: string;
  category?: string | null;
  transactionDate: string;
  transactionHash: string;
  createdAt: string;
}

export interface ManualTransactionRequest {
  requestId: string;
  amount: string;
  flowType: 'EXPENSE' | 'INCOME';
  contactName: string;
  transactionDate: string;
  paymentMethod: 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'WALLET' | 'OTHER';
  categoryId: number | null;
}

export interface TransactionCorrectionRequest {
  requestId: string;
  expectedRevision: number;
  amount: string;
  transactionDate: string;
  flowType: FlowType;
  reason: string;
}

export interface TransactionCorrectionEntry {
  id: number;
  reason: string;
  beforeAmount: number;
  afterAmount: number;
  beforeDate: string;
  afterDate: string;
  beforeFlow: FlowType;
  afterFlow: FlowType;
  beforeOperation: OperationType;
  afterOperation: OperationType;
  createdAt: string;
}

export interface TransactionCorrectionHistory {
  transaction: Transaction;
  revision: number;
  editable: boolean;
  entries: TransactionCorrectionEntry[];
  totalPages: number;
}

export interface ManualReconciliationPreview {
  manual: Transaction;
  candidates: { transaction: Transaction; previewToken: string }[];
  limit: number;
  hasMore: boolean;
  expiresAt: number;
}

export interface ManualReconciliationStatus {
  revision: number;
  manual: Transaction | null;
  captured: Transaction | null;
  reconciledAt: string | null;
}

export interface TransactionClassificationRequest {
  category?: string | null;
}

export type PendingReason = 'ALL' | 'CATEGORY';

export interface ClassificationRuleDraft {
  matchText: string;
  categoryName: string;
}

export interface ClassificationRuleRequest {
  matchText: string;
  categoryId: number;
  priority: number;
  enabled: boolean;
}

export interface ClassificationRule extends ClassificationRuleRequest {
  id: number;
  categoryName: string;
  categoryActive: boolean;
}

export interface RuleHistoryPreview {
  ruleId: number;
  matchText: string;
  categoryName: string;
  transactions: Transaction[];
  limit: number;
  hasMore: boolean;
  expiresAt: number;
  previewToken: string;
}

export interface RuleHistoryApplyResponse {
  appliedCount: number;
  skippedCount: number;
}

export interface Category {
  id: number;
  name: string;
  icon?: string;
  color?: string;
  system: boolean;
  active: boolean;
  parentId?: number | null;
  subcategories?: Category[];
}

export interface CategoryRequest {
  name: string;
  icon?: string;
  color?: string;
  parentId?: number | null;
  active?: boolean;
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
  largestExpense?: { merchantName: string; amount: number; transactionDate: string } | null;
  peakExpenseDay?: { date: string; amount: number } | null;
  previousPeriodStart?: string | null;
  previousPeriodEnd?: string | null;
  expenseDifference?: number | null;
  periodStart?: string;
  periodEnd?: string;
  categoryBreakdown: CategoryBreakdown[];
  periodName: string;
  monthlyExpense: number;
  monthlyIncome: number;
  internalTransfersAmount: number;
  previousPeriodExpense: number | null;
  previousPeriodIncome?: number | null;
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

export interface CategoryBreakdown {
  category: string | null;
  amount: number;
  percentage: number;
  count: number;
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

export interface TransactionFilterSummary {
  totalExpense: number;
  totalIncome: number;
  internalTransfersAmount: number;
}

export interface TransactionPageResponse extends PageResponse<Transaction> {
  summary?: TransactionFilterSummary | null;
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

export interface PhoneReceptionStatus {
  lastReceivedAt: string | null;
}

export interface GoogleAuthStatus {
  connected: boolean;
  email: string | null;
  lastSuccessfulSyncAt: string | null;
  lastSyncFailed: boolean;
  lastSyncAttemptAt?: string | null;
  lastSyncFailureCode?: string | null;
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

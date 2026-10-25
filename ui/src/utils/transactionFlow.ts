import type { Transaction } from '../types';

export function isOwnCardPayment(transaction: Transaction): boolean {
  return transaction.operationType === 'OWN_CARD_PAYMENT' && transaction.flowType === 'INTERNAL_TRANSFER';
}

export function canConfirmOwnCardPayment(transaction: Transaction): boolean {
  return transaction.operationType === 'UNKNOWN'
    && (transaction.flowType === 'EXPENSE' || transaction.flowType === 'INTERNAL_TRANSFER');
}

export function getTransactionFlowLabel(transaction: Transaction): string {
  if (transaction.flowType === 'INCOME') return 'Entrada';
  if (transaction.flowType === 'INTERNAL_TRANSFER') {
    return isOwnCardPayment(transaction) ? 'Pago de tarjeta propia' : 'Transferencia propia';
  }
  return 'Gasto';
}

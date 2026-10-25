import type { Transaction } from '../types';

export function isBcpOwnCardPayment(transaction: Transaction): boolean {
  return transaction.contactName === 'Pago Tarjeta Crédito BCP'
    && ['TARJETA_CREDITO_BCP', 'TARJETA_DEBITO_BCP', 'BCP_TRANSFERENCIA', 'YAPE'].includes(transaction.channel);
}

export function getTransactionFlowLabel(transaction: Transaction): string {
  if (transaction.flowType === 'INCOME') return 'Entrada';
  if (transaction.flowType === 'INTERNAL_TRANSFER') {
    return isBcpOwnCardPayment(transaction) ? 'Pago de tarjeta propia' : 'Transferencia propia';
  }
  return 'Gasto';
}

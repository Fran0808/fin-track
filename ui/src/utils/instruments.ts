import type { FinancialInstrument, FinancialInstrumentRequest, InstrumentType } from '../types';

export const instrumentLabels: Record<InstrumentType, string> = {
  BANK_ACCOUNT: 'Cuenta bancaria', DEBIT_CARD: 'Tarjeta de débito', CREDIT_CARD: 'Tarjeta de crédito',
};

export function instrumentBank(item: Pick<FinancialInstrument, 'bank' | 'institutionName'>) {
  return item.bank === 'OTHER' ? item.institutionName || 'Otro' : item.bank === 'INTERBANK' ? 'Interbank' : item.bank;
}

export function instrumentRequest(item: FinancialInstrument): FinancialInstrumentRequest {
  return {
    type: item.type, alias: item.alias, bank: item.bank, institutionName: item.institutionName || null,
    lastFour: item.lastFour || null, active: item.active, linkedAccountId: item.linkedAccountId || null,
  };
}

import type {
  FinancialSummary,
  PeriodAnalytics,
  Transaction,
  TransactionCorrectionRequest,
  TransactionCorrectionHistory,
  TransactionOrigin,
  ManualTransactionRequest,
  ManualReconciliationPreview,
  ManualReconciliationStatus,
  TransactionClassificationRequest,
  TransactionPageResponse,
  EmailSyncResponse,
  EmailConnectionStatus,
  GoogleAuthStatus,
  PhoneReceptionStatus,
  UserProfile,
  FlowType,
  DevicePairingInfo,
  Category,
  CategoryRequest,
  PendingReason,
  ClassificationRule,
  ClassificationRuleRequest,
  RuleHistoryPreview,
  RuleHistoryApplyResponse,
} from '../types';

const BASE_URL = '/api/v1';

async function fetchWithAuth(url: string, init?: RequestInit): Promise<Response> {
  const token = localStorage.getItem('auth_token');
  const headers = new Headers(init?.headers || {});

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  // A delayed response must not invalidate a newer session or an OAuth callback.
  if (response.status === 401 && token && localStorage.getItem('auth_token') === token) {
    localStorage.removeItem('auth_token');
    window.dispatchEvent(new CustomEvent('auth:unauthorized'));
  }

  return response;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let errorMessage = `HTTP error ${response.status}: ${response.statusText}`;
    try {
      const errorJson = await response.json();
      if (errorJson.message) {
        errorMessage = errorJson.message;
      }
    } catch {
      // Keep default message if not JSON
    }
    throw new Error(errorMessage);
  }
  return response.json();
}

export interface TransactionFilterParams {
  categoryGroup?: string;
  merchantName?: string;
  page?: number;
  size?: number;
  flowType?: FlowType | '';
  search?: string;
  startDate?: string;
  endDate?: string;
  category?: string;
  channel?: string;
  minAmount?: number;
  maxAmount?: number;
  pendingReason?: PendingReason | '';
  allHistory?: boolean;
}

function requireReviewFilter(response: Response, reason?: PendingReason | '') {
  if (response.ok && reason && response.headers.get('X-Fintrack-Review-Filter') !== reason) {
    throw new Error('El servidor necesita actualizarse para usar los filtros de revisión.');
  }
}

function requireMerchantFilter(response: Response, merchantName?: string) {
  if (response.ok && merchantName !== undefined && response.headers.get('X-Fintrack-Merchant-Filter') !== 'APPLIED') {
    throw new Error('El servidor necesita actualizarse para filtrar por comercio.');
  }
}

function requireCategoryFilter(response: Response, categoryGroup?: string) {
  if (response.ok && categoryGroup !== undefined && response.headers.get('X-Fintrack-Category-Filter') !== 'APPLIED') {
    throw new Error('El servidor necesita actualizarse para abrir los movimientos por categoría.');
  }
}

function validManualEvidence(item: Transaction | null | undefined): item is Transaction {
  return !!item && Number.isSafeInteger(item.id) && item.id > 0 && Number.isFinite(item.amount) && item.amount > 0
    && ['EXPENSE','INCOME','INTERNAL_TRANSFER'].includes(item.flowType) && typeof item.contactName === 'string'
    && typeof item.transactionDate === 'string' && !Number.isNaN(Date.parse(item.transactionDate));
}

export const api = {
  async getTransactionCorrections(id: number, page = 0): Promise<TransactionCorrectionHistory> {
    const result = await handleResponse<TransactionCorrectionHistory>(await fetchWithAuth(`${BASE_URL}/transactions/${id}/corrections?page=${page}`, { cache: 'no-store' }));
    if (!result || !validManualEvidence(result.transaction) || result.transaction.id !== id || !Number.isSafeInteger(result.revision) || result.revision < 0 || typeof result.editable !== 'boolean' || !Array.isArray(result.entries) || !Number.isSafeInteger(result.totalPages)) throw new Error('No se pudo verificar el historial de correcciones.');
    return result;
  },
  async correctTransaction(id: number, request: TransactionCorrectionRequest): Promise<Transaction> {
    const result = await handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/${id}/corrections`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) }));
    if (!validManualEvidence(result) || result.id !== id) throw new Error('No se pudo verificar la corrección. Consulta el detalle antes de reintentar.');
    return result;
  },
  async createManualTransaction(request: ManualTransactionRequest): Promise<Transaction> {
    const result = await handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/manual`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) }));
    if (!result || !validManualEvidence(result) || result.captureOrigin !== 'MANUAL') throw new Error('No se pudo verificar el registro. Reintenta con los mismos datos antes de crear otro.');
    return result;
  },
  async previewManualReconciliation(id: number): Promise<ManualReconciliationPreview> {
    const result = await handleResponse<ManualReconciliationPreview>(await fetchWithAuth(`${BASE_URL}/transactions/manual/${id}/reconciliation-preview`, { cache: 'no-store' }));
    if (!result || !validManualEvidence(result.manual) || result.manual.captureOrigin !== 'MANUAL' || !Array.isArray(result.candidates) || !Number.isSafeInteger(result.expiresAt) || result.candidates.length > 50 || result.candidates.some(item => !validManualEvidence(item.transaction) || !['EMAIL','PHONE'].includes(item.transaction.captureOrigin ?? '') || typeof item.previewToken !== 'string' || !/^[a-f0-9]{64}$/.test(item.previewToken))) throw new Error('No se recibió una revisión válida. Actualiza las coincidencias.');
    return result;
  },
  async getManualReconciliation(id: number): Promise<ManualReconciliationStatus> {
    const result = await handleResponse<ManualReconciliationStatus>(await fetchWithAuth(`${BASE_URL}/transactions/${id}/manual-reconciliation`, { cache: 'no-store' }));
    if (!result || !Number.isSafeInteger(result.revision) || result.revision < 0 || !Object.prototype.hasOwnProperty.call(result, 'manual') || !Object.prototype.hasOwnProperty.call(result, 'captured') || (result.manual !== null && (!validManualEvidence(result.manual) || result.manual.captureOrigin !== 'MANUAL')) || (result.captured !== null && !validManualEvidence(result.captured))) throw new Error('No se recibió información válida de conciliación.');
    return result;
  },
  async reconcileManual(id: number, capturedId: number, expiresAt: number, previewToken: string): Promise<Transaction> {
    const result = await handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/manual/${id}/reconcile`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ capturedId, expiresAt, previewToken }) }));
    if (!validManualEvidence(result) || result.id !== capturedId || !['EMAIL','PHONE'].includes(result.captureOrigin ?? '')) throw new Error('No se pudo verificar la conciliación. Actualiza su estado antes de repetir la acción.');
    return result;
  },
  async undoManualReconciliation(id: number, capturedId: number, revision: number): Promise<Transaction> {
    const result = await handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/manual/${id}/reconciliation/${capturedId}?revision=${revision}`, { method: 'DELETE' }));
    if (!validManualEvidence(result) || result.id !== capturedId) throw new Error('No se pudo verificar la conciliación. Actualiza su estado.');
    return result;
  },

  async getTransactionOrigin(id: number): Promise<TransactionOrigin> {
    const result = await handleResponse<TransactionOrigin>(await fetchWithAuth(`${BASE_URL}/transactions/${id}/origin`, { cache: 'no-store' }));
    if (!result || typeof result.origin !== 'string'
        || (result.registeredAt !== null && typeof result.registeredAt !== 'string')
        || (result.operationReference !== null && typeof result.operationReference !== 'string')) {
      throw new Error('No se recibió información válida sobre el origen.');
    }
    return { ...result, origin: result.origin === 'EMAIL' || result.origin === 'PHONE' || result.origin === 'MANUAL' ? result.origin : 'UNKNOWN' };
  },

  async previewRuleHistory(id: number): Promise<RuleHistoryPreview> {
    const response = await fetchWithAuth(`${BASE_URL}/classification-rules/${id}/history-preview`, { cache: 'no-store' });
    if (response.status === 404 || response.status === 405) {
      throw new Error('No se pudo abrir la vista previa. Recarga las reglas y comprueba que la API esté actualizada.');
    }
    const preview = await handleResponse<RuleHistoryPreview>(response);
    if (preview.ruleId !== id || !Array.isArray(preview.transactions) || typeof preview.previewToken !== 'string'
        || typeof preview.expiresAt !== 'number') throw new Error('La API no devolvió una vista previa válida.');
    return preview;
  },

  async applyRuleHistory(preview: RuleHistoryPreview): Promise<RuleHistoryApplyResponse> {
    const result = await handleResponse<RuleHistoryApplyResponse>(await fetchWithAuth(`${BASE_URL}/classification-rules/${preview.ruleId}/history-apply`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ transactionIds: preview.transactions.map(t => t.id), expiresAt: preview.expiresAt, previewToken: preview.previewToken }),
    }));
    if (!Number.isInteger(result.appliedCount) || !Number.isInteger(result.skippedCount)
        || result.appliedCount < 0 || result.skippedCount < 0
        || result.appliedCount + result.skippedCount !== preview.transactions.length) {
      throw new Error('La API no confirmó la clasificación. Revisa de nuevo los movimientos.');
    }
    return result;
  },

  async getClassificationRules(): Promise<ClassificationRule[]> {
    return handleResponse<ClassificationRule[]>(await fetchWithAuth(`${BASE_URL}/classification-rules`));
  },

  async saveClassificationRule(data: ClassificationRuleRequest, id?: number): Promise<ClassificationRule> {
    return handleResponse<ClassificationRule>(await fetchWithAuth(`${BASE_URL}/classification-rules${id === undefined ? '' : '/' + id}`, {
      method: id === undefined ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }));
  },

  async deleteClassificationRule(id: number): Promise<void> {
    const response = await fetchWithAuth(`${BASE_URL}/classification-rules/${id}`, { method: 'DELETE' });
    if (!response.ok) await handleResponse<never>(response);
  },

  async updateTransactionClassification(transactionId: number, data: TransactionClassificationRequest): Promise<Transaction> {
    return handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/${transactionId}/classification`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }));
  },

  async getFinancialSummary(): Promise<FinancialSummary> {
    const response = await fetchWithAuth(`${BASE_URL}/analytics/summary`);
    return handleResponse<FinancialSummary>(response);
  },

  async getPeriodAnalytics(year?: number, month?: number): Promise<PeriodAnalytics> {
    const query = new URLSearchParams();
    if (year) query.set('year', String(year));
    if (month) query.set('month', String(month));
    const qs = query.toString() ? `?${query.toString()}` : '';

    const response = await fetchWithAuth(`${BASE_URL}/analytics/period${qs}`);
    return handleResponse<PeriodAnalytics>(response);
  },

  async getTransactions(params: TransactionFilterParams = {}): Promise<TransactionPageResponse> {
    const query = new URLSearchParams();
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 10));
    if (params.merchantName !== undefined) query.set('merchantName', params.merchantName);
    if (params.categoryGroup !== undefined) query.set('categoryGroup', params.categoryGroup);
    if (params.pendingReason) query.set('pendingReason', params.pendingReason);

    if (params.flowType) {
      query.set('flowType', params.flowType);
    }
    if (params.search && params.search.trim()) {
      query.set('search', params.search.trim());
    }
    if (params.startDate) {
      query.set('startDate', params.startDate);
    }
    if (params.endDate) {
      query.set('endDate', params.endDate);
    }
    if (params.category) {
      query.set('category', params.category);
    }
    if (params.channel) {
      query.set('channel', params.channel);
    }
    if (params.minAmount !== undefined && params.minAmount !== null && !isNaN(params.minAmount)) {
      query.set('minAmount', String(params.minAmount));
    }
    if (params.maxAmount !== undefined && params.maxAmount !== null && !isNaN(params.maxAmount)) {
      query.set('maxAmount', String(params.maxAmount));
    }

    const response = await fetchWithAuth(`${BASE_URL}/transactions?${query.toString()}`);
    requireReviewFilter(response, params.pendingReason);
    requireMerchantFilter(response, params.merchantName);
    requireCategoryFilter(response, params.categoryGroup);
    return handleResponse<TransactionPageResponse>(response);
  },

  async exportTransactionsCsv(params: TransactionFilterParams = {}): Promise<Blob> {
    const query = new URLSearchParams();
    if (params.merchantName !== undefined) query.set('merchantName', params.merchantName);
    if (params.categoryGroup !== undefined) query.set('categoryGroup', params.categoryGroup);
    if (params.pendingReason) query.set('pendingReason', params.pendingReason);
    if (params.flowType) query.set('flowType', params.flowType);
    if (params.search && params.search.trim()) query.set('search', params.search.trim());
    if (params.startDate) query.set('startDate', params.startDate);
    if (params.endDate) query.set('endDate', params.endDate);
    if (params.category) query.set('category', params.category);
    if (params.channel) query.set('channel', params.channel);
    if (params.minAmount !== undefined && params.minAmount !== null && !isNaN(params.minAmount)) {
      query.set('minAmount', String(params.minAmount));
    }
    if (params.maxAmount !== undefined && params.maxAmount !== null && !isNaN(params.maxAmount)) {
      query.set('maxAmount', String(params.maxAmount));
    }

    const response = await fetchWithAuth(`${BASE_URL}/transactions/export?${query.toString()}`);
    requireReviewFilter(response, params.pendingReason);
    requireMerchantFilter(response, params.merchantName);
    requireCategoryFilter(response, params.categoryGroup);
    if (!response.ok) {
      throw new Error(`Error al exportar transacciones: ${response.statusText}`);
    }
    return response.blob();
  },

  async syncEmails(): Promise<EmailSyncResponse> {
    const response = await fetchWithAuth(`${BASE_URL}/emails/sync`, {
      method: 'POST',
    });
    return handleResponse<EmailSyncResponse>(response);
  },

  async testEmailConnection(): Promise<EmailConnectionStatus> {
    const response = await fetchWithAuth(`${BASE_URL}/emails/test-connection`);
    return handleResponse<EmailConnectionStatus>(response);
  },

  async getGoogleAuthUrl(): Promise<string> {
    const response = await fetch(`${BASE_URL}/auth/google/url`, { credentials: 'same-origin', cache: 'no-store' });
    const data = await handleResponse<{ authUrl: string }>(response);
    return data.authUrl;
  },

  async redeemGoogleSession(code: string): Promise<string> {
    const response = await fetch(`${BASE_URL}/auth/google/session`, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code }),
    });
    const data = await handleResponse<{ token: string }>(response);
    return data.token;
  },

  async getPhoneReceptionStatus(): Promise<PhoneReceptionStatus> {
    const response = await fetchWithAuth(`${BASE_URL}/user/phone-status`, { cache: 'no-store' });
    const data = await handleResponse<PhoneReceptionStatus>(response);
    if (!data || !Object.prototype.hasOwnProperty.call(data, 'lastReceivedAt') ||
        (data.lastReceivedAt !== null && (typeof data.lastReceivedAt !== 'string' || Number.isNaN(Date.parse(data.lastReceivedAt))))) {
      throw new Error('El servidor no ofrece información válida de recepción del teléfono.');
    }
    return data;
  },

  async getGoogleAuthStatus(): Promise<GoogleAuthStatus> {
    const response = await fetchWithAuth(`${BASE_URL}/auth/google/status`, { cache: 'no-store' });
    return handleResponse<GoogleAuthStatus>(response);
  },

  async getCurrentUser(): Promise<UserProfile> {
    const response = await fetchWithAuth(`${BASE_URL}/auth/google/me`);
    return handleResponse<UserProfile>(response);
  },

  async disconnectGoogle(): Promise<void> {
    const response = await fetchWithAuth(`${BASE_URL}/auth/google/disconnect`, {
      method: 'POST',
    });
    if (!response.ok) {
      throw new Error(`Error al desvincular Google: ${response.statusText}`);
    }
  },

  async getPairingInfo(): Promise<DevicePairingInfo> {
    const response = await fetchWithAuth(`${BASE_URL}/user/pairing-info`);
    return handleResponse<DevicePairingInfo>(response);
  },

  async regeneratePairingToken(): Promise<DevicePairingInfo> {
    const response = await fetchWithAuth(`${BASE_URL}/user/pairing-info/regenerate`, {
      method: 'POST',
    });
    return handleResponse<DevicePairingInfo>(response);
  },

  async getCategories(active?: boolean): Promise<Category[]> {
    const query = active !== undefined ? `?active=${active}` : '';
    const response = await fetchWithAuth(`${BASE_URL}/categories${query}`);
    return handleResponse<Category[]>(response);
  },

  async createCategory(category: CategoryRequest): Promise<Category> {
    const response = await fetchWithAuth(`${BASE_URL}/categories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(category),
    });
    return handleResponse<Category>(response);
  },

  async updateCategory(id: number, category: CategoryRequest): Promise<Category> {
    const response = await fetchWithAuth(`${BASE_URL}/categories/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(category),
    });
    return handleResponse<Category>(response);
  },

  async toggleCategory(id: number): Promise<Category> {
    const response = await fetchWithAuth(`${BASE_URL}/categories/${id}/toggle`, {
      method: 'PATCH',
    });
    return handleResponse<Category>(response);
  },

  async deleteCategory(id: number): Promise<void> {
    const response = await fetchWithAuth(`${BASE_URL}/categories/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error(`Error al eliminar categoría: ${response.statusText}`);
    }
  },
};

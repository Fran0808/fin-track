import type {
  FinancialSummary,
  PeriodAnalytics,
  Transaction,
  TransactionClassificationRequest,
  PageResponse,
  EmailSyncResponse,
  EmailConnectionStatus,
  GoogleAuthStatus,
  UserProfile,
  FlowType,
  DevicePairingInfo,
  FinancialInstrument,
  FinancialInstrumentRequest,
  Category,
  CategoryRequest,
  PendingReason,
  ClassificationRule,
  ClassificationRuleRequest,
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
  financialInstrumentId?: number;
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

export const api = {
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

  async getFinancialInstruments(active?: boolean): Promise<FinancialInstrument[]> {
    const query = active === undefined ? '' : `?active=${active}`;
    return handleResponse<FinancialInstrument[]>(await fetchWithAuth(`${BASE_URL}/financial-instruments${query}`));
  },

  async saveFinancialInstrument(data: FinancialInstrumentRequest, id?: number): Promise<FinancialInstrument> {
    return handleResponse<FinancialInstrument>(await fetchWithAuth(`${BASE_URL}/financial-instruments${id === undefined ? '' : '/' + id}`, {
      method: id === undefined ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }));
  },

  async assignFinancialInstrument(transactionId: number, financialInstrumentId: number | null): Promise<Transaction> {
    return handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/${transactionId}/financial-instrument`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ financialInstrumentId }),
    }));
  },

  async updateTransactionClassification(transactionId: number, data: TransactionClassificationRequest): Promise<Transaction> {
    return handleResponse<Transaction>(await fetchWithAuth(`${BASE_URL}/transactions/${transactionId}/classification`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    }));
  },

  async getInstrumentSuggestions(transactionId: number): Promise<FinancialInstrument[]> {
    return handleResponse<FinancialInstrument[]>(await fetchWithAuth(`${BASE_URL}/transactions/${transactionId}/financial-instrument-suggestions`));
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

  async getTransactions(params: TransactionFilterParams = {}): Promise<PageResponse<Transaction>> {
    const query = new URLSearchParams();
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 10));
    if (params.merchantName !== undefined) query.set('merchantName', params.merchantName);
    if (params.categoryGroup !== undefined) query.set('categoryGroup', params.categoryGroup);
    if (params.pendingReason) query.set('pendingReason', params.pendingReason);
    if (params.financialInstrumentId !== undefined) query.set('financialInstrumentId', String(params.financialInstrumentId));

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
    return handleResponse<PageResponse<Transaction>>(response);
  },

  async exportTransactionsCsv(params: TransactionFilterParams = {}): Promise<Blob> {
    const query = new URLSearchParams();
    if (params.merchantName !== undefined) query.set('merchantName', params.merchantName);
    if (params.categoryGroup !== undefined) query.set('categoryGroup', params.categoryGroup);
    if (params.pendingReason) query.set('pendingReason', params.pendingReason);
    if (params.financialInstrumentId !== undefined) query.set('financialInstrumentId', String(params.financialInstrumentId));
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

  async getGoogleAuthStatus(): Promise<GoogleAuthStatus> {
    const response = await fetchWithAuth(`${BASE_URL}/auth/google/status`);
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

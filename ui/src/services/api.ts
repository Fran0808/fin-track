import type {
  FinancialSummary,
  PeriodAnalytics,
  Transaction,
  PageResponse,
  EmailSyncResponse,
  EmailConnectionStatus,
  GoogleAuthStatus,
  UserProfile,
  FlowType,
  DevicePairingInfo,
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
  page?: number;
  size?: number;
  flowType?: FlowType | '';
  search?: string;
  startDate?: string;
  endDate?: string;
}

export const api = {
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

    const response = await fetchWithAuth(`${BASE_URL}/transactions?${query.toString()}`);
    return handleResponse<PageResponse<Transaction>>(response);
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
    const response = await fetch(`${BASE_URL}/auth/google/url`);
    const data = await handleResponse<{ authUrl: string }>(response);
    return data.authUrl;
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
};

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { api } from '../services/api';
import type { UserProfile } from '../types';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  error: string | null;
  loginWithGoogle: () => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const token = localStorage.getItem('auth_token');
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const profile = await api.getCurrentUser();
      if (localStorage.getItem('auth_token') === token) {
        setUser(profile);
        setError(null);
      }
    } catch (err) {
      console.warn('Failed to fetch authenticated user profile:', err);
      if (localStorage.getItem('auth_token') === token) logout();
    } finally {
      const currentToken = localStorage.getItem('auth_token');
      if (!currentToken || currentToken === token) setLoading(false);
    }
  }, [logout]);

  useEffect(() => {
    // 1. Check for token in URL callback
    const params = new URLSearchParams(window.location.search);
    const tokenFromUrl = params.get('token');
    const authStatus = params.get('auth');

    if (tokenFromUrl) {
      localStorage.setItem('auth_token', tokenFromUrl);
      // Clean query params from URL without page reload
      window.history.replaceState({}, '', window.location.pathname);
    } else if (authStatus === 'google_error') {
      setError('Error al autenticar con Google. Inténtalo de nuevo.');
      window.history.replaceState({}, '', window.location.pathname);
    }

    // 2. Listen for unauthorized 401 events from fetchWithAuth
    const handleUnauthorized = () => {
      logout();
    };
    window.addEventListener('auth:unauthorized', handleUnauthorized);

    // 3. Load user profile
    refreshUser();

    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, [logout, refreshUser]);

  const loginWithGoogle = async () => {
    try {
      setError(null);
      const url = await api.getGoogleAuthUrl();
      window.location.href = url;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Error al iniciar sesión con Google';
      setError(msg);
      throw err;
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        error,
        loginWithGoogle,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

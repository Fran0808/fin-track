import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
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
  const initialization = useRef<Promise<void> | null>(null);
  const sessionGeneration = useRef(0);

  const logout = useCallback(() => {
    sessionGeneration.current++;
    localStorage.removeItem('auth_token');
    setUser(null);
    setLoading(false);
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
    // Session exchange is shared across StrictMode effect replays.
    if (!initialization.current) {
      const generation = sessionGeneration.current;
      const params = new URLSearchParams(window.location.search);
      const code = new URLSearchParams(window.location.hash.slice(1)).get('login_code');
      const failed = params.get('auth') === 'google_error' || params.has('token');
      if (code || params.has('auth') || params.has('token')) {
        params.delete('auth');
        params.delete('token');
        const query = params.toString();
        window.history.replaceState({}, '', window.location.pathname + (query ? `?${query}` : ''));
      }
      initialization.current = (async () => {
        try {
          if (failed) setError('Error al autenticar con Google. Inténtalo de nuevo.');
          if (code) {
            const token = await api.redeemGoogleSession(code);
            if (sessionGeneration.current !== generation) return;
            localStorage.setItem('auth_token', token);
          }
          if (sessionGeneration.current === generation) await refreshUser();
        } catch {
          if (sessionGeneration.current === generation) {
            setError('No se pudo completar el inicio de sesión. Inténtalo de nuevo.');
            setLoading(false);
          }
        }
      })();
    }

    const handleUnauthorized = () => {
      logout();
    };
    window.addEventListener('auth:unauthorized', handleUnauthorized);

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

'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { authApi, AuthApiError, type AuthUser } from '@/lib/authApi';
import {
  AUTH_EXPIRED_EVENT,
  PERMISSIONS_CHANGED_EVENT,
  PERMISSIONS_STALE_EVENT,
} from '@/lib/authEvents';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  refresh: () => Promise<AuthUser | null>;
  setUser: (user: AuthUser | null) => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const refreshInFlight = useRef<Promise<AuthUser | null> | null>(null);

  const refresh = useCallback(() => {
    if (refreshInFlight.current) return refreshInFlight.current;
    const pending = (async () => {
      try {
        const current = await authApi.me();
        setUser(current);
        return current;
      } catch (error) {
        if (error instanceof AuthApiError && error.status === 401) setUser(null);
        return null;
      } finally {
        setLoading(false);
        refreshInFlight.current = null;
      }
    })();
    refreshInFlight.current = pending;
    return pending;
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    const expire = () => setUser(null);
    const refreshPermissions = () => { void refresh(); };
    window.addEventListener(AUTH_EXPIRED_EVENT, expire);
    window.addEventListener(PERMISSIONS_STALE_EVENT, refreshPermissions);
    window.addEventListener(PERMISSIONS_CHANGED_EVENT, refreshPermissions);
    window.addEventListener('focus', refreshPermissions);
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, expire);
      window.removeEventListener(PERMISSIONS_STALE_EVENT, refreshPermissions);
      window.removeEventListener(PERMISSIONS_CHANGED_EVENT, refreshPermissions);
      window.removeEventListener('focus', refreshPermissions);
    };
  }, [refresh]);

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, loading, refresh, setUser, logout }), [user, loading, refresh, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

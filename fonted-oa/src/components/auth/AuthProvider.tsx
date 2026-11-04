'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { authApi, AuthApiError, type AuthUser } from '@/lib/authApi';
import { bindChatIdentity } from '@/store/aiChatStore';
import {
  AUTH_EXPIRED_EVENT,
  PERMISSIONS_CHANGED_EVENT,
  PERMISSIONS_STALE_EVENT,
  bindAuthResponseIdentity,
  captureAuthResponseScope,
  invalidateAuthResponses,
  isAuthResponseCurrent,
  isCurrentAuthEvent,
} from '@/lib/authEvents';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  refresh: () => Promise<AuthUser | null>;
  setUser: (user: AuthUser | null) => void;
  logout: () => Promise<boolean>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUserState] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const identityRevision = useRef(0);
  const mounted = useRef(true);
  const identityKey = useRef<string | null>(null);
  const refreshInFlight = useRef<Promise<AuthUser | null> | null>(null);
  const setUser = useCallback((next: AuthUser | null) => {
    if (!mounted.current) return;
    const key = next ? `${next.tenantId}:${next.id}` : null;
    if (identityKey.current !== key) refreshInFlight.current = null;
    identityKey.current = key;
    identityRevision.current += 1;
    bindAuthResponseIdentity(key, JSON.stringify([next?.permissionVersion, [...(next?.roles ?? [])].sort(), [...(next?.permissions ?? [])].sort(), [...(next?.dataScopes ?? [])].sort()]));
    bindChatIdentity(key);
    setUserState(next); setLoading(false);
  }, []);

  const refresh = useCallback(() => {
    if (!mounted.current) return Promise.resolve(null);
    if (refreshInFlight.current) return refreshInFlight.current;
    const revision = identityRevision.current;
    const pending = (async () => {
      try {
        const current = await authApi.me();
        if (!mounted.current || identityRevision.current !== revision) return null;
        setUser(current);
        return current;
      } catch (error) {
        if (mounted.current && identityRevision.current === revision && error instanceof AuthApiError && error.status === 401) setUser(null);
        return null;
      } finally {
        if (mounted.current && identityRevision.current === revision) setLoading(false);
      }
    })();
    refreshInFlight.current = pending;
    void pending.then(() => { if (refreshInFlight.current === pending) refreshInFlight.current = null; });
    return pending;
  }, [setUser]);

  useEffect(() => {
    mounted.current = true;
    void refresh();
    return () => {
      mounted.current = false; identityRevision.current++; refreshInFlight.current = null;
      invalidateAuthResponses();
    };
  }, [refresh]);

  useEffect(() => {
    const expire = (event: Event) => { if (isCurrentAuthEvent(event)) setUser(null); };
    const refreshPermissions = (event: Event) => { if (event.type === 'focus' || isCurrentAuthEvent(event)) void refresh(); };
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
  }, [refresh, setUser]);

  const logout = useCallback(async () => {
    const scope = captureAuthResponseScope();
    await authApi.logout();
    if (!mounted.current || !isAuthResponseCurrent(scope)) return false;
    setUser(null);
    return true;
  }, [setUser]);

  const value = useMemo(() => ({ user, loading, refresh, setUser, logout }), [user, loading, refresh, setUser, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

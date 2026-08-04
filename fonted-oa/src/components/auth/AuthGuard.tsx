'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from '@/lib/nextCompat';
import { Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from './AuthProvider';

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation();
  const { user, loading } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace(`/oa/auth?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [loading, pathname, router, user]);

  if (loading || !user) {
    return <div className="auth-route-loading"><Spin size="large" description={t('auth.guard.verifying')} /></div>;
  }
  return children;
}

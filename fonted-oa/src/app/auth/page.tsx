import { Suspense } from 'react';
import { App } from 'antd';
import AuthPage from '@/components/auth/AuthPage';

export default function AuthenticationPage() {
  return <App><Suspense fallback={null}><AuthPage /></Suspense></App>;
}

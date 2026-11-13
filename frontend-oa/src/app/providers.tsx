import { useEffect } from 'react';
import { App } from 'antd';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { setMessageInstance } from '@/lib/antdMessage';
import I18nProvider from '@/i18n/I18nProvider';

function MessageBridge() {
  const { message } = App.useApp();
  useEffect(() => {
    setMessageInstance(message);
  }, [message]);
  return null;
}

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <I18nProvider>
      <App>
        <MessageBridge />
        <AuthProvider>{children}</AuthProvider>
      </App>
    </I18nProvider>
  );
}

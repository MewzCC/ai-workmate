import { useEffect } from 'react';
import { App } from 'antd';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { setMessageInstance } from '@/lib/antdMessage';

function MessageBridge() {
  const { message } = App.useApp();
  useEffect(() => {
    setMessageInstance(message);
  }, [message]);
  return null;
}

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <App>
      <MessageBridge />
      <AuthProvider>{children}</AuthProvider>
    </App>
  );
}

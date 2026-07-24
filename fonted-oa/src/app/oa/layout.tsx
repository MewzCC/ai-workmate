import AuthGuard from '@/components/auth/AuthGuard';
import AdminLayout from '@/components/oa/AdminLayout';

export default function OaLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AdminLayout />
      {children}
    </AuthGuard>
  );
}

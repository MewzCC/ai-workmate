import AuthGuard from '@/components/auth/AuthGuard';

export default function OaLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}

import { Routes, Route, Navigate } from 'react-router-dom';
import Providers from './app/providers';
import AuthPage from './components/auth/AuthPage';
import AuthGuard from './components/auth/AuthGuard';
import AdminLayout from './components/oa/AdminLayout';

export default function App() {
  return (
    <Providers>
      <Routes>
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/oa" element={<AuthGuard><AdminLayout /></AuthGuard>} />
        <Route path="/oa/:pageId" element={<AuthGuard><AdminLayout /></AuthGuard>} />
        <Route path="/oa/approval-tasks/:id" element={<AuthGuard><AdminLayout /></AuthGuard>} />
        <Route path="*" element={<Navigate to="/oa" replace />} />
      </Routes>
    </Providers>
  );
}

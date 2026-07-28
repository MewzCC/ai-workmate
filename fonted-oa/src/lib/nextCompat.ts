import {
  useNavigate,
  useLocation,
  useSearchParams as useRRSearchParams,
} from 'react-router-dom';

export function useRouter() {
  const navigate = useNavigate();
  return {
    push: (path: string) => navigate(path),
    replace: (path: string) => navigate(path, { replace: true }),
    refresh: () => window.location.reload(),
  };
}

export function usePathname() {
  return useLocation().pathname;
}

export function useSearchParams() {
  const [searchParams] = useRRSearchParams();
  return searchParams;
}

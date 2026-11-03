import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from './usePermission';
import { collectPagedRecords } from '@/lib/collectPagedRecords';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useAsyncActionGuard } from './useAsyncActionGuard';

/** Shared owned-list / assigned-approval loading; domain writes stay in the page and service. */
export function useAdminAssetApprovalQueue<T, S extends string>({ pageId, readPermission, createPermission, mine: queryMine, pending: queryPending }: {
  pageId: string; readPermission: string; createPermission: string;
  statuses: readonly S[];
  mine: (query: { page: number; size: number; status?: S }) => Promise<{ records: T[]; total: number }>;
  pending: (query: { page: number; size: number }) => Promise<{ records: T[]; total: number }>;
}) {
  const { user } = useAuth();
  const { hasPermission, permissionVersion } = usePermission(`route:${pageId}`);
  const canEnter = hasPermission(`route:${pageId}`);
  const canRead = canEnter && hasPermission(readPermission);
  const canCreate = canEnter && hasPermission(createPermission);
  const canDecide = canEnter && hasPermission('approval:act');
  const scope = `${user?.tenantId}:${user?.id}:${permissionVersion}:${canRead}:${canCreate}:${canDecide}`;
  const { capture: captureScope } = useAsyncActionGuard(scope);
  const request = useRef(0);
  const [dataScope, setDataScope] = useState(scope);
  const [activeTab, setActiveTab] = useState('mine');
  const [mine, setMine] = useState<T[]>([]);
  const [pending, setPending] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [status, setStatus] = useState<S>();
  const loadMine = useCallback(async (p = page, s = size, filter: S | null = status ?? null) => {
    const isCurrent = captureScope();
    if (!isCurrent()) return;
    const ticket = ++request.current;
    setPage(p); setSize(s); setActiveTab('mine');
    if (!canRead) { setMine([]); setTotal(0); setLoading(false); return; }
    const current = () => ticket === request.current && isCurrent();
    setLoading(true);
    try {
      const result = await queryMine({ page: p, size: s, status: filter ?? undefined });
      if (current()) { setMine(result.records); setTotal(result.total); setDataScope(scope); }
    } catch (error) {
      if (current()) { setMine([]); setTotal(0); message.error(formatOaApiError(error)); }
    } finally { if (current()) setLoading(false); }
  }, [page, size, status, canRead, scope, queryMine, captureScope]);
  const loadPending = useCallback(async () => {
    const isCurrent = captureScope();
    if (!isCurrent()) return;
    const ticket = ++request.current;
    if (!canDecide) { setPending([]); setLoading(false); return; }
    setActiveTab('pending');
    const current = () => ticket === request.current && isCurrent();
    setLoading(true);
    try {
      const result = await collectPagedRecords((page, size) => {
        if (!current()) throw new Error('STALE_QUEUE_REQUEST');
        return queryPending({ page, size });
      });
      if (current()) { setPending(result); setDataScope(scope); }
    } catch (error) {
      if (current()) { setPending([]); message.error(formatOaApiError(error)); }
    } finally { if (current()) setLoading(false); }
  }, [canDecide, scope, queryPending, captureScope]);
  const latestRefresh = useRef<() => Promise<void>>(loadMine);
  latestRefresh.current = activeTab === 'pending' ? loadPending : () => loadMine();
  const refresh = useCallback(async () => {
    if (captureScope()()) await latestRefresh.current();
  }, [captureScope]);
  const initialLoad = useRef(loadMine); initialLoad.current = loadMine;
  useEffect(() => {
    setMine([]); setPending([]); setTotal(0); setDataScope(scope); setStatus(undefined);
    void initialLoad.current(1, 10, null);
    return () => { request.current += 1; };
  }, [scope]);
  const handleTabChange = (key: string) => {
    if (!captureScope()()) return;
    if (key === 'pending') { if (canDecide) void loadPending(); }
    else void loadMine(1);
  };
  return { activeTab, mine: dataScope === scope ? mine : [], pending: dataScope === scope ? pending : [],
    total: dataScope === scope ? total : 0, loading, page, size, setPage, setSize,
    loadMine, loadPending, refresh, captureScope, handleTabChange, status,
    changeStatus: (value: S | undefined) => { if (captureScope()()) { setStatus(value); void loadMine(1, size, value ?? null); } },
    canRead, canCreate, canDecide, scope };
}

import { useCallback, useMemo, useRef, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import { approvalApi, todoApi, formatOaApiError, type LeaveStatus, type LeaveType } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { useRegisterPageUiCommands } from '@/components/oa/PageUiCommandBridge';
import { usePublishPageAgentContext } from '@/components/oa/PageAgentContext';
import { usePermission } from './usePermission';
import { useScopedOverview } from './useScopedOverview';
import { useAsyncActionGuard } from './useAsyncActionGuard';
import { requireUiPagination, uiDateRange } from './usePageUiEditorCommands';

export const APPROVAL_STATUS_KEYS: (LeaveStatus | '')[] = ['', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'DRAFT'];
export const APPROVAL_LEAVE_TYPES: LeaveType[] = ['ANNUAL', 'PERSONAL', 'SICK', 'MARRIAGE', 'MATERNITY', 'PATERNITY', 'BEREAVEMENT', 'COMPENSATORY', 'OTHER'];

export function useApprovalListPage() {
  const router = useRouter(), { allowed, hasPermission } = usePermission('approval:read');
  const [status, setStatus] = useState<LeaveStatus>(), [keyword, setKeyword] = useState(''), [search, setSearch] = useState('');
  const [leaveType, setLeaveType] = useState<LeaveType>(), [range, setRange] = useState<[string, string]>();
  const [page, setPage] = useState(1), [size, setSize] = useState(20);
  const query = useCallback(async () => {
    if (!allowed) return null;
    const [list, stats] = await Promise.all([approvalApi.list({ status, keyword: keyword || undefined, leaveType,
      from: range?.[0], to: range?.[1], page, size }), approvalApi.stats()]);
    return { list, stats };
  }, [allowed, keyword, leaveType, page, range, size, status]);
  const overview = useScopedOverview(query), { capture, invalidate } = useAsyncActionGuard(overview.scope);
  const [selection, setSelection] = useState<{ scope: string; id: number }>();
  const opening = useRef(0);
  const drawerTaskId = allowed && selection?.scope === overview.scope ? selection.id : null;
  const records = useMemo(() => allowed ? overview.data?.list.records ?? [] : [], [allowed, overview.data]);
  const stats = useMemo(() => allowed ? overview.data?.stats ?? [] : [], [allowed, overview.data]);
  const openDetail = async (id: string | number, navigate = false) => {
    const current = capture(), ticket = ++opening.current;
    if (!allowed || !current() || !overview.isCurrent() || typeof id !== 'number' || !Number.isSafeInteger(id) || id < 1)
      throw new Error('PAGE_UI_FIELD_REJECTED');
    const result = await todoApi.detail(id);
    if (!current() || !overview.isCurrent() || ticket !== opening.current) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (result.taskId !== id || !Number.isSafeInteger(result.id) || result.id < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    if (navigate) router.push(`/oa/approval-tasks/${id}?from=approval-list`);
    else setSelection({ scope: overview.scope, id });
  };
  const tryOpen = (id: number, navigate = false) => {
    const current = capture(); void openDetail(id, navigate).catch(error => { if (current() && overview.isCurrent()) message.error(formatOaApiError(error)); });
  };
  const refresh = async () => { if (!allowed || !overview.isCurrent() || !await overview.load()) throw new Error('PAGE_UI_FIELD_REJECTED'); };
  usePublishPageAgentContext({ taskId: drawerTaskId ?? undefined, status, keyword, leaveType, from: range?.[0], to: range?.[1], page, size });
  useRegisterPageUiCommands({
    'ui.refreshPage': refresh,
    'ui.applyFilter': ({ payload: { filters } }) => {
      if (!allowed || !overview.isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { status: APPROVAL_STATUS_KEYS, keyword: 'string', leaveType: ['', ...APPROVAL_LEAVE_TYPES], from: 'string', to: 'string', page: 'number', size: 'number' });
      requireUiPagination(filters);
      if (typeof filters.size === 'number' && filters.size > 50 || typeof filters.keyword === 'string' && filters.keyword.length > 120) throw new Error('PAGE_UI_FIELD_REJECTED');
      const nextRange = uiDateRange(filters, range);
      if (filters.status !== undefined) setStatus((filters.status || undefined) as LeaveStatus | undefined);
      if (typeof filters.keyword === 'string') { setKeyword(filters.keyword); setSearch(filters.keyword); }
      if (filters.leaveType !== undefined) setLeaveType((filters.leaveType || undefined) as LeaveType | undefined);
      if (typeof filters.size === 'number') setSize(filters.size);
      setRange(nextRange); setPage(Number(filters.page ?? 1));
    },
    'ui.openDetail': ({ payload: { recordId } }) => openDetail(recordId),
  });
  return { ...overview, allowed, canAct: hasPermission('approval:act'), records, stats, total: allowed ? overview.data?.list.total ?? 0 : 0,
    status, setStatus, keyword, search, setSearch, applySearch: (value = search) => { setKeyword(value.trim()); setPage(1); },
    leaveType, setLeaveType, range, setRange, page, setPage, size, setSize, drawerTaskId, tryOpen,
    closeDetail: () => { invalidate(); setSelection(undefined); }, refresh };
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { App } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import type { ApprovalConfigStatus, ApprovalForm } from '@/lib/approvalEngineApi';
import { message } from '@/lib/antdMessage';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { formatOaApiError, type PageResponse } from '@/lib/oaApi';
import { parseRecordId } from '@/lib/recordSelection';
import { usePublishPageAgentContext } from './PageAgentContext';

interface DefinitionRecord { id: number; version: number; canEdit: boolean; canDelete: boolean }
interface DefinitionPayload { status: ApprovalConfigStatus; version?: number }
interface DefinitionQuery { keyword?: string; status?: ApprovalConfigStatus; page: number; size: number }
interface DefinitionApi<T, P> {
  list: (query: DefinitionQuery) => Promise<PageResponse<T>>;
  get: (id: number) => Promise<T>;
  create: (payload: P) => Promise<T>;
  update: (id: number, payload: P) => Promise<T>;
  remove: (id: number) => Promise<void>;
}

/** Owns approval-definition selection and persistence; designers only produce domain payloads. */
export function useApprovalDefinitionPage<T extends DefinitionRecord, P extends DefinitionPayload>({
  pageId, idField, api, loadForms, messages,
}: {
  pageId: 'form-engine' | 'process-config' | 'approval-rules';
  idField: 'formId' | 'processId' | 'ruleId';
  api: DefinitionApi<T, P>;
  loadForms?: () => Promise<ApprovalForm[]>;
  messages: { publish?: string; create: string; update: string };
}) {
  const { t } = useTranslation();
  const { modal } = App.useApp();
  const { user } = useAuth();
  const { hasPermission, permissionVersion } = usePermission(`route:${pageId}`);
  const canRead = hasPermission(`route:${pageId}`) && hasPermission('approval:read');
  const canManage = canRead && hasPermission('approval:manage');
  const scope = `${pageId}:${user?.id}:${user?.tenantId}:${permissionVersion}:${canRead}:${canManage}`;
  const activeScope = useRef<string | undefined>(scope);
  activeScope.current = scope;
  const [dataScope, setDataScope] = useState(scope);
  const [records, setRecords] = useState<T[]>([]);
  const [storedTotal, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ApprovalConfigStatus>();
  const [detail, setDetail] = useState<T>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [designerOpen, setDesignerOpen] = useState(false);
  const [designerEditing, setDesignerEditing] = useState<T | null>(null);
  const [editorLoading, setEditorLoading] = useState(false);
  const [forms, setForms] = useState<ApprovalForm[]>([]);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);
  const editorRequest = useRef(0);
  const selectedId = useRef<number | undefined>(undefined);
  const confirmation = useRef<{ destroy: () => void } | undefined>(undefined);
  const confirmEpoch = useRef(0);
  const dismissConfirmation = useCallback(() => {
    confirmEpoch.current += 1;
    confirmation.current?.destroy();
    confirmation.current = undefined;
  }, []);
  const router = useRouter();
  const requestedId = useSearchParams().get('id');
  const path = `/oa/${pageId}`;
  const sameScope = dataScope === scope;
  const selected = !sameScope ? undefined : designerOpen ? designerEditing ?? undefined : detailOpen ? detail : undefined;
  usePublishPageAgentContext({ [idField]: selected?.id, version: selected?.version, keyword: keyword || undefined, status, page, size: 20 });

  const closeWindows = useCallback(() => {
    dismissConfirmation();
    detailRequest.current += 1;
    editorRequest.current += 1;
    selectedId.current = undefined;
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false);
    setDesignerEditing(null); setDesignerOpen(false); setEditorLoading(false); setForms([]);
  }, [dismissConfirmation]);
  useEffect(() => {
    activeScope.current = scope;
    closeWindows(); listRequest.current += 1;
    setDataScope(scope); setRecords([]); setTotal(0);
    return () => {
      activeScope.current = undefined;
      dismissConfirmation(); listRequest.current += 1; detailRequest.current += 1; editorRequest.current += 1;
      selectedId.current = undefined;
    };
  }, [scope, closeWindows, dismissConfirmation]);
  const load = useCallback(async () => {
    const request = ++listRequest.current;
    if (!canRead) { setLoading(false); return; }
    setLoading(true);
    try {
      const result = await api.list({ keyword: keyword || undefined, status, page, size: 20 });
      if (request !== listRequest.current || activeScope.current !== scope) return;
      setRecords(result.records); setTotal(result.total);
    } catch (error) {
      if (request === listRequest.current && activeScope.current === scope) {
        setRecords([]); setTotal(0); message.error(formatOaApiError(error));
      }
    } finally { if (request === listRequest.current && activeScope.current === scope) setLoading(false); }
  }, [api, canRead, keyword, status, page, scope]);
  const latestLoad = useRef(load); latestLoad.current = load;
  useEffect(() => { void load(); }, [load]);
  const loadDetail = useCallback(async (id: number) => {
    if (!canRead) return;
    dismissConfirmation();
    const request = ++detailRequest.current;
    selectedId.current = id; setDetail(undefined); setDetailOpen(true); setDetailLoading(true);
    try {
      const result = await api.get(id);
      if (request !== detailRequest.current || activeScope.current !== scope) return;
      if (result.id !== id) throw new Error('RESOURCE_MISMATCH');
      setDetail(result);
    } catch (error) {
      if (request === detailRequest.current && activeScope.current === scope) {
        selectedId.current = undefined; setDetail(undefined); setDetailOpen(false);
        message.error(formatOaApiError(error));
      }
    } finally { if (request === detailRequest.current && activeScope.current === scope) setDetailLoading(false); }
  }, [api, canRead, scope, dismissConfirmation]);
  useEffect(() => {
    closeWindows();
    const id = parseRecordId(requestedId);
    if (id && canRead) void loadDetail(id);
    return () => {
      dismissConfirmation(); detailRequest.current += 1; editorRequest.current += 1; selectedId.current = undefined;
    };
  }, [requestedId, canRead, scope, closeWindows, loadDetail, dismissConfirmation]);
  const close = () => { closeWindows(); if (requestedId !== null) router.replace(path); };
  const showDetail = (id: number, replace = false) => {
    if (!canRead) return;
    closeWindows();
    if (requestedId === String(id)) void loadDetail(id);
    else if (replace) router.replace(`${path}?id=${id}`);
    else router.push(`${path}?id=${id}`);
  };
  const openEditor = async (record?: T) => {
    if (!canManage || (record && !record.canEdit)) return;
    closeWindows();
    const request = ++editorRequest.current;
    setDesignerOpen(true); setEditorLoading(true);
    try {
      const [options, fresh] = await Promise.all([loadForms ? loadForms() : Promise.resolve([]),
        record ? api.get(record.id) : Promise.resolve(undefined)]);
      if (request !== editorRequest.current || activeScope.current !== scope) return;
      if (record && (!fresh || fresh.id !== record.id || !fresh.canEdit)) throw new Error('RESOURCE_MISMATCH');
      setForms(options); setDesignerEditing(fresh ?? null); selectedId.current = fresh?.id;
    } catch (error) {
      if (request === editorRequest.current && activeScope.current === scope) { closeWindows(); message.error(formatOaApiError(error)); }
    } finally { if (request === editorRequest.current && activeScope.current === scope) setEditorLoading(false); }
  };
  const save = async (payload: P, request: number) => {
    if (!canManage || !designerOpen || editorLoading || (designerEditing && !designerEditing.canEdit)
      || request !== editorRequest.current || activeScope.current !== scope) return;
    const identity = scope;
    const editing = designerEditing;
    try {
      const result = editing ? await api.update(editing.id, { ...payload, version: editing.version }) : await api.create(payload);
      if (activeScope.current !== identity) return;
      if (request === editorRequest.current) {
        message.success(t(payload.status === 'ENABLED' && messages.publish ? messages.publish : editing ? messages.update : messages.create));
        showDetail(result.id, true);
      }
      await latestLoad.current();
    } catch (error) { if (request === editorRequest.current && activeScope.current === identity) message.error(formatOaApiError(error)); }
  };
  const remove = (record: T) => {
    if (!canManage || !record.canDelete) return;
    dismissConfirmation();
    const epoch = confirmEpoch.current;
    const request = detailRequest.current;
    const editor = editorRequest.current;
    const isCurrent = () => activeScope.current === scope && epoch === confirmEpoch.current
      && request === detailRequest.current && editor === editorRequest.current;
    confirmation.current = modal.confirm({ title: t('approval.config.common.deleteConfirm'), okText: t('common.delete'),
      cancelText: t('common.cancel'), okButtonProps: { danger: true },
      onCancel: () => { if (epoch === confirmEpoch.current) dismissConfirmation(); },
      onOk: async () => {
        if (!isCurrent()) return;
        try {
          await api.remove(record.id);
          if (!isCurrent()) return;
          message.success(t('approval.config.common.deleteSuccess'));
          if (selectedId.current === record.id) close();
          await latestLoad.current();
        } catch (error) { if (isCurrent()) { message.error(formatOaApiError(error)); throw error; } }
      },
    });
  };
  const refresh = async () => {
    const identity = scope; const id = selectedId.current; const request = detailRequest.current;
    await latestLoad.current();
    if (activeScope.current === identity && id !== undefined && request === detailRequest.current && !designerOpen) await loadDetail(id);
  };
  return { canRead, canManage, data: sameScope ? records : [], total: sameScope ? storedTotal : 0, loading,
    page, setPage, search, setSearch, status, setStatus, applySearch: (value: string) => { setKeyword(value.trim()); setPage(1); },
    detail: sameScope ? detail : undefined, detailOpen: detailOpen && canRead && sameScope, detailLoading,
    designerOpen: designerOpen && canManage && sameScope, designerEditing, editorLoading, forms, editorTicket: editorRequest.current,
    close, showDetail, openEditor, save, remove, refresh };
}

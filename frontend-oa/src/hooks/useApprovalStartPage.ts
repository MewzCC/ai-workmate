import { useCallback, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { approvalEngineApi, type ApprovalApplication, type ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import { collectPagedRecords } from '@/lib/collectPagedRecords';
import { useRouter } from '@/lib/nextCompat';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useRegisterPageUiCommands } from '@/components/oa/PageUiCommandBridge';
import { usePublishPageAgentContext } from '@/components/oa/PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { usePermission } from './usePermission';
import { useScopedOverview } from './useScopedOverview';
import { useAsyncActionGuard } from './useAsyncActionGuard';
import { useGenericApplicationActions } from './useGenericApplicationActions';

export function useApprovalStartPage() {
  const { t } = useTranslation(), { user } = useAuth(), { allowed, hasPermission } = usePermission('route:approval-start');
  const router = useRouter();
  const canTemplates = allowed && hasPermission('approval:create'), canMine = allowed && hasPermission('approval:mine:read');
  const canEdit = canTemplates && hasPermission('route:approval-form');
  const canLeave = allowed && hasPermission('leave:create') && hasPermission('route:leave-application');
  const [keyword, setKeyword] = useState(''), [formKey, setFormKey] = useState('');
  const lifetimeKey = JSON.stringify([user?.tenantId, user?.id, user?.permissionVersion, [...(user?.permissions ?? [])].sort(), user?.roles, user?.dataScopes]);
  const { capture: captureCatalogue } = useAsyncActionGuard(lifetimeKey);
  const templatesQuery = useCallback(() => {
    if (!canTemplates) return Promise.resolve(null);
    const current = captureCatalogue();
    return collectPagedRecords((page, size) => {
      if (!current()) throw new Error('PAGE_UI_COMMAND_REJECTED');
      return approvalEngineApi.listApplicationTemplates({ page, size });
    });
  }, [canTemplates, captureCatalogue]);
  const templates = useScopedOverview(templatesQuery);
  const ownQuery = useCallback(async () => {
    if (!canMine) return null;
    const [drafts, applications] = await Promise.all([
      approvalEngineApi.listMyApplications({ status: 'DRAFT', page: 1, size: 20 }),
      approvalEngineApi.listMyApplications({ page: 1, size: 20 }),
    ]);
    if ([...drafts.records, ...applications.records].some(item => item.applicantUserId !== user?.id)
        || drafts.records.some(item => item.status !== 'DRAFT')) throw new Error(t('approval.genericApplications.invalidResult'));
    return { drafts: drafts.records, recent: applications.records.filter(item => item.status !== 'DRAFT') };
  }, [canMine, t, user?.id]);
  const own = useScopedOverview(ownQuery), forms = canTemplates ? templates.data ?? [] : [];
  const drafts = canMine ? own.data?.drafts ?? [] : [], recent = canMine ? own.data?.recent ?? [] : [];
  const [selection, setSelection] = useState<{ scope: string; item: ApprovalApplication }>(), [detailRevision, setDetailRevision] = useState(0);
  const selected = canMine && selection?.scope === own.scope ? selection.item : undefined;
  const action = useGenericApplicationActions({ scope: own.scope, records: [...drafts, ...recent], isCurrent: own.isCurrent, reload: own.load,
    onUpdated: item => { if (selected?.id === item.id) setDetailRevision(value => value + 1); },
    onReopened: item => router.push(`/oa/approval-form?formKey=${encodeURIComponent(item.formKey)}&draftId=${item.id}`) });
  const intentScope = `${templates.scope}:${own.scope}:${keyword}:${formKey}:${action.busy}`, { capture } = useAsyncActionGuard(intentScope);
  const opening = useRef(0);
  const openTemplate = async (item: ApprovalApplicationTemplate) => {
    const current = capture(), ticket = ++opening.current;
    if (!current() || !templates.isCurrent() || action.busy || !(item.formKey === 'leave-application' ? canLeave : canEdit)) throw new Error('PAGE_UI_FIELD_REJECTED');
    const result = await approvalEngineApi.listApplicationTemplates({ formKey: item.formKey, page: 1, size: 1 });
    if (!current() || !templates.isCurrent() || ticket !== opening.current) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (!result.records.some(value => value.formId === item.formId && value.formKey === item.formKey)) throw new Error('PAGE_UI_FIELD_REJECTED');
    router.push(item.formKey === 'leave-application' ? '/oa/leave-application' : `/oa/approval-form?formKey=${encodeURIComponent(item.formKey)}`);
  };
  const editDraft = async (item: ApprovalApplication) => {
    const current = capture(), ticket = ++opening.current;
    if (!canEdit || !canMine || !current() || !own.isCurrent() || action.busy) throw new Error('PAGE_UI_FIELD_REJECTED');
    const detail = await approvalEngineApi.getApplication(item.id);
    if (!current() || !own.isCurrent() || ticket !== opening.current) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (detail.id !== item.id || detail.applicantUserId !== user?.id || detail.formKey !== item.formKey || detail.status !== 'DRAFT' || !detail.canEditDraft)
      throw new Error('PAGE_UI_FIELD_REJECTED');
    router.push(`/oa/approval-form?formKey=${encodeURIComponent(detail.formKey)}&draftId=${detail.id}`);
  };
  const tryOpen = (operation: () => Promise<void>) => { const current = capture(); void operation().catch(error => { if (current() && own.isCurrent() && templates.isCurrent()) message.error(formatOaApiError(error)); }); };
  const openLeave = () => { if (canLeave && !action.busy && templates.isCurrent()) router.push('/oa/leave-application'); };
  const refresh = async () => {
    if (!allowed || !templates.isCurrent() || !own.isCurrent() || !canTemplates && !canMine) throw new Error('PAGE_UI_FIELD_REJECTED');
    const results = await Promise.all([canTemplates ? templates.load() : Promise.resolve(true), canMine ? own.load() : Promise.resolve(true)]);
    if (results.some(value => !value)) throw new Error('PAGE_UI_FIELD_REJECTED');
  };
  usePublishPageAgentContext({ keyword: keyword || undefined, formKey: formKey || undefined });
  useRegisterPageUiCommands({ ...(allowed && (canTemplates || canMine) ? { 'ui.refreshPage': refresh } : {}), ...(canTemplates ? {
    'ui.applyFilter': ({ payload: { filters } }: Extract<import('@/lib/pageUiCommands').PageUiCommand, { code: 'ui.applyFilter' }>) => {
      if (!templates.isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { keyword: 'string', formKey: 'string' });
      if (typeof filters.keyword === 'string' && filters.keyword.length > 120 || typeof filters.formKey === 'string' && filters.formKey !== '' && !/^[a-z][a-z0-9_-]{0,63}$/.test(filters.formKey)) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (filters.formKey !== undefined) { setFormKey(String(filters.formKey)); if (filters.keyword === undefined) setKeyword(''); }
      if (filters.keyword !== undefined) { setKeyword(String(filters.keyword)); if (filters.formKey === undefined) setFormKey(''); }
    },
    'ui.openDetail': ({ payload: { recordId } }: Extract<import('@/lib/pageUiCommands').PageUiCommand, { code: 'ui.openDetail' }>) => {
      if (typeof recordId !== 'number' || !Number.isSafeInteger(recordId) || recordId < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
      const item = forms.find(value => value.formId === recordId); if (!item) throw new Error('PAGE_UI_FIELD_REJECTED');
      return openTemplate(item);
    },
  } : {}) });
  return { allowed, canTemplates, canMine, canEdit, canLeave, forms, drafts, recent, templates, own, action, selected, detailRevision,
    setSelection, keyword, formKey, changeSearch: (text: string) => { setKeyword(text); setFormKey(''); },
    visibleForms: forms.filter(value => value.formKey !== 'leave-application' && (!formKey || value.formKey === formKey)
      && (!keyword.trim() || [value.formName, value.formKey, value.description].some(text => text?.toLowerCase().includes(keyword.trim().toLowerCase())))),
    refresh, openTemplate: (item: ApprovalApplicationTemplate) => tryOpen(() => openTemplate(item)), editDraft: (item: ApprovalApplication) => tryOpen(() => editDraft(item)),
    openLeave, showDetail: (item: ApprovalApplication) => { if (canMine && !action.busy && own.isCurrent()) setSelection({ scope: own.scope, item }); },
    viewAll: () => { if (canMine && hasPermission('route:my-applications') && own.isCurrent()) router.push('/oa/my-applications?kind=generic'); },
    canViewAll: canMine && hasPermission('route:my-applications') };
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { Descriptions, Form } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { approvalEngineApi, type ApprovalApplication } from '@/lib/approvalEngineApi';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { normalizeApprovalValues, restoreApprovalValues, approvalUiFields } from '@/components/oa/approvalFormFields';
import { usePublishPageAgentContext } from '@/components/oa/PageAgentContext';
import { usePermission } from './usePermission';
import { useScopedOverview } from './useScopedOverview';
import { useAsyncActionGuard } from './useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from './useAsyncFormAction';
import { useScopedModalConfirm } from './useScopedModalConfirm';
import { usePageUiFormContext } from './usePageUiFormContext';
import { usePageUiEditorCommands } from './usePageUiEditorCommands';

/** Owns one applicant form. UI commands can edit/preview but never call these write actions. */
export function useGenericApplicationForm() {
  const { t } = useTranslation(), { user } = useAuth();
  const { allowed, hasPermission } = usePermission(['route:approval-form', 'route:approval-start', 'approval:create']);
  const router = useRouter(), params = useSearchParams();
  const formKey = params.get('formKey') || '', rawDraftId = params.get('draftId');
  const draftId = parseRecordId(rawDraftId);
  const [form] = Form.useForm<Record<string, unknown>>();
  const query = useCallback(async () => {
    if (!allowed || !formKey) return null;
    if (rawDraftId !== null && !draftId) throw new Error(t('approval.formPage.draftUnavailable'));
    const [templates, draft] = await Promise.all([
      approvalEngineApi.listApplicationTemplates({ formKey, page: 1, size: 1 }),
      draftId ? approvalEngineApi.getApplication(draftId) : Promise.resolve(null),
    ]);
    if (draft && (draft.id !== draftId || draft.applicantUserId !== user?.id || draft.status !== 'DRAFT'
        || !draft.canEditDraft || draft.formKey !== formKey)) throw new Error(t('approval.formPage.draftUnavailable'));
    const template = templates.records.find(item => item.formKey === formKey) ?? null;
    return { template, draft, values: template && draft ? restoreApprovalValues(draft.dataJson, template.fields) : {} };
  }, [allowed, draftId, rawDraftId, formKey, t, user?.id]);
  const overview = useScopedOverview(query);
  const template = allowed ? overview.data?.template ?? null : null;
  const [saved, setSaved] = useState<{ scope: string; draft: ApprovalApplication }>();
  const draft = saved?.scope === overview.scope ? saved.draft : overview.data?.draft ?? null;
  const fields = template?.fields ?? [];
  const process = template?.process ?? null;
  const action = useAsyncFormAction(overview.scope);
  const [editRevision, setEditRevision] = useState(0);
  const formScope = `${overview.scope}:${draft?.id}:${draft?.version}:${editRevision}`;
  const confirm = useScopedModalConfirm(formScope);
  const { capture } = useAsyncActionGuard(formScope);
  const initialValues = overview.data?.values;
  useEffect(() => {
    form.resetFields();
    if (template && initialValues) form.setFieldsValue(initialValues);
  }, [overview.scope, template, initialValues, form]);
  const editable = allowed && Boolean(template) && !overview.loading && overview.error === undefined
    && (!draft || draft.status === 'DRAFT' && draft.canEditDraft);
  const [confirmation, setConfirmation] = useState<string>();
  const confirming = confirmation === formScope;
  const uiForm = usePageUiFormContext(editable && !action.busy && !confirming, draft?.id, draft?.version, formScope);
  const changed = () => { uiForm.renew(); setEditRevision(value => value + 1); };
  usePublishPageAgentContext({ formKey, applicationId: draft?.id, version: draft?.version, processKey: process?.processKey });
  usePageUiEditorCommands({ form, open: editable && !action.busy && !confirming, canEdit: editable && !action.busy && !confirming, context: uiForm.context,
    create: () => { if (!overview.isCurrent()) throw new Error('PAGE_UI_FORM_CLOSED'); changed();
      if (draft) router.push(`/oa/approval-form?formKey=${encodeURIComponent(formKey)}`); else form.resetFields(); },
    fields: approvalUiFields(fields), labels: Object.fromEntries(fields.filter(field => field.type !== 'divider').map(field => [field.name, field.label])),
    normalizePreview: values => normalizeApprovalValues(values, fields),
  });
  const liveDraft = useRef(draft); liveDraft.current = draft;
  const validReceipt = (result: ApprovalApplication, status: ApprovalApplication['status'], previous?: ApprovalApplication | null) => {
    if (!Number.isSafeInteger(result.id) || result.id <= 0 || result.applicantUserId !== user?.id || result.formKey !== formKey
        || result.status !== status || !Number.isSafeInteger(result.version) || result.version < 0
        || previous && (result.id !== previous.id || result.version <= previous.version))
      throw new Error(t('approval.genericApplications.invalidResult'));
    return result;
  };
  const backToStart = () => router.push('/oa/approval-start');
  const remember = (value: ApprovalApplication) => { setSaved({ scope: overview.scope, draft: value }); liveDraft.current = value; };
  const persist = async (values: Record<string, unknown>, previous: ApprovalApplication | null) => validReceipt(previous
    ? await approvalEngineApi.updateDraft(previous.id, { processKey: process?.processKey, formData: values, version: previous.version })
    : await approvalEngineApi.createDraft({ formKey, processKey: process?.processKey, formData: values }), 'DRAFT', previous);
  const saveDraft = async () => {
    if (!editable || confirming || !overview.isCurrent()) return;
    const write = action.begin(); if (!write) return;
    try {
      const result = await persist(normalizeApprovalValues(form.getFieldsValue(true), fields), liveDraft.current);
      if (!write.current() || !overview.isCurrent()) return;
      remember(result); message.success(t('approval.formPage.draftSaved'));
      if (!draftId) router.replace(`/oa/approval-form?formKey=${encodeURIComponent(formKey)}&draftId=${result.id}`);
    } catch (error) { if (write.current() && overview.isCurrent()) message.error(formatOaApiError(error)); }
    finally { write.finish(); }
  };
  const cancelDraft = () => {
    if (!editable || !draft?.canCancel || !hasPermission('approval:cancel') || !overview.isCurrent() || action.busy) return;
    confirm({ title: t('approval.formPage.cancelDraftTitle'), content: t('approval.formPage.cancelDraftContent'),
      okText: t('approval.formPage.cancelDraftOk'), cancelText: t('common.cancel'), okButtonProps: { danger: true },
      onOk: async () => {
        if (!overview.isCurrent()) return;
        const write = action.begin(); if (!write) return;
        try {
          const result = await approvalEngineApi.cancelDraft(draft.id, draft.version);
          if (!write.current() || !overview.isCurrent()) return;
          validReceipt(result, 'CANCELLED', draft); message.success(t('approval.formPage.draftCancelled')); backToStart();
        } catch (error) { if (write.current() && overview.isCurrent()) message.error(formatOaApiError(error)); }
        finally { write.finish(); }
      } });
  };
  const validating = useRef(false);
  const submit = async () => {
    if (!editable || !process || !hasPermission('approval:submit') || action.busy || confirming || validating.current) return;
    const current = capture(); validating.current = true;
    try {
      const validated = await form.validateFields();
      if (!current() || !overview.isCurrent()) return;
      const values = normalizeApprovalValues(validated, fields), previous = liveDraft.current;
      setConfirmation(formScope);
      confirm({ title: t('approval.formPage.confirmTitle'), width: 560,
        content: <Descriptions column={1} size="small" items={[
          { key: '_process', label: t('approval.start.boundProcess'), children: process.processName },
          ...fields.filter(field => values[field.name] !== undefined).map(field => ({ key: field.name, label: field.label, children: String(values[field.name]) })),
        ]} />, okText: t('approval.formPage.confirmOk'), cancelText: t('common.cancel'),
        afterClose: () => { if (current()) setConfirmation(undefined); },
        onOk: async () => {
          if (!current() || !overview.isCurrent()) return;
          const write = action.begin(); if (!write) return;
          try {
            let stored = previous;
            if (stored) {
              stored = await persist(values, stored);
              if (!write.current() || !overview.isCurrent()) return;
              // Keep the acknowledged version if submission fails; never automatically repeat a write.
              remember(stored);
            }
            const result = stored ? await approvalEngineApi.submitDraft(stored.id, stored.version)
              : await approvalEngineApi.submitApplication({ formKey, processKey: process.processKey, formData: values });
            if (!write.current() || !overview.isCurrent()) return;
            validReceipt(result, 'PENDING', stored); remember(result); message.success(t('approval.formPage.submitSuccess'));
            if (hasPermission('route:my-applications') && hasPermission('approval:mine:read'))
              router.push(`/oa/my-applications?kind=generic&id=${result.id}`);
            else backToStart();
          } catch (error) { if (write.current() && overview.isCurrent()) message.error(formatOaApiError(error)); }
          finally { write.finish(); }
        } });
    } catch (error) { if (current() && !isFormValidationError(error)) message.error(formatOaApiError(error)); }
    finally { validating.current = false; }
  };
  return { form, formKey, formDef: template, process, fields, nodes: process?.nodes ?? [], draft, loading: overview.loading,
    loadError: overview.error === undefined ? undefined : formatOaApiError(overview.error), load: overview.load,
    allowed, editable, busy: action.busy, confirming, canSubmit: editable && hasPermission('approval:submit'), canCancel: hasPermission('approval:cancel'),
    changed, saveDraft, cancelDraft, submit, backToStart };
}

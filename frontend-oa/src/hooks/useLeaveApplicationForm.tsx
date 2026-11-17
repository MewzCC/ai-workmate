import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Descriptions, Form } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { formatOaApiError, leaveApi, type ApproverCandidate, type LeaveApplication, type LeaveApplicationPayload } from '@/lib/oaApi';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { message } from '@/lib/antdMessage';
import { calculateHalfDays, initialLeaveValues, leaveFormPayload, LEAVE_TYPE_KEYS, PERIOD_KEYS, restoreLeaveValues, type LeaveFormValues } from '@/lib/leaveFormValues';
import { usePublishPageAgentContext } from '@/components/oa/PageAgentContext';
import { usePermission } from './usePermission';
import { useScopedOverview } from './useScopedOverview';
import { useAsyncActionGuard } from './useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from './useAsyncFormAction';
import { useScopedModalConfirm } from './useScopedModalConfirm';
import { usePageUiFormContext } from './usePageUiFormContext';
import { usePageUiEditorCommands } from './usePageUiEditorCommands';

/** This editor coordinates human writes. Agent UI commands are unsaved editing and preview only. */
export function useLeaveApplicationForm() {
  const { t } = useTranslation(), { user } = useAuth();
  const { allowed, hasPermission } = usePermission(['route:leave-application', 'leave:create']);
  const router = useRouter(), rawId = useSearchParams().get('id'), editingId = parseRecordId(rawId);
  const [form] = Form.useForm<LeaveFormValues>();
  const query = useCallback(async () => {
    if (!allowed) return null;
    if (rawId !== null && !editingId) throw new Error(t('approval.form.notEditableDraft'));
    const [context, application] = await Promise.all([leaveApi.approvalContext(), editingId ? leaveApi.detail(editingId) : Promise.resolve(null)]);
    if (application && (application.id !== editingId || application.applicantUserId !== user?.id || application.status !== 'DRAFT' || !application.canEdit))
      throw new Error(t('approval.form.notEditableDraft'));
    return { context, application, values: application ? restoreLeaveValues(application) : initialLeaveValues(context.approverUserId) };
  }, [allowed, editingId, rawId, t, user?.id]);
  const overview = useScopedOverview(query), action = useAsyncFormAction(overview.scope);
  const [saved, setSaved] = useState<{ scope: string; application: LeaveApplication }>();
  const application = saved?.scope === overview.scope ? saved.application : overview.data?.application ?? null;
  const context = allowed ? overview.data?.context ?? null : null;
  const initialValues = overview.data?.values;
  useEffect(() => { form.resetFields(); if (initialValues) form.setFieldsValue(initialValues); }, [overview.scope, initialValues, form]);
  const [revision, setRevision] = useState(0);
  const formScope = `${overview.scope}:${application?.id}:${application?.version}:${revision}`;
  const confirm = useScopedModalConfirm(formScope), { capture } = useAsyncActionGuard(formScope);
  const [confirmation, setConfirmation] = useState<string>();
  const confirming = confirmation === formScope;
  const editable = allowed && Boolean(context) && !overview.loading && overview.error === undefined
    && (!application || application.status === 'DRAFT' && application.canEdit);
  const uiForm = usePageUiFormContext(editable && !action.busy && !confirming, application?.id, application?.version, formScope);
  const changed = () => { setRevision(value => value + 1); uiForm.renew(); };
  const values = Form.useWatch([], form);
  const duration = useMemo(() => calculateHalfDays(values), [values]);
  const activeCandidates = editable && !action.busy && !confirming;
  const [candidates, setCandidates] = useState<{ scope: string; rows: Map<number, { value: ApproverCandidate; ticket: number }> }>();
  const candidateRequest = useRef(0);
  const candidateScope = overview.scope, candidateCurrent = overview.isCurrent;
  const queryCandidates = useCallback(async (params: { keyword?: string; page: number; size: number }) => {
    if (!candidateCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    const ticket = ++candidateRequest.current;
    const result = await leaveApi.approverCandidates(params);
    if (!candidateCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    setCandidates(previous => {
      if (!candidateCurrent()) return previous;
      const rows = new Map(previous?.scope === candidateScope ? previous.rows : []);
      result.records.forEach(value => { if ((rows.get(value.id)?.ticket ?? 0) < ticket) rows.set(value.id, { value, ticket }); });
      return { scope: candidateScope, rows };
    });
    return { records: result.records.map(value => ({ id: value.id, name: candidateLabel(value) })), total: result.total };
  }, [candidateCurrent, candidateScope]);
  const selectedId = values?.approverUserId;
  const selectedApprover: ApproverCandidate | null = selectedId ? (candidates?.scope === overview.scope ? candidates.rows.get(selectedId)?.value : undefined)
    ?? { id: selectedId, name: (application?.approverUserId === selectedId ? application.approverName : context?.approverUserId === selectedId ? context.approverName : undefined) || `#${selectedId}`, recommended: false } : null;
  const labels = { leaveType: t('approval.form.leaveTypeLabel'), approverUserId: t('approval.form.approverLabel'),
    startDate: t('approval.form.startTime'), endDate: t('approval.form.endTime'), startPeriod: t('approval.form.startPeriodLabel'),
    endPeriod: t('approval.form.endPeriodLabel'), reason: t('approval.form.reasonLabel') };
  usePublishPageAgentContext({ applicationId: application?.id?.toString(), version: application?.version, status: application?.status });
  usePageUiEditorCommands({ form, open: activeCandidates, canEdit: activeCandidates, context: uiForm.context,
    create: () => { if (!overview.isCurrent()) throw new Error('PAGE_UI_FORM_CLOSED'); changed();
      if (application) router.push('/oa/leave-application'); else { form.resetFields(); form.setFieldsValue(initialLeaveValues(context?.approverUserId)); } },
    fields: { leaveType: LEAVE_TYPE_KEYS, approverUserId: 'number', startDate: { date: 'date' }, endDate: { date: 'date' }, startPeriod: PERIOD_KEYS, endPeriod: PERIOD_KEYS, reason: 'string' }, labels });
  const liveApplication = useRef(application); liveApplication.current = application;
  const remember = (result: LeaveApplication) => { liveApplication.current = result; setSaved({ scope: overview.scope, application: result }); };
  const validateReceipt = (result: LeaveApplication, status: 'DRAFT' | 'PENDING', payload: LeaveApplicationPayload, previous?: LeaveApplication | null) => {
    if (!Number.isSafeInteger(result.id) || result.id < 1 || result.applicantUserId !== user?.id || result.status !== status
        || !Number.isSafeInteger(result.version) || result.version < 0 || previous && (result.id !== previous.id || result.version <= previous.version)
        || result.leaveType !== payload.leaveType || result.startDate !== payload.startDate || result.endDate !== payload.endDate
        || result.startPeriod !== payload.startPeriod || result.endPeriod !== payload.endPeriod || result.reason !== payload.reason
        || status === 'DRAFT' && result.approverUserId !== payload.approverUserId)
      throw new Error(t('approval.genericApplications.invalidResult'));
    return result;
  };
  const persist = async (valid: LeaveFormValues, previous: LeaveApplication | null) => {
    const payload = leaveFormPayload(valid, previous?.version);
    return validateReceipt(previous ? await leaveApi.update(previous.id, payload) : await leaveApi.create(payload), 'DRAFT', payload, previous);
  };
  const saveDraft = async () => {
    if (!editable || confirming || !overview.isCurrent()) return;
    const write = action.begin(); if (!write) return;
    try {
      const valid = await form.validateFields();
      if (!write.current() || !overview.isCurrent()) return;
      const result = await persist(valid, liveApplication.current);
      if (!write.current() || !overview.isCurrent()) return;
      remember(result); message.success(t('approval.form.draftSaved'));
      if (!editingId) router.replace(`/oa/leave-application?id=${result.id}`);
    } catch (error) { if (write.current() && overview.isCurrent() && !isFormValidationError(error)) message.error(formatOaApiError(error)); }
    finally { write.finish(); }
  };
  const validating = useRef(false);
  const submit = async () => {
    if (!editable || action.busy || confirming || validating.current || !overview.isCurrent() || application && !application.canSubmit) return;
    const current = capture(); validating.current = true;
    try {
      const valid = await form.validateFields();
      if (!current() || !overview.isCurrent()) return;
      const payload = leaveFormPayload(valid), previous = liveApplication.current;
      setConfirmation(formScope);
      confirm({ title: t('approval.form.submitConfirmTitle'), okText: t('approval.form.submitConfirmOk'), cancelText: t('approval.form.submitConfirmCancel'),
        content: <Descriptions column={1} size="small" items={[
          { key: 'type', label: labels.leaveType, children: t(`approval.leaveType.${payload.leaveType}`) },
          { key: 'approver', label: labels.approverUserId, children: `${selectedApprover?.name || ''} (#${payload.approverUserId})` },
          { key: 'from', label: labels.startDate, children: `${payload.startDate} ${t(`approval.period.${payload.startPeriod}`)}` },
          { key: 'to', label: labels.endDate, children: `${payload.endDate} ${t(`approval.period.${payload.endPeriod}`)}` },
          { key: 'reason', label: labels.reason, children: payload.reason },
        ]} />,
        afterClose: () => { if (current()) setConfirmation(undefined); },
        onOk: async () => {
          if (!current() || !overview.isCurrent()) return;
          const write = action.begin(); if (!write) return;
          try {
            const stored = await persist(valid, previous);
            if (!write.current() || !overview.isCurrent()) return;
            remember(stored);
            const result = await leaveApi.submit(stored.id, stored.version);
            if (!write.current() || !overview.isCurrent()) return;
            validateReceipt(result, 'PENDING', payload, stored); remember(result); message.success(t('approval.form.submitSuccess'));
            router.push(hasPermission('route:my-applications') && hasPermission('leave:read:self') ? '/oa/my-applications' : '/oa');
          } catch (error) {
            if (write.current() && overview.isCurrent()) {
              message.error(formatOaApiError(error));
              const stored = liveApplication.current;
              // Preserve a known draft across refresh; the editor reloads its real state, never retries submission.
              if (!editingId && stored?.status === 'DRAFT') router.replace(`/oa/leave-application?id=${stored.id}`);
            }
          }
          finally { write.finish(); }
        } });
    } catch (error) { if (current() && overview.isCurrent() && !isFormValidationError(error)) message.error(formatOaApiError(error)); }
    finally { validating.current = false; }
  };
  return { form, application, context, values, duration, selectedApprover, allowed, editable, loading: overview.loading, error: overview.error,
    load: overview.load, scope: overview.scope, saving: action.busy, confirming, changed, queryCandidates, activeCandidates, saveDraft, submit };
}
function candidateLabel(value: ApproverCandidate) { return [value.name, value.departmentName, value.positionName].filter(Boolean).join(' · '); }

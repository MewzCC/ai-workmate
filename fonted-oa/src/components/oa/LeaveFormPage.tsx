'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Alert,
  Avatar,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  formatOaApiError,
  leaveApi,
  type HalfDayPeriod,
  type ApproverCandidate,
  type LeaveApplication,
  type LeaveApplicationPayload,
  type LeaveApprovalContext,
  type LeaveType,
} from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import LeaveWorkflowPanel from './LeaveWorkflowPanel';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';

interface FormValues {
  leaveType: LeaveType;
  approverUserId: number;
  startDate: Dayjs;
  startPeriod: HalfDayPeriod;
  endDate: Dayjs;
  endPeriod: HalfDayPeriod;
  reason: string;
}

const LEAVE_TYPE_KEYS: LeaveType[] = [
  'ANNUAL', 'PERSONAL', 'SICK', 'MARRIAGE', 'MATERNITY',
  'PATERNITY', 'BEREAVEMENT', 'COMPENSATORY', 'OTHER',
];

const PERIOD_KEYS: HalfDayPeriod[] = ['AM', 'PM'];

export default function LeaveFormPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useTranslation();
  const editingId = Number(searchParams.get('id')) || undefined;
  const [form] = Form.useForm<FormValues>();
  const [application, setApplication] = useState<LeaveApplication | null>(null);
  const [context, setContext] = useState<LeaveApprovalContext | null>(null);
  const [approvers, setApprovers] = useState<ApproverCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const values = Form.useWatch([], form);

  const leaveTypeOptions = LEAVE_TYPE_KEYS.map((value) => ({
    value,
    label: t(`approval.leaveType.${value}`),
  }));
  const periodOptions = PERIOD_KEYS.map((value) => ({
    value,
    label: t(`approval.period.${value}`),
  }));

  useEffect(() => {
    const today = dayjs();
    form.setFieldsValue({
      leaveType: 'ANNUAL',
      startDate: today,
      startPeriod: 'AM',
      endDate: today,
      endPeriod: 'PM',
    });

    const requests: Promise<unknown>[] = [
      Promise.all([leaveApi.approvalContext(), leaveApi.approverCandidates()]).then(([nextContext, page]) => {
        setContext(nextContext);
        setApprovers(page.records);
        if (!editingId && nextContext.approverUserId) {
          form.setFieldValue('approverUserId', nextContext.approverUserId);
        }
      }),
    ];
    if (editingId) {
      requests.push(
        leaveApi.detail(editingId).then((item) => {
          if (!item.canEdit) throw new Error(t('approval.form.notEditableDraft'));
          setApplication(item);
          form.setFieldsValue({
            leaveType: item.leaveType,
            approverUserId: item.approverUserId,
            startDate: dayjs(item.startDate),
            startPeriod: item.startPeriod,
            endDate: dayjs(item.endDate),
            endPeriod: item.endPeriod,
            reason: item.reason,
          });
        }),
      );
    }
    Promise.all(requests)
      .catch((error) => message.error(error instanceof Error ? error.message : formatOaApiError(error)))
      .finally(() => setLoading(false));
  }, [editingId, form]);

  const duration = useMemo(() => calculateHalfDays(values), [values]);
  const selectedApprover = useMemo(
    () => approvers.find((a) => a.id === values?.approverUserId) ?? null,
    [approvers, values?.approverUserId],
  );

  const persist = async (): Promise<LeaveApplication> => {
    const valid = await form.validateFields();
    const payload: LeaveApplicationPayload = {
      leaveType: valid.leaveType,
      approverUserId: valid.approverUserId,
      startDate: valid.startDate.format('YYYY-MM-DD'),
      startPeriod: valid.startPeriod,
      endDate: valid.endDate.format('YYYY-MM-DD'),
      endPeriod: valid.endPeriod,
      reason: valid.reason.trim(),
      version: application?.version,
    };
    return application
      ? leaveApi.update(application.id, payload)
      : leaveApi.create(payload);
  };

  const saveDraft = async () => {
    setSaving(true);
    try {
      const saved = await persist();
      setApplication(saved);
      message.success(t('approval.form.draftSaved'));
      router.replace(`/oa/leave-application?id=${saved.id}`);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    let valid: FormValues;
    try {
      valid = await form.validateFields();
    } catch {
      return;
    }
    Modal.confirm({
      title: t('approval.form.submitConfirmTitle'),
      content: t('approval.form.submitConfirmContent', {
        start: valid.startDate.format('YYYY-MM-DD'),
        startPeriod: periodLabel(valid.startPeriod),
        end: valid.endDate.format('YYYY-MM-DD'),
        endPeriod: periodLabel(valid.endPeriod),
        days: duration / 2,
      }),
      okText: t('approval.form.submitConfirmOk'),
      cancelText: t('approval.form.submitConfirmCancel'),
      onOk: async () => {
        setSaving(true);
        try {
          const saved = await persist();
          const submitted = await leaveApi.submit(saved.id, saved.version);
          setApplication(submitted);
          message.success(t('approval.form.submitSuccess'));
          router.push('/oa/my-applications');
        } catch (error) {
          message.error(formatOaApiError(error));
        } finally {
          setSaving(false);
        }
      },
    });
  };

  return (
    <section className="leave-workbench">
      <header className="leave-page-hero">
        <div>
          <div className="leave-page-hero__kicker">
            <span>OA · LEAVE REQUEST</span>
          </div>
          <Typography.Title level={2}>
            {application ? t('approval.form.titleEdit') : t('approval.form.titleCreate')}
          </Typography.Title>
          <Typography.Paragraph>
            {t('approval.form.heroDescription')}
          </Typography.Paragraph>
        </div>
        <div className="leave-page-hero__serial">
          <span>{t('approval.form.serialLabel')}</span>
          <strong>{application ? `LV-${String(application.id).padStart(6, '0')}` : t('approval.form.serialPending')}</strong>
        </div>
      </header>

      <Spin spinning={loading}>
        <div className="leave-compose-grid">
          <Card className="leave-form-card" variant="borderless">
            <div className="leave-section-title">
              <span className="leave-section-title__index">01</span>
              <div>
                <Typography.Title level={4}>{t('approval.form.sectionApplicationTitle')}</Typography.Title>
                <Typography.Text type="secondary">{t('approval.form.sectionApplicationHint')}</Typography.Text>
              </div>
            </div>

            {!approvers.length && !loading && (
              <Alert
                className="leave-inline-alert"
                showIcon
                type="error"
                title={t('approval.form.approvalChainMissing')}
                description={t('approval.form.approvalChainMissingDesc')}
              />
            )}

            <Form form={form} layout="vertical" className="leave-enterprise-form">
              <Form.Item name="leaveType" label={t('approval.form.leaveTypeLabel')} rules={[{ required: true }]}>
                <Select size="large" options={leaveTypeOptions} placeholder={t('approval.form.leaveTypePlaceholder')} />
              </Form.Item>

              <Form.Item
                name="approverUserId"
                label={t('approval.form.approverLabel')}
                rules={[{ required: true, message: t('approval.form.approverRequired') }]}
                extra={t('approval.form.approverExtra')}
              >
                <Select
                  size="large"
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('approval.form.approverSearchPlaceholder')}
                  notFoundContent={t('approval.form.approverNotFound')}
                  options={approvers.map((approver) => ({
                    value: approver.id,
                    label: `${approver.name} · ${approver.departmentName || t('approval.form.departmentUnset')} · ${approver.positionName || t('approval.form.positionUnset')}${approver.recommended ? t('approval.form.approverRecommended') : ''}`,
                    avatarUrl: approver.avatarUrl,
                    name: approver.name,
                  }))}
                  optionRender={(option) => (
                    <Space>
                      <Avatar size="small" src={option.data.avatarUrl || undefined}>{option.data.name.slice(0, 1).toUpperCase()}</Avatar>
                      <span>{option.data.label}</span>
                    </Space>
                  )}
                />
              </Form.Item>

              <div className="leave-time-range">
                <div className="leave-time-range__node">
                  <span className="leave-time-range__dot" />
                  <Typography.Text strong>{t('approval.form.startTime')}</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="startDate" rules={[{ required: true, message: t('approval.form.startDateRequired') }]}>
                    <DatePicker
                      size="large"
                      className="leave-control-full"
                      format={t('common.dateFormat')}
                      disabledDate={(current) => isDateBeforeToday(current)}
                      onChange={(date) => {
                        const endDate = form.getFieldValue('endDate');
                        if (date && endDate && endDate.startOf('day').isBefore(date.startOf('day'))) {
                          form.setFieldValue('endDate', date);
                        }
                      }}
                    />
                  </Form.Item>
                  <Form.Item name="startPeriod" rules={[{ required: true }]}>
                    <Select size="large" options={periodOptions} />
                  </Form.Item>
                </div>
                <div className="leave-time-range__line" />
                <div className="leave-time-range__node">
                  <span className="leave-time-range__dot leave-time-range__dot--end" />
                  <Typography.Text strong>{t('approval.form.endTime')}</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="endDate" rules={[{ required: true, message: t('approval.form.endDateRequired') }]}>
                    <DatePicker
                      size="large"
                      className="leave-control-full"
                      format={t('common.dateFormat')}
                      disabledDate={(current) => {
                        const startDate = form.getFieldValue('startDate') as Dayjs | undefined;
                        const minimum = startDate?.startOf('day') || dayjs().startOf('day');
                        return current.startOf('day').isBefore(minimum);
                      }}
                    />
                  </Form.Item>
                  <Form.Item name="endPeriod" rules={[{ required: true }]}>
                    <Select size="large" options={periodOptions} />
                  </Form.Item>
                </div>
              </div>

              <div className={`leave-duration-strip ${duration <= 0 ? 'is-invalid' : ''}`}>
                <div>
                  <OaIcon name="attendance" />
                  <span>{t('approval.form.durationLabel')}</span>
                </div>
                <strong>{duration > 0 ? t('approval.daysCount', { days: duration / 2 }) : t('approval.form.durationInvalid')}</strong>
                <small>{t('approval.form.durationHint')}</small>
              </div>

              <Form.Item
                name="reason"
                label={t('approval.form.reasonLabel')}
                rules={[
                  { required: true, whitespace: true, message: t('approval.form.reasonRequired') },
                  { max: 500 },
                ]}
              >
                <Input.TextArea
                  rows={6}
                  showCount
                  maxLength={500}
                  placeholder={t('approval.form.reasonPlaceholder')}
                />
              </Form.Item>

              <div className="leave-form-footer">
                <Typography.Text type="secondary">
                  {t('approval.form.footerConfirm')}
                </Typography.Text>
                <Space wrap>
                  <Button
                    size="large"
                    icon={<OaIcon name="save" />}
                    loading={saving}
                    onClick={() => void saveDraft()}
                  >
                    {t('approval.form.saveDraft')}
                  </Button>
                  <Button
                    size="large"
                    type="primary"
                    icon={<OaIcon name="send" />}
                    loading={saving}
                    disabled={duration <= 0 || !approvers.length}
                    onClick={() => void submit()}
                  >
                    {t('approval.form.submit')}
                  </Button>
                </Space>
              </div>
            </Form>
          </Card>

          <LeaveWorkflowPanel
            application={application}
            context={context}
            durationDays={duration > 0 ? duration / 2 : 0}
            selectedApprover={selectedApprover}
          />
        </div>
      </Spin>
    </section>
  );
}

export function calculateHalfDays(values?: Partial<FormValues>): number {
  if (!values?.startDate || !values.endDate || !values.startPeriod || !values.endPeriod) return 0;
  const days = values.endDate.startOf('day').diff(values.startDate.startOf('day'), 'day');
  const startSlot = values.startPeriod === 'PM' ? 1 : 0;
  const endSlot = values.endPeriod === 'PM' ? 1 : 0;
  return days * 2 + endSlot - startSlot + 1;
}

export function isDateBeforeToday(date: Dayjs, today: Dayjs = dayjs()): boolean {
  return date.startOf('day').isBefore(today.startOf('day'));
}

function periodLabel(period: HalfDayPeriod) {
  return i18n.t('approval.period.' + period);
}

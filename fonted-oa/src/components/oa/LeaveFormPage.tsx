import { Alert, Button, Card, DatePicker, Form, Input, Select, Space, Spin, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslation } from 'react-i18next';
import { formatOaApiError } from '@/lib/oaApi';
import { LEAVE_TYPE_KEYS, PERIOD_KEYS, calculateHalfDays, isDateBeforeToday } from '@/lib/leaveFormValues';
import { useLeaveApplicationForm } from '@/hooks/useLeaveApplicationForm';
import { OaIcon } from '@/components/OaIcon';
import LeaveWorkflowPanel from './LeaveWorkflowPanel';
import { BusinessUserSelect } from './BusinessUserSelect';

export { calculateHalfDays, isDateBeforeToday } from '@/lib/leaveFormValues';

export default function LeaveFormPage() {
  const { t } = useTranslation();
  const { form, application, context, duration, selectedApprover, allowed, editable, loading, error, load, scope,
    saving, confirming, changed, queryCandidates, activeCandidates, saveDraft, submit } = useLeaveApplicationForm();
  const leaveTypeOptions = LEAVE_TYPE_KEYS.map(value => ({ value, label: t(`approval.leaveType.${value}`) }));
  const periodOptions = PERIOD_KEYS.map(value => ({ value, label: t(`approval.period.${value}`) }));
  if (!allowed) return <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />;
  if (error !== undefined) return <Card><Alert type="error" showIcon title={formatOaApiError(error)}
    action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} /></Card>;

  return (
    <section className="leave-workbench">
      <header className="leave-page-hero">
        <div>
          <div className="leave-page-hero__kicker">
            <span>{t('approval.form.kicker')}</span>
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

            <Form form={form} layout="vertical" className="leave-enterprise-form" disabled={!editable || saving || confirming} onValuesChange={changed}>
              <Form.Item name="leaveType" label={t('approval.form.leaveTypeLabel')} rules={[{ required: true }]}>
                <Select size="large" options={leaveTypeOptions} placeholder={t('approval.form.leaveTypePlaceholder')} />
              </Form.Item>

              <Form.Item
                name="approverUserId"
                label={t('approval.form.approverLabel')}
                rules={[{ required: true, message: t('approval.form.approverRequired') }]}
                extra={t('approval.form.approverPagingHint')}
              >
                <BusinessUserSelect active={activeCandidates} scopeKey={scope} query={queryCandidates}
                  selectedOption={selectedApprover ? { id: selectedApprover.id, name: selectedApprover.name } : undefined}
                  placeholder={t('approval.form.approverSearchPlaceholder')} />
              </Form.Item>

              <div className="leave-time-range">
                <div className="leave-time-range__node">
                  <span className="leave-time-range__dot" />
                  <Typography.Text strong>{t('approval.form.startTime')}</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="startDate" rules={[{ required: true, message: t('approval.form.startDateRequired') },
                    { validator: (_, value: Dayjs | undefined) => !value || value.isValid() && !isDateBeforeToday(value)
                      ? Promise.resolve() : Promise.reject(new Error(t('approval.form.startInPast'))) } ]}>
                    <DatePicker
                      aria-label={t('approval.form.startTime')}
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
                    <Select aria-label={t('approval.form.startPeriodLabel')} size="large" options={periodOptions} />
                  </Form.Item>
                </div>
                <div className="leave-time-range__line" />
                <div className="leave-time-range__node">
                  <span className="leave-time-range__dot leave-time-range__dot--end" />
                  <Typography.Text strong>{t('approval.form.endTime')}</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="endDate" dependencies={['startDate', 'startPeriod', 'endPeriod']} rules={[{ required: true, message: t('approval.form.endDateRequired') },
                    { validator: () => calculateHalfDays(form.getFieldsValue()) > 0 ? Promise.resolve() : Promise.reject(new Error(t('approval.form.durationInvalid'))) } ]}>
                    <DatePicker
                      aria-label={t('approval.form.endTime')}
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
                    <Select aria-label={t('approval.form.endPeriodLabel')} size="large" options={periodOptions} />
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
                    disabled={!editable || saving || confirming}
                    onClick={() => void saveDraft()}
                  >
                    {t('approval.form.saveDraft')}
                  </Button>
                  <Button
                    size="large"
                    type="primary"
                    icon={<OaIcon name="send" />}
                    loading={saving}
                    disabled={!editable || saving || confirming || duration <= 0 || !selectedApprover}
                    onClick={() => void submit()}
                  >
                    {t('approval.form.submit')}
                  </Button>
                </Space>
              </div>
            </Form>
          </Card>

          <LeaveWorkflowPanel
            application={application ? { ...application, approverName: selectedApprover?.name, approverAvatarUrl: selectedApprover?.avatarUrl } : null}
            context={context ? { ...context, approverName: selectedApprover?.name, approverUserId: selectedApprover?.id } : null}
            durationDays={duration > 0 ? duration / 2 : 0}
            selectedApprover={selectedApprover}
          />
        </div>
      </Spin>
    </section>
  );
}

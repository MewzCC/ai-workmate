'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import dayjs from 'dayjs';
import {
  formatOaApiError,
  OaApiError,
  todoApi,
  type LeaveApplication,
  type ApprovalParticipant,
  type WorkflowTimelineItem,
} from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { leaveTypeLabel, periodLabel, StatusTag } from './MyApplicationsPage';
import LeaveWorkflowPanel from './LeaveWorkflowPanel';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';

export default function ApprovalDetailPage({ taskId }: { taskId: number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useTranslation();
  const fromMyApplications = searchParams.get('from') === 'my-applications';
  const fromTodo = searchParams.get('from') === 'todo';
  const [application, setApplication] = useState<LeaveApplication>();
  const [timeline, setTimeline] = useState<WorkflowTimelineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [decision, setDecision] = useState<'approve' | 'reject'>();
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ comment?: string }>();
  const [collaboration, setCollaboration] = useState<'transfer' | 'copy' | 'pre-sign' | 'post-sign'>();
  const [participants, setParticipants] = useState<ApprovalParticipant[]>([]);
  const [participantLoading, setParticipantLoading] = useState(false);
  const [collaborationForm] = Form.useForm<{ targetUserId: number; reason: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [detail, events] = await Promise.all([
        todoApi.detail(taskId),
        todoApi.timeline(taskId),
      ]);
      setApplication(detail);
      setTimeline(events);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => { void load(); }, [load]);

  const { target: returnTarget, label: returnLabel } = useMemo(() => {
    if (fromTodo) return { target: '/oa/todo', label: t('approval.approvalDetail.backToTodo') };
    if (fromMyApplications) return { target: '/oa/my-applications', label: t('approval.approvalDetail.backToMyApplications') };
    if (searchParams.get('from') === 'approval-list') {
      return { target: '/oa/approval-list', label: t('approval.approvalDetail.backToApprovalList') };
    }
    return application?.canApprove
      ? { target: '/oa/todo', label: t('approval.approvalDetail.backToTodo') }
      : { target: '/oa/my-applications', label: t('approval.approvalDetail.backToMyApplications') };
  }, [fromTodo, fromMyApplications, application?.canApprove, searchParams, t]);

  const submitDecision = async () => {
    if (application?.taskVersion == null || application.taskVersion < 0) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (decision === 'reject') {
        await todoApi.reject(taskId, application.taskVersion, values.comment!.trim());
        message.success(t('approval.approvalDetail.rejectSuccess'));
      } else {
        const result = await todoApi.approve(taskId, application.taskVersion, values.comment?.trim());
        message.success(t(result.status === 'PENDING'
          ? 'approval.approvalDetail.approveNextSuccess'
          : 'approval.approvalDetail.approveSuccess'));
      }
      setDecision(undefined);
      form.resetFields();
      await load();
    } catch (error) {
      message.error(formatOaApiError(error));
      if (error instanceof OaApiError && error.status === 409) await load();
    } finally {
      setSaving(false);
    }
  };

  const openCollaboration = async (mode: 'transfer' | 'copy' | 'pre-sign' | 'post-sign') => {
    setCollaboration(mode);
    collaborationForm.resetFields();
    setParticipantLoading(true);
    try {
      setParticipants(await todoApi.participantCandidates(taskId));
    } catch (error) {
      message.error(formatOaApiError(error));
      setCollaboration(undefined);
    } finally {
      setParticipantLoading(false);
    }
  };

  const submitCollaboration = async () => {
    if (!application || application.taskVersion == null || !collaboration) return;
    const values = await collaborationForm.validateFields();
    setSaving(true);
    try {
      if (collaboration === 'transfer') {
        await todoApi.transfer(taskId, values.targetUserId, application.taskVersion, values.reason.trim());
        message.success(t('approval.approvalDetail.transferSuccess'));
        setCollaboration(undefined);
        router.push('/oa/todo');
        return;
      }
      if (collaboration === 'pre-sign' || collaboration === 'post-sign') {
        await todoApi.addSign(
          taskId,
          values.targetUserId,
          application.taskVersion,
          collaboration === 'pre-sign' ? 'PRE' : 'POST',
          values.reason.trim(),
        );
        message.success(t(collaboration === 'pre-sign'
          ? 'approval.approvalDetail.preSignSuccess'
          : 'approval.approvalDetail.postSignSuccess'));
        setCollaboration(undefined);
        collaborationForm.resetFields();
        if (collaboration === 'pre-sign') {
          router.push('/oa/todo');
          return;
        }
        await load();
        return;
      }
      await todoApi.copyTo(taskId, values.targetUserId, application.taskVersion, values.reason.trim());
      message.success(t('approval.approvalDetail.copySuccess'));
      setCollaboration(undefined);
      collaborationForm.resetFields();
      await load();
    } catch (error) {
      message.error(formatOaApiError(error));
      if (error instanceof OaApiError && error.status === 409) await load();
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="approval-workbench">
      <header className="approval-detail-hero">
        <Button
          type="text"
          icon={<OaIcon name="previous" />}
          onClick={() => router.push(returnTarget)}
        >
          {returnLabel}
        </Button>
        <div className="approval-detail-hero__title">
          <span>LEAVE APPROVAL · #{taskId}</span>
          <Typography.Title level={2}>{t('approval.approvalDetail.title')}</Typography.Title>
        </div>
        {application && <StatusTag status={application.status} />}
      </header>

      <Spin spinning={loading}>
        {!application ? (
          <Card className="leave-empty-card">
            <Empty description={t('approval.approvalDetail.empty')} />
          </Card>
        ) : (
          <div className="approval-detail-layout">
            <main className="approval-detail-main">
              <Card className="approval-summary-card" variant="borderless">
                <div className="approval-applicant">
                  <Avatar size={54} src={application.applicantAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                  <div>
                    <Typography.Text type="secondary">{t('approval.approvalDetail.applicantLabel')}</Typography.Text>
                    <Typography.Title level={4}>{application.applicantName}</Typography.Title>
                    <Typography.Text type="secondary">
                      {t('approval.approvalDetail.submittedAt', { time: formatDateTime(application.submittedAt || application.createdAt) })}
                    </Typography.Text>
                  </div>
                  <div className="approval-summary-card__duration">
                    <span>{t('approval.approvalDetail.durationLabel')}</span>
                    <strong>{application.durationDays}</strong>
                    <small>{t('approval.dayUnit')}</small>
                  </div>
                </div>

                {!application.canApprove && application.status === 'PENDING' && (
                  <Alert
                    className="leave-inline-alert"
                    showIcon
                    type="warning"
                    message={t('approval.approvalDetail.notAssigneeTitle')}
                    description={t('approval.approvalDetail.notAssigneeDesc')}
                  />
                )}

                <Descriptions
                  className="approval-business-details"
                  column={{ xs: 1, sm: 2 }}
                  bordered
                >
                  <Descriptions.Item label={t('approval.approvalDetail.descApplicationNo')}>
                    LV-{String(application.id).padStart(6, '0')}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descLeaveType')}>
                    <Tag bordered={false}>{leaveTypeLabel(application.leaveType)}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descStartTime')}>
                    {application.startDate} {periodLabel(application.startPeriod)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descEndTime')}>
                    {application.endDate} {periodLabel(application.endPeriod)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descApprover')}>
                    <Space>
                      <Avatar size="small" src={application.approverAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                      {application.approverName || t('approval.approvalDetail.approverUnset')}
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descDueAt')}>
                    {application.taskDueAt ? formatDateTime(application.taskDueAt) : '-'}
                    {application.overdue && <Tag color="error">{t('approval.approvalDetail.overdue')}</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descReminder')}>
                    {t('approval.approvalDetail.reminderCount', { count: application.reminderCount })}
                    {application.lastRemindedAt && (
                      <Typography.Text type="secondary">
                        {' · '}{formatDateTime(application.lastRemindedAt)}
                      </Typography.Text>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descReason')} span={2}>
                    <Typography.Paragraph className="approval-reason">
                      {application.reason}
                    </Typography.Paragraph>
                  </Descriptions.Item>
                </Descriptions>
              </Card>

              <Card
                className="approval-history-card"
                variant="borderless"
                title={(
                  <div className="leave-card-title">
                    <OaIcon name="history" />
                    <span>{t('approval.approvalDetail.historyTitle')}</span>
                    <Tag bordered={false}>{t('approval.approvalDetail.historyCount', { count: timeline.length })}</Tag>
                  </div>
                )}
              >
                {timeline.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.approvalDetail.historyEmpty')} />
                ) : (
                  <Timeline
                    items={timeline.map((item) => ({
                      color: timelineColor(item.toStatus),
                      dot: <span className={`approval-timeline-dot is-${item.toStatus.toLowerCase()}`} />,
                      children: (
                        <article className="approval-history-item">
                          <div className="approval-history-item__head">
                            <Typography.Text strong>{actionLabel(item.action)}</Typography.Text>
                            <Typography.Text type="secondary">
                              {formatDateTime(item.createdAt)}
                            </Typography.Text>
                          </div>
                          <Space size={8} wrap>
                            <Space size={6}>
                              <Avatar size="small" src={item.actorAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                              <Tag bordered={false}>{item.actorName}</Tag>
                            </Space>
                            <Typography.Text type="secondary">
                              {statusLabel(item.fromStatus)} → {statusLabel(item.toStatus)}
                            </Typography.Text>
                          </Space>
                          {item.comment && (
                            <blockquote>{item.comment}</blockquote>
                          )}
                          {item.targetUserName && (
                            <Typography.Text type="secondary">
                              {t('approval.approvalDetail.participantChange', {
                                from: item.originalAssigneeName || item.actorName,
                                to: item.targetUserName,
                              })}
                            </Typography.Text>
                          )}
                        </article>
                      ),
                    }))}
                  />
                )}
              </Card>

              {application.canApprove && (
                <div className="approval-decision-bar">
                  <div>
                    <Typography.Text strong>{t('approval.approvalDetail.decisionBarTitle')}</Typography.Text>
                    <Typography.Text type="secondary">
                      {t('approval.approvalDetail.decisionBarHint')}
                    </Typography.Text>
                  </div>
                  <Space wrap>
                    <Button size="large" onClick={() => void openCollaboration('pre-sign')}>
                      {t('approval.approvalDetail.preSignButton')}
                    </Button>
                    <Button size="large" onClick={() => void openCollaboration('post-sign')}>
                      {t('approval.approvalDetail.postSignButton')}
                    </Button>
                    <Button size="large" onClick={() => void openCollaboration('copy')}>
                      {t('approval.approvalDetail.copyButton')}
                    </Button>
                    <Button size="large" onClick={() => void openCollaboration('transfer')}>
                      {t('approval.approvalDetail.transferButton')}
                    </Button>
                    <Button size="large" danger onClick={() => setDecision('reject')}>
                      {t('approval.approvalDetail.rejectButton')}
                    </Button>
                    <Button
                      size="large"
                      type="primary"
                      icon={<OaIcon name="approval" />}
                      onClick={() => setDecision('approve')}
                    >
                      {t('approval.approvalDetail.approveButton')}
                    </Button>
                  </Space>
                </div>
              )}
            </main>

            <LeaveWorkflowPanel
              application={application}
              durationDays={application.durationDays}
            />
          </div>
        )}
      </Spin>

      <Modal
        className="approval-decision-modal"
        title={decision === 'reject' ? t('approval.approvalDetail.rejectModalTitle') : t('approval.approvalDetail.approveModalTitle')}
        open={Boolean(decision)}
        okText={decision === 'reject' ? t('approval.approvalDetail.rejectModalOk') : t('approval.approvalDetail.approveModalOk')}
        okButtonProps={{ danger: decision === 'reject', loading: saving }}
        onCancel={() => { setDecision(undefined); form.resetFields(); }}
        onOk={() => void submitDecision()}
      >
        <Alert
          showIcon
          type={decision === 'reject' ? 'warning' : 'info'}
          message={decision === 'reject'
            ? t('approval.approvalDetail.rejectAlertMessage')
            : t('approval.approvalDetail.approveAlertMessage')}
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="comment"
            label={t('approval.approvalDetail.commentLabel')}
            rules={[
              { required: decision === 'reject', whitespace: true, message: t('approval.approvalDetail.commentRejectRequired') },
              { max: 500 },
            ]}
          >
            <Input.TextArea
              rows={5}
              showCount
              maxLength={500}
              placeholder={decision === 'reject' ? t('approval.approvalDetail.commentRejectPlaceholder') : t('approval.approvalDetail.commentApprovePlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t(`approval.approvalDetail.${collaboration === 'transfer'
          ? 'transferModalTitle'
          : collaboration === 'pre-sign'
            ? 'preSignModalTitle'
            : collaboration === 'post-sign'
              ? 'postSignModalTitle'
              : 'copyModalTitle'}`)}
        open={Boolean(collaboration)}
        okText={t(`approval.approvalDetail.${collaboration === 'transfer'
          ? 'transferModalOk'
          : collaboration === 'pre-sign'
            ? 'preSignModalOk'
            : collaboration === 'post-sign'
              ? 'postSignModalOk'
              : 'copyModalOk'}`)}
        confirmLoading={saving}
        onCancel={() => { setCollaboration(undefined); collaborationForm.resetFields(); }}
        onOk={() => void submitCollaboration()}
      >
        <Alert
          showIcon
          type={collaboration === 'transfer' || collaboration === 'pre-sign' ? 'warning' : 'info'}
          message={t(`approval.approvalDetail.${collaboration === 'transfer'
            ? 'transferAlert'
            : collaboration === 'pre-sign'
              ? 'preSignAlert'
              : collaboration === 'post-sign'
                ? 'postSignAlert'
                : 'copyAlert'}`)}
        />
        <Form form={collaborationForm} layout="vertical">
          <Form.Item
            name="targetUserId"
            label={t('approval.approvalDetail.participantLabel')}
            rules={[{ required: true, message: t('approval.approvalDetail.participantRequired') }]}
          >
            <Select
              showSearch
              loading={participantLoading}
              optionFilterProp="label"
              placeholder={t('approval.approvalDetail.participantPlaceholder')}
              options={participants
                .filter((item) => collaboration === 'copy' || item.canApprove)
                .map((item) => ({ value: item.id, label: item.name }))}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label={t('approval.approvalDetail.collaborationReasonLabel')}
            rules={[
              { required: true, whitespace: true, message: t('approval.approvalDetail.collaborationReasonRequired') },
              { max: 500 },
            ]}
          >
            <Input.TextArea
              rows={4}
              showCount
              maxLength={500}
              placeholder={t('approval.approvalDetail.collaborationReasonPlaceholder')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

function actionLabel(action: string) {
  const key = 'approval.action.' + action;
  const translated = i18n.t(key);
  return translated === key ? action : translated;
}

function statusLabel(status?: string) {
  if (!status) return i18n.t('approval.statusStart');
  const key = 'approval.status.' + status;
  const translated = i18n.t(key);
  return translated === key ? status : translated;
}

function timelineColor(status: string) {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  if (status === 'WITHDRAWN') return 'orange';
  return 'blue';
}

function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

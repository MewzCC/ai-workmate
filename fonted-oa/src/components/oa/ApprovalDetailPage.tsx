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
  type WorkflowTimelineItem,
} from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { leaveTypeLabel, periodLabel, StatusTag } from './MyApplicationsPage';
import LeaveWorkflowPanel from './LeaveWorkflowPanel';

export default function ApprovalDetailPage({ taskId }: { taskId: number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromMyApplications = searchParams.get('from') === 'my-applications';
  const fromTodo = searchParams.get('from') === 'todo';
  const [application, setApplication] = useState<LeaveApplication>();
  const [timeline, setTimeline] = useState<WorkflowTimelineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [decision, setDecision] = useState<'approve' | 'reject'>();
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ comment?: string }>();

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
    if (fromTodo) return { target: '/oa/todo', label: '返回待办' };
    if (fromMyApplications) return { target: '/oa/my-applications', label: '返回我的申请' };
    return application?.canApprove
      ? { target: '/oa/todo', label: '返回待办' }
      : { target: '/oa/my-applications', label: '返回我的申请' };
  }, [fromTodo, fromMyApplications, application?.canApprove]);

  const submitDecision = async () => {
    if (application?.taskVersion == null || application.taskVersion < 0) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (decision === 'reject') {
        await todoApi.reject(taskId, application.taskVersion, values.comment!.trim());
        message.success('申请已退回，流程已结束');
      } else {
        await todoApi.approve(taskId, application.taskVersion, values.comment?.trim());
        message.success('申请已通过，流程已归档');
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
          <Typography.Title level={2}>请假审批详情</Typography.Title>
        </div>
        {application && <StatusTag status={application.status} />}
      </header>

      <Spin spinning={loading}>
        {!application ? (
          <Card className="leave-empty-card">
            <Empty description="未找到可访问的审批任务" />
          </Card>
        ) : (
          <div className="approval-detail-layout">
            <main className="approval-detail-main">
              <Card className="approval-summary-card" bordered={false}>
                <div className="approval-applicant">
                  <Avatar size={54} icon={<OaIcon name="user" />} />
                  <div>
                    <Typography.Text type="secondary">申请人</Typography.Text>
                    <Typography.Title level={4}>{application.applicantName}</Typography.Title>
                    <Typography.Text type="secondary">
                      于 {formatDateTime(application.submittedAt || application.createdAt)} 发起
                    </Typography.Text>
                  </div>
                  <div className="approval-summary-card__duration">
                    <span>本次请假</span>
                    <strong>{application.durationDays}</strong>
                    <small>天</small>
                  </div>
                </div>

                {!application.canApprove && application.status === 'PENDING' && (
                  <Alert
                    className="leave-inline-alert"
                    showIcon
                    type="warning"
                    message="当前账号不是该任务的受理人"
                    description="你可以查看流程，但不能代替当前审批人进行处理。"
                  />
                )}

                <Descriptions
                  className="approval-business-details"
                  column={{ xs: 1, sm: 2 }}
                  bordered
                >
                  <Descriptions.Item label="申请编号">
                    LV-{String(application.id).padStart(6, '0')}
                  </Descriptions.Item>
                  <Descriptions.Item label="请假类型">
                    <Tag bordered={false}>{leaveTypeLabel(application.leaveType)}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="开始时间">
                    {application.startDate} {periodLabel(application.startPeriod)}
                  </Descriptions.Item>
                  <Descriptions.Item label="结束时间">
                    {application.endDate} {periodLabel(application.endPeriod)}
                  </Descriptions.Item>
                  <Descriptions.Item label="当前审批人">
                    {application.approverName || '未配置'}
                  </Descriptions.Item>
                  <Descriptions.Item label="审批截止">
                    {application.taskDueAt ? formatDateTime(application.taskDueAt) : '-'}
                    {application.overdue && <Tag color="error">已超时</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label="请假事由" span={2}>
                    <Typography.Paragraph className="approval-reason">
                      {application.reason}
                    </Typography.Paragraph>
                  </Descriptions.Item>
                </Descriptions>
              </Card>

              <Card
                className="approval-history-card"
                bordered={false}
                title={(
                  <div className="leave-card-title">
                    <OaIcon name="history" />
                    <span>流程处理记录</span>
                    <Tag bordered={false}>{timeline.length} 条</Tag>
                  </div>
                )}
              >
                {timeline.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无处理记录" />
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
                            <Tag bordered={false} icon={<OaIcon name="user" />}>
                              {item.actorName}
                            </Tag>
                            <Typography.Text type="secondary">
                              {statusLabel(item.fromStatus)} → {statusLabel(item.toStatus)}
                            </Typography.Text>
                          </Space>
                          {item.comment && (
                            <blockquote>{item.comment}</blockquote>
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
                    <Typography.Text strong>处理当前审批节点</Typography.Text>
                    <Typography.Text type="secondary">
                      退回意见必填，通过意见可选
                    </Typography.Text>
                  </div>
                  <Space>
                    <Button size="large" danger onClick={() => setDecision('reject')}>
                      退回申请
                    </Button>
                    <Button
                      size="large"
                      type="primary"
                      icon={<OaIcon name="approval" />}
                      onClick={() => setDecision('approve')}
                    >
                      审批通过
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
        title={decision === 'reject' ? '退回请假申请' : '通过请假申请'}
        open={Boolean(decision)}
        okText={decision === 'reject' ? '确认退回' : '确认通过'}
        okButtonProps={{ danger: decision === 'reject', loading: saving }}
        onCancel={() => { setDecision(undefined); form.resetFields(); }}
        onOk={() => void submitDecision()}
      >
        <Alert
          showIcon
          type={decision === 'reject' ? 'warning' : 'info'}
          message={decision === 'reject'
            ? '退回后流程立即结束，申请人需重新创建申请'
            : '通过后流程立即完成并写入审计记录'}
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[
              { required: decision === 'reject', whitespace: true, message: '退回时必须填写审批意见' },
              { max: 500 },
            ]}
          >
            <Input.TextArea
              rows={5}
              showCount
              maxLength={500}
              placeholder={decision === 'reject' ? '请清晰说明退回原因' : '可填写补充说明或工作安排'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

function actionLabel(action: string) {
  const labels: Record<string, string> = {
    SUBMIT: '提交申请',
    APPROVE: '审批通过',
    REJECT: '退回申请',
    WITHDRAW: '撤回申请',
  };
  return labels[action] || action;
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已退回',
    WITHDRAWN: '已撤回',
  };
  return status ? labels[status] || status : '开始';
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

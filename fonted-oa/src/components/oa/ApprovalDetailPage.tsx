'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import { Alert, Button, Card, Descriptions, Empty, Form, Input, Modal, Space, Spin, Tag, Timeline, Typography } from 'antd';
import { message } from '@/lib/antdMessage';
import {
  formatOaApiError,
  OaApiError,
  todoApi,
  type LeaveApplication,
  type WorkflowTimelineItem,
} from '@/lib/oaApi';
import { leaveTypeLabel, periodLabel, StatusTag } from './MyApplicationsPage';

export default function ApprovalDetailPage({ taskId }: { taskId: number }) {
  const router = useRouter();
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

  const submitDecision = async () => {
    if (application?.taskVersion == null || application.taskVersion < 0) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (decision === 'reject') {
        await todoApi.reject(taskId, application.taskVersion, values.comment!.trim());
        message.success('申请已退回');
      } else {
        await todoApi.approve(taskId, application.taskVersion, values.comment?.trim());
        message.success('申请已通过');
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
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>审批详情</Typography.Title>
          <Typography.Paragraph type="secondary">待办 #{taskId}</Typography.Paragraph>
        </div>
        <Button onClick={() => router.push('/oa/todo')}>返回我的待办</Button>
      </div>
      <Spin spinning={loading}>
        {!application ? <Empty description="未找到可访问的审批任务" /> : (
          <div className="oa-detail-grid">
            <Card className="oa-domain-card" title="申请信息">
              {!application.canApprove && application.status === 'PENDING' && (
                <Alert showIcon type="warning" title="当前用户不能处理该申请" />
              )}
              <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
                <Descriptions.Item label="申请人">{application.applicantName}</Descriptions.Item>
                <Descriptions.Item label="状态"><StatusTag status={application.status} /></Descriptions.Item>
                <Descriptions.Item label="请假类型">{leaveTypeLabel(application.leaveType)}</Descriptions.Item>
                <Descriptions.Item label="请假天数">{application.durationDays} 天</Descriptions.Item>
                <Descriptions.Item label="开始时间">
                  {application.startDate} {periodLabel(application.startPeriod)}
                </Descriptions.Item>
                <Descriptions.Item label="结束时间">
                  {application.endDate} {periodLabel(application.endPeriod)}
                </Descriptions.Item>
                <Descriptions.Item label="请假原因" span={2}>{application.reason}</Descriptions.Item>
              </Descriptions>
              {application.canApprove && (
                <Space className="oa-detail-actions">
                  <Button type="primary" onClick={() => setDecision('approve')}>通过</Button>
                  <Button danger onClick={() => setDecision('reject')}>退回</Button>
                </Space>
              )}
            </Card>
            <Card className="oa-domain-card" title="审批时间线">
              <Timeline
                items={timeline.map((item) => ({
                  color: item.toStatus === 'APPROVED' ? 'green'
                    : item.toStatus === 'REJECTED' ? 'red'
                      : item.toStatus === 'WITHDRAWN' ? 'orange' : 'blue',
                  children: (
                    <div>
                      <Space wrap>
                        <Typography.Text strong>{actionLabel(item.action)}</Typography.Text>
                        <Tag>{item.actorName}</Tag>
                        <Typography.Text type="secondary">
                          {new Date(item.createdAt).toLocaleString()}
                        </Typography.Text>
                      </Space>
                      {item.comment && <Typography.Paragraph>{item.comment}</Typography.Paragraph>}
                    </div>
                  ),
                }))}
              />
            </Card>
          </div>
        )}
      </Spin>
      <Modal
        title={decision === 'reject' ? '退回申请' : '通过申请'}
        open={Boolean(decision)}
        okText={decision === 'reject' ? '确认退回' : '确认通过'}
        okButtonProps={{ danger: decision === 'reject', loading: saving }}
        onCancel={() => { setDecision(undefined); form.resetFields(); }}
        onOk={() => void submitDecision()}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[
              { required: decision === 'reject', whitespace: true, message: '退回时必须填写审批意见' },
              { max: 500 },
            ]}
          >
            <Input.TextArea rows={4} showCount maxLength={500}
              placeholder={decision === 'reject' ? '请说明退回原因' : '可选填审批意见'} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

function actionLabel(action: string) {
  const labels: Record<string, string> = {
    SUBMIT: '提交申请', APPROVE: '审批通过', REJECT: '退回申请', WITHDRAW: '撤回申请',
  };
  return labels[action] || action;
}

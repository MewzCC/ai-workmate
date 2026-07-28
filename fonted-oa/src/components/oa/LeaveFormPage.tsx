'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Alert,
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

interface FormValues {
  leaveType: LeaveType;
  approverUserId: number;
  startDate: Dayjs;
  startPeriod: HalfDayPeriod;
  endDate: Dayjs;
  endPeriod: HalfDayPeriod;
  reason: string;
}

const leaveTypeOptions = [
  ['ANNUAL', '年假'], ['PERSONAL', '事假'], ['SICK', '病假'],
  ['MARRIAGE', '婚假'], ['MATERNITY', '产假'], ['PATERNITY', '陪产假'],
  ['BEREAVEMENT', '丧假'], ['COMPENSATORY', '调休'], ['OTHER', '其他'],
].map(([value, label]) => ({ value, label }));

const periodOptions = [
  { value: 'AM', label: '上午' },
  { value: 'PM', label: '下午' },
];

export default function LeaveFormPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const editingId = Number(searchParams.get('id')) || undefined;
  const [form] = Form.useForm<FormValues>();
  const [application, setApplication] = useState<LeaveApplication | null>(null);
  const [context, setContext] = useState<LeaveApprovalContext | null>(null);
  const [approvers, setApprovers] = useState<ApproverCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const values = Form.useWatch([], form);

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
          if (!item.canEdit) throw new Error('当前申请不是可编辑草稿');
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
      message.success('请假草稿已保存');
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
      title: '确认提交请假申请？',
      content: `${valid.startDate.format('YYYY-MM-DD')} ${periodLabel(valid.startPeriod)} 至 ${valid.endDate.format('YYYY-MM-DD')} ${periodLabel(valid.endPeriod)}，共 ${duration / 2} 天。提交后将生成唯一审批待办。`,
      okText: '确认提交',
      cancelText: '继续编辑',
      onOk: async () => {
        setSaving(true);
        try {
          const saved = await persist();
          const submitted = await leaveApi.submit(saved.id, saved.version);
          setApplication(submitted);
          message.success('申请已提交，审批流程已启动');
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
            {application ? '继续编辑请假草稿' : '发起请假申请'}
          </Typography.Title>
          <Typography.Paragraph>
            自主选择有效审批人，提交后生成唯一待办，所有处理节点、意见与状态变化完整留痕。
          </Typography.Paragraph>
        </div>
        <div className="leave-page-hero__serial">
          <span>申请编号</span>
          <strong>{application ? `LV-${String(application.id).padStart(6, '0')}` : '保存后生成'}</strong>
        </div>
      </header>

      <Spin spinning={loading}>
        <div className="leave-compose-grid">
          <Card className="leave-form-card" bordered={false}>
            <div className="leave-section-title">
              <span className="leave-section-title__index">01</span>
              <div>
                <Typography.Title level={4}>申请信息</Typography.Title>
                <Typography.Text type="secondary">请确认时间与事由准确，提交后不可修改</Typography.Text>
              </div>
            </div>

            {!approvers.length && !loading && (
              <Alert
                className="leave-inline-alert"
                showIcon
                type="error"
                message="审批链路尚未配置"
                description="请联系管理员配置组织关系并授予审批权限，当前无法保存或提交申请。"
              />
            )}

            <Form form={form} layout="vertical" className="leave-enterprise-form">
              <Form.Item name="leaveType" label="请假类型" rules={[{ required: true }]}>
                <Select size="large" options={leaveTypeOptions} placeholder="选择请假类型" />
              </Form.Item>

              <Form.Item
                name="approverUserId"
                label="本次审批人"
                rules={[{ required: true, message: '请选择审批人' }]}
                extra="候选人来自您所在部门及上级部门，并实时校验审批权限"
              >
                <Select
                  size="large"
                  showSearch
                  optionFilterProp="label"
                  placeholder="输入姓名、部门或岗位搜索"
                  notFoundContent="暂无符合组织与权限规则的审批人"
                  options={approvers.map((approver) => ({
                    value: approver.id,
                    label: `${approver.name} · ${approver.departmentName || '未配置部门'} · ${approver.positionName || '未配置岗位'}${approver.recommended ? '（推荐）' : ''}`,
                  }))}
                />
              </Form.Item>

              <div className="leave-time-range">
                <div className="leave-time-range__node">
                  <span className="leave-time-range__dot" />
                  <Typography.Text strong>开始时间</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="startDate" rules={[{ required: true, message: '请选择开始日期' }]}>
                    <DatePicker
                      size="large"
                      className="leave-control-full"
                      format="YYYY年MM月DD日"
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
                  <Typography.Text strong>结束时间</Typography.Text>
                </div>
                <div className="leave-time-range__fields">
                  <Form.Item name="endDate" rules={[{ required: true, message: '请选择结束日期' }]}>
                    <DatePicker
                      size="large"
                      className="leave-control-full"
                      format="YYYY年MM月DD日"
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
                  <span>系统核算时长</span>
                </div>
                <strong>{duration > 0 ? `${duration / 2} 天` : '时间范围无效'}</strong>
                <small>连续日历半天计算，周末计入</small>
              </div>

              <Form.Item
                name="reason"
                label="请假事由"
                rules={[
                  { required: true, whitespace: true, message: '请输入请假事由' },
                  { max: 500 },
                ]}
              >
                <Input.TextArea
                  rows={6}
                  showCount
                  maxLength={500}
                  placeholder="请说明请假原因及必要的工作交接安排"
                />
              </Form.Item>

              <div className="leave-form-footer">
                <Typography.Text type="secondary">
                  提交即表示确认信息真实准确
                </Typography.Text>
                <Space wrap>
                  <Button
                    size="large"
                    icon={<OaIcon name="save" />}
                    loading={saving}
                    onClick={() => void saveDraft()}
                  >
                    保存草稿
                  </Button>
                  <Button
                    size="large"
                    type="primary"
                    icon={<OaIcon name="send" />}
                    loading={saving}
                    disabled={duration <= 0 || !approvers.length}
                    onClick={() => void submit()}
                  >
                    提交审批
                  </Button>
                </Space>
              </div>
            </Form>
          </Card>

          <LeaveWorkflowPanel
            application={application}
            context={context}
            durationDays={duration > 0 ? duration / 2 : 0}
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
  return period === 'AM' ? '上午' : '下午';
}

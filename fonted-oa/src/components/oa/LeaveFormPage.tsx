'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { Alert, Button, Card, DatePicker, Form, Input, Modal, Select, Space, Spin, Typography } from 'antd';
import { message } from '@/lib/antdMessage';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  formatOaApiError,
  leaveApi,
  type HalfDayPeriod,
  type LeaveApplication,
  type LeaveApplicationPayload,
  type LeaveType,
} from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

interface FormValues {
  leaveType: LeaveType;
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
  const [loading, setLoading] = useState(Boolean(editingId));
  const [saving, setSaving] = useState(false);
  const values = Form.useWatch([], form);

  useEffect(() => {
    if (!editingId) {
      const today = dayjs();
      form.setFieldsValue({
        leaveType: 'ANNUAL',
        startDate: today,
        startPeriod: 'AM',
        endDate: today,
        endPeriod: 'PM',
      });
      return;
    }
    setLoading(true);
    leaveApi.detail(editingId)
      .then((item) => {
        if (!item.canEdit) throw new Error('当前申请不是可编辑草稿');
        setApplication(item);
        form.setFieldsValue({
          leaveType: item.leaveType,
          startDate: dayjs(item.startDate),
          startPeriod: item.startPeriod,
          endDate: dayjs(item.endDate),
          endPeriod: item.endPeriod,
          reason: item.reason,
        });
      })
      .catch((error) => message.error(error instanceof Error ? error.message : formatOaApiError(error)))
      .finally(() => setLoading(false));
  }, [editingId, form]);

  const duration = useMemo(() => calculateHalfDays(values), [values]);

  const persist = async (): Promise<LeaveApplication> => {
    const valid = await form.validateFields();
    const payload: LeaveApplicationPayload = {
      leaveType: valid.leaveType,
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
      content: `${valid.startDate.format('YYYY-MM-DD')} ${periodLabel(valid.startPeriod)} 至 ${valid.endDate.format('YYYY-MM-DD')} ${periodLabel(valid.endPeriod)}，共 ${duration / 2} 天。提交后将生成审批待办。`,
      okText: '确认提交',
      cancelText: '继续编辑',
      onOk: async () => {
        setSaving(true);
        try {
          const saved = await persist();
          const submitted = await leaveApi.submit(saved.id, saved.version);
          setApplication(submitted);
          message.success('申请已提交');
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
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>{application ? '编辑请假草稿' : '请假申请'}</Typography.Title>
          <Typography.Paragraph type="secondary">
            请假时间按连续日历半天计算，周末也计入；最终天数以服务端计算为准。
          </Typography.Paragraph>
        </div>
      </div>
      <Spin spinning={loading}>
        <Card className="oa-domain-card">
          <Alert
            showIcon
            type="info"
            title="最小请假单位为半天"
            description="起止时段均包含在请假区间内，例如同一天上午至上午为 0.5 天。"
          />
          <Form form={form} layout="vertical" className="oa-leave-form">
            <Form.Item name="leaveType" label="请假类型" rules={[{ required: true }]}>
              <Select options={leaveTypeOptions} />
            </Form.Item>
            <div className="oa-form-grid-four">
              <Form.Item name="startDate" label="开始日期" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="startPeriod" label="开始时段" rules={[{ required: true }]}>
                <Select options={periodOptions} />
              </Form.Item>
              <Form.Item name="endDate" label="结束日期" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="endPeriod" label="结束时段" rules={[{ required: true }]}>
                <Select options={periodOptions} />
              </Form.Item>
            </div>
            <Alert
              className="oa-duration-alert"
              type={duration > 0 ? 'success' : 'error'}
              showIcon
              title={duration > 0 ? `预计请假 ${duration / 2} 天` : '结束时间不得早于开始时间'}
            />
            <Form.Item
              name="reason"
              label="请假原因"
              rules={[{ required: true, whitespace: true, message: '请输入请假原因' }, { max: 500 }]}
            >
              <Input.TextArea rows={5} showCount maxLength={500} placeholder="请说明请假原因" />
            </Form.Item>
            <Space wrap>
              <Button icon={<OaIcon name="save" />} loading={saving} onClick={() => void saveDraft()}>
                保存草稿
              </Button>
              <Button type="primary" icon={<OaIcon name="send" />} loading={saving}
                disabled={duration <= 0} onClick={() => void submit()}>
                提交审批
              </Button>
              <Button onClick={() => router.push('/oa/my-applications')}>查看我的申请</Button>
            </Space>
          </Form>
        </Card>
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

function periodLabel(period: HalfDayPeriod) {
  return period === 'AM' ? '上午' : '下午';
}

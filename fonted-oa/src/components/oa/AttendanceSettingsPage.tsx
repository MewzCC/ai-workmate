'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Form, InputNumber, Space, Spin, Switch, Table, Tag, TimePicker, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { message } from '@/lib/antdMessage';
import { attendanceApi } from '@/lib/attendanceApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useAuth } from '@/components/auth/AuthProvider';
import AttendancePageShell from './AttendancePageShell';

interface SettingsFormValues {
  workStartTime: Dayjs;
  workEndTime: Dayjs;
  startFlexMinutes: number;
  endFlexMinutes: number;
  flexLinked: boolean;
}

interface SampleRow {
  key: string;
  inTime: string;
  outTime: string;
  note: 'normal' | 'early' | 'flex' | 'late';
}

function fmtDayjs(v?: Dayjs): string | null {
  return v ? v.format('HH:mm') : null;
}

function toMin(hhmmValue: string): number {
  const [h, m] = hhmmValue.split(':').map(Number);
  return h * 60 + (Number.isNaN(m) ? 0 : m);
}

function fmtMin(minutes: number): string {
  const m = ((Math.round(minutes) % 1440) + 1440) % 1440;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

function durationOf(values: Partial<SettingsFormValues>): number {
  const s = fmtDayjs(values.workStartTime as Dayjs);
  const e = fmtDayjs(values.workEndTime as Dayjs);
  if (!s || !e) return 0;
  return toMin(e) - toMin(s);
}

/** 生成示例：围绕标准上班时间取 提前1h / 准点 / +15 / +30 / +60。 */
function buildSamples(values: Partial<SettingsFormValues>): SampleRow[] {
  const start = fmtDayjs(values.workStartTime as Dayjs);
  const end = fmtDayjs(values.workEndTime as Dayjs);
  const startFlex = Number(values.startFlexMinutes ?? 0);
  const linked = Boolean(values.flexLinked);
  if (!start || !end) return [];
  const startMin = toMin(start);
  const endMin = toMin(end);
  const duration = endMin - startMin;
  const ins = [startMin - 60, startMin, startMin + 15, startMin + 30, startMin + 60].map(fmtMin);

  return ins.map((inTime, i) => {
    const arrival = toMin(inTime);
    const base = linked ? Math.max(arrival, startMin) : startMin;
    const out = linked ? fmtMin(base + duration) : end;
    let note: SampleRow['note'];
    if (arrival < startMin) note = 'early';
    else if (arrival <= startMin + startFlex) note = arrival === startMin ? 'normal' : 'flex';
    else note = 'late';
    return { key: String(i), inTime, outTime: out, note };
  });
}

const NOTE_COLOR: Record<SampleRow['note'], string> = {
  normal: 'green',
  early: 'blue',
  flex: 'orange',
  late: 'red',
};

export default function AttendanceSettingsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'SYSTEM_ADMIN';
  const [form] = Form.useForm<SettingsFormValues>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const start = Form.useWatch('workStartTime', form);
  const end = Form.useWatch('workEndTime', form);
  const startFlex = Form.useWatch('startFlexMinutes', form);
  const endFlex = Form.useWatch('endFlexMinutes', form);
  const flexLinked = Form.useWatch('flexLinked', form);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    attendanceApi
      .getSettings()
      .then((settings) => {
        if (cancelled) return;
        form.setFieldsValue({
          workStartTime: dayjs(settings.workStartTime, 'HH:mm'),
          workEndTime: dayjs(settings.workEndTime, 'HH:mm'),
          startFlexMinutes: settings.startFlexMinutes,
          endFlexMinutes: settings.endFlexMinutes,
          flexLinked: settings.flexLinked,
        });
      })
      .catch((err) => {
        if (!cancelled) message.error(formatOaApiError(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [form]);

  const handleSave = async () => {
    let values: SettingsFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return; // 表单校验错误由 Form.Item 展示
    }
    if (values.workStartTime.isSame(values.workEndTime)) {
      message.warning(t('attendance.settings.workHoursOrderHint'));
      return;
    }
    setSaving(true);
    try {
      await attendanceApi.updateSettings({
        workStartTime: values.workStartTime.format('HH:mm'),
        workEndTime: values.workEndTime.format('HH:mm'),
        startFlexMinutes: values.startFlexMinutes,
        endFlexMinutes: values.endFlexMinutes,
        flexLinked: values.flexLinked,
      });
      message.success(t('attendance.settings.saveSuccess'));
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setSaving(false);
    }
  };

  const startStr = fmtDayjs(start as Dayjs) ?? '--:--';
  const endStr = fmtDayjs(end as Dayjs) ?? '--:--';
  const dur = durationOf({ workStartTime: start as Dayjs, workEndTime: end as Dayjs });
  const refOut = endStr;
  const noLateFrom = fmtMin(toMin(startStr) + Number(startFlex ?? 0));
  const noEarlyFrom = fmtMin(Math.max(0, toMin(refOut) - Number(endFlex ?? 0)));

  const samples = buildSamples({
    workStartTime: start as Dayjs,
    workEndTime: end as Dayjs,
    startFlexMinutes: startFlex as number,
    flexLinked,
  });

  const sampleColumns: ColumnsType<SampleRow> = [
    {
      title: t('attendance.settings.previewIn'),
      dataIndex: 'inTime',
      key: 'inTime',
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    { title: t('attendance.settings.previewOut'), dataIndex: 'outTime', key: 'outTime' },
    {
      title: t('attendance.settings.previewNote'),
      dataIndex: 'note',
      key: 'note',
      render: (n: SampleRow['note']) => (
        <Tag color={NOTE_COLOR[n]}>{t(`attendance.settings.note.${n}`)}</Tag>
      ),
    },
  ];

  const RuleChip = ({ label, value }: { label: string; value: string }) => (
    <div className="oa-clock-chip">
      <span className="oa-clock-chip__label">{label}</span>
      <span className="oa-clock-chip__value">{value}</span>
    </div>
  );

  return (
    <AttendancePageShell
      eyebrow={t('attendance.eyebrow')}
      title={t('attendance.settings.title')}
      description={t('attendance.settings.description')}
    >
      <Spin spinning={loading}>
        <div className="oa-attendance-stack">
          <Card className="oa-attendance-card" title={t('attendance.settings.editTitle')} variant="outlined">
            {!isAdmin && (
              <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={t('attendance.settings.adminOnly')} />
            )}
            <Form form={form} layout="vertical">
              <Space size="large" wrap>
                <Form.Item
                  name="workStartTime"
                  label={t('attendance.settings.workStartTime')}
                  rules={[{ required: true, message: t('attendance.settings.workStartTimeRequired') }]}
                >
                  <TimePicker format="HH:mm" minuteStep={5} disabled={!isAdmin} />
                </Form.Item>
                <Form.Item
                  name="workEndTime"
                  label={t('attendance.settings.workEndTime')}
                  rules={[{ required: true, message: t('attendance.settings.workEndTimeRequired') }]}
                >
                  <TimePicker format="HH:mm" minuteStep={5} disabled={!isAdmin} />
                </Form.Item>
              </Space>
              <Space size="large" wrap>
                <Form.Item
                  name="startFlexMinutes"
                  label={t('attendance.settings.startFlex')}
                  tooltip={t('attendance.settings.startFlexHint')}
                  rules={[{ required: true, message: t('attendance.settings.flexRequired') }]}
                >
                  <InputNumber min={0} max={480} step={5} disabled={!isAdmin} addonAfter={t('attendance.common.minute')} />
                </Form.Item>
                <Form.Item
                  name="endFlexMinutes"
                  label={t('attendance.settings.endFlex')}
                  tooltip={t('attendance.settings.endFlexHint')}
                  rules={[{ required: true, message: t('attendance.settings.flexRequired') }]}
                >
                  <InputNumber min={0} max={480} step={5} disabled={!isAdmin} addonAfter={t('attendance.common.minute')} />
                </Form.Item>
                <Form.Item
                  name="flexLinked"
                  label={t('attendance.settings.linkedFlex')}
                  valuePropName="checked"
                  tooltip={t('attendance.settings.linkedFlexHint')}
                >
                  <Switch disabled={!isAdmin} />
                </Form.Item>
              </Space>
            </Form>
            <Space style={{ marginTop: 8 }}>
              <Button type="primary" loading={saving} disabled={!isAdmin} onClick={() => void handleSave()}>
                {t('attendance.settings.save')}
              </Button>
              <Button disabled={!isAdmin} onClick={() => form.resetFields()}>
                {t('common.reset')}
              </Button>
            </Space>
          </Card>

          <Card className="oa-attendance-card" title={t('attendance.settings.rules')} variant="outlined">
            <div className="oa-clock-summary">
              <RuleChip label={t('attendance.settings.workStartTime')} value={startStr} />
              <RuleChip label={t('attendance.settings.standardEnd')} value={endStr} />
              <RuleChip label={t('attendance.settings.workDuration')} value={dur > 0 ? `${dur} ${t('attendance.common.minute')}` : '--'} />
              <RuleChip
                label={t('attendance.settings.noLateWindow')}
                value={Number(startFlex ?? 0) > 0 ? `${startStr} ~ ${noLateFrom}` : startStr}
              />
              <RuleChip
                label={t('attendance.settings.noEarlyWindow')}
                value={Number(endFlex ?? 0) > 0 ? `${noEarlyFrom} ~ ${refOut}` : refOut}
              />
              <RuleChip
                label={t('attendance.settings.linkedFlex')}
                value={flexLinked ? t('attendance.settings.linkOn') : t('attendance.settings.linkOff')}
              />
            </div>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
              {t('attendance.settings.rulesDesc')}
            </Typography.Paragraph>
          </Card>

          <Card className="oa-attendance-card" title={t('attendance.settings.preview')} variant="outlined">
            <Table
              rowKey="key"
              size="middle"
              columns={sampleColumns}
              dataSource={samples}
              pagination={false}
              locale={{ emptyText: t('attendance.common.noData') }}
            />
          </Card>
        </div>
      </Spin>
    </AttendancePageShell>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { Button, Card, Descriptions, Empty, Space, Spin, Table, Tag } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  attendanceApi,
  type AttendanceClockType,
  type AttendanceRecord,
  type AttendanceStatus,
  type AttendanceTodayStatus,
} from '@/lib/attendanceApi';
import { formatOaApiError } from '@/lib/oaApi';
import AttendancePageShell from './AttendancePageShell';

const STATUS_TAG_COLOR: Record<AttendanceStatus, string> = {
  NORMAL: 'success',
  LATE: 'warning',
  EARLY_LEAVE: 'warning',
  LATE_AND_EARLY: 'error',
  MISSING_CLOCK: 'error',
};

function formatTime(value?: string | null): string {
  if (!value) return '-';
  return dayjs(value).format('HH:mm:ss');
}

export default function AttendanceClockPage() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<AttendanceTodayStatus | null>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [clocking, setClocking] = useState(false);

  const loadAll = async () => {
    setLoading(true);
    try {
      const [today, week] = await Promise.all([
        attendanceApi.getTodayStatus(),
        attendanceApi.listRecords({
          from: dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
          to: dayjs().format('YYYY-MM-DD'),
          size: 50,
        }),
      ]);
      setStatus(today);
      setRecords(week.records);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleClock = async (clockType: AttendanceClockType) => {
    setClocking(true);
    try {
      await attendanceApi.clock({ clockType });
      message.success(
        t(`attendance.clock.${clockType === 'CLOCK_IN' ? 'clockInSuccess' : 'clockOutSuccess'}`),
      );
      await loadAll();
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setClocking(false);
    }
  };

  const columns: ColumnsType<AttendanceRecord> = [
    {
      title: t('attendance.common.date'),
      dataIndex: 'clockDate',
      key: 'clockDate',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: t('attendance.clock.clockInTime'),
      dataIndex: 'clockInTime',
      key: 'clockInTime',
      render: formatTime,
    },
    {
      title: t('attendance.clock.clockOutTime'),
      dataIndex: 'clockOutTime',
      key: 'clockOutTime',
      render: formatTime,
    },
    {
      title: t('attendance.common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: AttendanceStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`attendance.status.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('attendance.common.lateMinutes'),
      dataIndex: 'lateMinutes',
      key: 'lateMinutes',
      render: (v: number) => (v > 0 ? `${v} ${t('attendance.common.minute')}` : '-'),
    },
    {
      title: t('attendance.common.earlyLeaveMinutes'),
      dataIndex: 'earlyLeaveMinutes',
      key: 'earlyLeaveMinutes',
      render: (v: number) => (v > 0 ? `${v} ${t('attendance.common.minute')}` : '-'),
    },
  ];

  return (
    <AttendancePageShell
      eyebrow={t('attendance.eyebrow')}
      title={t('attendance.clock.title')}
      description={t('attendance.clock.description')}
    >
      <Spin spinning={loading}>
        <div className="oa-attendance-stack">
          <Card className="oa-attendance-card" title={t('attendance.clock.todayStatus')} variant="outlined">
          {status ? (
            <>
              <Descriptions column={2} size="small" style={{ marginBottom: 16 }}>
                <Descriptions.Item label={t('attendance.common.date')}>
                  {dayjs(status.clockDate).format('YYYY-MM-DD')}
                </Descriptions.Item>
                <Descriptions.Item label={t('attendance.common.status')}>
                  {status.status ? (
                    <Tag color={STATUS_TAG_COLOR[status.status]}>
                      {t(`attendance.status.${status.status}`, { defaultValue: status.status })}
                    </Tag>
                  ) : (
                    <Tag>{t('attendance.clock.notClockedYet')}</Tag>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label={t('attendance.clock.clockInTime')}>
                  {formatTime(status.clockInTime)}
                </Descriptions.Item>
                <Descriptions.Item label={t('attendance.clock.clockOutTime')}>
                  {formatTime(status.clockOutTime)}
                </Descriptions.Item>
                {status.lateMinutes > 0 && (
                  <Descriptions.Item label={t('attendance.common.lateMinutes')}>
                    {status.lateMinutes} {t('attendance.common.minute')}
                  </Descriptions.Item>
                )}
                {status.earlyLeaveMinutes > 0 && (
                  <Descriptions.Item label={t('attendance.common.earlyLeaveMinutes')}>
                    {status.earlyLeaveMinutes} {t('attendance.common.minute')}
                  </Descriptions.Item>
                )}
              </Descriptions>
              <Space>
                <Button
                  type="primary"
                  size="large"
                  disabled={!status.canClockIn}
                  loading={clocking}
                  onClick={() => handleClock('CLOCK_IN')}
                >
                  {status.clockInTime
                    ? `${t('attendance.clock.clockIn')} ${formatTime(status.clockInTime)}`
                    : t('attendance.clock.clockIn')}
                </Button>
                <Button
                  type="primary"
                  size="large"
                  disabled={!status.canClockOut}
                  loading={clocking}
                  onClick={() => handleClock('CLOCK_OUT')}
                >
                  {status.clockOutTime
                    ? `${t('attendance.clock.clockOut')} ${formatTime(status.clockOutTime)}`
                    : t('attendance.clock.clockOut')}
                </Button>
              </Space>
            </>
          ) : (
            <Empty description={t('attendance.clock.notClockedYet')} />
          )}
          </Card>

          <Card className="oa-attendance-card oa-attendance-card--grow" title={t('attendance.clock.recentRecords')} variant="outlined">
            <Table
              rowKey="id"
              columns={columns}
              dataSource={records}
              pagination={false}
              size="middle"
              scroll={{ x: 780 }}
              locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
            />
          </Card>
        </div>
      </Spin>
    </AttendancePageShell>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { Card, DatePicker, Empty, Space, Spin, Table, Tag } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  attendanceApi,
  type AttendanceRecord,
  type AttendanceStatus,
} from '@/lib/attendanceApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useAuth } from '@/components/auth/AuthProvider';

const { RangePicker } = DatePicker;

const STATUS_TAG_COLOR: Record<AttendanceStatus, string> = {
  NORMAL: 'success',
  LATE: 'warning',
  EARLY_LEAVE: 'warning',
  LATE_AND_EARLY: 'error',
  MISSING_CLOCK: 'error',
};

export default function AttendanceExceptionPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'SYSTEM_ADMIN';
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('month'),
    dayjs(),
  ]);

  const load = async (p = page, s = size, r = range) => {
    setLoading(true);
    try {
      const res = await attendanceApi.listExceptions({
        from: r[0].format('YYYY-MM-DD'),
        to: r[1].format('YYYY-MM-DD'),
        page: p,
        size: s,
      });
      setRecords(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: ColumnsType<AttendanceRecord> = [
    {
      title: t('attendance.common.date'),
      dataIndex: 'clockDate',
      key: 'clockDate',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    ...(isAdmin
      ? [
          {
            title: t('attendance.common.userName'),
            dataIndex: 'userName',
            key: 'userName',
          } as ColumnsType<AttendanceRecord>[number],
        ]
      : []),
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
      title: t('attendance.clock.clockInTime'),
      dataIndex: 'clockInTime',
      key: 'clockInTime',
      render: (v?: string | null) => (v ? dayjs(v).format('HH:mm:ss') : '-'),
    },
    {
      title: t('attendance.clock.clockOutTime'),
      dataIndex: 'clockOutTime',
      key: 'clockOutTime',
      render: (v?: string | null) => (v ? dayjs(v).format('HH:mm:ss') : '-'),
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
    <Spin spinning={loading}>
      <Card
        title={t('attendance.exception.title')}
        variant="outlined"
        extra={
          <Space>
            <RangePicker
              value={range}
              onChange={(val) => {
                if (val && val[0] && val[1]) {
                  const r: [Dayjs, Dayjs] = [val[0], val[1]];
                  setRange(r);
                  load(1, size, r);
                }
              }}
              allowClear={false}
            />
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          size="middle"
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
              load(p, s);
            },
          }}
          locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
        />
      </Card>
    </Spin>
  );
}

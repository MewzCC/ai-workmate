'use client';

import { useEffect, useRef, useState } from 'react';
import { Card, DatePicker, Empty, Spin, Tag } from 'antd';
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
import AttendancePageShell from './AttendancePageShell';
import ResponsiveTable from './ResponsiveTable';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination, uiDateRange } from '@/hooks/usePageUiEditorCommands';

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
  const scope = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const request = useRef(0);
  const [dataScope, setDataScope] = useState(scope);

  const load = async (p = page, s = size, r = range) => {
    const ticket = ++request.current;
    setPage(p); setSize(s);
    setLoading(true);
    try {
      const res = await attendanceApi.listExceptions({
        from: r[0].format('YYYY-MM-DD'),
        to: r[1].format('YYYY-MM-DD'),
        page: p,
        size: s,
      });
      if (ticket !== request.current || activeScope.current !== scope) return;
      setDataScope(scope); setRecords(res.records);
      setTotal(res.total);
    } catch (err) {
      if (ticket !== request.current || activeScope.current !== scope) return;
      setRecords([]); setTotal(0);
      message.error(formatOaApiError(err));
    } finally {
      if (ticket === request.current && activeScope.current === scope) setLoading(false);
    }
  };
  const initialLoad = useRef(load); initialLoad.current = load;
  useEffect(() => {
    activeScope.current = scope;
    setRecords([]); setTotal(0); void initialLoad.current(1);
    return () => { request.current += 1; activeScope.current = undefined; };
  }, [scope]);
  usePublishPageAgentContext({ resource: 'EXCEPTIONS', from: range[0].format('YYYY-MM-DD'), to: range[1].format('YYYY-MM-DD'), page, size });
  useRegisterPageUiCommands({ 'ui.refreshPage': () => load(),
    'ui.applyFilter': async ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { resource: ['EXCEPTIONS'], from: 'string', to: 'string', page: 'number', size: 'number' });
      requireUiPagination(filters);
      const dates = uiDateRange(filters, [range[0].format('YYYY-MM-DD'), range[1].format('YYYY-MM-DD')]);
      const next: [Dayjs, Dayjs] = dates ? [dayjs(dates[0]), dayjs(dates[1])] : range;
      setRange(next); await load(Number(filters.page ?? 1), Number(filters.size ?? size), next);
    },
  });

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
    <AttendancePageShell
      eyebrow={t('attendance.eyebrow')}
      title={t('attendance.exception.title')}
      description={t('attendance.exception.description')}
      actions={(
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
      )}
    >
      <Spin spinning={loading}>
        <Card
          className="oa-attendance-card oa-attendance-card--fill"
          title={t('attendance.exception.listTitle')}
          variant="outlined"
        >
          <ResponsiveTable
            rowKey="id"
            columns={columns}
            dataSource={dataScope === scope ? records : []}
            size="middle"
            scroll={{ x: 900 }}
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
    </AttendancePageShell>
  );
}

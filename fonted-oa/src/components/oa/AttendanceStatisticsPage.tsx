'use client';

import { useEffect, useRef, useState } from 'react';
import { Card, DatePicker, Empty, Spin, Statistic, } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  attendanceApi,
  type AttendanceStatistics,
  type AttendanceTeamMemberStats,
} from '@/lib/attendanceApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useAuth } from '@/components/auth/AuthProvider';
import AttendancePageShell from './AttendancePageShell';
import ResponsiveTable from './ResponsiveTable';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';

export default function AttendanceStatisticsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'SYSTEM_ADMIN';
  const [storedStats, setStats] = useState<AttendanceStatistics | null>(null);
  const [loading, setLoading] = useState(true);
  const [month, setMonth] = useState<Dayjs>(dayjs());
  const scope = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const request = useRef(0);
  const [dataScope, setDataScope] = useState(scope);
  const stats = dataScope === scope ? storedStats : null;

  const load = async (m = month) => {
    const ticket = ++request.current;
    setLoading(true);
    try {
      const res = await attendanceApi.getStatistics({
        year: m.year(),
        month: m.month() + 1,
      });
      if (ticket !== request.current || activeScope.current !== scope) return;
      setStats(res); setDataScope(scope);
    } catch (err) {
      if (ticket !== request.current || activeScope.current !== scope) return;
      setStats(null);
      message.error(formatOaApiError(err));
    } finally {
      if (ticket === request.current && activeScope.current === scope) setLoading(false);
    }
  };

  const initialLoad = useRef(load); initialLoad.current = load;
  useEffect(() => {
    activeScope.current = scope; setStats(null); void initialLoad.current();
    return () => { request.current += 1; activeScope.current = undefined; };
  }, [scope]);
  usePublishPageAgentContext({ resource: 'STATISTICS', year: month.year(), month: month.month() + 1 });
  useRegisterPageUiCommands({ 'ui.refreshPage': () => load(),
    'ui.applyFilter': async ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { resource: ['STATISTICS'], year: 'number', month: 'number' });
      const year = Number(filters.year ?? month.year()), selectedMonth = Number(filters.month ?? month.month() + 1);
      if (!Number.isInteger(year) || year < 2000 || year > 2100 || !Number.isInteger(selectedMonth) || selectedMonth < 1 || selectedMonth > 12)
        throw new Error('PAGE_UI_FIELD_REJECTED');
      const next = dayjs(`${year}-${String(selectedMonth).padStart(2, '0')}-01`);
      setMonth(next); await load(next);
    },
  });

  const teamColumns: ColumnsType<AttendanceTeamMemberStats> = [
    {
      title: t('attendance.common.userName'),
      dataIndex: 'userName',
      key: 'userName',
    },
    {
      title: t('attendance.statistics.totalDays'),
      dataIndex: 'totalDays',
      key: 'totalDays',
    },
    {
      title: t('attendance.statistics.normalDays'),
      dataIndex: 'normalDays',
      key: 'normalDays',
    },
    {
      title: t('attendance.statistics.lateDays'),
      dataIndex: 'lateDays',
      key: 'lateDays',
      render: (v: number) => (v > 0 ? v : '-'),
    },
    {
      title: t('attendance.statistics.earlyLeaveDays'),
      dataIndex: 'earlyLeaveDays',
      key: 'earlyLeaveDays',
      render: (v: number) => (v > 0 ? v : '-'),
    },
    {
      title: t('attendance.statistics.missingDays'),
      dataIndex: 'missingDays',
      key: 'missingDays',
      render: (v: number) => (v > 0 ? v : '-'),
    },
  ];

  return (
    <AttendancePageShell
      eyebrow={t('attendance.eyebrow')}
      title={t('attendance.statistics.title')}
      description={t('attendance.statistics.description')}
      actions={(
        <DatePicker
          picker="month"
          value={month}
          onChange={(val) => {
            if (val) {
              setMonth(val);
              load(val);
            }
          }}
          allowClear={false}
        />
      )}
    >
      <Spin spinning={loading}>
        <div className="oa-attendance-stack">
        <Card
          className="oa-attendance-card"
          title={t('attendance.statistics.title')}
          variant="outlined"
        >
          {stats?.personal ? (
            <div className="oa-attendance-metrics">
              <Statistic
                title={t('attendance.statistics.totalDays')}
                value={stats.personal.totalDays}
              />
              <Statistic
                title={t('attendance.statistics.normalDays')}
                value={stats.personal.normalDays}
                valueStyle={{ color: '#52c41a' }}
              />
              <Statistic
                title={t('attendance.statistics.lateDays')}
                value={stats.personal.lateDays}
                valueStyle={{ color: stats.personal.lateDays > 0 ? '#faad14' : undefined }}
              />
              <Statistic
                title={t('attendance.statistics.earlyLeaveDays')}
                value={stats.personal.earlyLeaveDays}
                valueStyle={{ color: stats.personal.earlyLeaveDays > 0 ? '#faad14' : undefined }}
              />
              <Statistic
                title={t('attendance.statistics.missingDays')}
                value={stats.personal.missingDays}
                valueStyle={{ color: stats.personal.missingDays > 0 ? '#ff4d4f' : undefined }}
              />
              <Statistic
                title={t('attendance.statistics.pendingReissueCount')}
                value={stats.personal.pendingReissueCount}
                valueStyle={{ color: stats.personal.pendingReissueCount > 0 ? '#1677ff' : undefined }}
              />
            </div>
          ) : (
            <Empty description={t('attendance.common.noData')} />
          )}
        </Card>

        {isAdmin && (
          <Card
            className="oa-attendance-card oa-attendance-card--fill"
            title={t('attendance.statistics.teamTitle')}
            variant="outlined"
          >
            <ResponsiveTable
              rowKey="userId"
              columns={teamColumns}
              dataSource={stats?.team || []}
              size="middle"
              pagination={{ pageSize: 20, showSizeChanger: true }}
              locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
            />
          </Card>
        )}
        </div>
      </Spin>
    </AttendancePageShell>
  );
}

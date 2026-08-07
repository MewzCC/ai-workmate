'use client';

import { useEffect, useState } from 'react';
import { Card, DatePicker, Empty, Space, Spin, Statistic, Table } from 'antd';
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

export default function AttendanceStatisticsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const isAdmin = user?.role === 'SUPER_ADMIN' || user?.role === 'SYSTEM_ADMIN';
  const [stats, setStats] = useState<AttendanceStatistics | null>(null);
  const [loading, setLoading] = useState(true);
  const [month, setMonth] = useState<Dayjs>(dayjs());

  const load = async (m = month) => {
    setLoading(true);
    try {
      const res = await attendanceApi.getStatistics({
        year: m.year(),
        month: m.month() + 1,
      });
      setStats(res);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    <Spin spinning={loading}>
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        <Card
          title={t('attendance.statistics.title')}
          variant="outlined"
          extra={
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
          }
        >
          {stats?.personal ? (
            <Space size="large" wrap>
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
            </Space>
          ) : (
            <Empty description={t('attendance.common.noData')} />
          )}
        </Card>

        {isAdmin && (
          <Card title={t('attendance.statistics.teamTitle')} variant="outlined">
            <Table
              rowKey="userId"
              columns={teamColumns}
              dataSource={stats?.team || []}
              size="middle"
              pagination={{ pageSize: 20, showSizeChanger: true }}
              locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
            />
          </Card>
        )}
      </Space>
    </Spin>
  );
}

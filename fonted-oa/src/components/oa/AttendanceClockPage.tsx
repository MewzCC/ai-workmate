'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Modal, Spin, Table, Tag } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import {
  attendanceApi,
  type AttendanceClockType,
  type AttendanceRecord,
  type AttendanceSettings,
  type AttendanceStatus,
  type AttendanceTodayStatus,
} from '@/lib/attendanceApi';
import { formatOaApiError, getServerTime } from '@/lib/oaApi';
import AttendancePageShell from './AttendancePageShell';

const STATUS_TAG_COLOR: Record<AttendanceStatus, string> = {
  NORMAL: 'success',
  LATE: 'warning',
  EARLY_LEAVE: 'warning',
  LATE_AND_EARLY: 'error',
  MISSING_CLOCK: 'error',
};

/** 取 HH:mm（兼容 ISO 字符串）。 */
function hhmm(value?: string | null): string | null {
  if (!value) return null;
  const d = dayjs(value);
  return d.isValid() ? d.format('HH:mm') : value.slice(0, 5);
}

function toMin(hhmmValue: string): number {
  const [h, m] = hhmmValue.split(':').map(Number);
  return h * 60 + (Number.isNaN(m) ? 0 : m);
}

function fmtMin(minutes: number): string {
  const m = ((Math.round(minutes) % 1440) + 1440) % 1440;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

/** 预计下班时间：弹性联动开启时 = max(实际打卡, 标准上班) + 工时；否则为标准下班时间。 */
function expectedClockOut(clockIn: string | null, settings: AttendanceSettings): string {
  const duration = toMin(settings.workEndTime) - toMin(settings.workStartTime);
  if (!settings.flexLinked || !clockIn) return settings.workEndTime;
  const base = Math.max(toMin(hhmm(clockIn) || settings.workStartTime), toMin(settings.workStartTime));
  return fmtMin(base + duration);
}

function formatTime(value?: string | null): string {
  const t = hhmm(value);
  return t ? t : '-';
}

export default function AttendanceClockPage() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<AttendanceTodayStatus | null>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [settings, setSettings] = useState<AttendanceSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [clocking, setClocking] = useState(false);
  // 服务器时间偏移：serverEpoch - Date.now()，按钮显示 = Date.now() + offset
  // 这样按钮时间与后端落库时间使用同一时间源，避免浏览器时钟与服务器时钟不一致
  const [serverOffsetMs, setServerOffsetMs] = useState(0);
  const [now, setNow] = useState(() => dayjs());

  // 启动时同步一次服务器时间，计算偏移；每 5 分钟重新校准一次防止时钟漂移
  useEffect(() => {
    let cancelled = false;
    const sync = async () => {
      try {
        const t0 = Date.now();
        const { epochMillis } = await getServerTime();
        const t1 = Date.now();
        if (cancelled) return;
        // 用往返中点作为客户端采样时刻，减小网络延迟误差
        const clientNow = Math.round((t0 + t1) / 2);
        setServerOffsetMs(epochMillis - clientNow);
      } catch {
        // 同步失败时静默回退到浏览器本地时间
      }
    };
    void sync();
    const syncTimer = window.setInterval(sync, 5 * 60 * 1000);
    return () => {
      cancelled = true;
      window.clearInterval(syncTimer);
    };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(
      () => setNow(dayjs(Date.now() + serverOffsetMs)),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [serverOffsetMs]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [today, week, cfg] = await Promise.all([
        attendanceApi.getTodayStatus(),
        attendanceApi.listRecords({
          from: dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
          to: dayjs().format('YYYY-MM-DD'),
          size: 50,
        }),
        attendanceApi.getSettings(),
      ]);
      setStatus(today);
      setRecords(week.records);
      setSettings(cfg);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const askConfirm = async (content: string): Promise<boolean> =>
    new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: t('attendance.clock.confirmTitle'),
        content,
        okText: t('common.confirm'),
        cancelText: t('common.cancel'),
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

  const handleClock = async (clockType: AttendanceClockType) => {
    if (!settings || clocking) return;
    const nowMin = now.hour() * 60 + now.minute();
    const startMin = toMin(settings.workStartTime);
    const endMin = toMin(settings.workEndTime);
    let confirmed = true;

    if (clockType === 'CLOCK_IN') {
      if (nowMin < startMin) {
        confirmed = await askConfirm(t('attendance.clock.confirmEarlyClockIn', { time: settings.workStartTime }));
      } else if (nowMin > endMin) {
        confirmed = await askConfirm(t('attendance.clock.confirmLateClockIn', { time: settings.workEndTime }));
      }
    } else {
      const out = expectedClockOut(hhmm(status?.clockInTime), settings);
      const flexEnd = Math.max(0, toMin(out) - settings.endFlexMinutes);
      if (nowMin < flexEnd) {
        confirmed = await askConfirm(t('attendance.clock.confirmEarlyClockOut', { time: out }));
      }
    }
    if (!confirmed) return;

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

  const expectedOutDisplay = settings ? expectedClockOut(hhmm(status?.clockInTime), settings) : '-';

  // 单按钮：可上班打卡时=上班打卡；已上班未下班=下班打卡；均已打卡=完成态
  const action: AttendanceClockType | null = !status
    ? null
    : status.canClockIn
      ? 'CLOCK_IN'
      : status.canClockOut
        ? 'CLOCK_OUT'
        : null;
  const actionLabel = action === 'CLOCK_IN'
    ? t('attendance.clock.clockIn')
    : action === 'CLOCK_OUT'
      ? t('attendance.clock.clockOut')
      : t('attendance.clock.done');
  const statusLine = !status?.clockInTime
    ? t('attendance.clock.tapToClock')
    : !status?.clockOutTime
      ? `${t('attendance.clock.clockedAt')} ${formatTime(status.clockInTime)} · ${t('attendance.clock.readyClockOut')}`
      : t('attendance.clock.clockedDone');
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
            {status && settings ? (
              <>
                <div className="oa-clock-hero">
                  <Button
                    type="primary"
                    shape="circle"
                    className="oa-clock-circle"
                    disabled={!action}
                    loading={clocking}
                    onClick={() => action && void handleClock(action)}
                  >
                    <span className="oa-clock-circle__time">{now.format('HH:mm:ss')}</span>
                    <span className="oa-clock-circle__label">{actionLabel}</span>
                    <span className="oa-clock-circle__hint">{statusLine}</span>
                  </Button>
                </div>

                {settings.flexLinked && (
                  <div className="oa-clock-flex-note">{t('attendance.clock.flexHint')}</div>
                )}

                <div className="oa-clock-summary">
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">{t('attendance.common.date')}</span>
                    <span className="oa-clock-chip__value">{dayjs(status.clockDate).format('YYYY-MM-DD')}</span>
                  </div>
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">{t('attendance.common.status')}</span>
                    <span className="oa-clock-chip__value">
                      {status.status ? (
                        <Tag color={STATUS_TAG_COLOR[status.status]}>
                          {t(`attendance.status.${status.status}`, { defaultValue: status.status })}
                        </Tag>
                      ) : (
                        <Tag>{t('attendance.clock.notClockedYet')}</Tag>
                      )}
                    </span>
                  </div>
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">{t('attendance.clock.clockInTime')}</span>
                    <span className="oa-clock-chip__value">{formatTime(status.clockInTime)}</span>
                  </div>
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">
                      {status.clockInTime && !status.clockOutTime
                        ? t('attendance.clock.expectedOut')
                        : t('attendance.clock.clockOutTime')}
                    </span>
                    <span className="oa-clock-chip__value">
                      {status.clockOutTime ? formatTime(status.clockOutTime) : expectedOutDisplay}
                    </span>
                  </div>
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">{t('attendance.common.lateMinutes')}</span>
                    <span className="oa-clock-chip__value">
                      {status.lateMinutes > 0 ? `${status.lateMinutes} ${t('attendance.common.minute')}` : '-'}
                    </span>
                  </div>
                  <div className="oa-clock-chip">
                    <span className="oa-clock-chip__label">{t('attendance.common.earlyLeaveMinutes')}</span>
                    <span className="oa-clock-chip__value">
                      {status.earlyLeaveMinutes > 0 ? `${status.earlyLeaveMinutes} ${t('attendance.common.minute')}` : '-'}
                    </span>
                  </div>
                </div>
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
              scroll={{ x: 720 }}
              locale={{ emptyText: <Empty description={t('attendance.common.noData')} /> }}
            />
          </Card>
        </div>
      </Spin>
    </AttendancePageShell>
  );
}

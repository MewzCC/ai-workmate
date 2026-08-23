'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Avatar,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  approvalApi,
  formatOaApiError,
  type ApprovalStatusCount,
  type LeaveApplication,
  type LeaveStatus,
  type LeaveType,
} from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { leaveTypeLabel, StatusTag } from './MyApplicationsPage';
import ApprovalDetailDrawer from './ApprovalDetailDrawer';
import { useTranslation } from 'react-i18next';

const { RangePicker } = DatePicker;

const STATUS_FILTER_KEYS: (LeaveStatus | '')[] = [
  '', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'DRAFT',
];

const LEAVE_TYPE_KEYS: LeaveType[] = [
  'ANNUAL', 'PERSONAL', 'SICK', 'MARRIAGE', 'MATERNITY',
  'PATERNITY', 'BEREAVEMENT', 'COMPENSATORY', 'OTHER',
];

export default function ApprovalListPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [records, setRecords] = useState<LeaveApplication[]>([]);
  const [stats, setStats] = useState<ApprovalStatusCount[]>([]);
  const [status, setStatus] = useState<LeaveStatus | undefined>();
  const [keyword, setKeyword] = useState('');
  const [leaveType, setLeaveType] = useState<LeaveType | undefined>();
  const [range, setRange] = useState<[string, string]>();
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [drawerTaskId, setDrawerTaskId] = useState<number | null>(null);

  const statusOptions = STATUS_FILTER_KEYS.map((value) => ({
    value,
    label: value ? t(`approval.status.${value}`) : t('common.all'),
  }));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, counts] = await Promise.all([
        approvalApi.list({
          status,
          keyword: keyword || undefined,
          leaveType,
          from: range?.[0],
          to: range?.[1],
          page,
          size: 20,
        }),
        approvalApi.stats(),
      ]);
      setRecords(list.records);
      setTotal(list.total);
      setStats(counts);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, leaveType, page, range, status]);

  useEffect(() => { void load(); }, [load]);

  const metricCount = useMemo(() => (value: LeaveStatus) =>
    stats.find((item) => item.status === value)?.count ?? 0,
  [stats]);
  const metricPending = metricCount('PENDING');
  const metricApproved = metricCount('APPROVED');
  const metricRejected = metricCount('REJECTED');
  const metricDraft = metricCount('DRAFT');
  // 「全部单据」与其余 4 个指标同口径：stats 全局求和，而非筛选后的分页 total
  const metricTotal = useMemo(
    () => stats.reduce((sum, item) => sum + item.count, 0),
    [stats],
  );

  const columns: ColumnsType<LeaveApplication> = [
    {
      title: t('approval.approvalList.columnRequest'),
      key: 'request',
      width: 240,
      render: (_, item) => (
        <div className="leave-table-subject">
          <span className="leave-table-subject__icon is-approval"><OaIcon name="approval" /></span>
          <div>
            <Space>
              <Avatar size="small" src={item.applicantAvatarUrl || undefined}>
                {item.applicantName.slice(0, 1).toUpperCase()}
              </Avatar>
              <Typography.Text strong>{item.applicantName}</Typography.Text>
            </Space>
            <Typography.Text type="secondary">
              LV-{String(item.id).padStart(6, '0')} · {leaveTypeLabel(item.leaveType)}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: t('approval.approvalList.columnApprover'),
      key: 'approver',
      width: 160,
      render: (_, item) => item.approverName
        ? (
          <Space size={6}>
            <Avatar size="small" src={item.approverAvatarUrl || undefined}>
              {item.approverName.slice(0, 1).toUpperCase()}
            </Avatar>
            <Typography.Text>{item.approverName}</Typography.Text>
          </Space>
        )
        : <Typography.Text type="secondary">{t('approval.approvalList.approverUnset')}</Typography.Text>,
    },
    {
      title: t('approval.approvalList.columnRange'),
      key: 'range',
      width: 210,
      render: (_, item) => (
        <Typography.Text type="secondary">
          {item.startDate} {item.startPeriod} ~ {item.endDate} {item.endPeriod}
        </Typography.Text>
      ),
    },
    {
      title: t('approval.approvalList.columnDuration'),
      dataIndex: 'durationDays',
      width: 90,
      render: (value) => <Typography.Text strong>{t('approval.approvalList.durationDays', { count: value })}</Typography.Text>,
    },
    {
      title: t('approval.approvalList.columnSubmittedAt'),
      dataIndex: 'submittedAt',
      width: 170,
      render: (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 110,
      render: (value) => <StatusTag status={value} />,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, item) => (
        item.canApprove && item.taskId
          ? (
            <Button type="primary" size="small"
              onClick={(event) => {
                event.stopPropagation();
                router.push(`/oa/approval-tasks/${item.taskId}?from=approval-list`);
              }}>
              {t('approval.approvalList.actionProcess')}
            </Button>
          )
          : item.taskId
            ? (
              <Button type="link" size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  router.push(`/oa/approval-tasks/${item.taskId}?from=approval-list`);
                }}>
                {t('approval.approvalList.actionView')}
              </Button>
            )
            : item.status === 'DRAFT'
              ? <Tag>{t('approval.approvalList.draftTag')}</Tag>
              : null
      ),
    },
  ];

  return (
    <section className="leave-list-workbench">
      <header className="leave-list-hero approval-list-hero">
        <div>
          <span className="leave-list-hero__kicker">APPROVAL DESK</span>
          <Typography.Title level={2}>{t('approval.approvalList.title')}</Typography.Title>
          <Typography.Paragraph>{t('approval.approvalList.description')}</Typography.Paragraph>
        </div>
        <div className="approval-list-hero__badge">
          <OaIcon name="approval" size={20} />
          <span>{t('approval.approvalList.scopeLabel')}</span>
        </div>
      </header>

      <div className="leave-metric-strip approval-metric-strip">
        <div className={metricPending ? 'is-attention' : ''}>
          <span>{t('approval.approvalList.metricPending')}</span>
          <strong>{metricPending}</strong>
          <small>{t('approval.approvalList.metricPendingUnit')}</small>
        </div>
        <div><span>{t('approval.approvalList.metricApproved')}</span><strong>{metricApproved}</strong><small>{t('approval.approvalList.metricApprovedUnit')}</small></div>
        <div><span>{t('approval.approvalList.metricRejected')}</span><strong>{metricRejected}</strong><small>{t('approval.approvalList.metricRejectedUnit')}</small></div>
        <div><span>{t('approval.approvalList.metricDraft')}</span><strong>{metricDraft}</strong><small>{t('approval.approvalList.metricDraftUnit')}</small></div>
        <div><span>{t('approval.approvalList.metricTotal')}</span><strong>{metricTotal}</strong><small>{t('approval.approvalList.metricTotalUnit')}</small></div>
      </div>

      <Card className="leave-list-card" variant="borderless">
        <div className="leave-list-toolbar">
          <Segmented
            block
            value={status || ''}
            options={statusOptions}
            onChange={(value) => {
              setStatus((value || undefined) as LeaveStatus | undefined);
              setPage(1);
            }}
          />
          <Space wrap>
            <Input.Search
              placeholder={t('approval.approvalList.searchPlaceholder')}
              allowClear
              style={{ width: 220 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={() => setPage(1)}
            />
            <Select
              allowClear
              placeholder={t('approval.approvalList.typeFilterPlaceholder')}
              style={{ width: 150 }}
              value={leaveType}
              onChange={(value) => { setLeaveType(value as LeaveType | undefined); setPage(1); }}
              options={LEAVE_TYPE_KEYS.map((value) => ({
                value,
                label: leaveTypeLabel(value),
              }))}
            />
            <RangePicker
              showTime
              onChange={(dates) => {
                if (dates?.[0] && dates[1]) {
                  // from 原样保留：纯日期选择时即为当日 00:00:00，显式选了时间则准确到分
                  const from = dates[0];
                  // to 默认落在所选日期 00:00:00（纯日期选择），资源 dayjs 无时区偏移，
                  // 需扩到当日 23:59:59 才不会漏掉结束日当天记录；显式选了时间则原样保留
                  const to = dates[1].isSame(dates[1].startOf('day'))
                    ? dates[1].endOf('day')
                    : dates[1];
                  // 后端按 LocalDateTime + ISO.DATE_TIME 解析，禁止 toISOString() 的 UTC Z 后缀与 8 小时偏移
                  setRange([
                    from.format('YYYY-MM-DDTHH:mm:ss'),
                    to.format('YYYY-MM-DDTHH:mm:ss'),
                  ]);
                } else {
                  setRange(undefined);
                }
                setPage(1);
              }}
            />
            <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
          </Space>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description={t('approval.approvalList.empty')} /> }}
          scroll={{ x: 1100 }}
          onRow={(item) => ({
            onClick: () => {
              if (item.taskId) setDrawerTaskId(item.taskId);
            },
            className: 'approval-table-row-clickable',
          })}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            showTotal: (value) => t('common.total', { count: value }),
            onChange: setPage,
          }}
        />
      </Card>

      <ApprovalDetailDrawer
        taskId={drawerTaskId}
        open={drawerTaskId != null}
        onClose={() => {
          setDrawerTaskId(null);
          void load();
        }}
      />
    </section>
  );
}
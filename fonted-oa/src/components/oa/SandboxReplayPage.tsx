'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { OaIcon } from '@/components/OaIcon';
import { useIsMobile } from '@/hooks/useIsMobile';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import {
  sandboxReplayApi,
  type SandboxReplayBaseline,
  type SandboxReplayComparison,
  type SandboxReplayDetail,
  type SandboxReplayRecord,
  type SandboxReplayStatus,
} from '@/lib/sandboxReplayApi';
import ResponsiveTable from './ResponsiveTable';

type ReplayFormValue = { sourceInvocationId: number; reason: string };

const STATUSES: SandboxReplayStatus[] = ['RUNNING', 'SUCCESS', 'FAILED'];
const STATUS_COLORS: Record<SandboxReplayStatus, string> = {
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
};
const COMPARISON_COLORS: Record<SandboxReplayComparison, string> = {
  MATCHED: 'success',
  CHANGED: 'warning',
};

const formatTime = (value: string | undefined, locale: string) => value
  ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
  : '-';

export default function SandboxReplayPage() {
  const { t, i18n } = useTranslation();
  const isMobile = useIsMobile();
  const [form] = Form.useForm<ReplayFormValue>();
  const [records, setRecords] = useState<SandboxReplayRecord[]>([]);
  const [stats, setStats] = useState({ total: 0, matched: 0, changed: 0, successful: 0, failed: 0 });
  const [loading, setLoading] = useState(true);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<SandboxReplayStatus>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [canExecute, setCanExecute] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [baselines, setBaselines] = useState<SandboxReplayBaseline[]>([]);
  const [baselinesLoading, setBaselinesLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selectedBaselineId, setSelectedBaselineId] = useState<number>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<SandboxReplayDetail>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await sandboxReplayApi.list({
        keyword: keyword || undefined,
        status,
        page,
        size,
      });
      setRecords(response.records);
      setStats(response.stats);
      setTotal(response.total);
      setCanExecute(response.canExecute);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, page, size, status]);

  useEffect(() => { void load(); }, [load]);

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      setDetail(await sandboxReplayApi.detail(id));
    } catch (error) {
      message.error(formatOaApiError(error));
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const openEditor = async () => {
    setEditorOpen(true);
    setBaselinesLoading(true);
    setSelectedBaselineId(undefined);
    form.resetFields();
    try {
      setBaselines(await sandboxReplayApi.baselines(undefined, 50));
    } catch (error) {
      message.error(formatOaApiError(error));
      setEditorOpen(false);
    } finally {
      setBaselinesLoading(false);
    }
  };

  const executeReplay = async () => {
    try {
      const value = await form.validateFields();
      setSaving(true);
      const result = await sandboxReplayApi.execute({
        sourceInvocationId: value.sourceInvocationId,
        reason: value.reason.trim(),
        idempotencyKey: crypto.randomUUID(),
      });
      message.success(t('sandboxReplay.messages.completed'));
      setEditorOpen(false);
      await load();
      setDetail(result);
      setDetailOpen(true);
    } catch (error) {
      if (error instanceof Error) {
        message.error(formatOaApiError(error));
      }
    } finally {
      setSaving(false);
    }
  };

  const selectedBaseline = baselines.find((item) => item.invocationId === selectedBaselineId);

  const columns = useMemo<ColumnsType<SandboxReplayRecord>>(() => [
    {
      title: t('sandboxReplay.columns.startedAt'),
      dataIndex: 'startedAt',
      width: 188,
      render: (value: string) => formatTime(value, i18n.language),
    },
    {
      title: t('sandboxReplay.columns.endpoint'),
      key: 'endpoint',
      minWidth: 250,
      render: (_, record) => (
        <div className="oa-sandbox-replay-endpoint">
          <Space size={6} wrap>
            <Tag color={record.method === 'GET' ? 'blue' : 'gold'}>{record.method}</Tag>
            <Typography.Text strong>{record.endpointName}</Typography.Text>
          </Space>
          <Typography.Text type="secondary">{record.endpointCode} · {record.relativePath}</Typography.Text>
        </div>
      ),
    },
    {
      title: t('sandboxReplay.columns.baseline'),
      key: 'baseline',
      width: 134,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Tag color={record.baselineOutcome === 'SUCCESS' ? 'success' : 'error'}>
            {t(`sandboxReplay.status.${record.baselineOutcome}`)}
          </Tag>
          <Typography.Text type="secondary">HTTP {record.baselineHttpStatus ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('sandboxReplay.columns.replay'),
      key: 'replay',
      width: 134,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Tag color={STATUS_COLORS[record.status]}>{t(`sandboxReplay.status.${record.status}`)}</Tag>
          <Typography.Text type="secondary">HTTP {record.replayHttpStatus ?? '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('sandboxReplay.columns.comparison'),
      dataIndex: 'comparisonResult',
      width: 130,
      render: (value?: SandboxReplayComparison) => value
        ? <Tag color={COMPARISON_COLORS[value]}>{t(`sandboxReplay.comparison.${value}`)}</Tag>
        : <Tag>{t('sandboxReplay.comparison.PENDING')}</Tag>,
    },
    {
      title: t('sandboxReplay.columns.duration'),
      dataIndex: 'replayDurationMs',
      width: 118,
      render: (value?: number) => value == null ? '-' : `${value} ms`,
    },
    {
      title: t('sandboxReplay.columns.operator'),
      dataIndex: 'requestedByLabel',
      width: 142,
      ellipsis: true,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      fixed: 'right',
      width: 112,
      render: (_, record) => (
        <Button type="link" onClick={() => void openDetail(record.id)}>
          {t('sandboxReplay.actions.view')}
        </Button>
      ),
    },
  ], [i18n.language, openDetail, t]);

  const detailItems = detail ? [
    { key: 'endpoint', label: t('sandboxReplay.detail.endpoint'), children: `${detail.endpointName} (${detail.endpointCode})` },
    { key: 'operation', label: t('sandboxReplay.detail.operation'), children: <Space className="oa-sandbox-replay-operation"><Tag>{detail.method}</Tag><Typography.Text code>{detail.relativePath}</Typography.Text></Space> },
    { key: 'baseline', label: t('sandboxReplay.detail.baselineId'), children: <Typography.Text copyable>#{detail.sourceInvocationId}</Typography.Text> },
    { key: 'status', label: t('sandboxReplay.detail.status'), children: <Tag color={STATUS_COLORS[detail.status]}>{t(`sandboxReplay.status.${detail.status}`)}</Tag> },
    { key: 'comparison', label: t('sandboxReplay.detail.comparison'), children: detail.comparisonResult ? <Tag color={COMPARISON_COLORS[detail.comparisonResult]}>{t(`sandboxReplay.comparison.${detail.comparisonResult}`)}</Tag> : '-' },
    { key: 'operator', label: t('sandboxReplay.detail.operator'), children: detail.requestedByLabel },
    { key: 'started', label: t('sandboxReplay.detail.startedAt'), children: formatTime(detail.startedAt, i18n.language) },
    { key: 'completed', label: t('sandboxReplay.detail.completedAt'), children: formatTime(detail.completedAt, i18n.language) },
    { key: 'reason', label: t('sandboxReplay.detail.reason'), span: isMobile ? 1 : 2, children: detail.reason },
    { key: 'fingerprint', label: t('sandboxReplay.detail.fingerprint'), span: isMobile ? 1 : 2, children: <Typography.Text code copyable>{detail.baselineRequestFingerprint}</Typography.Text> },
    { key: 'trace', label: t('sandboxReplay.detail.trace'), span: isMobile ? 1 : 2, children: <Typography.Text code copyable>{detail.traceId}</Typography.Text> },
    ...(detail.replayErrorCode ? [{ key: 'error', label: t('sandboxReplay.detail.errorCode'), span: isMobile ? 1 : 2, children: <Tag color="error">{detail.replayErrorCode}</Tag> }] : []),
  ] : [];

  return (
    <section className="oa-sandbox-replay-page">
      <header className="oa-sandbox-replay-header">
        <div className="oa-sandbox-replay-heading">
          <span><OaIcon name="sandbox" size={26} /></span>
          <div>
            <Typography.Text className="oa-sandbox-replay-eyebrow">{t('sandboxReplay.eyebrow')}</Typography.Text>
            <Typography.Title level={3}>{t('sandboxReplay.title')}</Typography.Title>
            <Typography.Paragraph>{t('sandboxReplay.subtitle')}</Typography.Paragraph>
          </div>
        </div>
        <Space orientation="vertical" align="end">
          <Tag color="blue" icon={<OaIcon name="lock" />}>{t('sandboxReplay.safetyBadge')}</Tag>
          <Typography.Text type="secondary">{t('sandboxReplay.safetyHint')}</Typography.Text>
          {canExecute && (
            <Button type="primary" icon={<OaIcon name="sandbox" />} onClick={() => void openEditor()}>
              {t('sandboxReplay.actions.create')}
            </Button>
          )}
        </Space>
      </header>

      <div className="oa-sandbox-replay-stats">
        <Card><Statistic title={t('sandboxReplay.stats.total')} value={stats.total} /></Card>
        <Card><Statistic title={t('sandboxReplay.stats.matched')} value={stats.matched} /></Card>
        <Card><Statistic title={t('sandboxReplay.stats.changed')} value={stats.changed} /></Card>
        <Card><Statistic title={t('sandboxReplay.stats.successful')} value={stats.successful} /></Card>
        <Card><Statistic title={t('sandboxReplay.stats.failed')} value={stats.failed} /></Card>
      </div>

      <Card className="oa-sandbox-replay-list">
        <Alert
          type="info"
          showIcon
          title={t('sandboxReplay.boundary.title')}
          description={t('sandboxReplay.boundary.description')}
        />
        <div className="oa-sandbox-replay-toolbar">
          <Input.Search
            allowClear
            value={keywordInput}
            placeholder={t('sandboxReplay.filters.keyword')}
            onChange={(event) => {
              setKeywordInput(event.target.value);
              if (!event.target.value) { setKeyword(''); setPage(1); }
            }}
            onSearch={(value) => { setKeyword(value.trim()); setPage(1); }}
          />
          <Select
            allowClear
            value={status}
            placeholder={t('sandboxReplay.filters.status')}
            options={STATUSES.map((value) => ({ value, label: t(`sandboxReplay.status.${value}`) }))}
            onChange={(value) => { setStatus(value); setPage(1); }}
          />
          <Button icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.refresh')}</Button>
        </div>
        <ResponsiveTable
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 1260 }}
          locale={{ emptyText: <Empty description={t('sandboxReplay.empty')} /> }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); },
          }}
        />
      </Card>

      <Modal
        open={editorOpen}
        title={t('sandboxReplay.editor.title')}
        okText={t('sandboxReplay.actions.execute')}
        cancelText={t('common.cancel')}
        confirmLoading={saving}
        width={isMobile ? 'calc(100vw - 24px)' : 680}
        destroyOnHidden
        onCancel={() => setEditorOpen(false)}
        onOk={() => void executeReplay()}
      >
        <div className="oa-sandbox-replay-editor">
          <Alert type="warning" showIcon description={t('sandboxReplay.editor.confirmation')} />
          <Form form={form} layout="vertical">
            <Form.Item
              name="sourceInvocationId"
              label={t('sandboxReplay.editor.baseline')}
              rules={[{ required: true, message: t('sandboxReplay.editor.baselineRequired') }]}
            >
              <Select
                showSearch
                loading={baselinesLoading}
                optionFilterProp="labelText"
                placeholder={t('sandboxReplay.editor.baselinePlaceholder')}
                notFoundContent={<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('sandboxReplay.editor.noBaseline')} />}
                options={baselines.map((baseline) => ({
                  value: baseline.invocationId,
                  labelText: `${baseline.endpointCode} ${baseline.endpointName} ${baseline.relativePath}`,
                  label: `${baseline.endpointCode} · ${baseline.endpointName} · ${baseline.method} ${baseline.relativePath}`,
                }))}
                onChange={setSelectedBaselineId}
              />
            </Form.Item>
            {selectedBaseline && (
              <Card size="small" className="oa-sandbox-replay-baseline-card" title={t('sandboxReplay.editor.selectedTitle')}>
                <Space wrap>
                  <Tag>{selectedBaseline.method}</Tag>
                  <Typography.Text strong>{selectedBaseline.endpointName}</Typography.Text>
                  <Typography.Text code>{selectedBaseline.relativePath}</Typography.Text>
                </Space>
                <Typography.Paragraph type="secondary">
                  {t('sandboxReplay.editor.originalRun', {
                    outcome: t(`sandboxReplay.status.${selectedBaseline.outcome}`),
                    status: selectedBaseline.httpStatus ?? '-',
                    duration: selectedBaseline.durationMs,
                  })}
                </Typography.Paragraph>
              </Card>
            )}
            <Form.Item
              name="reason"
              label={t('sandboxReplay.editor.reason')}
              rules={[
                { required: true, message: t('sandboxReplay.editor.reasonRequired') },
                { min: 5, max: 500, message: t('sandboxReplay.editor.reasonRequired') },
              ]}
            >
              <Input.TextArea rows={4} maxLength={500} showCount placeholder={t('sandboxReplay.editor.reasonPlaceholder')} />
            </Form.Item>
          </Form>
        </div>
      </Modal>

      <Drawer
        open={detailOpen}
        loading={detailLoading}
        size={760}
        title={t('sandboxReplay.detail.title')}
        onClose={() => setDetailOpen(false)}
      >
        {detail && (
          <div className="oa-sandbox-replay-detail">
            <Alert
              type="info"
              showIcon
              title={t('sandboxReplay.detail.safeTitle')}
              description={t('sandboxReplay.detail.safeDescription')}
            />
            <Descriptions bordered column={isMobile ? 1 : 2} items={detailItems} />
            <div className="oa-sandbox-replay-preview-grid">
              <Card size="small" title={t('sandboxReplay.detail.baselinePreview')}>
                {detail.baselineResponsePreview
                  ? <pre>{detail.baselineResponsePreview}</pre>
                  : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('sandboxReplay.detail.noPreview')} />}
              </Card>
              <Card size="small" title={t('sandboxReplay.detail.replayPreview')}>
                {detail.replayResponsePreview
                  ? <pre>{detail.replayResponsePreview}</pre>
                  : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('sandboxReplay.detail.noPreview')} />}
              </Card>
            </div>
          </div>
        )}
      </Drawer>
    </section>
  );
}

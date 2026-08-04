'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, DatePicker, Empty, Input, Select, Table, Tag, Typography } from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import { auditApi, formatOaApiError, type AuditRecord } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';

const { RangePicker } = DatePicker;

export default function AuditCenterPage() {
  const { t } = useTranslation();
  const [records, setRecords] = useState<AuditRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [action, setAction] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [result, setResult] = useState('');
  const [range, setRange] = useState<[string, string]>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await auditApi.list({
        action: action || undefined,
        resourceType: resourceType || undefined,
        result: result || undefined,
        from: range?.[0],
        to: range?.[1],
        page,
        size: 20,
      });
      setRecords(response.records);
      setTotal(response.total);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoading(false);
    }
  }, [action, page, range, resourceType, result]);

  useEffect(() => { void load(); }, [load]);

  const columns: ColumnsType<AuditRecord> = [
    { title: t('approval.audit.columnTime'), dataIndex: 'createdAt', width: 190, render: (value) => new Date(value).toLocaleString() },
    { title: t('approval.audit.columnActor'), dataIndex: 'actorName', width: 150 },
    {
      title: t('approval.audit.columnAction'),
      dataIndex: 'action',
      width: 150,
      render: (value: string) => {
        const key = 'approval.action.' + value;
        const translated = t(key);
        return translated === key ? value : translated;
      },
    },
    { title: t('approval.audit.columnResource'), key: 'resource', width: 190, render: (_, item) => `${item.resourceType} #${item.resourceId}` },
    {
      title: t('approval.audit.columnResult'),
      dataIndex: 'result',
      width: 110,
      render: (value: string) => {
        const key = 'approval.audit.result.' + value;
        const translated = t(key);
        return <Tag color={value === 'SUCCESS' ? 'success' : value === 'DENIED' ? 'error' : 'warning'}>{translated === key ? value : translated}</Tag>;
      },
    },
    { title: t('approval.audit.columnSummary'), dataIndex: 'summary' },
    { title: 'Trace ID', dataIndex: 'traceId', width: 220, ellipsis: true },
  ];

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>{t('approval.audit.title')}</Typography.Title>
          <Typography.Paragraph type="secondary">{t('approval.audit.description')}</Typography.Paragraph>
        </div>
      </div>
      <Card className="oa-domain-card">
        <div className="oa-domain-toolbar">
          <Input value={action} onChange={(event) => setAction(event.target.value)}
            placeholder={t('approval.audit.actionPlaceholder')} allowClear style={{ width: 190 }} />
          <Input value={resourceType} onChange={(event) => setResourceType(event.target.value)}
            placeholder={t('approval.audit.resourceTypePlaceholder')} allowClear style={{ width: 180 }} />
          <Select value={result} style={{ width: 150 }} options={[
            { value: '', label: t('approval.audit.resultAll') },
            { value: 'SUCCESS', label: t('approval.audit.result.SUCCESS') },
            { value: 'DENIED', label: t('approval.audit.result.DENIED') },
            { value: 'CONFLICT', label: t('approval.audit.result.CONFLICT') },
            { value: 'FAILURE', label: t('approval.audit.result.FAILURE') },
          ]} onChange={setResult} />
          <RangePicker showTime onChange={(dates) => setRange(
            dates?.[0] && dates[1] ? [dates[0].toISOString(), dates[1].toISOString()] : undefined
          )} />
          <Button type="primary" icon={<OaIcon name="search" />} onClick={() => { setPage(1); void load(); }}>
            {t('approval.audit.search')}
          </Button>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description={t('approval.audit.empty')} /> }}
          scroll={{ x: 1150 }}
          pagination={{
            current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage,
          }}
        />
      </Card>
    </section>
  );
}

'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Card, DatePicker, Empty, Input, Select, Tag, Typography } from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import { auditApi, formatOaApiError, type AuditRecord } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import ResponsiveTable from './ResponsiveTable';
import dayjs from 'dayjs';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { showUiFormPreview } from './UiFormPreview';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination, uiDateRange } from '@/hooks/usePageUiEditorCommands';

const { RangePicker } = DatePicker;

export default function AuditCenterPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const scope = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const request = useRef(0);
  const [dataScope, setDataScope] = useState(scope);
  const [storedRecords, setRecords] = useState<AuditRecord[]>([]);
  const records = dataScope === scope ? storedRecords : [];
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [action, setAction] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [result, setResult] = useState('');
  const [range, setRange] = useState<[string, string]>();

  const load = useCallback(async () => {
    const ticket = ++request.current;
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
      if (ticket !== request.current || activeScope.current !== scope) return;
      setDataScope(scope); setRecords(response.records);
      setTotal(response.total);
    } catch (error) {
      if (ticket !== request.current || activeScope.current !== scope) return;
      setRecords([]); setTotal(0);
      message.error(formatOaApiError(error));
    } finally {
      if (ticket === request.current && activeScope.current === scope) setLoading(false);
    }
  }, [action, page, range, resourceType, result, scope]);

  useEffect(() => {
    activeScope.current = scope; setRecords([]); setTotal(0);
    return () => { activeScope.current = undefined; request.current += 1; };
  }, [scope]);
  useEffect(() => { void load(); return () => { request.current += 1; }; }, [load]);
  usePublishPageAgentContext({ action: action || undefined, resourceType: resourceType || undefined, result: result || undefined,
    from: range?.[0], to: range?.[1], page, size: 20 });
  useRegisterPageUiCommands({ 'ui.refreshPage': load,
    'ui.openDetail': ({ payload: { recordId } }) => {
      const id = Number(recordId), record = records.find(item => item.id === id);
      if (!Number.isSafeInteger(id) || id < 1 || !record) throw new Error('PAGE_UI_FIELD_REJECTED');
      showUiFormPreview({ createdAt: record.createdAt, actor: record.actorName, action: record.action,
        resource: `${record.resourceType} #${record.resourceId}`, result: record.result, summary: record.summary },
      { createdAt: t('approval.audit.columnTime'), actor: t('approval.audit.columnActor'), action: t('approval.audit.columnAction'),
        resource: t('approval.audit.columnResource'), result: t('approval.audit.columnResult'), summary: t('approval.audit.columnSummary') });
    },
    'ui.applyFilter': ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { action: 'string', resourceType: 'string', result: ['', 'SUCCESS', 'DENIED', 'CONFLICT', 'FAILURE'],
        from: 'string', to: 'string', page: 'number', size: 'number' });
      requireUiPagination(filters);
      if (filters.size !== undefined && filters.size !== 20) throw new Error('PAGE_UI_FIELD_REJECTED');
      const dates = uiDateRange(filters, range);
      if (filters.action !== undefined) setAction(String(filters.action));
      if (filters.resourceType !== undefined) setResourceType(String(filters.resourceType));
      if (filters.result !== undefined) setResult(String(filters.result));
      if (filters.from !== undefined || filters.to !== undefined) setRange(dates);
      setPage(Number(filters.page ?? 1));
    },
  });

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
          <RangePicker showTime value={range ? [dayjs(range[0]), dayjs(range[1])] : null} onChange={(dates) => setRange(
            dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DDTHH:mm:ss'), dates[1].format('YYYY-MM-DDTHH:mm:ss')] : undefined
          )} />
          <Button type="primary" icon={<OaIcon name="search" />} onClick={() => { if (page === 1) void load(); else setPage(1); }}>
            {t('approval.audit.search')}
          </Button>
        </div>
        <ResponsiveTable
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

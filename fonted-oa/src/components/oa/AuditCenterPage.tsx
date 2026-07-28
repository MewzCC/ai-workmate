'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, DatePicker, Empty, Input, Select, Space, Table, Tag, Typography } from 'antd';
import { message } from '@/lib/antdMessage';
import type { ColumnsType } from 'antd/es/table';
import { auditApi, formatOaApiError, type AuditRecord } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

const { RangePicker } = DatePicker;

export default function AuditCenterPage() {
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
    { title: '时间', dataIndex: 'createdAt', width: 190, render: (value) => new Date(value).toLocaleString() },
    { title: '操作人', dataIndex: 'actorName', width: 150 },
    { title: '动作', dataIndex: 'action', width: 150 },
    { title: '资源', key: 'resource', width: 190, render: (_, item) => `${item.resourceType} #${item.resourceId}` },
    {
      title: '结果',
      dataIndex: 'result',
      width: 110,
      render: (value) => <Tag color={value === 'SUCCESS' ? 'success' : value === 'DENIED' ? 'error' : 'warning'}>{value}</Tag>,
    },
    { title: '摘要', dataIndex: 'summary' },
    { title: 'Trace ID', dataIndex: 'traceId', width: 220, ellipsis: true },
  ];

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>审计中心</Typography.Title>
          <Typography.Paragraph type="secondary">查看当前租户的业务写入、拒绝与冲突记录。</Typography.Paragraph>
        </div>
      </div>
      <Card className="oa-domain-card">
        <div className="oa-domain-toolbar">
          <Input value={action} onChange={(event) => setAction(event.target.value)}
            placeholder="动作，例如 APPROVE" allowClear style={{ width: 190 }} />
          <Input value={resourceType} onChange={(event) => setResourceType(event.target.value)}
            placeholder="资源类型" allowClear style={{ width: 180 }} />
          <Select value={result} style={{ width: 150 }} options={[
            { value: '', label: '全部结果' },
            { value: 'SUCCESS', label: '成功' },
            { value: 'DENIED', label: '拒绝' },
            { value: 'CONFLICT', label: '冲突' },
            { value: 'FAILURE', label: '失败' },
          ]} onChange={setResult} />
          <RangePicker showTime onChange={(dates) => setRange(
            dates?.[0] && dates[1] ? [dates[0].toISOString(), dates[1].toISOString()] : undefined
          )} />
          <Button type="primary" icon={<OaIcon name="search" />} onClick={() => { setPage(1); void load(); }}>
            查询
          </Button>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={records}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无审计记录" /> }}
          scroll={{ x: 1150 }}
          pagination={{
            current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage,
          }}
        />
      </Card>
    </section>
  );
}

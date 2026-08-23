'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  approvalEngineApi,
  type ApprovalConfigStatus,
  type ApprovalForm,
  type ApprovalProcess,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import ApprovalConfigShell from './ApprovalConfigShell';
import ProcessDesignerModal from './ProcessDesignerModal';
import { StatusTag } from './FormEnginePage';

function parseNodes(nodeJson: string): unknown[] {
  try {
    const parsed = JSON.parse(nodeJson);
    if (Array.isArray(parsed)) return parsed;
  } catch {
    // 非法 JSON 视为空节点列表，交由设计器处理
  }
  return [];
}

export default function ProcessConfigPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<ApprovalProcess[]>([]);
  const [forms, setForms] = useState<ApprovalForm[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ApprovalConfigStatus | undefined>(undefined);

  const [designerOpen, setDesignerOpen] = useState(false);
  const [designerEditing, setDesignerEditing] = useState<ApprovalProcess | null>(null);

  const load = useCallback(async (p = page) => {
    setLoading(true);
    try {
      const res = await approvalEngineApi.listProcesses({
        keyword: keyword || undefined,
        status,
        page: p,
        size: 20,
      });
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [keyword, status, page]);

  useEffect(() => {
    void load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 关联表单下拉数据：表单引擎只读列表（approval:read 即可）
  useEffect(() => {
    approvalEngineApi.listForms({ page: 1, size: 100 })
      .then((res) => setForms(res.records))
      .catch(() => setForms([]));
  }, []);

  const openCreate = () => {
    setDesignerEditing(null);
    setDesignerOpen(true);
  };

  const openEdit = (record: ApprovalProcess) => {
    setDesignerEditing(record);
    setDesignerOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await approvalEngineApi.deleteProcess(id);
      message.success(t('approval.config.common.deleteSuccess'));
      await load(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const columns: ColumnsType<ApprovalProcess> = [
    {
      title: t('approval.config.process.columnKey'),
      dataIndex: 'processKey',
      key: 'processKey',
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: t('approval.config.process.columnName'),
      dataIndex: 'processName',
      key: 'processName',
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: t('approval.config.process.columnForm'),
      dataIndex: 'formName',
      key: 'formName',
      render: (v?: string | null) => v || <Typography.Text type="secondary">{t('approval.config.process.unbound')}</Typography.Text>,
    },
    {
      title: t('approval.config.process.columnNodes'),
      key: 'nodes',
      width: 110,
      render: (_, record) => t('approval.config.process.nodesCount', { count: parseNodes(record.nodeJson).length }),
    },
    {
      title: t('approval.config.common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: ApprovalConfigStatus) => <StatusTag status={v} />,
    },
    {
      title: t('approval.config.common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 80,
    },
    {
      title: t('approval.config.common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 130,
      render: (_, record) =>
        record.canEdit ? (
          <Space>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              {t('approval.config.common.edit')}
            </Button>
            <Popconfirm
              title={t('approval.config.common.deleteConfirm')}
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" size="small" danger disabled={!record.canDelete}>
                {t('approval.config.common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <ApprovalConfigShell
      eyebrow="PROCESS CONFIG"
      title={t('approval.config.process.title')}
      description={t('approval.config.process.description')}
      actions={
        <Button type="primary" onClick={openCreate}>
          {t('approval.config.process.create')}
        </Button>
      }
    >
      <Card className="leave-list-card" variant="borderless">
        <div className="leave-list-toolbar">
          <Input.Search
            placeholder={t('approval.config.common.searchPlaceholder')}
            allowClear
            style={{ width: 240 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={() => { setPage(1); void load(1); }}
          />
          <Space wrap>
            <Select
              allowClear
              placeholder={t('approval.config.common.status')}
              style={{ width: 130 }}
              value={status}
              onChange={(v) => { setStatus(v); setPage(1); }}
              options={(['ENABLED', 'DISABLED'] as const).map((s) => ({
                value: s,
                label: t(`approval.config.common.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
            <Button icon={<OaIcon name="reload" />} onClick={() => void load(page)}>
              {t('common.refresh')}
            </Button>
          </Space>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          size="middle"
          locale={{ emptyText: <Empty description={t('approval.config.common.noData')} /> }}
          scroll={{ x: 980 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            onChange: (p) => { setPage(p); void load(p); },
          }}
        />
      </Card>

      <ProcessDesignerModal
        open={designerOpen}
        editing={designerEditing}
        forms={forms}
        onClose={() => setDesignerOpen(false)}
        onSaved={() => {
          setDesignerOpen(false);
          void load(page);
        }}
      />
    </ApprovalConfigShell>
  );
}
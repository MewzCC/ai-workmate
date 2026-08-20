'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import {
  adminAssetsApi,
  type MeetingRoom,
  type MeetingRoomPayload,
  type MeetingRoomStatus,
} from '@/lib/adminAssetsApi';
import { formatOaApiError } from '@/lib/oaApi';
import AdminAssetsPageShell from './AdminAssetsPageShell';

const STATUS_TAG_COLOR: Record<MeetingRoomStatus, string> = {
  OPEN: 'success',
  CLOSED: 'default',
};

export default function MeetingRoomPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<MeetingRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);

  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<MeetingRoom | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<MeetingRoomPayload>();

  const load = useCallback(async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await adminAssetsApi.listMeetingRooms({
        keyword: keyword || undefined,
        status: status as MeetingRoomStatus | undefined,
        page: p,
        size: s,
      });
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [keyword, status, page, size]);

  useEffect(() => {
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    setPage(1);
    load(1);
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setEditOpen(true);
  };

  const openEdit = (record: MeetingRoom) => {
    setEditing(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      location: record.location || '',
      capacity: record.capacity,
      facilities: record.facilities || '',
      status: record.status,
      remark: record.remark || '',
    });
    setEditOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await adminAssetsApi.updateMeetingRoom(editing.id, values);
        message.success(t('adminAssets.meeting.updateSuccess'));
      } else {
        await adminAssetsApi.createMeetingRoom(values);
        message.success(t('adminAssets.meeting.createSuccess'));
      }
      setEditOpen(false);
      await load(page);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await adminAssetsApi.deleteMeetingRoom(id);
      message.success(t('adminAssets.meeting.deleteSuccess'));
      await load(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const columns: ColumnsType<MeetingRoom> = [
    { title: t('adminAssets.meeting.code'), dataIndex: 'code', key: 'code' },
    { title: t('adminAssets.meeting.name'), dataIndex: 'name', key: 'name' },
    {
      title: t('adminAssets.meeting.location'),
      dataIndex: 'location',
      key: 'location',
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.meeting.capacity'),
      dataIndex: 'capacity',
      key: 'capacity',
    },
    {
      title: t('adminAssets.meeting.facilities'),
      dataIndex: 'facilities',
      key: 'facilities',
      ellipsis: true,
      render: (v?: string | null) => v || '-',
    },
    {
      title: t('adminAssets.meeting.status'),
      dataIndex: 'status',
      key: 'status',
      render: (s: MeetingRoomStatus) => (
        <Tag color={STATUS_TAG_COLOR[s]}>
          {t(`adminAssets.meeting.statusOption.${s}`, { defaultValue: s })}
        </Tag>
      ),
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_: unknown, record: MeetingRoom) =>
        record.canEdit ? (
          <Space>
            <Button type="link" onClick={() => openEdit(record)}>
              {t('adminAssets.common.edit')}
            </Button>
            <Popconfirm
              title={t('adminAssets.meeting.deleteConfirm')}
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" danger disabled={!record.canDelete}>
                {t('adminAssets.common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <AdminAssetsPageShell
      eyebrow={t('adminAssets.eyebrow')}
      title={t('adminAssets.meeting.title')}
      description={t('adminAssets.meeting.description')}
      actions={
        <Button type="primary" onClick={openCreate}>
          {t('adminAssets.meeting.create')}
        </Button>
      }
    >
      <Spin spinning={loading}>
        <Card className="oa-admin-assets-card oa-admin-assets-card--fill" variant="outlined">
          <Space className="oa-admin-assets-filters" wrap>
            <Input.Search
              placeholder={t('adminAssets.meeting.searchPlaceholder')}
              allowClear
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={handleSearch}
            />
            <Select
              allowClear
              placeholder={t('adminAssets.meeting.status')}
              style={{ width: 130 }}
              value={status}
              onChange={(v) => setStatus(v)}
              options={Object.keys(STATUS_TAG_COLOR).map((s) => ({
                value: s,
                label: t(`adminAssets.meeting.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
          </Space>

          <Table
            rowKey="id"
            columns={columns}
            dataSource={data}
            size="middle"
            style={{ marginTop: 16 }}
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
            locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
          />
        </Card>
      </Spin>

      <Modal
        title={editing ? t('adminAssets.meeting.edit') : t('adminAssets.meeting.create')}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        okText={t('adminAssets.common.save')}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'OPEN', capacity: 0 }}>
          <Form.Item
            name="code"
            label={t('adminAssets.meeting.code')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('adminAssets.meeting.name')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item name="location" label={t('adminAssets.meeting.location')}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="capacity" label={t('adminAssets.meeting.capacity')}>
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="facilities" label={t('adminAssets.meeting.facilities')}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="status" label={t('adminAssets.meeting.status')}>
            <Select
              options={Object.keys(STATUS_TAG_COLOR).map((s) => ({
                value: s,
                label: t(`adminAssets.meeting.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
          </Form.Item>
          <Form.Item name="remark" label={t('adminAssets.meeting.remark')}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </AdminAssetsPageShell>
  );
}

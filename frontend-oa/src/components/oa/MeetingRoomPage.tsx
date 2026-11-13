'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
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
import { usePermission } from '@/hooks/usePermission';
import AdminAssetsPageShell from './AdminAssetsPageShell';
import MeetingBookingPanel from './MeetingBookingPanel';
import ResponsiveTable from './ResponsiveTable';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { showUiFormPreview } from './UiFormPreview';
import { usePageUiFormContext } from '@/hooks/usePageUiFormContext';

const STATUS_TAG_COLOR: Record<MeetingRoomStatus, string> = {
  OPEN: 'success',
  CLOSED: 'default',
};

export default function MeetingRoomPage() {
  const { t } = useTranslation();
  const { allowed: canManage, permissionVersion } = usePermission('meeting:write');
  const [data, setData] = useState<MeetingRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<MeetingRoom | null>(null);

  usePublishPageAgentContext({
    roomId: editOpen ? editing?.id : undefined,
    version: editOpen ? editing?.version : undefined,
    keyword: keyword || undefined,
    roomStatus: status,
    page,
    size,
  });

  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<MeetingRoomPayload>();
  const uiForm = usePageUiFormContext(editOpen && !submitting, editing?.id, editing?.version);
  const listRequest = useRef(0);

  const load = useCallback(async (p = page, s = size, filters?: { keyword: string; status?: string }) => {
    const request = ++listRequest.current;
    setLoading(true);
    try {
      const res = await adminAssetsApi.listMeetingRooms({
        keyword: (filters ? filters.keyword : keyword) || undefined,
        status: ((filters ? filters.status : status) || undefined) as MeetingRoomStatus | undefined,
        page: p,
        size: s,
      });
      if (request !== listRequest.current) return;
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      if (request === listRequest.current) { setData([]); setTotal(0); message.error(formatOaApiError(err)); }
    } finally {
      if (request === listRequest.current) setLoading(false);
    }
  }, [keyword, status, page, size]);

  useEffect(() => {
    setPage(1);
    load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [permissionVersion]);

  const handleSearch = () => {
    setPage(1);
    load(1);
  };

  const openCreate = () => {
    uiForm.renew();
    setEditing(null);
    form.resetFields();
    setEditOpen(true);
  };

  useRegisterPageUiCommands({
    'ui.applyFilter': async ({ payload: { filters } }) => {
      requireUiFieldTypes(filters, { keyword: 'string', roomStatus: ['', 'OPEN', 'CLOSED'], page: 'number', size: 'number' });
      const next = { keyword: String(filters.keyword ?? keyword), status: String(filters.roomStatus ?? status ?? '') };
      const nextPage = Number(filters.page ?? 1), nextSize = Number(filters.size ?? size);
      if (!Number.isInteger(nextPage) || nextPage < 1 || nextPage > 10000 || !Number.isInteger(nextSize) || nextSize < 1 || nextSize > 100)
        throw new Error('PAGE_UI_FIELD_REJECTED');
      setKeyword(next.keyword); setStatus(next.status || undefined); setPage(nextPage); setSize(nextSize);
      await load(nextPage, nextSize, next);
    },
    'ui.refreshPage': async () => { await load(); },
    ...(canManage ? {
      'ui.openCreateForm': () => openCreate(),
      'ui.fillForm': async ({ payload: { values } }: Extract<import('@/lib/pageUiCommands').PageUiCommand, { code: 'ui.fillForm' }>) => {
        if (!editOpen) throw new Error('PAGE_UI_FORM_CONTEXT_REQUIRED');
        requireUiFieldTypes(values, { code: 'string', name: 'string', location: 'string', capacity: 'number', facilities: 'string', status: ['OPEN', 'CLOSED'], remark: 'string' });
        form.setFieldsValue(values as Partial<MeetingRoomPayload>);
        await form.validateFields(Object.keys(values));
      },
      'ui.previewSubmission': async () => {
        if (!editOpen) throw new Error('PAGE_UI_FORM_CLOSED');
        const values = await form.validateFields();
        showUiFormPreview(values, Object.fromEntries(['code', 'name', 'location', 'capacity', 'facilities', 'status', 'remark']
          .map((name) => [name, t(`adminAssets.meeting.${name}`)])));
      },
    } : {}),
  }, uiForm.context);

  const openEdit = (record: MeetingRoom) => {
    uiForm.renew();
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
    if (!canManage) return;
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await adminAssetsApi.updateMeetingRoom(editing.id, { ...values, version: editing.version });
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
    if (!canManage) return;
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
        canManage && record.canEdit ? (
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
      className="oa-admin-assets-page--meeting"
      eyebrow={t('adminAssets.eyebrow')}
      title={t('adminAssets.meeting.title')}
      description={t('adminAssets.meeting.description')}
      actions={
        canManage ? (
          <Button type="primary" onClick={openCreate}>
            {t('adminAssets.meeting.create')}
          </Button>
        ) : undefined
      }
    >
      <div className="oa-meeting-booking-stack">
        <MeetingBookingPanel rooms={data} canManage={canManage} />
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

          <ResponsiveTable
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
      </div>

      <Modal
        title={editing ? t('adminAssets.meeting.edit') : t('adminAssets.meeting.create')}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleSubmit}
        okButtonProps={{ disabled: !canManage }}
        confirmLoading={submitting}
        okText={t('adminAssets.common.save')}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'OPEN', capacity: 0 }} onValuesChange={uiForm.renew}>
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

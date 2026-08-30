'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Button,
  Calendar,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { message } from '@/lib/antdMessage';
import {
  adminAssetsApi,
  type MeetingBooking,
  type MeetingBookingPayload,
  type MeetingRoom,
} from '@/lib/adminAssetsApi';
import { formatOaApiError } from '@/lib/oaApi';

interface Props {
  rooms: MeetingRoom[];
  canManage: boolean;
}

interface BookingFormValues {
  roomId: number;
  title: string;
  agenda?: string;
  timeRange: [Dayjs, Dayjs];
  attendeeCount: number;
}

export default function MeetingBookingPanel({ rooms, canManage }: Props) {
  const { t } = useTranslation();
  const [mine, setMine] = useState<MeetingBooking[]>([]);
  const [adminRows, setAdminRows] = useState<MeetingBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [scope, setScope] = useState<'mine' | 'admin'>('mine');
  const [view, setView] = useState<'calendar' | 'list'>('calendar');
  const [bookingOpen, setBookingOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<BookingFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const myResponse = await adminAssetsApi.listMyMeetingBookings({ page: 1, size: 100 });
      setMine(myResponse.records);
      if (canManage) {
        const adminResponse = await adminAssetsApi.listAdminMeetingBookings({ page: 1, size: 100 });
        setAdminRows(adminResponse.records);
      } else {
        setAdminRows([]);
        setScope('mine');
      }
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [canManage]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = scope === 'admin' ? adminRows : mine;
  const calendarRows = useMemo(() => rows.filter((row) => row.status === 'BOOKED'), [rows]);

  const openBooking = () => {
    form.resetFields();
    form.setFieldsValue({ attendeeCount: 1 });
    setBookingOpen(true);
  };

  const submitBooking = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: MeetingBookingPayload = {
        roomId: values.roomId,
        title: values.title,
        agenda: values.agenda,
        startAt: values.timeRange[0].format('YYYY-MM-DDTHH:mm:ss'),
        endAt: values.timeRange[1].format('YYYY-MM-DDTHH:mm:ss'),
        attendeeCount: values.attendeeCount,
      };
      await adminAssetsApi.createMeetingBooking(payload);
      setBookingOpen(false);
      message.success(t('adminAssets.meeting.booking.createSuccess'));
      await load();
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const cancelBooking = async (booking: MeetingBooking) => {
    try {
      await adminAssetsApi.cancelMeetingBooking(booking.id, { version: booking.version });
      message.success(t('adminAssets.meeting.booking.cancelSuccess'));
      await load();
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const columns: ColumnsType<MeetingBooking> = [
    {
      title: t('adminAssets.meeting.booking.time'),
      key: 'time',
      render: (_, row) => (
        <Space className="oa-meeting-booking-time" direction="vertical" size={0}>
          <Typography.Text>{dayjs(row.startAt).format('YYYY-MM-DD HH:mm')}</Typography.Text>
          <Typography.Text type="secondary">{dayjs(row.endAt).format('YYYY-MM-DD HH:mm')}</Typography.Text>
        </Space>
      ),
    },
    { title: t('adminAssets.meeting.booking.title'), dataIndex: 'title', key: 'title' },
    {
      title: t('adminAssets.meeting.booking.room'),
      key: 'room',
      render: (_, row) => [row.roomName, row.roomLocation].filter(Boolean).join(' · ') || '-',
    },
    ...(scope === 'admin' ? [{
      title: t('adminAssets.meeting.booking.organizer'), dataIndex: 'organizerName', key: 'organizerName',
      responsive: ['md'],
    }] as ColumnsType<MeetingBooking> : []),
    {
      title: t('adminAssets.meeting.booking.attendeeCount'),
      dataIndex: 'attendeeCount',
      key: 'attendeeCount',
      responsive: ['md'],
    },
    {
      title: t('adminAssets.common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: MeetingBooking['status']) => (
        <Tag color={status === 'BOOKED' ? 'success' : 'default'}>
          {t(`adminAssets.meeting.booking.status.${status}`)}
        </Tag>
      ),
    },
    {
      title: t('adminAssets.common.action'),
      key: 'action',
      render: (_, row) => row.canCancel ? (
        <Popconfirm
          title={t('adminAssets.meeting.booking.cancelConfirm')}
          onConfirm={() => void cancelBooking(row)}
        >
          <Button type="link" danger>{t('adminAssets.meeting.booking.cancel')}</Button>
        </Popconfirm>
      ) : '-',
    },
  ];

  return (
    <Card
      className="oa-admin-assets-card"
      title={t('adminAssets.meeting.booking.sectionTitle')}
      extra={<Button type="primary" onClick={openBooking}>{t('adminAssets.meeting.booking.create')}</Button>}
    >
      <Space className="oa-meeting-booking-toolbar" wrap>
        <Segmented
          value={view}
          onChange={(value) => setView(value as 'calendar' | 'list')}
          options={[
            { value: 'calendar', label: t('adminAssets.meeting.booking.calendarView') },
            { value: 'list', label: t('adminAssets.meeting.booking.listView') },
          ]}
        />
        {canManage && (
          <Segmented
            value={scope}
            onChange={(value) => setScope(value as 'mine' | 'admin')}
            options={[
              { value: 'mine', label: t('adminAssets.meeting.booking.mine') },
              { value: 'admin', label: t('adminAssets.meeting.booking.admin') },
            ]}
          />
        )}
      </Space>
      <Spin spinning={loading}>
        {view === 'calendar' ? (
          <Calendar
            fullscreen={false}
            cellRender={(date, info) => info.type === 'date' ? (
              <Space className="oa-meeting-booking-calendar-cell" direction="vertical" size={2}>
                {calendarRows.filter((row) => dayjs(row.startAt).isSame(date, 'day')).slice(0, 3).map((row) => (
                  <Badge
                    key={row.id}
                    status="processing"
                    text={`${dayjs(row.startAt).format('HH:mm')} ${row.roomName || ''}`}
                  />
                ))}
              </Space>
            ) : info.originNode}
          />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={rows}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            locale={{ emptyText: <Empty description={t('adminAssets.common.noData')} /> }}
          />
        )}
      </Spin>

      <Modal
        title={t('adminAssets.meeting.booking.create')}
        open={bookingOpen}
        onCancel={() => setBookingOpen(false)}
        onOk={() => void submitBooking()}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="roomId"
            label={t('adminAssets.meeting.booking.room')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={rooms.filter((room) => room.status === 'OPEN').map((room) => ({
                value: room.id,
                label: `${room.name} · ${room.location || '-'} · ${room.capacity}${t('adminAssets.meeting.booking.people')}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="title"
            label={t('adminAssets.meeting.booking.title')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            name="timeRange"
            label={t('adminAssets.meeting.booking.time')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <DatePicker.RangePicker
              className="oa-meeting-booking-field"
              showTime={{ format: 'HH:mm' }}
              format="YYYY-MM-DD HH:mm"
              disabledDate={(date) => date.endOf('day').isBefore(dayjs())}
            />
          </Form.Item>
          <Form.Item
            name="attendeeCount"
            label={t('adminAssets.meeting.booking.attendeeCount')}
            rules={[{ required: true, message: t('adminAssets.common.fieldRequired') }]}
          >
            <InputNumber className="oa-meeting-booking-field" min={1} max={10000} />
          </Form.Item>
          <Form.Item name="agenda" label={t('adminAssets.meeting.booking.agenda')}>
            <Input.TextArea maxLength={500} showCount rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

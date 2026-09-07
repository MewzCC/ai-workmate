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
  List,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
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
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';

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
  const { t, i18n } = useTranslation();
  const [mine, setMine] = useState<MeetingBooking[]>([]);
  const [adminRows, setAdminRows] = useState<MeetingBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [scope, setScope] = useState<'mine' | 'admin'>('mine');
  const [view, setView] = useState<'calendar' | 'list'>('calendar');
  const calendarLocale = i18n.language === 'zh-CN' ? 'zh-cn' : 'en';
  const [calendarValue, setCalendarValue] = useState(() => dayjs().locale(calendarLocale));
  const [selectedDate, setSelectedDate] = useState(() => dayjs().locale(calendarLocale));
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
  const bookingsByDate = useMemo(() => calendarRows.reduce<Record<string, MeetingBooking[]>>((result, row) => {
    const dateKey = dayjs(row.startAt).format('YYYY-MM-DD');
    (result[dateKey] ||= []).push(row);
    return result;
  }, {}), [calendarRows]);
  const selectedRows = bookingsByDate[selectedDate.format('YYYY-MM-DD')] || [];

  useEffect(() => {
    setCalendarValue((value) => value.locale(calendarLocale));
    setSelectedDate((value) => value.locale(calendarLocale));
  }, [calendarLocale]);

  const openBooking = (date?: Dayjs) => {
    form.resetFields();
    const bookingDate = date?.startOf('day').isBefore(dayjs().startOf('day')) ? dayjs() : date;
    const startAt = bookingDate
      ? bookingDate.hour(9).minute(0).second(0)
      : undefined;
    form.setFieldsValue({
      attendeeCount: 1,
      timeRange: startAt ? [startAt, startAt.add(1, 'hour')] : undefined,
    });
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
      extra={<Button type="primary" onClick={() => openBooking()}>{t('adminAssets.meeting.booking.create')}</Button>}
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
          <div className="oa-meeting-calendar-board">
            <div className="oa-meeting-calendar-main">
              <Calendar
                value={calendarValue}
                fullscreen
                onChange={(date) => setCalendarValue(date.locale(calendarLocale))}
                onSelect={(date) => {
                  setCalendarValue(date.locale(calendarLocale));
                  setSelectedDate(date.locale(calendarLocale));
                }}
                onPanelChange={(date) => setCalendarValue(date.locale(calendarLocale))}
                headerRender={({ value, onChange }) => {
                  const year = value.year();
                  const month = value.month();
                  const yearOptions = Array.from({ length: 11 }, (_, index) => year - 5 + index)
                    .map((item) => ({ value: item, label: t('adminAssets.meeting.booking.yearLabel', { year: item }) }));
                  const monthOptions = Array.from({ length: 12 }, (_, index) => ({
                    value: index,
                    label: dayjs().locale(calendarLocale).month(index).format('MMM'),
                  }));
                  return (
                    <div className="oa-meeting-calendar-header">
                      <div>
                        <Typography.Title level={4} className="oa-meeting-calendar-title">
                          {value.locale(calendarLocale).format(t('adminAssets.meeting.booking.monthFormat'))}
                        </Typography.Title>
                        <Typography.Text type="secondary">
                          {t('adminAssets.meeting.booking.monthSummary', { count: calendarRows.filter((row) => dayjs(row.startAt).isSame(value, 'month')).length })}
                        </Typography.Text>
                      </div>
                      <Space wrap size={8}>
                        <Button onClick={() => {
                          const today = dayjs().locale(calendarLocale);
                          onChange(today);
                          setSelectedDate(today);
                        }}>
                          {t('adminAssets.meeting.booking.today')}
                        </Button>
                        <Tooltip title={t('adminAssets.meeting.booking.previousMonth')}>
                          <Button
                            aria-label={t('adminAssets.meeting.booking.previousMonth')}
                            icon={<OaIcon name="previous" />}
                            onClick={() => onChange(value.subtract(1, 'month'))}
                          />
                        </Tooltip>
                        <Select
                          aria-label={t('adminAssets.meeting.booking.selectYear')}
                          value={year}
                          options={yearOptions}
                          onChange={(nextYear) => onChange(value.year(nextYear))}
                        />
                        <Select
                          aria-label={t('adminAssets.meeting.booking.selectMonth')}
                          value={month}
                          options={monthOptions}
                          onChange={(nextMonth) => onChange(value.month(nextMonth))}
                        />
                        <Tooltip title={t('adminAssets.meeting.booking.nextMonth')}>
                          <Button
                            aria-label={t('adminAssets.meeting.booking.nextMonth')}
                            icon={<OaIcon name="next" />}
                            onClick={() => onChange(value.add(1, 'month'))}
                          />
                        </Tooltip>
                      </Space>
                    </div>
                  );
                }}
                fullCellRender={(date, info) => {
                  if (info.type !== 'date') return info.originNode;
                  const dateRows = bookingsByDate[date.format('YYYY-MM-DD')] || [];
                  const outsideMonth = !date.isSame(calendarValue, 'month');
                  return (
                    <div className={`oa-meeting-calendar-cell${outsideMonth ? ' is-outside' : ''}`}>
                      <div className="oa-meeting-calendar-cell-head">
                        <span className="oa-meeting-calendar-day">{date.date()}</span>
                        {dateRows.length > 0 && <Badge count={dateRows.length} size="small" />}
                      </div>
                      <Space className="oa-meeting-calendar-events" direction="vertical" size={4}>
                        {dateRows.slice(0, 2).map((row) => (
                          <Tooltip key={row.id} title={`${dayjs(row.startAt).format('HH:mm')} · ${row.title}`}>
                            <div className="oa-meeting-calendar-event">
                              <span>{dayjs(row.startAt).format('HH:mm')}</span>
                              <span>{row.roomName || row.title}</span>
                            </div>
                          </Tooltip>
                        ))}
                        {dateRows.length > 2 && (
                          <Typography.Text className="oa-meeting-calendar-more" type="secondary">
                            {t('adminAssets.meeting.booking.moreBookings', { count: dateRows.length - 2 })}
                          </Typography.Text>
                        )}
                      </Space>
                    </div>
                  );
                }}
              />
            </div>
            <aside className="oa-meeting-calendar-agenda">
              <div className="oa-meeting-calendar-agenda-head">
                <div>
                  <Typography.Text type="secondary">{t('adminAssets.meeting.booking.selectedDate')}</Typography.Text>
                  <Typography.Title level={4}>{selectedDate.format(t('adminAssets.meeting.booking.dateFormat'))}</Typography.Title>
                </div>
                <Badge count={selectedRows.length} showZero color="var(--oa-primary)" />
              </div>
              <List
                className="oa-meeting-calendar-agenda-list"
                dataSource={selectedRows}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('adminAssets.meeting.booking.noBookings')} /> }}
                renderItem={(row) => (
                  <List.Item>
                    <div className="oa-meeting-calendar-agenda-item">
                      <Typography.Text className="oa-meeting-calendar-agenda-time">
                        {dayjs(row.startAt).format('HH:mm')} – {dayjs(row.endAt).format('HH:mm')}
                      </Typography.Text>
                      <Typography.Text strong ellipsis>{row.title}</Typography.Text>
                      <Typography.Text type="secondary" ellipsis>
                        {[row.roomName, row.roomLocation].filter(Boolean).join(' · ') || '-'}
                      </Typography.Text>
                    </div>
                  </List.Item>
                )}
              />
              <Button
                block
                type="primary"
                icon={<OaIcon name="add" />}
                onClick={() => openBooking(selectedDate)}
              >
                {t('adminAssets.meeting.booking.bookSelectedDate')}
              </Button>
            </aside>
          </div>
        ) : (
          <ResponsiveTable
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

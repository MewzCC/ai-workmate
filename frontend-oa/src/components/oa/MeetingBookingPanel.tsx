'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { collectPagedRecords } from '@/lib/collectPagedRecords';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useAuth } from '@/components/auth/AuthProvider';

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
  const { user } = useAuth();
  const canReadMine = user?.permissions.includes('meeting:read:self') ?? false;
  const canBook = user?.permissions.includes('meeting:book') ?? false;
  const canCancelOwn = user?.permissions.includes('meeting:cancel') ?? false;
  const identity = `${user?.tenantId}:${user?.id}:${user?.permissionVersion}`;
  const activeIdentity = useRef<string | undefined>(identity); activeIdentity.current = identity;
  const [dataIdentity, setDataIdentity] = useState(identity);
  const [mine, setMine] = useState<MeetingBooking[]>([]);
  const [adminRows, setAdminRows] = useState<MeetingBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [scope, setScope] = useState<'mine' | 'admin'>('mine');
  const [view, setView] = useState<'calendar' | 'list'>('calendar');
  const calendarLocale = i18n.language === 'zh-CN' ? 'zh-cn' : 'en';
  const [calendarValue, setCalendarValue] = useState(() => dayjs().locale(calendarLocale));
  const [selectedDate, setSelectedDate] = useState(() => dayjs().locale(calendarLocale));
  const [bookingOpen, setBookingOpen] = useState(false);
  const { busy: submitting, begin: beginBooking, reset: resetBooking } = useAsyncFormAction(identity);
  const [form] = Form.useForm<BookingFormValues>();
  const loadSequence = useRef(0);
  const bookingSession = useRef(0);
  const rangeFrom = calendarValue.startOf('month').subtract(7, 'day').format('YYYY-MM-DDTHH:mm:ss');
  const rangeTo = calendarValue.endOf('month').add(7, 'day').format('YYYY-MM-DDTHH:mm:ss');

  usePublishPageAgentContext({
    from: calendarValue.startOf('month').format('YYYY-MM-DDTHH:mm:ss'),
    to: calendarValue.endOf('month').format('YYYY-MM-DDTHH:mm:ss'),
    bookingStatus: 'BOOKED',
  });

  const load = useCallback(async () => {
    if (activeIdentity.current !== identity) return;
    const sequence = ++loadSequence.current;
    setLoading(true);
    setMine([]);
    setAdminRows([]);
    try {
      const params = { from: rangeFrom, to: rangeTo };
      const [myRows, allRows] = await Promise.all([
        canReadMine ? collectPagedRecords((page, size) => adminAssetsApi.listMyMeetingBookings({ ...params, page, size })) : Promise.resolve([]),
        canManage ? collectPagedRecords((page, size) => adminAssetsApi.listAdminMeetingBookings({ ...params, page, size })) : Promise.resolve([]),
      ]);
      if (sequence !== loadSequence.current || activeIdentity.current !== identity) return;
      setDataIdentity(identity);
      setMine(myRows);
      setAdminRows(allRows);
      if (!canManage) setScope('mine');
    } catch (err) {
      if (sequence === loadSequence.current && activeIdentity.current === identity) message.error(formatOaApiError(err));
    } finally {
      if (sequence === loadSequence.current && activeIdentity.current === identity) setLoading(false);
    }
  }, [canManage, canReadMine, rangeFrom, rangeTo, identity]);
  const latestLoad = useRef(load); latestLoad.current = load;

  const invalidateLoad = useCallback(() => { loadSequence.current++; activeIdentity.current = undefined; }, []);
  useEffect(() => {
    activeIdentity.current = identity;
    void load();
    return invalidateLoad;
  }, [load, identity, invalidateLoad]);
  useEffect(() => {
    bookingSession.current++;
    setBookingOpen(false); resetBooking(); form.resetFields(); setScope('mine');
  }, [identity, form, resetBooking]);

  const visibleScope = canManage && !canReadMine ? 'admin' : scope;
  const rows = useMemo(() => dataIdentity === identity
    ? visibleScope === 'admin' && canManage ? adminRows : canReadMine ? mine : []
    : [], [dataIdentity, identity, visibleScope, canManage, canReadMine, adminRows, mine]);
  const calendarRows = useMemo(() => rows.filter((row) => row.status === 'BOOKED'), [rows]);
  const bookingsByDate = useMemo(() => calendarRows.reduce<Record<string, MeetingBooking[]>>((result, row) => {
    const from = dayjs(rangeFrom);
    const to = dayjs(rangeTo);
    let date = dayjs(row.startAt).startOf('day');
    if (date.isBefore(from)) date = from.startOf('day');
    const end = dayjs(row.endAt);
    while (date.isBefore(end) && !date.isAfter(to)) {
      (result[date.format('YYYY-MM-DD')] ||= []).push(row);
      date = date.add(1, 'day');
    }
    return result;
  }, {}), [calendarRows, rangeFrom, rangeTo]);
  const selectedRows = bookingsByDate[selectedDate.format('YYYY-MM-DD')] || [];

  useEffect(() => {
    setCalendarValue((value) => value.locale(calendarLocale));
    setSelectedDate((value) => value.locale(calendarLocale));
  }, [calendarLocale]);

  const openBooking = (date?: Dayjs) => {
    if (!canBook) return;
    bookingSession.current++;
    resetBooking();
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
    if (!canBook) return;
    const attempt = beginBooking();
    if (!attempt) return;
    const session = bookingSession.current;
    try {
      const values = await form.validateFields();
      if (!attempt.current() || activeIdentity.current !== identity || session !== bookingSession.current) return;
      const payload: MeetingBookingPayload = {
        roomId: values.roomId,
        title: values.title,
        agenda: values.agenda,
        startAt: values.timeRange[0].format('YYYY-MM-DDTHH:mm:ss'),
        endAt: values.timeRange[1].format('YYYY-MM-DDTHH:mm:ss'),
        attendeeCount: values.attendeeCount,
      };
      await adminAssetsApi.createMeetingBooking(payload);
      if (activeIdentity.current !== identity) return;
      if (session === bookingSession.current) {
        setBookingOpen(false);
        message.success(t('adminAssets.meeting.booking.createSuccess'));
      }
      await latestLoad.current();
    } catch (err) {
      if (activeIdentity.current !== identity || session !== bookingSession.current) return;
      if (isFormValidationError(err)) return;
      message.error(formatOaApiError(err));
    } finally {
      attempt.finish();
    }
  };

  const cancelBooking = async (booking: MeetingBooking) => {
    if (!(canManage || canCancelOwn) || activeIdentity.current !== identity) return;
    try {
      await adminAssetsApi.cancelMeetingBooking(booking.id, { version: booking.version });
      if (activeIdentity.current !== identity) return;
      message.success(t('adminAssets.meeting.booking.cancelSuccess'));
      await latestLoad.current();
    } catch (err) {
      if (activeIdentity.current === identity) message.error(formatOaApiError(err));
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
      render: (_, row) => row.canCancel && (canManage || canCancelOwn) ? (
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
      extra={canBook && <Button type="primary" onClick={() => openBooking()}>{t('adminAssets.meeting.booking.create')}</Button>}
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
        {canManage && canReadMine && (
          <Segmented
                value={visibleScope}
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
                          {t('adminAssets.meeting.booking.monthSummary', { count: calendarRows.filter((row) => dayjs(row.startAt).isBefore(value.add(1, 'month').startOf('month')) && dayjs(row.endAt).isAfter(value.startOf('month'))).length })}
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
              {canBook && <Button
                block
                type="primary"
                icon={<OaIcon name="add" />}
                onClick={() => openBooking(selectedDate)}
              >
                {t('adminAssets.meeting.booking.bookSelectedDate')}
              </Button>}
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
        open={bookingOpen && canBook}
        onCancel={() => { bookingSession.current++; setBookingOpen(false); resetBooking(); }}
        onOk={() => void submitBooking()}
        okButtonProps={{ loading: submitting }}
        cancelText={t(submitting ? 'common.close' : 'common.cancel')}
        destroyOnClose
      >
        <Form form={form} disabled={submitting} layout="vertical">
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

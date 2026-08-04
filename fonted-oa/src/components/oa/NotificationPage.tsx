'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Space, Spin, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import {
  fetchUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '@/lib/notificationApi';

const TYPE_COLOR: Record<string, string> = {
  approval: 'blue',
  system: 'default',
  alert: 'error',
  todo: 'orange',
};

export default function NotificationPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [unread, setUnread] = useState(0);
  const [size] = useState(20);

  const load = useCallback(async (currentPage: number) => {
    setLoading(true);
    try {
      const [pageResult, unreadCount] = await Promise.all([
        listNotifications(currentPage, 20),
        fetchUnreadCount(),
      ]);
      setItems(pageResult.records);
      setTotal(pageResult.total);
      setUnread(unreadCount);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('pages.notification.loadFailed'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void load(page);
  }, [load, page]);

  const handleRead = async (item: NotificationItem) => {
    if (item.read) return;
    try {
      await markNotificationRead(item.id);
      await load(page);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('pages.notification.markReadFailed'));
    }
  };

  const handleReadAll = async () => {
    try {
      await markAllNotificationsRead();
      message.success(t('pages.notification.markAllReadSuccess'));
      await load(page);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('pages.notification.operationFailed'));
    }
  };

  const columns: TableProps<NotificationItem>['columns'] = [
    {
      title: t('pages.notification.columnType'),
      dataIndex: 'type',
      width: 90,
      render: (type: string) => {
        const color = TYPE_COLOR[type] || 'default';
        const labelKey = `pages.notification.type.${type}`;
        const label = t(labelKey);
        return <Tag color={color}>{label === labelKey ? type : label}</Tag>;
      },
    },
    {
      title: t('pages.notification.columnContent'),
      dataIndex: 'title',
      render: (title: string, item) => (
        <div className="oa-notification-cell">
          <span className={`oa-notification-title ${item.read ? '' : 'is-unread'}`}>
            {!item.read && <span className="oa-notification-dot" />}
            {title}
          </span>
          <span className="oa-notification-content">{item.content}</span>
        </div>
      ),
    },
    {
      title: t('pages.notification.columnTime'),
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 110,
      render: (_, item) =>
        item.read ? (
          <Typography.Text type="secondary">{t('pages.notification.read')}</Typography.Text>
        ) : (
          <Button size="small" onClick={() => void handleRead(item)}>{t('pages.notification.markRead')}</Button>
        ),
    },
  ];

  return (
    <section className="oa-domain-page">
      <div className="oa-domain-heading">
        <Space align="center" size={12}>
          <span className="oa-domain-heading-icon">
            <OaIcon name="messages" size={20} />
          </span>
          <div>
            <Typography.Title level={4} style={{ marginBottom: 0 }}>{t('pages.notification.title')}</Typography.Title>
            <Typography.Text type="secondary">{t('pages.notification.unreadCount', { count: unread })}</Typography.Text>
          </div>
        </Space>
        <Space>
          <Button icon={<OaIcon name="reload" />} onClick={() => void load(page)}>{t('common.refresh')}</Button>
          <Button type="primary" disabled={unread === 0} onClick={() => void handleReadAll()}>
            {t('pages.notification.markAllRead')}
          </Button>
        </Space>
      </div>

      <Card className="oa-domain-card" size="small">
        <Spin spinning={loading}>
          <Table<NotificationItem>
            rowKey="id"
            columns={columns}
            dataSource={items}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showSizeChanger: false,
              onChange: setPage,
            }}
            locale={{
              emptyText: <Empty description={t('pages.notification.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />,
            }}
          />
        </Spin>
      </Card>
    </section>
  );
}

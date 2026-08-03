'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Space, Spin, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';
import {
  fetchUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '@/lib/notificationApi';

const TYPE_TAG: Record<string, { color: string; label: string }> = {
  approval: { color: 'blue', label: '审批' },
  system: { color: 'default', label: '系统' },
  alert: { color: 'error', label: '告警' },
  todo: { color: 'orange', label: '待办' },
};

function typeTag(type: string) {
  const config = TYPE_TAG[type] || { color: 'default', label: type };
  return <Tag color={config.color}>{config.label}</Tag>;
}

export default function NotificationPage() {
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
      message.error(error instanceof Error ? error.message : '加载通知失败');
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
      message.error(error instanceof Error ? error.message : '标记已读失败');
    }
  };

  const handleReadAll = async () => {
    try {
      await markAllNotificationsRead();
      message.success('已全部标记为已读');
      await load(page);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const columns: TableProps<NotificationItem>['columns'] = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (type: string) => typeTag(type),
    },
    {
      title: '内容',
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
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, item) =>
        item.read ? (
          <Typography.Text type="secondary">已读</Typography.Text>
        ) : (
          <Button size="small" onClick={() => void handleRead(item)}>标记已读</Button>
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
            <Typography.Title level={4} style={{ marginBottom: 0 }}>消息中心</Typography.Title>
            <Typography.Text type="secondary">未读 {unread} 条</Typography.Text>
          </div>
        </Space>
        <Space>
          <Button icon={<OaIcon name="reload" />} onClick={() => void load(page)}>刷新</Button>
          <Button type="primary" disabled={unread === 0} onClick={() => void handleReadAll()}>
            全部已读
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
              emptyText: <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
            }}
          />
        </Spin>
      </Card>
    </section>
  );
}

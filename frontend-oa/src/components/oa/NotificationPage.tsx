'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Modal, Space, Spin, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import {
  fetchUnreadCount,
  getNotification,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '@/lib/notificationApi';
import ResponsiveTable from './ResponsiveTable';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { usePublishPageAgentContext } from './PageAgentContext';
import { usePermission } from '@/hooks/usePermission';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination } from '@/hooks/usePageUiEditorCommands';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { genericApprovalTaskApi } from '@/lib/genericApprovalTaskApi';
import { useRouter } from '@/lib/nextCompat';
import { formatOaApiError } from '@/lib/oaApi';

const TYPE_COLOR: Record<string, string> = {
  approval: 'blue',
  system: 'default',
  alert: 'error',
  todo: 'orange',
};

export default function NotificationPage() {
  const { t } = useTranslation();
  const { allowed, hasPermission } = usePermission('notification:read:self');
  const router = useRouter();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  usePublishPageAgentContext({ page, size });

  const query = useCallback(async () => {
    if (!allowed) return null;
    const [result, unread] = await Promise.all([listNotifications(page, size), fetchUnreadCount()]);
    return { ...result, unread };
  }, [allowed, page, size]);
  const { data, loading, error, load, scope, isCurrent } = useScopedOverview(query);
  const { capture } = useAsyncActionGuard(scope);
  const write = useAsyncFormAction(scope);
  const detailRequest = useRef(0);
  const [detail, setDetail] = useState<{ scope: string; item: NotificationItem }>();
  const visibleDetail = allowed && detail?.scope === scope ? detail.item : undefined;
  const canOpenGenericTask = visibleDetail?.bizType === 'generic-approval-task' && Number.isSafeInteger(visibleDetail.bizId)
    && Number(visibleDetail.bizId) > 0 && hasPermission('todo:read') && hasPermission('route:todo');
  const openGenericTask = async () => {
    if (!allowed || !canOpenGenericTask || !visibleDetail || !isCurrent()) return;
    const action = write.begin(); if (!action) return;
    const ticket = detailRequest.current;
    try {
      const id = visibleDetail.bizId!;
      const response = await genericApprovalTaskApi.detail(id);
      if (!action.current() || !isCurrent() || ticket !== detailRequest.current) return;
      if (response.task.id !== id) throw new Error('PAGE_UI_FIELD_REJECTED');
      router.push(`/oa/todo?kind=generic&taskId=${id}`);
    } catch (error) { if (action.current() && isCurrent() && ticket === detailRequest.current) message.error(formatOaApiError(error)); }
    finally { action.finish(); }
  };
  useEffect(() => { setDetail(undefined); }, [scope, allowed]);
  const items = allowed ? data?.records ?? [] : [];
  const total = allowed ? data?.total ?? 0 : 0;
  const unread = allowed ? data?.unread ?? 0 : 0;
  const openDetail = async (recordId: string | number) => {
    const current = capture();
    if (!allowed || !current() || !isCurrent() || typeof recordId !== 'number' || !Number.isSafeInteger(recordId) || recordId < 1)
      throw new Error('PAGE_UI_FIELD_REJECTED');
    const ticket = ++detailRequest.current;
    setDetail(undefined);
    const item = await getNotification(recordId);
    if (!current() || !isCurrent() || ticket !== detailRequest.current) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (item.id !== recordId) throw new Error('PAGE_UI_FIELD_REJECTED');
    setDetail({ scope, item });
  };

  useRegisterPageUiCommands({
    'ui.refreshPage': async () => { if (!allowed || !isCurrent() || !await load()) throw new Error('PAGE_UI_FIELD_REJECTED'); },
    'ui.applyFilter': ({ payload: { filters } }) => {
      if (!allowed || !isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { page: 'number', size: 'number' });
      requireUiPagination(filters);
      if (typeof filters.size === 'number') setSize(filters.size);
      setPage(Number(filters.page ?? 1));
    },
    'ui.openDetail': ({ payload: { recordId } }) => openDetail(recordId),
  });

  const handleRead = async (item: NotificationItem) => {
    if (!allowed || item.read) return;
    const action = write.begin(); if (!action || !isCurrent()) return;
    try {
      await markNotificationRead(item.id);
      if (action.current() && isCurrent()) await load();
    } catch (error) {
      if (action.current() && isCurrent()) message.error(error instanceof Error ? error.message : t('pages.notification.markReadFailed'));
    } finally { action.finish(); }
  };

  const handleReadAll = async () => {
    if (!allowed) return;
    const action = write.begin(); if (!action || !isCurrent()) return;
    try {
      await markAllNotificationsRead();
      if (!action.current() || !isCurrent()) return;
      message.success(t('pages.notification.markAllReadSuccess'));
      await load();
    } catch (error) {
      if (action.current() && isCurrent()) message.error(error instanceof Error ? error.message : t('pages.notification.operationFailed'));
    } finally { action.finish(); }
  };

  const columns: TableProps<NotificationItem>['columns'] = [
    {
      title: t('pages.notification.columnType'),
      dataIndex: 'type',
      width: 90,
      render: (type: string) => {
        const color = TYPE_COLOR[type.toLowerCase()] || 'default';
        const labelKey = `pages.notification.type.${type.toLowerCase()}`;
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
      width: 180,
      render: (_, item) =>
        <Space><Button size="small" onClick={() => { void openDetail(item.id).catch(failure => { if (isCurrent()) message.error(failure instanceof Error ? failure.message : t('common.loadingFailed')); }); }}>{t('common.view')}</Button>{item.read ? (
          <Typography.Text type="secondary">{t('pages.notification.read')}</Typography.Text>
        ) : (
          <Button size="small" disabled={write.busy || !allowed} onClick={() => void handleRead(item)}>{t('pages.notification.markRead')}</Button>
        )}</Space>,
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
          <Button icon={<OaIcon name="reload" />} disabled={!allowed} onClick={() => void load()}>{t('common.refresh')}</Button>
          {allowed && <Button type="primary" loading={write.busy} disabled={unread === 0 || write.busy} onClick={() => void handleReadAll()}>
            {t('pages.notification.markAllRead')}
          </Button>}
        </Space>
      </div>

      <Card className="oa-domain-card" size="small">
        {!allowed && <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />}
        {allowed && error !== undefined && <Alert type="error" showIcon title={t('common.loadingFailed')}
          action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
        <Spin spinning={loading && allowed}>
          <ResponsiveTable<NotificationItem>
            rowKey="id"
            columns={columns}
            dataSource={items}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              onChange: (next, nextSize) => { setPage(next); setSize(nextSize); },
            }}
            locale={{
              emptyText: <Empty description={t('pages.notification.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />,
            }}
          />
        </Spin>
      </Card>
      {visibleDetail && <Modal open title={visibleDetail.title} footer={null} destroyOnHidden onCancel={() => { detailRequest.current++; setDetail(undefined); }}>
        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{visibleDetail.content}</Typography.Paragraph>
        {canOpenGenericTask && <Button loading={write.busy} onClick={() => void openGenericTask()}>{t('approval.genericTasks.openRequest')}</Button>}
      </Modal>}
    </section>
  );
}

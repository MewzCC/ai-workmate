import { useCallback, useState } from 'react';
import { Alert, Button, Descriptions, Empty, Modal, Pagination, Space, Spin, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import { approvalEngineApi } from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { ApprovalAttachmentList } from './ApprovalAttachmentField';

/** Bounded own-fields projection, shared by application entry and list pages. */
export function ApplicationFieldsModal({ id, allowed, scope, revision = 0, processVersion, onClose }: {
  id?: number; allowed: boolean; scope: string; revision?: number; processVersion?: number | null; onClose: () => void;
}) {
  const { t } = useTranslation();
  const key = `${scope}:${id}:${revision}`, [selection, setSelection] = useState({ key, page: 1 });
  const page = selection.key === key ? selection.page : 1;
  const query = useCallback(async () => {
    if (!allowed || id === undefined) return null;
    if (!Number.isSafeInteger(id) || id < 1) throw new Error(t('approval.genericApplications.invalidResult'));
    const result = await approvalEngineApi.ownApplicationFields(id, page);
    if (result.application.applicationId !== id || result.fieldPage !== page || result.fields.length > 50)
      throw new Error(t('approval.genericApplications.invalidResult'));
    return { ...result, scope, revision };
  }, [allowed, id, page, revision, scope, t]);
  const detail = useScopedOverview(query), data = allowed ? detail.data : null;
  return <Modal open={allowed && id !== undefined} title={data?.application.formName || t('approval.start.historicalDetailTitle')}
    onCancel={onClose} destroyOnHidden footer={<Button onClick={onClose}>{t('common.close')}</Button>} width={720}>
    <Spin spinning={detail.loading}>
      {detail.error !== undefined ? <Alert type="error" showIcon title={formatOaApiError(detail.error)} action={<Button onClick={() => void detail.load()}>{t('common.retry')}</Button>} /> : data && <Space orientation="vertical" style={{ width: '100%' }}>
        <Alert type="info" title={t(data.schemaSource === 'CURRENT_DRAFT' ? 'approval.genericApplications.currentDraftNotice' : 'approval.start.snapshotNotice')} />
        <Descriptions size="small" bordered column={2} items={[
          { key: 'form', label: t('approval.start.snapshotFormVersion'), children: data.formVersion ?? '-' },
          { key: 'process', label: t('approval.start.snapshotProcessVersion'), children: processVersion ?? '-' },
          { key: 'status', label: t('approval.start.snapshotStatus'), children: <Tag>{t(`approval.status.${data.application.status}`)}</Tag> },
          { key: 'submitted', label: t('approval.start.snapshotSubmittedAt'), children: data.application.submittedAt || '-' },
        ]} />
        <Descriptions size="small" bordered column={1} items={data.fields.map(field => ({ key: field.name, label: field.label,
          children: field.redacted ? t('approval.genericApplications.redacted')
            : (field.type === 'file' || field.type === 'image') && id !== undefined
              ? <ApprovalAttachmentList applicationId={id} fieldName={field.name} ids={field.values} active={allowed}
                scope={key} /> : field.values.join(', ') || '-' }))} />
        {data.fields.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t(data.schemaSource === 'CURRENT_DRAFT' ? 'approval.start.emptyFields' : 'approval.start.snapshotUnavailable')} />}
        <Pagination current={page} pageSize={50} total={data.fieldsTotal} showSizeChanger={false} onChange={value => setSelection({ key, page: value })} />
      </Space>}
    </Spin>
  </Modal>;
}

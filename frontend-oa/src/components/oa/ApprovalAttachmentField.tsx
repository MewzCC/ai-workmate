import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Image, List, Space, Spin, Upload } from 'antd';
import { useTranslation } from 'react-i18next';
import { approvalAttachmentApi, type ApprovalAttachment } from '@/lib/approvalAttachmentApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { OaIcon } from '@/components/OaIcon';

export function ApprovalAttachmentList({ applicationId, fieldName, ids, taskId, active, scope, onRemove }: {
  applicationId: number; fieldName: string; ids: string[]; taskId?: number; active: boolean; scope: string;
  onRemove?: (id: string) => void;
}) {
  const { t } = useTranslation();
  const selection = JSON.stringify(ids);
  const query = useCallback(async () => {
    if (!active) return { scope, items: [] };
    const references = JSON.parse(selection) as string[];
    if (references.length > 20 || new Set(references).size !== references.length) throw new Error('PAGE_UI_FIELD_REJECTED');
    const results = await Promise.all(references.map(id => approvalAttachmentApi.metadata(applicationId, id, taskId)));
    if (results.some((result, index) => result.id !== references[index] || result.fieldName !== fieldName))
      throw new Error('PAGE_UI_FIELD_REJECTED');
    return { scope, items: results };
  }, [active, applicationId, fieldName, selection, taskId, scope]);
  const detail = useScopedOverview(query);
  const previewScope = `${scope}:${selection}:${active}:${detail.scope}`;
  const previewUrl = useRef<string | undefined>(undefined);
  const [preview, setPreview] = useState<{ scope: string; url: string }>();
  useEffect(() => () => {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = undefined;
  }, [previewScope]);
  const { capture } = useAsyncActionGuard(`${scope}:${selection}:${active}`);
  const download = async (attachment: ApprovalAttachment, showImage = false) => {
    const current = capture();
    try {
      const blob = await approvalAttachmentApi.download(applicationId, attachment, taskId);
      if (!active || !current() || !detail.isCurrent()) return;
      const url = URL.createObjectURL(blob);
      if (showImage) {
        if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
        previewUrl.current = url; setPreview({ scope: previewScope, url });
        return;
      }
      try {
        const link = document.createElement('a'); link.href = url; link.download = attachment.displayName; link.click();
      } finally { URL.revokeObjectURL(url); }
    } catch (error) { if (current()) message.error(formatOaApiError(error)); }
  };
  if (!active || ids.length === 0) return null;
  return <Spin spinning={detail.loading}>
    {detail.error !== undefined ? <Alert type="error" showIcon title={formatOaApiError(detail.error)}
      action={<Button onClick={() => void detail.load()}>{t('common.retry')}</Button>} /> : <List size="small"
      dataSource={detail.data?.items ?? []} renderItem={item => <List.Item actions={[
        <Button key="download" type="link" onClick={() => void download(item)}>{t('approval.attachments.download')}</Button>,
        ...(['image/png', 'image/jpeg', 'image/webp'].includes(item.mimeType)
          ? [<Button key="preview" type="link" onClick={() => void download(item, true)}>{t('approval.attachments.preview')}</Button>] : []),
        ...(onRemove ? [<Button key="remove" type="link" onClick={() => onRemove(item.id)}>{t('approval.attachments.remove')}</Button>] : []),
      ]}>{item.displayName}</List.Item>} />}
    {preview?.scope === previewScope && <Image src={preview.url} width={0} preview={{ open: true, onOpenChange: open => {
      if (!open) {
        if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
        previewUrl.current = undefined; setPreview(undefined);
      }
    } }} />}
  </Spin>;
}

export function ApprovalAttachmentField({ applicationId, fieldName, fieldType, value = [], onChange, active, scope,
  disabled, saveDraft, onBusy }: {
  applicationId?: number; fieldName: string; fieldType: 'file' | 'image'; value?: string[];
  onChange?: (ids: string[]) => void; active: boolean; scope: string; disabled: boolean;
  saveDraft: () => void; onBusy: (busy: boolean) => void;
}) {
  const { t } = useTranslation();
  const { capture } = useAsyncActionGuard(`${scope}:${active}`);
  const pending = useRef<AbortController | undefined>(undefined);
  const [uploading, setUploading] = useState(false);
  const busyCallback = useRef(onBusy); busyCallback.current = onBusy;
  useEffect(() => () => { pending.current?.abort(); }, [scope, active]);
  const upload = async (file: File) => {
    if (pending.current || !applicationId || !active || disabled || value.length >= 20) return;
    const current = capture();
    const controller = new AbortController(); pending.current = controller;
    const notifyBusy = busyCallback.current;
    setUploading(true); notifyBusy(true);
    try {
      const receipt = await approvalAttachmentApi.upload(applicationId, fieldName, file, controller.signal);
      if (!current() || controller.signal.aborted) return;
      if (receipt.fieldName !== fieldName || receipt.fieldType !== fieldType || !/^[1-9][0-9]{0,18}$/.test(receipt.id)
        || value.includes(receipt.id)) throw new Error(t('approval.attachments.invalidResult'));
      onChange?.([...value, receipt.id]);
    } catch (error) { if (current() && !controller.signal.aborted) message.error(formatOaApiError(error)); }
    finally {
      if (pending.current === controller) { pending.current = undefined; setUploading(false); }
      notifyBusy(false);
    }
  };
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {applicationId === undefined ? <Button onClick={saveDraft} disabled={!active || disabled}>{t('approval.attachments.saveFirst')}</Button>
      : <Upload accept={fieldType === 'image' ? 'image/jpeg,image/png,image/webp' : undefined} showUploadList={false}
        disabled={!active || disabled || uploading || value.length >= 20}
        beforeUpload={file => { void upload(file); return Upload.LIST_IGNORE; }}>
        <Button loading={uploading} icon={<OaIcon name="upload" />} disabled={!active || disabled || value.length >= 20}>{t('approval.attachments.upload')}</Button>
      </Upload>}
    {uploading && <Alert type="info" title={t('approval.attachments.uploading')} />}
    {value.length >= 20 && <Alert type="info" title={t('approval.attachments.limit')} />}
    {applicationId !== undefined && <ApprovalAttachmentList applicationId={applicationId} fieldName={fieldName}
      ids={value} active={active} scope={scope}
      onRemove={!disabled && !uploading ? id => onChange?.(value.filter(reference => reference !== id)) : undefined} />}
  </Space>;
}

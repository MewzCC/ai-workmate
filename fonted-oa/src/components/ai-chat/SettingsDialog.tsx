'use client';

import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography } from 'antd';
import { AI_MODEL_OPTIONS } from '@/config/aiModels';
import type { ChatSettings } from '@/types/chat';

interface SettingsDialogProps {
  open: boolean;
  settings: ChatSettings;
  onClose: () => void;
  onSave: (settings: ChatSettings) => void;
  onClearAll: () => Promise<void>;
}

export default function SettingsDialog({ open, settings, onClose, onSave, onClearAll }: SettingsDialogProps) {
  const { t } = useTranslation();
  const [form] = Form.useForm<ChatSettings>();
  useEffect(() => { if (open) form.setFieldsValue(settings); }, [form, open, settings]);

  return (
    <Modal title={t('chat.settingsTitle')} open={open} onCancel={onClose} onOk={() => form.submit()} okText={t('chat.saveSettings')}>
      <Form form={form} layout="vertical" onFinish={(values) => { onSave({ ...settings, ...values }); onClose(); }}>
        <Form.Item label="API Key">
          <Input.Password value={t('chat.apiKeyManaged')} disabled />
          <Typography.Text type="secondary">{t('chat.apiKeyHint')}</Typography.Text>
        </Form.Item>
        <Form.Item name="model" label={t('chat.model')} rules={[{ required: true, message: t('chat.selectModelRequired') }]}>
          <Select options={[...AI_MODEL_OPTIONS]} />
        </Form.Item>
        <Form.Item name="maxContextRounds" label={t('chat.maxContextRounds')} rules={[{ required: true }]}>
          <InputNumber min={1} max={20} className="ai-settings-number" />
        </Form.Item>
        <Form.Item name="stream" label={t('chat.streamOutput')} valuePropName="checked">
          <Switch />
        </Form.Item>
        <Alert type="info" showIcon title={t('chat.baseUrlHint')} />
      </Form>
      <div className="ai-settings-danger">
        <Space orientation="vertical">
          <Typography.Text strong>{t('chat.dataManagement')}</Typography.Text>
          <Button danger onClick={() => Modal.confirm({
            title: t('chat.clearAllTitle'),
            content: t('chat.clearAllContent'),
            okText: t('chat.confirmClear'), okButtonProps: { danger: true }, cancelText: t('common.cancel'),
            onOk: onClearAll,
          })}>{t('chat.clearAllRecords')}</Button>
        </Space>
      </div>
    </Modal>
  );
}

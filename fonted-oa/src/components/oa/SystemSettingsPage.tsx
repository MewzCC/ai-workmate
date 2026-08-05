'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { AI_MODEL_OPTIONS } from '@/config/aiModels';
import type { ChatSettings } from '@/types/chat';
import { useAiChatStore } from '@/store/aiChatStore';
import { getOcrSettings, updateOcrSettings } from '@/lib/userSettingsApi';

export default function SystemSettingsPage() {
  const { t } = useTranslation();
  const [form] = Form.useForm<ChatSettings>();
  const settings = useAiChatStore((state) => state.settings);
  const updateSettings = useAiChatStore((state) => state.updateSettings);
  const clearAll = useAiChatStore((state) => state.clearAll);
  const [forcePdfOcr, setForcePdfOcr] = useState(false);
  const [ocrSettingsLoading, setOcrSettingsLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    form.setFieldsValue(settings);
  }, [form, settings]);

  useEffect(() => {
    let cancelled = false;
    setOcrSettingsLoading(true);
    getOcrSettings()
      .then((result) => {
        if (!cancelled) setForcePdfOcr(result.forcePdfOcr);
      })
      .catch(() => {
        if (!cancelled) message.error(t('errors.chat.ocrSettingsLoadFailed'));
      })
      .finally(() => {
        if (!cancelled) setOcrSettingsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [t]);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      updateSettings({ ...settings, ...values });
      await updateOcrSettings(forcePdfOcr);
      message.success(t('chat.settingsSaved'));
    } catch {
      message.error(t('errors.chat.ocrSettingsSaveFailed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="oa-settings-page">
      <Typography.Title level={4}>{t('oa.menu.system-config')}</Typography.Title>
      <Card size="small" title={t('chat.settingsTitle')} className="oa-domain-card" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical">
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
      </Card>
      <Card size="small" title={t('chat.ocrSettings')} className="oa-domain-card" style={{ marginBottom: 16 }}>
        <Form layout="vertical">
          <Form.Item label={t('chat.forcePdfOcr')} tooltip={t('chat.forcePdfOcrHint')}>
            <Switch checked={forcePdfOcr} loading={ocrSettingsLoading} onChange={setForcePdfOcr} />
          </Form.Item>
        </Form>
      </Card>
      <Card size="small" title={t('chat.dataManagement')} className="oa-domain-card">
        <Space direction="vertical">
          <Typography.Text>{t('chat.clearAllContent')}</Typography.Text>
          <Button
            danger
            onClick={() => {
              Modal.confirm({
                title: t('chat.clearAllTitle'),
                content: t('chat.clearAllContent'),
                okText: t('chat.confirmClear'),
                okButtonProps: { danger: true },
                cancelText: t('common.cancel'),
                onOk: clearAll,
              });
            }}
          >
            {t('chat.clearAllRecords')}
          </Button>
        </Space>
      </Card>
      <Space style={{ marginTop: 16 }}>
        <Button type="primary" loading={saving} onClick={() => void save()}>
          {t('chat.saveSettings')}
        </Button>
        <Button onClick={() => form.resetFields()}>{t('common.reset')}</Button>
      </Space>
    </div>
  );
}

'use client';

import { useEffect, useRef, useState } from 'react';
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
import { getChatPreferences, updateChatPreferences, type ChatPreferences } from '@/lib/userSettingsApi';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { usePageUiEditorCommands } from '@/hooks/usePageUiEditorCommands';
import { usePageUiFormContext } from '@/hooks/usePageUiFormContext';

interface SettingsFormValues extends Omit<ChatSettings, 'kbId'> { forcePdfOcr: boolean }

export default function SystemSettingsPage() {
  const { t } = useTranslation();
  const [form] = Form.useForm<SettingsFormValues>();
  const clearAll = useAiChatStore((state) => state.clearAll);
  const { data: preferences, setData: setPreferences, loading, load, scope } = useScopedOverview<ChatPreferences>(getChatPreferences);
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    activeScope.current = scope;
    return () => { activeScope.current = undefined; };
  }, [scope]);
  useEffect(() => {
    form.resetFields();
    if (preferences) form.setFieldsValue(preferences);
  }, [form, preferences]);
  const uiForm = usePageUiFormContext(Boolean(preferences) && !loading && !saving, undefined, undefined, `${scope}:${JSON.stringify(preferences)}`);
  usePageUiEditorCommands({ form, open: Boolean(preferences) && !loading && !saving, canEdit: Boolean(preferences), context: uiForm.context,
    fields: { model: AI_MODEL_OPTIONS.map(option => option.value), maxContextRounds: 'number', stream: 'boolean', forcePdfOcr: 'boolean' },
    labels: { model: t('chat.model'), maxContextRounds: t('chat.maxContextRounds'), stream: t('chat.streamOutput'), forcePdfOcr: t('chat.forcePdfOcr') },
    commands: { 'ui.refreshPage': async () => { await load(); } },
  });

  const save = async () => {
    if (!preferences || loading || saving) return;
    let values: SettingsFormValues;
    try { values = await form.validateFields(); } catch { return; }
    if (activeScope.current !== scope) return;
    setSaving(true);
    try {
      const updated = await updateChatPreferences({ model: values.model, maxContextRounds: values.maxContextRounds,
        stream: values.stream, forcePdfOcr: values.forcePdfOcr });
      if (activeScope.current !== scope) return;
      setPreferences(updated);
      useAiChatStore.setState(state => ({ settings: { ...state.settings, model: updated.model, maxContextRounds: updated.maxContextRounds, stream: updated.stream } }));
      message.success(t('chat.settingsSaved'));
    } catch {
      if (activeScope.current === scope) message.error(t('errors.chat.ocrSettingsSaveFailed'));
    } finally {
      if (activeScope.current === scope) setSaving(false);
    }
  };

  return (
    <section className="oa-settings-page">
      <header className="oa-settings-heading">
        <Typography.Title level={4}>{t('oa.menu.system-config')}</Typography.Title>
      </header>
      <Form form={form} layout="vertical" disabled={!preferences || loading || saving} onValuesChange={uiForm.renew}>
      <div className="oa-settings-grid">
        <Card size="small" title={t('chat.settingsTitle')} className="oa-domain-card oa-settings-card oa-settings-card--primary">
            <Form.Item label={t('chat.apiKeyLabel')}>
              <Input.Password value={t('chat.apiKeyManaged')} disabled />
              <Typography.Text type="secondary">{t('chat.apiKeyHint')}</Typography.Text>
            </Form.Item>
            <Form.Item name="model" label={t('chat.model')} rules={[{ required: true, message: t('chat.selectModelRequired') }]}>
              <Select options={[...AI_MODEL_OPTIONS]} />
            </Form.Item>
            <Form.Item name="maxContextRounds" label={t('chat.maxContextRounds')} rules={[{ required: true, type: 'integer', min: 1, max: 20 }]}>
              <InputNumber min={1} max={20} precision={0} className="ai-settings-number" />
            </Form.Item>
            <Form.Item name="stream" label={t('chat.streamOutput')} valuePropName="checked">
              <Switch />
            </Form.Item>
            <Alert type="info" showIcon title={t('chat.baseUrlHint')} />
        </Card>
        <Card size="small" title={t('chat.ocrSettings')} className="oa-domain-card oa-settings-card">
            <Form.Item name="forcePdfOcr" valuePropName="checked" label={t('chat.forcePdfOcr')} tooltip={t('chat.forcePdfOcrHint')}>
              <Switch loading={loading} />
            </Form.Item>
        </Card>
        <Card size="small" title={t('chat.dataManagement')} className="oa-domain-card oa-settings-card">
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
      </div>
      <Space className="oa-settings-actions">
        <Button type="primary" loading={saving} onClick={() => void save()}>
          {t('chat.saveSettings')}
        </Button>
        <Button onClick={() => { if (preferences) { uiForm.renew(); form.setFieldsValue(preferences); } }}>{t('common.reset')}</Button>
      </Space>
      </Form>
    </section>
  );
}

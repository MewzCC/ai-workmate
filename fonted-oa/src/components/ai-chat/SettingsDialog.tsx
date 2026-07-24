'use client';

import { useEffect } from 'react';
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
  const [form] = Form.useForm<ChatSettings>();
  useEffect(() => { if (open) form.setFieldsValue(settings); }, [form, open, settings]);

  return (
    <Modal title="AI Workspace 设置" open={open} onCancel={onClose} onOk={() => form.submit()} okText="保存设置">
      <Form form={form} layout="vertical" onFinish={(values) => { onSave(values); onClose(); }}>
        <Form.Item label="API Key">
          <Input.Password value="由服务端环境变量管理" disabled />
          <Typography.Text type="secondary">密钥不会下发到浏览器，请通过后端 `AI_API_KEY` 配置。</Typography.Text>
        </Form.Item>
        <Form.Item name="model" label="对话模型" rules={[{ required: true, message: '请选择对话模型' }]}>
          <Select options={[...AI_MODEL_OPTIONS]} />
        </Form.Item>
        <Form.Item name="maxContextRounds" label="最大上下文轮数" rules={[{ required: true }]}>
          <InputNumber min={1} max={20} className="ai-settings-number" />
        </Form.Item>
        <Form.Item name="stream" label="流式输出" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Alert type="info" showIcon message="接口地址由服务端 AI_BASE_URL 管理，避免凭据和内部网关信息暴露。" />
      </Form>
      <div className="ai-settings-danger">
        <Space direction="vertical">
          <Typography.Text strong>数据管理</Typography.Text>
          <Button danger onClick={() => Modal.confirm({
            title: '清空全部聊天记录？',
            content: '该操作会删除当前账号的全部会话、消息和附件，且无法恢复。',
            okText: '确认清空', okButtonProps: { danger: true }, cancelText: '取消',
            onOk: onClearAll,
          })}>清空全部聊天记录</Button>
        </Space>
      </div>
    </Modal>
  );
}

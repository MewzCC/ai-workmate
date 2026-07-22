'use client';

import { useEffect, useState } from 'react';
import { Alert, Button, Form, Image, Input, Space, Typography, message } from 'antd';
import { LockOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons';
import { getCaptcha, login } from '@/lib/api';

interface WorkspaceAuthGateProps {
  onAuthenticated: () => void;
}

export default function WorkspaceAuthGate({ onAuthenticated }: WorkspaceAuthGateProps) {
  const [captchaId, setCaptchaId] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [loading, setLoading] = useState(false);

  const refreshCaptcha = async () => {
    try {
      const result = await getCaptcha();
      setCaptchaId(result.captchaId);
      setCaptchaImage(result.captchaImage);
    } catch {
      message.error('验证码加载失败，请检查后端服务');
    }
  };

  useEffect(() => { refreshCaptcha(); }, []);

  return (
    <div className="ai-auth-gate">
      <div className="ai-auth-panel">
        <div className="ai-empty-mark">AI</div>
        <Typography.Title level={3}>登录后进入 AI Workspace</Typography.Title>
        <Typography.Paragraph type="secondary">会话、附件和 OA 操作权限都以服务端 JWT 身份为准。</Typography.Paragraph>
        <Alert type="warning" showIcon message="前端角色切换不构成授权，AI 无法绕过当前账号权限。" />
        <Form layout="vertical" onFinish={async (values) => {
          setLoading(true);
          try {
            await login(values.username, values.password, captchaId, values.captchaCode);
            message.success('登录成功');
            onAuthenticated();
          } catch (error) {
            message.error(error instanceof Error ? error.message : '登录失败');
            refreshCaptcha();
          } finally {
            setLoading(false);
          }
        }}>
          <Form.Item name="username" label="账号" rules={[{ required: true }]}><Input prefix={<UserOutlined />} /></Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true }]}><Input.Password prefix={<LockOutlined />} /></Form.Item>
          <Form.Item name="captchaCode" label="验证码" rules={[{ required: true }]}>
            <Space.Compact block>
              <Input maxLength={6} />
              {captchaImage ? <Image className="ai-captcha" preview={false} src={captchaImage} alt="图形验证码" onClick={refreshCaptcha} /> : <Button icon={<ReloadOutlined />} onClick={refreshCaptcha} />}
            </Space.Compact>
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>安全登录</Button>
        </Form>
      </div>
    </div>
  );
}

'use client';

import { useState } from 'react';
import { EyeInvisibleOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Image, Input } from 'antd';
import type { InputProps } from 'antd';

export function FormInput(props: InputProps) {
  return <Input size="large" {...props} />;
}

export function PasswordInput(props: InputProps) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="auth-password-field">
      <Input size="large" type={visible ? 'text' : 'password'} autoComplete="new-password" {...props} />
      <Button
        type="text"
        className="auth-password-visibility"
        icon={visible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
        aria-label={visible ? '隐藏密码' : '查看密码'}
        title={visible ? '隐藏密码' : '查看密码'}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => setVisible((current) => !current)}
      />
    </div>
  );
}

export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/;
export const PASSWORD_MESSAGE = '密码须为 8-32 位，并至少包含字母和数字';
export const EMAIL_CODE_PATTERN = /^\d{6}$/;
export const EMAIL_CODE_MESSAGE = '请输入 6 位数字邮箱验证码';

interface CaptchaInputProps {
  image?: string;
  loading: boolean;
  value?: string;
  onChange?: (value: string) => void;
  onRefresh: () => void;
}

export function CaptchaInput({ image, loading, value, onChange, onRefresh }: CaptchaInputProps) {
  return (
    <div className="auth-captcha-row">
      <Input
        size="large"
        placeholder="请输入图形验证码"
        maxLength={8}
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
      />
      <Button className="auth-captcha-image" onClick={onRefresh} loading={loading} aria-label="刷新图形验证码">
        {image ? <Image src={image} alt="图形验证码" preview={false} /> : <><ReloadOutlined /><span>重新加载</span></>}
      </Button>
    </div>
  );
}

interface EmailCodeInputProps {
  value?: string;
  cooldown: number;
  loading: boolean;
  onChange?: (value: string) => void;
  onSend: () => void;
}

export function EmailCodeInput({ value, cooldown, loading, onChange, onSend }: EmailCodeInputProps) {
  return (
    <div className="auth-code-row">
      <Input
        size="large"
        placeholder="请输入 6 位邮箱验证码"
        maxLength={6}
        inputMode="numeric"
        value={value}
        onChange={(event) => onChange?.(event.target.value.replace(/\D/g, '').slice(0, 6))}
      />
      <Button onClick={onSend} loading={loading} disabled={cooldown > 0}>
        {cooldown > 0 ? `重新发送 ${cooldown}s` : '发送验证码'}
      </Button>
    </div>
  );
}

export function PasswordStrength({ value = '' }: { value?: string }) {
  const medium = value.length >= 8 && /[A-Za-z]/.test(value) && /\d/.test(value);
  const strong = medium && /[a-z]/.test(value) && /[A-Z]/.test(value) && /[^A-Za-z\d\s]/.test(value);
  const score = !value ? 0 : strong ? 3 : medium ? 2 : 1;
  return (
    <div className="auth-password-strength" aria-label={`密码强度 ${score} 级`}>
      {[1, 2, 3].map((level) => <span key={level} className={score >= level ? `active level-${Math.min(score, 3)}` : ''} />)}
    </div>
  );
}

export function AuthNotice({ children, type = 'error' }: { children?: React.ReactNode; type?: 'error' | 'success' }) {
  if (!children) return null;
  return <div className={`auth-notice auth-notice-${type}`} role="alert">{children}</div>;
}

export function GlassTabs({ active, onChange }: { active: 'login' | 'register'; onChange: (value: 'login' | 'register') => void }) {
  return (
    <div className={`auth-glass-tabs ${active === 'register' ? 'is-register' : ''}`} role="tablist" aria-label="账户入口">
      <span className="auth-tab-slider" aria-hidden="true" />
      <Button type="text" role="tab" aria-selected={active === 'login'} onClick={() => onChange('login')}>登录</Button>
      <Button type="text" role="tab" aria-selected={active === 'register'} onClick={() => onChange('register')}>邮箱注册</Button>
    </div>
  );
}

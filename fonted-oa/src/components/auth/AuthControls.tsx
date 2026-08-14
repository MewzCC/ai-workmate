'use client';

import { useState } from 'react';
import { EyeInvisibleOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Image, Input } from 'antd';
import type { InputProps } from 'antd';
import { useTranslation } from 'react-i18next';

export function FormInput(props: InputProps) {
  return <Input size="large" {...props} />;
}

export function PasswordInput(props: InputProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);

  return (
    <div className="auth-password-field">
      <Input size="large" type={visible ? 'text' : 'password'} autoComplete="new-password" {...props} />
      <Button
        type="text"
        className="auth-password-visibility"
        icon={visible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
        aria-label={visible ? t('auth.aria.hidePassword') : t('auth.aria.viewPassword')}
        title={visible ? t('auth.aria.hidePassword') : t('auth.aria.viewPassword')}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => setVisible((current) => !current)}
      />
    </div>
  );
}

export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/;
export const EMAIL_CODE_PATTERN = /^\d{6}$/;

interface CaptchaInputProps {
  image?: string;
  loading: boolean;
  value?: string;
  onChange?: (value: string) => void;
  onRefresh: () => void;
}

export function CaptchaInput({ image, loading, value, onChange, onRefresh }: CaptchaInputProps) {
  const { t } = useTranslation();
  return (
    <div className="auth-captcha-row">
      <Input
        size="large"
        placeholder={t('auth.field.captchaInputPlaceholder')}
        maxLength={8}
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
      />
      <Button className="auth-captcha-image" onClick={onRefresh} loading={loading} aria-label={t('auth.aria.refreshCaptcha')}>
        {image ? <Image src={image} alt={t('auth.captchaModal.imageAlt')} preview={false} /> : <><ReloadOutlined /><span>{t('auth.captchaModal.reload')}</span></>}
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
  const { t } = useTranslation();
  return (
    <div className="auth-code-row">
      <Input
        size="large"
        placeholder={t('auth.field.emailCodePlaceholder')}
        maxLength={6}
        inputMode="numeric"
        value={value}
        onChange={(event) => onChange?.(event.target.value.replace(/\D/g, '').slice(0, 6))}
      />
      <Button onClick={onSend} loading={loading} disabled={cooldown > 0}>
        {cooldown > 0 ? t('auth.codeInput.resend', { seconds: cooldown }) : t('auth.codeInput.send')}
      </Button>
    </div>
  );
}

export function PasswordStrength({ value = '' }: { value?: string }) {
  const { t } = useTranslation();
  const medium = value.length >= 8 && /[A-Za-z]/.test(value) && /\d/.test(value);
  const strong = medium && /[a-z]/.test(value) && /[A-Z]/.test(value) && /[^A-Za-z\d\s]/.test(value);
  const score = !value ? 0 : strong ? 3 : medium ? 2 : 1;
  return (
    <div className="auth-password-strength" aria-label={t('auth.aria.passwordStrength', { score })}>
      {[1, 2, 3].map((level) => <span key={level} className={score >= level ? `active level-${Math.min(score, 3)}` : ''} />)}
    </div>
  );
}

export function AuthNotice({ children, type = 'error' }: { children?: React.ReactNode; type?: 'error' | 'success' }) {
  if (!children) return null;
  return <div className={`auth-notice auth-notice-${type}`} role="alert">{children}</div>;
}

export function GlassTabs({ active, onChange }: { active: 'login' | 'register'; onChange: (value: 'login' | 'register') => void }) {
  const { t } = useTranslation();
  return (
    <div className={`auth-glass-tabs ${active === 'register' ? 'is-register' : ''}`} role="tablist" aria-label={t('auth.aria.accountEntry')}>
      <span className="auth-tab-slider" aria-hidden="true" />
      <Button type="text" role="tab" aria-selected={active === 'login'} onClick={() => onChange('login')}>{t('auth.tabs.login')}</Button>
      <Button type="text" role="tab" aria-selected={active === 'register'} onClick={() => onChange('register')}>{t('auth.tabs.register')}</Button>
    </div>
  );
}

export function LoginModeTabs({ active, onChange }: { active: 'password' | 'code'; onChange: (value: 'password' | 'code') => void }) {
  const { t } = useTranslation();
  return (
    <div className={`auth-login-tabs ${active === 'code' ? 'is-code' : ''}`} role="tablist" aria-label={t('auth.aria.loginMethod')}>
      <span className="auth-login-indicator" aria-hidden="true" />
      <Button type="text" role="tab" aria-selected={active === 'password'} onClick={() => onChange('password')}>{t('auth.loginMode.password')}</Button>
      <Button type="text" role="tab" aria-selected={active === 'code'} onClick={() => onChange('code')}>{t('auth.loginMode.code')}</Button>
    </div>
  );
}

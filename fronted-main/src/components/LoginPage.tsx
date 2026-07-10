'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Sparkles, RefreshCw, ShieldCheck, ChevronLeft } from 'lucide-react';
import { login, register, getCaptcha, sendEmailCode, resetPassword } from '@/lib/api';

type SiteTheme = 'day' | 'night';

interface Props {
  onLoginSuccess: () => void;
  theme?: SiteTheme;
}

type Mode = 'login' | 'register' | 'reset';

export default function LoginPage({ onLoginSuccess, theme = 'day' }: Props) {
  const [mode, setMode] = useState<Mode>('login');

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [captchaId, setCaptchaId] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [captchaCode, setCaptchaCode] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refreshCaptcha = useCallback(async () => {
    setCaptchaCode('');
    try {
      const data = await getCaptcha();
      setCaptchaId(data.captchaId);
      setCaptchaImage(data.captchaImage);
    } catch {
      // 静默
    }
  }, []);

  useEffect(() => { refreshCaptcha(); }, [refreshCaptcha]);

  useEffect(() => {
    if (countdown > 0) {
      timerRef.current = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) { if (timerRef.current) clearInterval(timerRef.current); return 0; }
          return c - 1;
        });
      }, 1000);
      return () => { if (timerRef.current) clearInterval(timerRef.current); };
    }
  }, [countdown]);

  const switchMode = (m: Mode) => {
    setMode(m);
    setError('');
    setUsername(''); setPassword(''); setEmail(''); setCaptchaCode('');
    setEmailCode(''); setNewPassword('');
    refreshCaptcha();
  };

  const handleSendCode = async () => {
    if (!email) { setError('请先输入邮箱'); return; }
    if (!captchaCode) { setError('请先输入图形验证码'); return; }
    setError('');
    setSendingCode(true);
    try {
      await sendEmailCode(captchaId, captchaCode, email);
      setCountdown(60);
      await refreshCaptcha();
    } catch (err: any) {
      setError(err.message || '验证码发送失败');
      await refreshCaptcha();
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (mode === 'login') {
        await login(username, password, captchaId, captchaCode);
        onLoginSuccess();
      } else if (mode === 'register') {
        await register(username, password, email, captchaId, captchaCode, emailCode);
        onLoginSuccess();
      } else {
        // reset
        await resetPassword(email, emailCode, newPassword);
        setError('');
        switchMode('login');
      }
    } catch (err: any) {
      setError(err.message || '操作失败');
      await refreshCaptcha();
    } finally {
      setLoading(false);
    }
  };

  const isLogin = mode === 'login';
  const isReset = mode === 'reset';
  const submitLabel = loading
    ? '处理中...'
    : isLogin ? '登录' : mode === 'register' ? '注册' : '重置密码';

  return (
    <div className={`wm-site ${theme === 'night' ? 'wm-night' : 'wm-day'} min-h-screen flex items-center justify-center relative overflow-hidden`}>
      <div className="wm-bg" aria-hidden="true">
        <span className="wm-mesh wm-mesh-a" />
        <span className="wm-mesh wm-mesh-b" />
        <span className="wm-mesh wm-mesh-c" />
        <span className="wm-grid" />
        <span className="wm-lightline wm-lightline-a" />
        <span className="wm-lightline wm-lightline-b" />
      </div>

      <div className="wm-login-card">
        <div className="wm-login-logo">
          <span className="wm-mark wm-login-mark"><Sparkles className="h-6 w-6" /></span>
          <h1>AI WorkMate</h1>
          <p>{isReset ? '找回密码' : '企业级 AI 助手平台'}</p>
        </div>

        <form onSubmit={handleSubmit} className="wm-login-form">
          {/* 用户名 — 登录/注册 */}
          {!isReset && (
            <div className="wm-login-field">
              <label>用户名</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
                autoComplete="username"
                required
              />
            </div>
          )}

          {/* 邮箱 — 注册/重置 */}
          {!isLogin && (
            <div className="wm-login-field">
              <label>邮箱</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={isReset ? '请输入注册时的邮箱' : '请输入邮箱'}
                autoComplete="email"
                required
              />
            </div>
          )}

          {/* 图形验证码 — 全部模式 */}
          <div className="wm-login-field">
            <label>图形验证码</label>
            <div className="wm-captcha-row">
              <input
                type="text"
                value={captchaCode}
                onChange={(e) => setCaptchaCode(e.target.value)}
                placeholder="请输入图中字符"
                maxLength={6}
                className="wm-captcha-input"
                required
              />
              {captchaImage ? (
                <img
                  src={captchaImage}
                  alt="验证码"
                  className="wm-captcha-img"
                  onClick={refreshCaptcha}
                  title="点击刷新"
                />
              ) : (
                <button type="button" onClick={refreshCaptcha} className="wm-captcha-placeholder">
                  <RefreshCw className="h-4 w-4" />
                </button>
              )}
            </div>
          </div>

          {/* 邮件验证码 — 注册/重置 */}
          {!isLogin && (
            <div className="wm-login-field">
              <label>邮箱验证码</label>
              <div className="wm-captcha-row">
                <input
                  type="text"
                  value={emailCode}
                  onChange={(e) => setEmailCode(e.target.value)}
                  placeholder="请输入邮箱验证码"
                  maxLength={6}
                  className="wm-captcha-input"
                  required
                />
                <button
                  type="button"
                  onClick={handleSendCode}
                  disabled={sendingCode || countdown > 0}
                  className="wm-send-code-btn"
                >
                  {sendingCode ? '发送中...' : countdown > 0 ? `${countdown}s` : '获取验证码'}
                </button>
              </div>
            </div>
          )}

          {/* 密码 — 登录/注册 */}
          {!isReset && (
            <div className="wm-login-field">
              <label>密码</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                required
              />
            </div>
          )}

          {/* 新密码 — 重置 */}
          {isReset && (
            <div className="wm-login-field">
              <label>新密码</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="请输入新密码（至少6位）"
                autoComplete="new-password"
                required
              />
            </div>
          )}

          {error && <div className="wm-login-error">{error}</div>}

          <button type="submit" disabled={loading} className="wm-try wm-login-submit">
            {submitLabel}
          </button>
        </form>

        {/* 切换链接 */}
        <div className="wm-login-links">
          {isReset ? (
            <button type="button" onClick={() => switchMode('login')} className="wm-login-toggle wm-login-back">
              <ChevronLeft className="h-3.5 w-3.5" />
              返回登录
            </button>
          ) : (
            <>
              <button type="button" onClick={() => switchMode(isLogin ? 'register' : 'login')} className="wm-login-toggle">
                {isLogin ? '没有账号？去注册' : '已有账号？去登录'}
              </button>
              {isLogin && (
                <button type="button" onClick={() => switchMode('reset')} className="wm-login-toggle wm-login-forgot">
                  忘记密码？
                </button>
              )}
            </>
          )}
        </div>

        <div className="wm-security-hint">
          <ShieldCheck className="h-3.5 w-3.5" />
          <span>{isLogin ? '登录注册均在安全模式下完成' : '注册/找回密码需邮箱验证'}</span>
        </div>
      </div>
    </div>
  );
}

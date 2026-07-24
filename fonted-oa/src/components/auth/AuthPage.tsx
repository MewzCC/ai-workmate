'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  BankOutlined,
  CheckCircleFilled,
  DingdingOutlined,
  SafetyCertificateOutlined,
  WechatOutlined,
} from '@ant-design/icons';
import { App, Button, Checkbox, ConfigProvider, Form, Input, Modal, Result } from 'antd';
import { authApi, AuthApiError, type CaptchaData, type CodeScene } from '@/lib/authApi';
import { useAuth } from './AuthProvider';
import {
  AuthNotice,
  CaptchaInput,
  EMAIL_CODE_MESSAGE,
  EMAIL_CODE_PATTERN,
  EmailCodeInput,
  FormInput,
  GlassTabs,
  PasswordInput,
  PasswordStrength,
  PASSWORD_MESSAGE,
  PASSWORD_PATTERN,
} from './AuthControls';

type MainMode = 'login' | 'register';
type LoginMode = 'password' | 'code';
type View = 'account' | 'forgot';

interface PasswordLoginValues { email: string; password: string; remember: boolean; captchaCode?: string; }
interface CodeLoginValues { email: string; emailCode: string; remember: boolean; }
interface RegisterValues { name: string; email: string; emailCode: string; password: string; confirmPassword: string; agreement: boolean; }
interface ResetValues { email: string; emailCode: string; newPassword: string; confirmPassword: string; }

function safeRedirect(value: string | null) {
  return value?.startsWith('/oa') ? value : '/oa';
}

export default function AuthPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { message } = App.useApp();
  const { user, loading: authLoading, setUser } = useAuth();
  const [mainMode, setMainMode] = useState<MainMode>('login');
  const [loginMode, setLoginMode] = useState<LoginMode>('password');
  const [view, setView] = useState<View>('account');
  const [captcha, setCaptcha] = useState<CaptchaData | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [captchaModalOpen, setCaptchaModalOpen] = useState(false);
  const [captchaCode, setCaptchaCode] = useState('');
  const [captchaError, setCaptchaError] = useState('');
  const [pendingCodeRequest, setPendingCodeRequest] = useState<{ scene: CodeScene; email: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState('');
  const [riskCaptcha, setRiskCaptcha] = useState(false);
  const [resetStep, setResetStep] = useState<0 | 1 | 2>(0);
  const [registerPassword, setRegisterPassword] = useState('');
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [passwordForm] = Form.useForm<PasswordLoginValues>();
  const [codeForm] = Form.useForm<CodeLoginValues>();
  const [registerForm] = Form.useForm<RegisterValues>();
  const [resetForm] = Form.useForm<ResetValues>();
  const redirect = useMemo(() => safeRedirect(searchParams.get('redirect')), [searchParams]);

  useEffect(() => {
    if (!authLoading && user) router.replace(redirect);
  }, [authLoading, redirect, router, user]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setInterval(() => setCooldown((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [cooldown]);

  const loadCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    try {
      setCaptcha(await authApi.captcha());
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '图形验证码加载失败';
      setCaptcha(null);
      setError(message);
      setCaptchaError(message);
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    if (riskCaptcha) void loadCaptcha();
  }, [loadCaptcha, riskCaptcha]);

  const completeLogin = (currentUser: Awaited<ReturnType<typeof authApi.passwordLogin>>) => {
    setUser(currentUser);
    message.success('登录成功');
    router.replace(redirect);
  };

  const handleError = (reason: unknown) => {
    const text = reason instanceof Error ? reason.message : '操作失败，请稍后重试';
    setError(text);
    if (reason instanceof AuthApiError && reason.errorCode === 'AUTH_CAPTCHA_REQUIRED') {
      setRiskCaptcha(true);
      void loadCaptcha();
    }
  };

  const beginSendCode = async (scene: CodeScene, form: typeof codeForm | typeof registerForm | typeof resetForm) => {
    try {
      const values = await form.validateFields(['email']);
      setPendingCodeRequest({ scene, email: values.email });
      setCaptchaCode('');
      setCaptchaError('');
      setCaptchaModalOpen(true);
      await loadCaptcha();
    } catch (reason) {
      if (reason instanceof Error) handleError(reason);
    }
  };

  const confirmSendCode = async () => {
    if (!pendingCodeRequest || !captcha) {
      setCaptchaError('图形验证码尚未加载，请点击重新加载');
      return;
    }
    if (!captchaCode.trim()) {
      setCaptchaError('请输入图形验证码');
      return;
    }
    try {
      setSendingCode(true);
      setCaptchaError('');
      await authApi.sendEmailCode({
        email: pendingCodeRequest.email,
        scene: pendingCodeRequest.scene,
        captchaId: captcha.captchaId,
        captchaCode: captchaCode.trim(),
      });
      setCooldown(59);
      message.success('验证码已发送，请检查邮箱');
      setCaptchaModalOpen(false);
      if (pendingCodeRequest.scene === 'reset_password') setResetStep(1);
    } catch (reason) {
      setCooldown(0);
      setCaptchaError(reason instanceof Error ? reason.message : '验证码发送失败');
      setCaptchaCode('');
      await loadCaptcha();
    } finally {
      setSendingCode(false);
    }
  };

  const submitPasswordLogin = async (values: PasswordLoginValues) => {
    setSubmitting(true); setError('');
    try {
      const currentUser = await authApi.passwordLogin({
        email: values.email,
        password: values.password,
        remember: values.remember,
        captchaId: riskCaptcha ? captcha?.captchaId : undefined,
        captchaCode: riskCaptcha ? values.captchaCode : undefined,
      });
      completeLogin(currentUser);
    } catch (reason) { handleError(reason); }
    finally { setSubmitting(false); }
  };

  const submitCodeLogin = async (values: CodeLoginValues) => {
    setSubmitting(true); setError('');
    try { completeLogin(await authApi.emailCodeLogin({ email: values.email, emailCode: values.emailCode, remember: values.remember })); }
    catch (reason) { handleError(reason); }
    finally { setSubmitting(false); }
  };

  const submitRegister = async (values: RegisterValues) => {
    setSubmitting(true); setError('');
    try {
      const currentUser = await authApi.register({
        name: values.name, email: values.email, emailCode: values.emailCode,
        password: values.password, agreement: values.agreement, requestId,
      });
      setUser(currentUser);
      message.success('企业账号创建成功');
      router.replace(redirect);
    } catch (reason) {
      setRequestId(crypto.randomUUID());
      handleError(reason);
    } finally { setSubmitting(false); }
  };

  const submitReset = async (values: ResetValues) => {
    setSubmitting(true); setError('');
    try {
      await authApi.resetPassword({ email: values.email, emailCode: values.emailCode, newPassword: values.newPassword });
      setResetStep(2);
    } catch (reason) { handleError(reason); }
    finally { setSubmitting(false); }
  };

  const switchMainMode = (mode: MainMode) => {
    setMainMode(mode); setView('account'); setError(''); setCooldown(0); setResetStep(0); setCaptchaModalOpen(false);
  };

  const heading = mainMode === 'register' ? ['创建企业账号', '使用工作邮箱注册并加入您的组织。']
    : view === 'forgot' ? ['找回密码', '验证企业邮箱后，设置新的登录密码。']
      : ['欢迎回来', '登录企业工作台，继续处理审批与协作任务。'];

  return (
    <ConfigProvider theme={{ token: { colorPrimary: '#315b9b', borderRadius: 12, fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif' } }}>
      <main className={`auth-page auth-mode-${mainMode}`}>
        <div className="auth-ambient auth-ambient-blue" /><div className="auth-ambient auth-ambient-green" /><div className="auth-ambient auth-ambient-gold" />
        <section className="auth-card" aria-labelledby="auth-title">
          <header className="auth-brand"><span>AI</span><div><strong>AI WorkMate</strong><small>ENTERPRISE WORKSPACE</small></div></header>
          <div className="auth-heading"><h1 id="auth-title">{heading[0]}</h1><p>{heading[1]}</p></div>
          <GlassTabs active={mainMode} onChange={switchMainMode} />

          {mainMode === 'login' && view === 'account' && <>
            <div className="auth-login-tabs">
              <Button type="text" className={loginMode === 'password' ? 'active' : ''} onClick={() => { setLoginMode('password'); setError(''); }}>密码登录</Button>
              <Button type="text" className={loginMode === 'code' ? 'active' : ''} onClick={() => { setLoginMode('code'); setError(''); }}>验证码登录</Button>
            </div>
            {loginMode === 'password' ? <Form form={passwordForm} layout="vertical" initialValues={{ remember: true }} onFinish={submitPasswordLogin} requiredMark={false}>
              <Form.Item label="企业邮箱" name="email" rules={[{ required: true, type: 'email', message: '请输入有效的企业邮箱' }]}><FormInput placeholder="name@company.com" autoComplete="email" /></Form.Item>
              <Form.Item label="登录密码" name="password" rules={[{ required: true, message: '请输入登录密码' }]}><PasswordInput placeholder="请输入登录密码" autoComplete="current-password" /></Form.Item>
              {riskCaptcha && <Form.Item label="安全验证" name="captchaCode" rules={[{ required: true, message: '请输入图形验证码' }]}><CaptchaInput image={captcha?.image} loading={captchaLoading} onRefresh={loadCaptcha} /></Form.Item>}
              <div className="auth-form-meta"><Form.Item name="remember" valuePropName="checked" noStyle><Checkbox>记住登录状态</Checkbox></Form.Item><Button type="link" onClick={() => { setView('forgot'); setResetStep(0); setError(''); }}>忘记密码？</Button></div>
              <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>登录工作台</Button>
            </Form> : <Form form={codeForm} layout="vertical" initialValues={{ remember: true }} onFinish={submitCodeLogin} requiredMark={false}>
              <Form.Item label="企业邮箱" name="email" rules={[{ required: true, type: 'email', message: '请输入有效的企业邮箱' }]}><FormInput placeholder="name@company.com" /></Form.Item>
              <Form.Item label="邮箱验证码" name="emailCode" rules={[{ required: true, message: EMAIL_CODE_MESSAGE }, { pattern: EMAIL_CODE_PATTERN, message: EMAIL_CODE_MESSAGE }]}><EmailCodeInput cooldown={cooldown} loading={sendingCode} onSend={() => void beginSendCode('login', codeForm)} /></Form.Item>
              <Form.Item name="remember" valuePropName="checked"><Checkbox>记住登录状态</Checkbox></Form.Item>
              <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>验证并登录</Button>
            </Form>}
            <div className="auth-divider"><span>其他登录方式</span></div>
            <div className="auth-social"><Button icon={<WechatOutlined />} onClick={() => message.info('企业微信登录尚未接入')}>企业微信</Button><Button icon={<DingdingOutlined />} onClick={() => message.info('钉钉登录尚未接入')}>钉钉</Button><Button icon={<BankOutlined />} onClick={() => message.info('企业 SSO 尚未接入')}>企业 SSO</Button></div>
            <p className="auth-switch-copy">还没有账号？<Button type="link" onClick={() => switchMainMode('register')}>使用邮箱注册</Button></p>
          </>}

          {mainMode === 'register' && <Form form={registerForm} layout="vertical" onFinish={submitRegister} requiredMark={false}>
            <Form.Item label="姓名" name="name" rules={[{ required: true, message: '请输入姓名' }]}><FormInput placeholder="请输入真实姓名" /></Form.Item>
            <Form.Item label="企业邮箱" name="email" rules={[{ required: true, type: 'email', message: '请输入有效的企业邮箱' }]}><FormInput placeholder="name@company.com" /></Form.Item>
            <Form.Item label="邮箱验证码" name="emailCode" rules={[{ required: true, message: EMAIL_CODE_MESSAGE }, { pattern: EMAIL_CODE_PATTERN, message: EMAIL_CODE_MESSAGE }]}><EmailCodeInput cooldown={cooldown} loading={sendingCode} onSend={() => void beginSendCode('register', registerForm)} /></Form.Item>
            <Form.Item label="设置密码" name="password" rules={[{ required: true, message: '请输入密码' }, { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE }]}><PasswordInput placeholder="8-32 位，至少包含字母和数字" onChange={(event) => setRegisterPassword(event.target.value)} /></Form.Item>
            <PasswordStrength value={registerPassword} />
            <Form.Item label="确认密码" name="confirmPassword" dependencies={['password']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}><PasswordInput placeholder="再次输入密码" /></Form.Item>
            <Form.Item name="agreement" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error('请先同意服务协议和隐私政策')) }]}><Checkbox>我已阅读并同意《服务协议》和《隐私政策》</Checkbox></Form.Item>
            <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>创建账号</Button>
            <p className="auth-switch-copy">已有账号？<Button type="link" onClick={() => switchMainMode('login')}>返回登录</Button></p>
          </Form>}

          {mainMode === 'login' && view === 'forgot' && <>
            {resetStep === 2 ? <Result status="success" icon={<CheckCircleFilled />} title="密码修改成功" subTitle="请使用新密码重新登录企业工作台。" extra={<Button type="primary" onClick={() => { setView('account'); setResetStep(0); }}>返回登录</Button>} /> : <Form form={resetForm} layout="vertical" onFinish={submitReset} requiredMark={false}>
              <Form.Item label="企业邮箱" name="email" rules={[{ required: true, type: 'email', message: '请输入有效的企业邮箱' }]}><FormInput placeholder="name@company.com" disabled={resetStep === 1} /></Form.Item>
              {resetStep === 0 && <Button block size="large" loading={sendingCode} onClick={() => void beginSendCode('reset_password', resetForm)}>发送邮箱验证码</Button>}
              {resetStep === 1 && <><Form.Item label="邮箱验证码" name="emailCode" rules={[{ required: true, message: EMAIL_CODE_MESSAGE }, { pattern: EMAIL_CODE_PATTERN, message: EMAIL_CODE_MESSAGE }]}><Input size="large" placeholder="请输入 6 位验证码" maxLength={6} inputMode="numeric" /></Form.Item><div className="auth-form-grid"><Form.Item label="新密码" name="newPassword" rules={[{ required: true, message: '请输入新密码' }, { pattern: PASSWORD_PATTERN, message: PASSWORD_MESSAGE }]}><PasswordInput placeholder="8-32 位，至少包含字母和数字" /></Form.Item><Form.Item label="确认新密码" name="confirmPassword" dependencies={['newPassword']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}><PasswordInput placeholder="再次输入" /></Form.Item></div><Button htmlType="submit" type="primary" block size="large" loading={submitting}>确认修改</Button></>}
              <AuthNotice>{error}</AuthNotice>
            </Form>}
            {resetStep !== 2 && <Button type="link" className="auth-back-login" onClick={() => { setView('account'); setResetStep(0); setError(''); }}>返回登录</Button>}
          </>}
          <footer className="auth-security"><SafetyCertificateOutlined /> 企业级加密与安全策略保护</footer>
        </section>
        <Modal
          className="auth-captcha-modal"
          width={456}
          title="完成安全验证"
          open={captchaModalOpen}
          okText="验证并发送"
          cancelText="取消"
          confirmLoading={sendingCode}
          okButtonProps={{ disabled: captchaLoading || !captcha }}
          onOk={() => void confirmSendCode()}
          onCancel={() => { setCaptchaModalOpen(false); setCaptchaError(''); }}
          destroyOnClose
        >
          <p className="auth-captcha-hint">发送邮箱验证码前，请先输入下图中的字符。</p>
          <CaptchaInput
            image={captcha?.image}
            loading={captchaLoading}
            value={captchaCode}
            onChange={setCaptchaCode}
            onRefresh={() => { setCaptchaCode(''); setCaptchaError(''); void loadCaptcha(); }}
          />
          <AuthNotice>{captchaError}</AuthNotice>
        </Modal>
      </main>
    </ConfigProvider>
  );
}

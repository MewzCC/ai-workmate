'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  BankOutlined,
  CheckCircleFilled,
  DingdingOutlined,
  SafetyCertificateOutlined,
  WechatOutlined,
} from '@ant-design/icons';
import { App, Button, Checkbox, ConfigProvider, Form, Input, Modal, Result } from 'antd';
import { useTranslation } from 'react-i18next';
import { authApi, AuthApiError, type CaptchaData, type CodeScene } from '@/lib/authApi';
import { uuid } from '@/lib/uuid';
import { useAuth } from './AuthProvider';
import {
  AuthNotice,
  CaptchaInput,
  EMAIL_CODE_PATTERN,
  EmailCodeInput,
  FormInput,
  GlassTabs,
  LoginModeTabs,
  PasswordInput,
  PasswordStrength,
  PASSWORD_PATTERN,
} from './AuthControls';
import { AuthLegalDocument, type LegalDocumentType } from './AuthLegalDocument';

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
  const { t } = useTranslation();
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
  const [requestId, setRequestId] = useState(() => uuid());
  const [legalDocument, setLegalDocument] = useState<LegalDocumentType | null>(null);
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
      const message = reason instanceof Error ? reason.message : t('auth.message.captchaLoadFailed');
      setCaptcha(null);
      setError(message);
      setCaptchaError(message);
    } finally {
      setCaptchaLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (riskCaptcha) void loadCaptcha();
  }, [loadCaptcha, riskCaptcha]);

  const completeLogin = (currentUser: Awaited<ReturnType<typeof authApi.passwordLogin>>) => {
    setUser(currentUser);
    message.success(t('auth.message.loginSuccess'));
    router.replace(redirect);
  };

  const handleError = (reason: unknown) => {
    const text = reason instanceof Error ? reason.message : t('auth.message.operationFailed');
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
      setCaptchaError(t('auth.captchaModal.notLoaded'));
      return;
    }
    if (!captchaCode.trim()) {
      setCaptchaError(t('auth.validation.captchaRequired'));
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
      message.success(t('auth.message.codeSent'));
      setCaptchaModalOpen(false);
      if (pendingCodeRequest.scene === 'reset_password') setResetStep(1);
    } catch (reason) {
      setCooldown(0);
      setCaptchaError(reason instanceof Error ? reason.message : t('auth.message.codeSendFailed'));
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
      message.success(t('auth.message.registerSuccess'));
      router.replace(redirect);
    } catch (reason) {
      setRequestId(uuid());
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

  const heading = mainMode === 'register' ? [t('auth.heading.registerTitle'), t('auth.heading.registerSubtitle')]
    : view === 'forgot' ? [t('auth.heading.forgotTitle'), t('auth.heading.forgotSubtitle')]
      : [t('auth.heading.loginTitle'), t('auth.heading.loginSubtitle')];

  return (
    <ConfigProvider theme={{ token: { colorPrimary: '#315b9b', borderRadius: 12, fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", sans-serif' } }}>
      <main className={`auth-page auth-mode-${mainMode}`}>
        <div className="auth-ambient auth-ambient-blue" /><div className="auth-ambient auth-ambient-green" /><div className="auth-ambient auth-ambient-gold" />
        <section className="auth-card" aria-labelledby="auth-title">
          <header className="auth-brand"><span>AI</span><div><strong>AI WorkMate</strong><small>ENTERPRISE WORKSPACE</small></div></header>
          <div className="auth-heading"><h1 id="auth-title">{heading[0]}</h1><p>{heading[1]}</p></div>
          <GlassTabs active={mainMode} onChange={switchMainMode} />

          {mainMode === 'login' && view === 'account' && <>
            <LoginModeTabs active={loginMode} onChange={(mode) => { setLoginMode(mode); setError(''); }} />
            {loginMode === 'password' ? <Form form={passwordForm} layout="vertical" initialValues={{ remember: true }} onFinish={submitPasswordLogin} requiredMark={false}>
              <Form.Item label={t('auth.field.email')} name="email" rules={[{ required: true, type: 'email', message: t('auth.validation.emailInvalid') }]}><FormInput placeholder="name@company.com" autoComplete="email" /></Form.Item>
              <Form.Item label={t('auth.field.password')} name="password" rules={[{ required: true, message: t('auth.validation.passwordRequired') }]}><PasswordInput placeholder={t('auth.field.passwordPlaceholder')} autoComplete="current-password" /></Form.Item>
              {riskCaptcha && <Form.Item label={t('auth.field.captcha')} name="captchaCode" rules={[{ required: true, message: t('auth.validation.captchaRequired') }]}><CaptchaInput image={captcha?.image} loading={captchaLoading} onRefresh={loadCaptcha} /></Form.Item>}
              <div className="auth-form-meta"><Form.Item name="remember" valuePropName="checked" noStyle><Checkbox>{t('auth.field.remember')}</Checkbox></Form.Item><Button type="link" onClick={() => { setView('forgot'); setResetStep(0); setError(''); }}>{t('auth.copy.forgotPassword')}</Button></div>
              <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>{t('auth.button.login')}</Button>
            </Form> : <Form form={codeForm} layout="vertical" initialValues={{ remember: true }} onFinish={submitCodeLogin} requiredMark={false}>
              <Form.Item label={t('auth.field.email')} name="email" rules={[{ required: true, type: 'email', message: t('auth.validation.emailInvalid') }]}><FormInput placeholder="name@company.com" /></Form.Item>
              <Form.Item label={t('auth.field.emailCode')} name="emailCode" rules={[{ required: true, message: t('auth.emailCodeRuleMessage') }, { pattern: EMAIL_CODE_PATTERN, message: t('auth.emailCodeRuleMessage') }]}><EmailCodeInput cooldown={cooldown} loading={sendingCode} onSend={() => void beginSendCode('login', codeForm)} /></Form.Item>
              <Form.Item name="remember" valuePropName="checked"><Checkbox>{t('auth.field.remember')}</Checkbox></Form.Item>
              <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>{t('auth.button.codeLogin')}</Button>
            </Form>}
            <div className="auth-divider"><span>{t('auth.social.divider')}</span></div>
            <div className="auth-social"><Button icon={<WechatOutlined />} onClick={() => message.info(t('auth.social.wechatUnavailable'))}>{t('auth.social.wechat')}</Button><Button icon={<DingdingOutlined />} onClick={() => message.info(t('auth.social.dingtalkUnavailable'))}>{t('auth.social.dingtalk')}</Button><Button icon={<BankOutlined />} onClick={() => message.info(t('auth.social.ssoUnavailable'))}>{t('auth.social.sso')}</Button></div>
            <p className="auth-switch-copy">{t('auth.copy.noAccount')}<Button type="link" onClick={() => switchMainMode('register')}>{t('auth.button.registerWithEmail')}</Button></p>
          </>}

          {mainMode === 'register' && <Form form={registerForm} layout="vertical" onFinish={submitRegister} requiredMark={false}>
            <Form.Item label={t('auth.field.name')} name="name" rules={[{ required: true, message: t('auth.validation.nameRequired') }]}><FormInput placeholder={t('auth.field.namePlaceholder')} /></Form.Item>
            <Form.Item label={t('auth.field.email')} name="email" rules={[{ required: true, type: 'email', message: t('auth.validation.emailInvalid') }]}><FormInput placeholder="name@company.com" /></Form.Item>
            <Form.Item label={t('auth.field.emailCode')} name="emailCode" rules={[{ required: true, message: t('auth.emailCodeRuleMessage') }, { pattern: EMAIL_CODE_PATTERN, message: t('auth.emailCodeRuleMessage') }]}><EmailCodeInput cooldown={cooldown} loading={sendingCode} onSend={() => void beginSendCode('register', registerForm)} /></Form.Item>
            <Form.Item label={t('auth.field.setName')} name="password" rules={[{ required: true, message: t('auth.validation.setPasswordRequired') }, { pattern: PASSWORD_PATTERN, message: t('auth.passwordRuleMessage') }]}><PasswordInput placeholder={t('auth.field.passwordSetPlaceholder')} onChange={(event) => setRegisterPassword(event.target.value)} /></Form.Item>
            <PasswordStrength value={registerPassword} />
            <Form.Item label={t('auth.field.confirmPassword')} name="confirmPassword" dependencies={['password']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error(t('auth.validation.passwordMismatch'))); } })]}><PasswordInput placeholder={t('auth.field.confirmPasswordPlaceholder')} /></Form.Item>
            <Form.Item className="auth-agreement-item" name="agreement" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(t('auth.validation.agreementRequired'))) }]}>
              <Checkbox>
                <span className="auth-agreement-copy">
                  {t('auth.copy.agreementPrefix')}
                  <Button type="link" onClick={(event) => { event.preventDefault(); event.stopPropagation(); setLegalDocument('service'); }}>{t('auth.legal.serviceLink')}</Button>
                  {t('auth.copy.agreementConnector')}
                  <Button type="link" onClick={(event) => { event.preventDefault(); event.stopPropagation(); setLegalDocument('privacy'); }}>{t('auth.legal.privacyLink')}</Button>
                </span>
              </Checkbox>
            </Form.Item>
            <AuthNotice>{error}</AuthNotice><Button htmlType="submit" type="primary" block size="large" loading={submitting}>{t('auth.button.createAccount')}</Button>
            <p className="auth-switch-copy">{t('auth.copy.hasAccount')}<Button type="link" onClick={() => switchMainMode('login')}>{t('auth.button.backToLogin')}</Button></p>
          </Form>}

          {mainMode === 'login' && view === 'forgot' && <>
            {resetStep === 2 ? <Result status="success" icon={<CheckCircleFilled />} title={t('auth.reset.successTitle')} subTitle={t('auth.reset.successSubtitle')} extra={<Button type="primary" onClick={() => { setView('account'); setResetStep(0); }}>{t('auth.button.backToLogin')}</Button>} /> : <Form form={resetForm} layout="vertical" onFinish={submitReset} requiredMark={false}>
              <Form.Item label={t('auth.field.email')} name="email" rules={[{ required: true, type: 'email', message: t('auth.validation.emailInvalid') }]}><FormInput placeholder="name@company.com" disabled={resetStep === 1} /></Form.Item>
              {resetStep === 0 && <Button block size="large" loading={sendingCode} onClick={() => void beginSendCode('reset_password', resetForm)}>{t('auth.button.sendEmailCode')}</Button>}
              {resetStep === 1 && <><Form.Item label={t('auth.field.emailCode')} name="emailCode" rules={[{ required: true, message: t('auth.emailCodeRuleMessage') }, { pattern: EMAIL_CODE_PATTERN, message: t('auth.emailCodeRuleMessage') }]}><Input size="large" placeholder={t('auth.field.emailCodeShortPlaceholder')} maxLength={6} inputMode="numeric" /></Form.Item><div className="auth-form-grid"><Form.Item label={t('auth.field.newPassword')} name="newPassword" rules={[{ required: true, message: t('auth.validation.newPasswordRequired') }, { pattern: PASSWORD_PATTERN, message: t('auth.passwordRuleMessage') }]}><PasswordInput placeholder={t('auth.field.newPasswordPlaceholder')} /></Form.Item><Form.Item label={t('auth.field.confirmNewPassword')} name="confirmPassword" dependencies={['newPassword']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error(t('auth.validation.passwordMismatch'))); } })]}><PasswordInput placeholder={t('auth.field.confirmNewPasswordPlaceholder')} /></Form.Item></div><Button htmlType="submit" type="primary" block size="large" loading={submitting}>{t('auth.button.confirmModify')}</Button></>}
              <AuthNotice>{error}</AuthNotice>
            </Form>}
            {resetStep !== 2 && <Button type="link" className="auth-back-login" onClick={() => { setView('account'); setResetStep(0); setError(''); }}>{t('auth.button.backToLogin')}</Button>}
          </>}
          <footer className="auth-security"><SafetyCertificateOutlined /> {t('auth.footer')}</footer>
        </section>
        <Modal
          className="auth-captcha-modal"
          width={456}
          title={t('auth.captchaModal.title')}
          open={captchaModalOpen}
          okText={t('auth.button.verifyAndSend')}
          cancelText={t('common.cancel')}
          confirmLoading={sendingCode}
          okButtonProps={{ disabled: captchaLoading || !captcha }}
          onOk={() => void confirmSendCode()}
          onCancel={() => { setCaptchaModalOpen(false); setCaptchaError(''); }}
          destroyOnHidden
        >
          <p className="auth-captcha-hint">{t('auth.captchaModal.hint')}</p>
          <CaptchaInput
            image={captcha?.image}
            loading={captchaLoading}
            value={captchaCode}
            onChange={setCaptchaCode}
            onRefresh={() => { setCaptchaCode(''); setCaptchaError(''); void loadCaptcha(); }}
          />
          <AuthNotice>{captchaError}</AuthNotice>
        </Modal>
        <Modal
          className="auth-legal-modal"
          width={720}
          title={legalDocument ? t(`auth.legal.${legalDocument}.title`) : undefined}
          open={legalDocument !== null}
          footer={<Button type="primary" onClick={() => setLegalDocument(null)}>{t('auth.legal.acknowledge')}</Button>}
          onCancel={() => setLegalDocument(null)}
          destroyOnHidden
        >
          {legalDocument && <AuthLegalDocument type={legalDocument} />}
        </Modal>
      </main>
    </ConfigProvider>
  );
}

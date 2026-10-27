import { useState, type ReactNode } from 'react';
import { Button } from 'antd';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import { OaIcon } from '@/components/OaIcon';
import './auth-experience.css';

const illustration = new URL('./assets/floating-islands.png', import.meta.url).href;
type Mode = 'login' | 'register';

interface Props {
  mode: Mode;
  onModeChange: (mode: Mode) => void;
  login: ReactNode;
  register: ReactNode;
  loginTitle: string;
  loginDescription: string;
}

export default function AuthExperience({ mode, onModeChange, login, register, loginTitle, loginDescription }: Props) {
  const { t } = useTranslation(undefined, { keyPrefix: 'authDesign' });
  const [entryMode] = useState(mode);
  const isRegister = mode === 'register';
  return <div className={`auth-review auth-production mode-${mode} entry-${entryMode}`}>
    <header className="review-header"><div className="wordmark"><span className="wordmark-monogram">{t('mark')}</span><strong>{t('brand')}</strong><span className="wordmark-divider" /><small>{t('workspace')}</small></div><div className="review-header-actions"><LanguageSwitcher /></div></header>
    <main className="auth-stage">
      <div className="fullscreen-art" aria-hidden="true"><img src={illustration} alt="" /><div className="art-light" /></div>
      <div className="forms-layer"><div className="glass-card-shell"><div className="card-glass-mask" aria-hidden="true" /><div className="flip-card">
        <section className="account-panel panel-login" aria-hidden={isRegister} inert={isRegister}><div className="account-content"><header className="account-heading"><span className="account-eyebrow">{t('loginEyebrow')}</span><h1>{loginTitle}</h1><p>{loginDescription}</p></header>{login}<footer className="account-security"><OaIcon name="safety" />{t('security')}</footer></div></section>
        <section className="account-panel panel-register" aria-hidden={!isRegister} inert={!isRegister}><div className="account-content"><header className="account-heading"><span className="account-eyebrow">{t('registerEyebrow')}</span><h1>{t('registerTitle')}</h1><p>{t('registerDescription')}</p></header>{register}<footer className="account-security"><OaIcon name="safety" />{t('security')}</footer></div></section>
      </div></div></div>
      <aside className="scene-panel"><div className="brand-panel-surface"><div className="brand-content-track">
        <div className="brand-message"><span className="brand-eyebrow scene-aligned"><span />{t('heroEyebrow')}</span><h2>{t(isRegister ? 'heroRegisterTitle' : 'heroLoginTitle').split('\n').map((line, index) => <span className="hero-title-line scene-aligned" key={index}>{line}</span>)}</h2><p>{t(isRegister ? 'heroRegisterDescription' : 'heroLoginDescription').split('\n').map((line, index) => <span className="hero-description-line scene-aligned" key={index}>{line}</span>)}</p></div>
        <div className="brand-invitation"><p className="scene-aligned">{t(isRegister ? 'heroRegisterPrompt' : 'heroLoginPrompt')}</p><div className="invitation-position scene-aligned"><Button onClick={() => onModeChange(isRegister ? 'login' : 'register')}><span>{t(isRegister ? 'heroRegisterAction' : 'heroLoginAction')}</span><span className="invitation-arrow" aria-hidden="true"><OaIcon name={isRegister ? 'previous' : 'next'} /></span></Button></div></div>
        <div className="brand-caption-position"><span className="brand-panel-caption scene-aligned">{t('heroFoot')}</span></div>
      </div></div></aside>
    </main>
  </div>;
}

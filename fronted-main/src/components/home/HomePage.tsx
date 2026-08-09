import { useEffect, useState } from 'react';
import {
  ArrowRight,
  Bot,
  BrainCircuit,
  CheckCircle2,
  DatabaseZap,
  FileSearch,
  Play,
  ShieldCheck,
  Sparkles,
  Zap,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import ThemeToggle from '@/components/ThemeToggle';
import LanguageSwitcher from '@/components/LanguageSwitcher';

type SiteTheme = 'day' | 'night';
type WorkflowKey = 'launch' | 'contract' | 'support' | 'weekly';

const workflowIcons: Record<WorkflowKey, typeof BrainCircuit> = {
  launch: BrainCircuit,
  contract: FileSearch,
  support: Bot,
  weekly: DatabaseZap,
};

interface WorkflowCopy {
  title: string;
  desc: string;
  steps: string[];
}

export default function HomePage() {
  const { t } = useTranslation();
  // 避免 SSR hydration mismatch：服务端和客户端首次渲染都用 'day'，
  // 挂载后再从 localStorage 读取真实偏好并切换。
  const [theme, setTheme] = useState<SiteTheme>('day');
  const [mounted, setMounted] = useState(false);
  const [workflow, setWorkflow] = useState<WorkflowKey>('launch');
  const [isRunning, setIsRunning] = useState(false);

  // 挂载后读取 localStorage 中的真实主题
  useEffect(() => {
    setMounted(true);
    if (typeof window === 'undefined') return;
    const saved = window.localStorage.getItem('wm-theme');
    if (saved === 'day' || saved === 'night') {
      setTheme(saved);
    }
  }, []);

  // 持久化日夜模式
  useEffect(() => {
    if (typeof window === 'undefined' || !mounted) return;
    window.localStorage.setItem('wm-theme', theme);
  }, [theme, mounted]);

  // 刷新页面时回到顶部（关闭浏览器自动 scroll restoration）
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, []);

  useEffect(() => {
    const revealItems = Array.from(document.querySelectorAll<HTMLElement>('.wm-scroll-reveal'));
    if (!revealItems.length) {
      return;
    }

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      revealItems.forEach((item) => item.classList.add('is-visible'));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        rootMargin: '0px 0px -12% 0px',
        threshold: 0.12,
      },
    );

    requestAnimationFrame(() => {
      revealItems.forEach((item) => observer.observe(item));
    });

    return () => observer.disconnect();
  }, [theme]);

  const workflowCopy = t(`home.workflows.${workflow}`, { returnObjects: true }) as WorkflowCopy;
  const featureCards = t('home.product.features', { returnObjects: true }) as Array<{
    num: string;
    title: string;
    desc: string;
  }>;
  const agentCards = t('home.agents.cards', { returnObjects: true }) as Array<{
    title: string;
    desc: string;
    stat: string;
  }>;
  const agentTasks = t('home.agents.tasks', { returnObjects: true }) as string[];
  const trustItems = t('home.trust.items', { returnObjects: true }) as string[];
  const metricItems = t('home.metrics.items', { returnObjects: true }) as Array<{
    value: string;
    label: string;
  }>;
  const securityItems = t('home.security.items', { returnObjects: true }) as Array<{
    title: string;
    desc: string;
  }>;
  const stackTitles = t('home.scenes.stackTitle', { returnObjects: true }) as string[];
  const workflowLabels = t('home.window.workflowLabels', { returnObjects: true }) as Record<
    WorkflowKey,
    string
  >;

  const isNight = theme === 'night';

  const startRun = () => {
    setIsRunning(true);
    window.setTimeout(() => setIsRunning(false), 1300);
  };

  const enterOa = () => {
    // 生产环境通过构建期环境变量注入 OA 入口地址（部署在反代/域名下时必须配置）
    const oaUrl = import.meta.env.VITE_OA_URL;
    if (oaUrl) {
      window.location.href = oaUrl;
      return;
    }
    // 默认指向同 host 的 3001 端口 /oa/：本地 dev 下主站 3000 与 OA 3001 分离。
    // 末尾斜杠必须保留：fonted-oa 的 vite base 为 '/oa/'，访问 /oa 会被 Vite 拦截重定向提示。
    // 生产 nginx 同 IP 同端口反代时，应在 .env 配置 VITE_OA_URL=/oa/（或完整域名）覆盖此默认值。
    const { hostname } = window.location;
    window.location.href = `http://${hostname}:3001/oa/`;
  };

  return (
    // 切换日夜模式时强制重挂载，触发所有 CSS keyframe 重新播放
    <main
      className={`wm-site ${isNight ? 'wm-night' : 'wm-day'}`}
      key={`wm-site-${theme}`}
    >
      <div className="wm-bg" aria-hidden="true">
        <span className="wm-mesh wm-mesh-a" />
        <span className="wm-mesh wm-mesh-b" />
        <span className="wm-mesh wm-mesh-c" />
        <span className="wm-grid" />
        <span className="wm-lightline wm-lightline-a" />
        <span className="wm-lightline wm-lightline-b" />
      </div>

      <header className="wm-nav">
        <button type="button" className="wm-brand" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
          <span className="wm-mark"><Sparkles className="h-5 w-5" /></span>
          <strong>{t('brand.name')}</strong>
        </button>
        <nav className="wm-links">
          <a href="#product">{t('nav.product')}</a>
          <a href="#scenes">{t('nav.scenes')}</a>
          <a href="#security">{t('nav.security')}</a>
          <a href="#docs">{t('nav.docs')}</a>
        </nav>
        <div className="wm-actions">
          <ThemeToggle theme={theme} onChange={setTheme} />
          <LanguageSwitcher />
          <button type="button" className="wm-login" onClick={enterOa}>{t('common.login')}</button>
          <button type="button" className="wm-try wm-try-nav" onClick={enterOa} aria-label={t('common.tryNow')}>
            <span className="wm-try-nav-text">{t('common.tryNow')}</span>
            <ArrowRight className="h-4 w-4" />
          </button>
        </div>
      </header>

      <section className="wm-hero">
        <p className="wm-eyebrow">{t('home.hero.eyebrow')}</p>
        <h1 className="wm-hero-title">
          <span className="wm-hero-line">{t('home.hero.titleLine1')}</span>
          <span className="wm-hero-line wm-hero-line-accent">{t('home.hero.titleLine2')}</span>
        </h1>
        <p className="wm-sub">{t('home.hero.sub')}</p>

        <div className="wm-hero-actions">
          <button type="button" className="wm-try wm-try-main" onClick={enterOa}>
            {t('common.tryNow')}
            <Play className="h-5 w-5 fill-current" />
          </button>
          <a href="#product" className="wm-secondary">
            {t('home.hero.productTour')}
          </a>
        </div>

        {isNight ? (
          <>
            <NightAgentBoard
              agentCards={agentCards}
              agentTasks={agentTasks}
            />
            <MetricStrip items={metricItems} aria={t('home.metrics.aria')} />
          </>
        ) : (
          <>
            <div className="wm-demo-stage">
              <DayProductWindow
                workflow={workflow}
                workflowCopy={workflowCopy}
                workflowLabels={workflowLabels}
                isRunning={isRunning}
                onWorkflowChange={setWorkflow}
                onRun={startRun}
              />
              <FloatingProof className="wm-proof-a" label={t('home.proof.ssoLabel')} value={t('home.proof.ssoValue')} />
              <FloatingProof className="wm-proof-b" label={t('home.proof.auditLabel')} value={t('home.proof.auditValue')} />
              <FloatingProof className="wm-proof-c" label={t('home.proof.toolLabel')} value={t('home.proof.toolValue')} />
            </div>
            <TrustRow items={trustItems} aria={t('home.trust.aria')} />
          </>
        )}
      </section>

      <section id="product" className="wm-section wm-scroll-reveal">
        <div className="wm-section-head wm-scroll-reveal">
          <p className="wm-eyebrow">{t('home.product.eyebrow')}</p>
          <h2>{t('home.product.title')}</h2>
          <p>{t('home.product.desc')}</p>
        </div>
        <div className="wm-feature-grid">
          {featureCards.map((card) => (
            <article className="wm-feature-card wm-scroll-reveal" key={card.title}>
              <small>{card.num}</small>
              <h3>{card.title}</h3>
              <p>{card.desc}</p>
            </article>
          ))}
        </div>
      </section>

      <section id="scenes" className="wm-split wm-scroll-reveal">
        <div className="wm-scroll-reveal">
          <p className="wm-eyebrow">{t('home.scenes.eyebrow')}</p>
          <h2>{t('home.scenes.title')}</h2>
          <p>{t('home.scenes.desc')}</p>
          <button type="button" className="wm-try" onClick={enterOa}>
            {t('common.tryNow')}
            <Zap className="h-5 w-5 fill-current" />
          </button>
        </div>
        <div className="wm-stack-panel wm-scroll-reveal">
          {stackTitles.map((item, index) => (
            <div className="wm-stack-row wm-scroll-reveal" key={item}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <div>
                <strong>{item}</strong>
                <p>{t('home.scenes.stackDesc')}</p>
              </div>
              <small>{index === 0 ? t('home.scenes.statusRunning') : t('home.scenes.statusReady')}</small>
            </div>
          ))}
        </div>
      </section>

      <section id="security" className="wm-security wm-scroll-reveal">
        <div className="wm-scroll-reveal">
          <p className="wm-eyebrow">{t('home.security.eyebrow')}</p>
          <h2>{t('home.security.title')}</h2>
        </div>
        <div className="wm-security-list">
          {securityItems.map((item) => (
            <article className="wm-scroll-reveal" key={item.title}>
              <ShieldCheck className="h-5 w-5" />
              <div>
                <strong>{item.title}</strong>
                <p>{item.desc}</p>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section id="docs" className="wm-final wm-scroll-reveal">
        <p className="wm-eyebrow">{t('home.final.eyebrow')}</p>
        <h2>{t('home.final.title')}</h2>
        <p>{t('home.final.desc')}</p>
        <button type="button" className="wm-try wm-try-main" onClick={enterOa}>
          {t('common.tryNow')}
          <ArrowRight className="h-5 w-5" />
        </button>
      </section>
    </main>
  );
}

function FloatingProof({ className, label, value }: { className: string; label: string; value: string }) {
  return (
    <div className={`wm-floating-proof ${className}`} aria-hidden="true">
      <div className="wm-floating-proof-inner">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

function TrustRow({ items, aria }: { items: string[]; aria: string }) {
  return (
    <div className="wm-trust-row wm-scroll-reveal" aria-label={aria}>
      {items.map((item) => (
        <span className="wm-scroll-reveal" key={item}>{item}</span>
      ))}
    </div>
  );
}

function MetricStrip({ items, aria }: { items: Array<{ value: string; label: string }>; aria: string }) {
  return (
    <div className="wm-metric-strip wm-scroll-reveal" aria-label={aria}>
      {items.map((item) => (
        <div className="wm-scroll-reveal" key={item.label}>
          <strong>{item.value}</strong>
          <span>{item.label}</span>
        </div>
      ))}
    </div>
  );
}

function DayProductWindow({
  workflow,
  workflowCopy,
  workflowLabels,
  isRunning,
  onWorkflowChange,
  onRun,
}: {
  workflow: WorkflowKey;
  workflowCopy: WorkflowCopy;
  workflowLabels: Record<WorkflowKey, string>;
  isRunning: boolean;
  onWorkflowChange: (key: WorkflowKey) => void;
  onRun: () => void;
}) {
  const { t } = useTranslation();
  const items = (Object.keys(workflowLabels) as WorkflowKey[]).map((key) => [
    key,
    workflowLabels[key],
    workflowIcons[key],
  ] as const);

  return (
    <div className={`wm-window ${isRunning ? 'is-running' : ''}`}>
      <div className="wm-window-top">
        <span className="wm-dots"><i /><i /><i /></span>
        <strong>{t('home.window.title')}</strong>
        <span className="wm-sync">{isRunning ? t('home.window.syncRunning') : t('home.window.syncSynced')}</span>
      </div>
      <div className="wm-workspace">
        <aside>
          <strong>{t('home.window.workflowsTitle')}</strong>
          {items.map(([key, label, Icon]) => (
            <button key={key} type="button" className={workflow === key ? 'active' : ''} onClick={() => onWorkflowChange(key)}>
              <Icon className="h-4 w-4" />
              {label}
            </button>
          ))}
        </aside>
        <div className="wm-main-panel">
          <h3>{workflowCopy.title}</h3>
          <p>{workflowCopy.desc}</p>
          <div className="wm-steps">
            {workflowCopy.steps.map((step, index) => (
              <div key={step} className={index < 3 ? 'done' : ''}>
                <CheckCircle2 className="h-5 w-5" />
                <span>{step}</span>
                <small>{index < 3 ? t('common.done') : t('common.pending')}</small>
              </div>
            ))}
          </div>
          <div className="wm-prompt">
            <span>{isRunning ? t('home.window.promptRunning') : t('home.window.promptIdle')}</span>
            <button type="button" onClick={onRun}>{isRunning ? t('home.window.syncRunning') : t('home.window.run')}</button>
          </div>
        </div>
      </div>
    </div>
  );
}

function NightAgentBoard({
  agentCards,
  agentTasks,
}: {
  agentCards: Array<{ title: string; desc: string; stat: string }>;
  agentTasks: string[];
}) {
  const { t } = useTranslation();
  return (
    <div className="wm-agent-board wm-scroll-reveal">
      {agentCards.map((card, index) => (
        <article className={`${index === 1 ? 'featured ' : ''}wm-scroll-reveal`} key={card.title}>
          <div className="wm-agent-orb" />
          <h3>{card.title}</h3>
          <p>{card.desc}</p>
          {agentTasks.map((task, taskIndex) => (
            <div className="wm-task" key={task}>
              <strong>{task}</strong>
              <span>
                {taskIndex === 0
                  ? card.stat
                  : taskIndex === 1
                    ? t('home.agents.taskReady')
                    : t('home.agents.taskApproval')}
              </span>
            </div>
          ))}
        </article>
      ))}
    </div>
  );
}

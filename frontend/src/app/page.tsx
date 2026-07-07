'use client';

import { useState } from 'react';
import { ConfigProvider, Switch } from 'antd';
import {
  ArrowRight,
  Bot,
  BrainCircuit,
  CheckCircle2,
  ChevronLeft,
  Command,
  DatabaseZap,
  FileSearch,
  LockKeyhole,
  Moon,
  Play,
  ShieldCheck,
  Sparkles,
  Sun,
  Workflow,
  Zap,
} from 'lucide-react';
import Sidebar from '@/components/Sidebar';
import ChatInterface from '@/components/ChatInterface';
import LoginPage from '@/components/LoginPage';

type SiteTheme = 'day' | 'night';
type AppMode = 'marketing' | 'experience';
type WorkflowKey = 'launch' | 'contract' | 'support' | 'weekly';

const workflowCopy: Record<WorkflowKey, { title: string; desc: string; steps: string[] }> = {
  launch: {
    title: '智能体正在准备企业级发布方案',
    desc: '已读取客户关系记录、产品文档、价格规则和安全策略，下一步生成营销页面并把审批流转给法务与销售运营。',
    steps: ['读取知识库', '生成产品叙事', '检查权限策略', '准备交接材料'],
  },
  contract: {
    title: '智能体正在审查重点客户合同',
    desc: '自动对照标准政策、价格例外和数据处理要求，标记高风险条款并发起人工复核。',
    steps: ['解析合同条款', '比对政策规则', '生成风险摘要', '发起法务复核'],
  },
  support: {
    title: '智能体正在分诊高风险客服工单',
    desc: '聚类近期工单，识别重复问题、高价值客户风险，并推荐回复话术与升级路径。',
    steps: ['聚类近期工单', '识别高风险客户', '推荐处理话术', '同步客户成功'],
  },
  weekly: {
    title: '智能体正在生成本周经营简报',
    desc: '汇总销售、交付、客服和产品指标，整理成管理层可直接阅读的周报，并附带来源和负责人。',
    steps: ['汇总业务指标', '提炼异常信号', '生成管理摘要', '发送负责人确认'],
  },
};

const featureCards = [
  ['01', '带权限的知识', '连接制度、合同、工单和业务数据，同时保留原有访问控制。'],
  ['02', '工作流，不是提示词', '把重复任务沉淀为带审批、工具和负责人的可复用流程。'],
  ['03', '可观测的执行', '每一次智能体行动都有来源、状态、负责人和审计记录。'],
  ['04', '企业级推广', '从一个部门试点，再把同一套运营方式扩展到全公司。'],
];

const agentCards = [
  ['研究智能体', '查找客户背景、竞品信息和来源文档。', '12 份文档'],
  ['销售智能体', '起草触达内容、方案和交接记录。', '3 个草稿'],
  ['运营智能体', '把审批和交付任务变成可复用流程。', '4 条流程'],
];

export default function Home() {
  const [mode, setMode] = useState<AppMode>('marketing');
  const [theme, setTheme] = useState<SiteTheme>('day');
  const [workflow, setWorkflow] = useState<WorkflowKey>('launch');
  const [isRunning, setIsRunning] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(() => {
    if (typeof window !== 'undefined') {
      return !!localStorage.getItem('token');
    }
    return false;
  });

  if (mode === 'experience') {
    return (
      <div className="relative min-h-screen bg-white dark:bg-dark-900">
        <button
          type="button"
          onClick={() => setMode('marketing')}
          className="fixed left-4 top-4 z-50 inline-flex items-center gap-2 rounded-full border border-white/30 bg-white/80 px-4 py-2 text-sm font-medium text-slate-700 shadow-2xl shadow-cyan-950/10 backdrop-blur-xl transition hover:-translate-y-0.5 hover:bg-white dark:border-white/10 dark:bg-dark-800/80 dark:text-white"
        >
          <ChevronLeft className="h-4 w-4" />
          返回官网
        </button>

        {!isLoggedIn ? (
          <LoginPage onLoginSuccess={() => setIsLoggedIn(true)} />
        ) : (
          <div className="flex h-screen overflow-hidden">
            <Sidebar
              onLogout={() => {
                localStorage.removeItem('token');
                setIsLoggedIn(false);
              }}
            />
            <main className="flex min-w-0 flex-1 flex-col">
              <ChatInterface />
            </main>
          </div>
        )}
      </div>
    );
  }

  const activeWorkflow = workflowCopy[workflow];
  const isNight = theme === 'night';

  const startRun = () => {
    setIsRunning(true);
    window.setTimeout(() => setIsRunning(false), 1300);
  };

  return (
    <main className={`wm-site ${isNight ? 'wm-night' : 'wm-day'}`}>
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
          <strong>AI WorkMate</strong>
        </button>
        <nav className="wm-links">
          <a href="#product">产品</a>
          <a href="#scenes">场景</a>
          <a href="#security">安全</a>
          <a href="#docs">文档</a>
        </nav>
        <div className="wm-actions">
          <ConfigProvider
            theme={{
              token: {
                colorPrimary: '#11100f',
                colorPrimaryHover: '#11100f',
                colorPrimaryBorder: '#11100f',
                colorTextQuaternary: '#11100f',
                controlHeight: 32,
                borderRadius: 999,
              },
              components: {
                Switch: {
                  handleBg: isNight ? '#ffffff' : '#11100f',
                  colorPrimary: '#11100f',
                  colorPrimaryHover: '#11100f',
                  colorPrimaryBorder: '#11100f',
                },
              },
            }}
          >
            <div className="wm-switcher" aria-label="日夜模式切换">
              <span className={isNight ? '' : 'active'}>
                <Sun className="h-4 w-4" />
              </span>
              <Switch
                aria-label="切换日间或夜间模式"
                checked={isNight}
                onChange={(checked) => setTheme(checked ? 'night' : 'day')}
                checkedChildren={<Moon className="h-3.5 w-3.5" />}
                unCheckedChildren={<Sun className="h-3.5 w-3.5" />}
              />
              <span className={isNight ? 'active' : ''}>
                <Moon className="h-4 w-4" />
              </span>
            </div>
          </ConfigProvider>
          <button type="button" className="wm-login">登录</button>
          <button type="button" className="wm-try wm-try-nav" onClick={() => setMode('experience')}>
            立即尝试
            <ArrowRight className="h-4 w-4" />
          </button>
        </div>
      </header>

      <section className="wm-hero">
        <p className="wm-eyebrow">ENTERPRISE AGENT OS</p>
        <h1>
          让公司的每项工作，
          <br />
          都能被智能体接手。
        </h1>
        <p className="wm-sub">
          AI WorkMate 为每个团队提供可治理的工作空间，让智能体读取知识、调用工具、遵循权限，并把过程和结果完整留痕。
        </p>

        <div className="wm-hero-actions">
          <button type="button" className="wm-try wm-try-main" onClick={() => setMode('experience')}>
            立即尝试
            <Play className="h-5 w-5 fill-current" />
          </button>
          <a href="#product" className="wm-secondary">
            查看 Product Tour
          </a>
        </div>

        {isNight ? (
          <>
            <NightAgentBoard />
            <MetricStrip />
          </>
        ) : (
          <>
            <div className="wm-demo-stage">
              <DayProductWindow
                workflow={workflow}
                activeWorkflow={activeWorkflow}
                isRunning={isRunning}
                onWorkflowChange={setWorkflow}
                onRun={startRun}
              />
              <FloatingProof className="wm-proof-a" label="SSO Ready" value="权限同步" />
              <FloatingProof className="wm-proof-b" label="Audit Trail" value="全链路留痕" />
              <FloatingProof className="wm-proof-c" label="Tool Calling" value="工具白名单" />
            </div>
            <TrustRow />
          </>
        )}
      </section>

      <section id="product" className="wm-section">
        <div className="wm-section-head">
          <p className="wm-eyebrow">Product Tour</p>
          <h2>不止停留在聊天框里，而是接管真实流程。</h2>
          <p>从官网首屏建立价值认知，再用真实产品入口承接试用。销售、运营、客服、法务都能拥有自己的 Agent Workspace。</p>
        </div>
        <div className="wm-feature-grid">
          {featureCards.map(([num, title, desc]) => (
            <article className="wm-feature-card" key={title}>
              <small>{num}</small>
              <h3>{title}</h3>
              <p>{desc}</p>
            </article>
          ))}
        </div>
      </section>

      <section id="scenes" className="wm-split">
        <div>
          <p className="wm-eyebrow">营销玩法</p>
          <h2>把“预约 Demo”变成“立即尝试”。</h2>
          <p>
            让潜在客户不用等待销售联系，直接进入当前产品路径。官网通过稀缺席位、同步状态、演示窗口和真实入口完成转化闭环。
          </p>
          <button type="button" className="wm-try" onClick={() => setMode('experience')}>
            立即尝试
            <Zap className="h-5 w-5 fill-current" />
          </button>
        </div>
        <div className="wm-stack-panel">
          {['销售赋能', '法务审查', '客服运营', '管理层汇报'].map((item, index) => (
            <div className="wm-stack-row" key={item}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <div>
                <strong>{item}</strong>
                <p>一键启动场景 Agent，生成材料、路由审批并留痕。</p>
              </div>
              <small>{index === 0 ? '运行中' : '就绪'}</small>
            </div>
          ))}
        </div>
      </section>

      <section id="security" className="wm-security">
        <div>
          <p className="wm-eyebrow">Enterprise Control</p>
          <h2>默认可治理，上线第一天就能用。</h2>
        </div>
        <div className="wm-security-list">
          {[
            ['按角色读取上下文', '智能体只访问当前用户、团队或业务单元授权的数据。'],
            ['人工审批闸口', '发送、写入系统、修改权限等高影响动作先进入确认。'],
            ['审计级记忆', '输入、来源、工具调用、负责人和输出结果全链路留痕。'],
          ].map(([title, desc]) => (
            <article key={title}>
              <ShieldCheck className="h-5 w-5" />
              <div>
                <strong>{title}</strong>
                <p>{desc}</p>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section id="docs" className="wm-final">
        <p className="wm-eyebrow">AI WorkMate</p>
        <h2>从一个想法，走到可运行流程。</h2>
        <p>现在就进入当前项目体验登录、鉴权和 SSE 流式聊天链路。后续可以继续承接知识库、Agent 模板市场和私有化部署介绍。</p>
        <button type="button" className="wm-try wm-try-main" onClick={() => setMode('experience')}>
          立即尝试
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

function TrustRow() {
  return (
    <div className="wm-trust-row" aria-label="示例客户">
      {['星河银行', '海河运营', '量点科技', '明日智能', '北辰数据', 'Atlas Ops'].map((item) => (
        <span key={item}>{item}</span>
      ))}
    </div>
  );
}

function MetricStrip() {
  return (
    <div className="wm-metric-strip" aria-label="产品指标">
      {[
        ['4.8x', '简报周期提速'],
        ['1.2万', '知识条目已索引'],
        ['38%', '人工交接减少'],
        ['99.9%', '审计链路覆盖'],
      ].map(([value, label]) => (
        <div key={label}>
          <strong>{value}</strong>
          <span>{label}</span>
        </div>
      ))}
    </div>
  );
}

function DayProductWindow({
  workflow,
  activeWorkflow,
  isRunning,
  onWorkflowChange,
  onRun,
}: {
  workflow: WorkflowKey;
  activeWorkflow: { title: string; desc: string; steps: string[] };
  isRunning: boolean;
  onWorkflowChange: (key: WorkflowKey) => void;
  onRun: () => void;
}) {
  const items: Array<[WorkflowKey, string, typeof BrainCircuit]> = [
    ['launch', '销售赋能', BrainCircuit],
    ['contract', '合同审查', FileSearch],
    ['support', '客服运营', Bot],
    ['weekly', '周报生成', DatabaseZap],
  ];

  return (
    <div className={`wm-window ${isRunning ? 'is-running' : ''}`}>
      <div className="wm-window-top">
        <span className="wm-dots"><i /><i /><i /></span>
        <strong>enterprise-agent-session</strong>
        <span className="wm-sync">{isRunning ? 'Running' : 'Synced'}</span>
      </div>
      <div className="wm-workspace">
        <aside>
          <strong>Agent Workflows</strong>
          {items.map(([key, label, Icon]) => (
            <button key={key} type="button" className={workflow === key ? 'active' : ''} onClick={() => onWorkflowChange(key)}>
              <Icon className="h-4 w-4" />
              {label}
            </button>
          ))}
        </aside>
        <div className="wm-main-panel">
          <h3>{activeWorkflow.title}</h3>
          <p>{activeWorkflow.desc}</p>
          <div className="wm-steps">
            {activeWorkflow.steps.map((step, index) => (
              <div key={step} className={index < 3 ? 'done' : ''}>
                <CheckCircle2 className="h-5 w-5" />
                <span>{step}</span>
                <small>{index < 3 ? '完成' : '待确认'}</small>
              </div>
            ))}
          </div>
          <div className="wm-prompt">
            <span>{isRunning ? '智能体正在调用工具、生成材料并等待审计记录...' : '让 AI WorkMate 搭建 Launch Workspace...'}</span>
            <button type="button" onClick={onRun}>{isRunning ? 'Running' : 'Run'}</button>
          </div>
        </div>
      </div>
    </div>
  );
}

function NightAgentBoard() {
  return (
    <div className="wm-agent-board">
      {agentCards.map(([title, desc, stat], index) => (
        <article className={index === 1 ? 'featured' : ''} key={title}>
          <div className="wm-agent-orb" />
          <h3>{title}</h3>
          <p>{desc}</p>
          {['收集来源', '起草输出', '请求复核'].map((task, taskIndex) => (
            <div className="wm-task" key={task}>
              <strong>{task}</strong>
              <span>{taskIndex === 0 ? stat : taskIndex === 1 ? '已就绪' : '人工审批'}</span>
            </div>
          ))}
        </article>
      ))}
    </div>
  );
}

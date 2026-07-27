'use client';

import {
  BookOutlined,
  BulbOutlined,
  EyeOutlined,
  KeyOutlined,
  LockOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Anchor, Button, Drawer, Space, Tag, Typography } from 'antd';
import type { OaRole } from '@/types/oa';

interface HelpDrawerProps {
  open: boolean;
  role: OaRole;
  onClose: () => void;
  onOpenAi: (prompt?: string) => void;
}

interface HelpSection {
  id: string;
  icon: React.ReactNode;
  title: string;
  body: React.ReactNode;
}

const ROLE_LABELS: Record<OaRole, string> = {
  super_admin: '超级管理员',
  system_admin: '系统管理员',
  process_admin: '流程管理员',
  finance_admin: '财务管理员',
  employee: '普通员工',
};

export default function HelpDrawer({ open, role, onClose, onOpenAi }: HelpDrawerProps) {
  const sections: HelpSection[] = [
    {
      id: 'overview',
      icon: <BookOutlined />,
      title: '工作台概览',
      body: (
        <Typography>
          <p>AI WorkMate OA 工作台是企业级 AI 助手与办公协同平台，当前已上线以下能力：</p>
          <ul>
            <li><strong>企业驾驶舱</strong>：实时查看运营指标、审批记录、系统健康度。</li>
            <li><strong>AI 工作空间</strong>：与 AI 智能体进行流式对话，支持上传附件、多轮上下文、模型切换。</li>
            <li><strong>外观设置</strong>：内置 7 套主题、自定义壁纸、AI 快捷卡片开关。</li>
            <li><strong>权限管理</strong>：基于角色的菜单/按钮/数据范围控制，支持动态路由。</li>
            <li><strong>AI 操作面板</strong>：按当前页面上下文生成执行计划，高风险动作需二次确认。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'navigation',
      icon: <RocketOutlined />,
      title: '导航与页面切换',
      body: (
        <Typography>
          <ul>
            <li>左侧菜单栏按目录分组，点击任意页面进入对应路由 <code>/oa/&lt;pageId&gt;</code>。</li>
            <li>支持同时展开多个目录，刷新叶子页面时自动恢复其目录链。</li>
            <li>菜单可折叠为图标态，鼠标移入侧栏时显示弹出子菜单。</li>
            <li>顶部面包屑显示当前路径层级，<strong>首次编译某路由可能需要 5–15 秒</strong>，二次访问秒开。</li>
            <li>侧栏右侧「新建流程」按钮可一键发起跨部门审批请求。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'ai-workspace',
      icon: <RobotOutlined />,
      title: 'AI 工作空间',
      body: (
        <Typography>
          <ul>
            <li><strong>新建聊天</strong>：点击左侧「新建聊天」进入草稿模式，输入首条消息后才真正创建后端会话。</li>
            <li><strong>多轮对话</strong>：支持流式输出（SSE），按 <Kbd>Enter</Kbd> 发送，<Kbd>Shift</Kbd> + <Kbd>Enter</Kbd> 换行。</li>
            <li><strong>附件上传</strong>：拖拽文件到输入框或点击回形针按钮，支持图片（jpg/png/webp，10MB）和文档（pdf/doc/xls/txt/csv/md，20MB）。</li>
            <li><strong>模型切换</strong>：顶部下拉切换对话模型，生成中不可切换。</li>
            <li><strong>会话管理</strong>：侧栏按「今天 / 7 天 / 30 天 / 更早」分组，悬停会话条目显示更多操作（重命名 / 删除）。</li>
            <li><strong>反馈</strong>：AI 回复支持点赞 / 踩，用于优化回答质量。</li>
            <li><strong>停止生成</strong>：流式输出中可点击「停止生成」中断。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'ai-drawer',
      icon: <ThunderboltOutlined />,
      title: 'AI 操作面板（页面级）',
      body: (
        <Typography>
          <ul>
            <li>右下角圆形悬浮按钮打开 AI 操作面板，展示当前页面、角色、数据范围、可执行动作。</li>
            <li><strong>计划生成</strong>：AI 根据当前上下文生成执行步骤，调用 <code>POST /api/ai/tasks/plan</code>。</li>
            <li><strong>确认执行</strong>：用户确认后调用 <code>POST /api/ai/tasks/execute</code>，高风险动作需二次确认。</li>
            <li>所有 AI 动作均经服务端 JWT 校验，前端角色仅用于 UI 展示，<strong>不作为鉴权依据</strong>。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'appearance',
      icon: <EyeOutlined />,
      title: '外观与主题',
      body: (
        <Typography>
          <ul>
            <li>顶部头像菜单 →「外观设置」打开外观 Drawer。</li>
            <li>内置 7 套主题：企业蓝、深青绿、高级紫、石墨灰、暖棕橙、首页风格、黑夜风格。</li>
            <li><strong>自定义壁纸</strong>：上传图片后进入裁剪流程（支持拖动 / 缩放 / 旋转 / 比例切换），确认后压缩并持久化到 localStorage。</li>
            <li>启用壁纸后，侧栏、顶栏、卡片、表格、抽屉统一切换为透明模糊材质。</li>
            <li>壁纸透明度与模糊度可调，实时预览。</li>
            <li><strong>AI 快捷卡片</strong>：开启后右下角显示「需要我接手当前流程吗？」快捷入口（AI 工作空间页面不显示）。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'permissions',
      icon: <LockOutlined />,
      title: '权限与角色',
      body: (
        <Typography>
          <p>当前角色：<Tag color="blue">{ROLE_LABELS[role]}</Tag></p>
          <ul>
            <li>菜单、按钮、AI 动作权限均由后端 <code>GET /api/navigation</code> 与 JWT 实时解析，<strong>角色变更无需重新登录</strong>。</li>
            <li>页面权限采用 <code>route:&lt;routeKey&gt;</code>，直接访问无权限路由会回到首个可访问页面。</li>
            <li>权限后台位于 <code>/oa/access-control</code>（需 <code>access:manage</code> 权限），支持角色管理、用户分配、动态路由配置。</li>
            <li>最后一名 <code>SUPER_ADMIN</code> 不可被降级，保证系统可管理性。</li>
            <li>普通员工不可见系统设置、不可执行审批 / 删除 / 权限修改 / 敏感导出等高风险操作。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'security',
      icon: <SafetyOutlined />,
      title: '安全边界',
      body: (
        <Typography>
          <ul>
            <li>所有接口均经 JWT 鉴权，AI 会话与消息按认证 <code>userId</code> 校验所有权。</li>
            <li>API Key 与 AI 网关地址仅在服务端环境变量配置，<strong>不读取、不回显、不持久化到浏览器</strong>。</li>
            <li>未接真实数据库 / 审批 / 文件上传 / 导出 / LLM 时，相关能力明确返回「能力不可用」，<strong>不模拟成功</strong>。</li>
            <li>会话超时或 JWT 失效时，前端自动跳转登录页。</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'shortcuts',
      icon: <KeyOutlined />,
      title: '快捷操作',
      body: (
        <Typography>
          <ul>
            <li><Kbd>Enter</Kbd> — 在 AI 工作空间发送消息</li>
            <li><Kbd>Shift</Kbd> + <Kbd>Enter</Kbd> — 在 AI 工作空间换行</li>
            <li>点击侧栏 OA logo — 回到企业驾驶舱</li>
            <li>右下角圆形 AI 按钮 — 打开当前页面 AI 操作面板</li>
          </ul>
        </Typography>
      ),
    },
    {
      id: 'tips',
      icon: <BulbOutlined />,
      title: '常见问题',
      body: (
        <Typography>
          <ul>
            <li><strong>首次进入页面很慢？</strong>Next.js dev 模式按需编译，首次 5–15 秒属正常，二次访问秒开。</li>
            <li><strong>切换页面没反应？</strong>正在编译该路由，会有「正在切换到：xxx」提示，请稍候。</li>
            <li><strong>新建会话后侧栏没出现？</strong>草稿模式下不创建空会话，发送首条消息后才真正创建。</li>
            <li><strong>AI 回复失败？</strong>检查后端 8080 是否运行、AI 网关环境变量是否配置。</li>
            <li><strong>登录后看不到菜单？</strong>账号可能未分配角色，请联系管理员在权限后台配置。</li>
          </ul>
        </Typography>
      ),
    },
  ];

  const anchorItems = sections.map((section) => ({
    key: section.id,
    href: `#help-${section.id}`,
    title: (
      <Space size={6}>
        {section.icon}
        <span>{section.title}</span>
      </Space>
    ),
  }));

  return (
    <Drawer
      title="帮助文档"
      placement="right"
      width={680}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      <div className="oa-help-drawer">
        <aside className="oa-help-anchor">
          <Anchor
            items={anchorItems}
            offsetTop={16}
            affix={false}
            getContainer={() => document.querySelector('.ant-drawer-body') as HTMLElement}
          />
        </aside>
        <article className="oa-help-content">
          {sections.map((section) => (
            <section key={section.id} id={`help-${section.id}`} className="oa-help-section">
              <h2 className="oa-help-section-title">
                <Space size={8}>
                  {section.icon}
                  <span>{section.title}</span>
                </Space>
              </h2>
              <div className="oa-help-section-body">{section.body}</div>
            </section>
          ))}
          <section className="oa-help-footer">
            <Typography.Paragraph type="secondary">
              仍有疑问？可以让 AI 帮你进一步解释当前页面能力。
            </Typography.Paragraph>
            <Button
              type="primary"
              icon={<RobotOutlined />}
              onClick={() => {
                onClose();
                onOpenAi('请根据当前 OA 工作台的能力，为我详细介绍我能做什么以及如何使用');
              }}
            >
              询问 AI 助手
            </Button>
          </section>
        </article>
      </div>
    </Drawer>
  );
}

function Kbd({ children }: { children: React.ReactNode }) {
  return <kbd className="oa-help-kbd">{children}</kbd>;
}

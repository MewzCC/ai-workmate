'use client';

import {
  BellOutlined,
  FileTextOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SkinOutlined,
} from '@ant-design/icons';
import { Breadcrumb, Button, Layout, Select, Space, Tag, message, notification } from 'antd';
import type { OaRole } from '@/types/oa';
import { roleOptions } from '@/mock/oaPermissions';

const { Header } = Layout;

interface TopbarProps {
  role: OaRole;
  pageTitle: string;
  onRoleChange: (role: OaRole) => void;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
}

export default function Topbar({ role, pageTitle, onRoleChange, onOpenAppearance, onOpenAi }: TopbarProps) {
  return (
    <Header className="oa-header">
      <div>
        <Breadcrumb items={[{ title: 'AI WorkMate' }, { title: pageTitle }]} />
        <h1>{pageTitle}</h1>
      </div>

      <Space wrap>
        <Select
          value={role}
          options={roleOptions}
          onChange={onRoleChange}
          className="oa-role-select"
          aria-label="角色切换"
        />
        <Tag color="processing">安全联调</Tag>
        <Button icon={<QuestionCircleOutlined />} onClick={() => message.info('已打开帮助文档：当前为 OA 工作台基础能力说明')}>
          帮助文档
        </Button>
        <Button
          icon={<BellOutlined />}
          onClick={() => notification.info({ message: '通知中心', description: '你有 3 条审批提醒、1 条接口告警待处理。' })}
        >
          通知
        </Button>
        <Button icon={<SkinOutlined />} onClick={onOpenAppearance}>
          外观设置
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => onOpenAi('帮我新建一个跨部门采购申请，并检查审批链是否完整')}
        >
          新建流程
        </Button>
        <Button icon={<FileTextOutlined />} onClick={() => message.warning('真实导出能力尚未接入')}>
          导出看板
        </Button>
      </Space>
    </Header>
  );
}

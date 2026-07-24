'use client';

import {
  BellOutlined,
  FileTextOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SkinOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Breadcrumb, Button, Layout, Space, message, notification } from 'antd';
import type { OaRole } from '@/types/oa';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import ProfileSettingsModal from '@/components/profile/ProfileSettingsModal';

const { Header } = Layout;

interface TopbarProps {
  role: OaRole;
  pageTitle: string;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
}

export default function Topbar({ role, pageTitle, onOpenAppearance, onOpenAi }: TopbarProps) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileOpen, setProfileOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    router.replace('/auth');
  };
  return (
    <Header className="oa-header">
      <div>
        <Breadcrumb items={[{ title: 'AI WorkMate' }, { title: pageTitle }]} />
        <h1>{pageTitle}</h1>
      </div>

      <Space wrap>
        <Button
          type="text"
          className="oa-profile-trigger"
          icon={<Avatar size={26} src={user?.avatarUrl} icon={<UserOutlined />} />}
          onClick={() => setProfileOpen(true)}
        >
          {user?.name || role}
        </Button>
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
        <Button icon={<LogoutOutlined />} onClick={() => void handleLogout()}>退出</Button>
      </Space>
      <ProfileSettingsModal open={profileOpen} onClose={() => setProfileOpen(false)} />
    </Header>
  );
}

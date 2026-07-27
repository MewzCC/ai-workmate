'use client';

import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Button, Card, Space, Tag } from 'antd';
import { OaIcon } from '@/components/OaIcon';

interface AiMiniPanelProps {
  onOpenAi: (prompt?: string) => void;
}

export default function AiMiniPanel({ onOpenAi }: AiMiniPanelProps) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  if (!mounted) return null;
  return createPortal(
    <Card
      className="oa-ai-mini"
      size="small"
      style={{
        position: 'fixed',
        right: 24,
        bottom: 96,
        width: 230,
        zIndex: 1050,
      }}
    >
      <Space direction="vertical" size={8}>
        <Tag color="purple">AI 快捷卡片</Tag>
        <strong>需要我接手当前流程吗？</strong>
        <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi('帮我总结当前工作台的风险和下一步动作')}>
          生成建议
        </Button>
      </Space>
    </Card>,
    document.body
  );
}

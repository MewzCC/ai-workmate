'use client';

import { Button, Card, Space, Tag } from 'antd';
import { RobotOutlined } from '@ant-design/icons';

interface AiMiniPanelProps {
  onOpenAi: (prompt?: string) => void;
}

export default function AiMiniPanel({ onOpenAi }: AiMiniPanelProps) {
  return (
    <Card className="oa-ai-mini" size="small">
      <Space direction="vertical" size={8}>
        <Tag color="purple">AI 快捷卡片</Tag>
        <strong>需要我接手当前流程吗？</strong>
        <Button type="primary" icon={<RobotOutlined />} onClick={() => onOpenAi('帮我总结当前工作台的风险和下一步动作')}>
          生成建议
        </Button>
      </Space>
    </Card>
  );
}

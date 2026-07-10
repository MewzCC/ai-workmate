'use client';

import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import {
  AuditOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  FileSearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { approvalRecords, oaMetrics, quickEntries, timelineSeed } from '@/mock/oaDashboard';
import { can } from '@/mock/oaPermissions';
import type { ApprovalRecord, OaRole } from '@/types/oa';
import EChartsCard from './EChartsCard';
import PermissionButton from './PermissionButton';

interface DashboardProps {
  role: OaRole;
  pageId: string;
  pageTitle: string;
  primaryColor: string;
  auditItems: Array<{ color: string; children: string }>;
  onOpenAi: (prompt?: string) => void;
  onAddAudit: (text: string) => void;
}

const statusText: Record<ApprovalRecord['status'], string> = {
  warning: '即将超时',
  processing: '待审批',
  success: '低风险',
  error: '资料缺失',
  default: '待补充',
};

const tagColor: Record<ApprovalRecord['status'], string> = {
  warning: 'warning',
  processing: 'processing',
  success: 'success',
  error: 'error',
  default: 'default',
};

export default function Dashboard({ role, pageId, pageTitle, primaryColor, auditItems, onOpenAi, onAddAudit }: DashboardProps) {
  const [query, setQuery] = useState('');
  const [form] = Form.useForm();

  const filteredRecords = approvalRecords.filter((record) => {
    if (!query.trim()) return true;
    return [record.name, record.applicant, record.department, record.node].some((value) => value.includes(query.trim()));
  });

  const chartOptions = useMemo(() => createChartOptions(primaryColor), [primaryColor]);

  const handleAction = (action: string, record: ApprovalRecord) => {
    const approveAction = ['处理', '预审', '通过', '退回', '催办'].includes(action);
    if (role === 'employee' && approveAction) {
      message.warning('当前角色无权限执行审批类操作');
      return;
    }

    if (['处理', '预审', '通过'].includes(action)) {
      onOpenAi(`帮我${action}${record.name}，并检查节点 ${record.node} 的风险`);
      return;
    }

    message.success(`${action}：${record.name}`);
    onAddAudit(`${action} ${record.id}`);
  };

  const columns: ColumnsType<ApprovalRecord> = [
    { title: '流程名称', dataIndex: 'name', key: 'name', ellipsis: true },
    { title: '发起人', dataIndex: 'applicant', key: 'applicant', width: 100 },
    { title: '部门', dataIndex: 'department', key: 'department', width: 120 },
    { title: '当前节点', dataIndex: 'node', key: 'node', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: ApprovalRecord['status']) => <Tag color={tagColor[status]}>{statusText[status]}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 330,
      render: (_, record) => (
        <Space size={4} wrap>
          {['处理', '查看', '预审', '通过', '退回', '催办'].map((action) => (
            <Button
              key={action}
              size="small"
              type={action === '处理' ? 'primary' : 'default'}
              onClick={() => handleAction(action, record)}
            >
              {action}
            </Button>
          ))}
        </Space>
      ),
    },
  ];

  if (pageId !== 'dashboard') {
    return (
      <Card className="oa-card oa-placeholder-card">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={`${pageTitle} 业务页面暂未展开，当前已完成菜单权限、标题切换和 AI 操作入口。`}
        />
        <Space>
          <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => onOpenAi(`帮我分析 ${pageTitle} 页面当前可以自动化的操作`)}>
            让 AI 分析本页
          </Button>
          <Button onClick={() => message.info('已记录页面访问审计')}>记录访问</Button>
        </Space>
      </Card>
    );
  }

  return (
    <div className="oa-dashboard">
      <section className="oa-page-title">
        <div>
          <Typography.Text type="secondary">Enterprise OA Workspace</Typography.Text>
          <Typography.Title level={2}>企业运营总览</Typography.Title>
          <Typography.Paragraph>
            企业级 OA 工作台，支持审批、财务、人事、资产、联调和 AI 操作。当前数据为前端 mock，AI 计划和执行走后端 mock 接口。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <PermissionButton role={role} menuId="dashboard" action="export" icon={<BarChartOutlined />} onClick={() => message.success('已生成看板导出任务')}>
            导出看板
          </PermissionButton>
          <Button icon={<AuditOutlined />} onClick={() => message.info('指标配置面板将在下一阶段接入')}>
            配置指标
          </Button>
          <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => onOpenAi('帮我预审当前列表，并输出风险排序')}>
            让 AI 预审
          </Button>
        </Space>
      </section>

      <Row gutter={[16, 16]}>
        {oaMetrics.map((metric) => (
          <Col xs={24} sm={12} lg={6} key={metric.title}>
            <Card className="oa-card oa-stat-card">
              <Statistic title={metric.title} value={metric.value} suffix={metric.suffix} valueStyle={{ color: primaryColor }} />
              <Tag color="blue">{metric.trend}</Tag>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        {quickEntries.map((entry) => (
          <Col xs={24} md={12} xl={6} key={entry.title}>
            <Card
              className="oa-card oa-quick-card"
              hoverable
              onClick={() => onOpenAi(entry.prompt)}
              actions={[
                <Button key="start" type="link" icon={<ThunderboltOutlined />} onClick={(event) => {
                  event.stopPropagation();
                  onOpenAi(entry.prompt);
                }}>
                  AI 新建任务
                </Button>,
              ]}
            >
              <Card.Meta title={entry.title} description={entry.description} />
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card
            className="oa-card"
            title="审批列表"
            extra={
              <Form form={form} layout="inline" onFinish={(values) => setQuery(values.keyword || '')}>
                <Form.Item name="keyword">
                  <Input.Search placeholder="查询流程、发起人、部门" allowClear onSearch={(value) => setQuery(value)} />
                </Form.Item>
                <Form.Item>
                  <Button htmlType="submit" icon={<FileSearchOutlined />}>查询</Button>
                </Form.Item>
              </Form>
            }
          >
            <Table rowKey="id" columns={columns} dataSource={filteredRecords} pagination={{ pageSize: 5 }} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card className="oa-card" title="AI 执行与审计时间线">
            <Timeline items={[...auditItems, ...timelineSeed]} />
            {!can(role, 'dashboard', 'ai_execute') && (
              <Alert type="warning" showIcon message="当前角色 AI 执行能力受限，只允许查看和提交本人任务。" />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
          <EChartsCard title="流程趋势" option={chartOptions.line} />
        </Col>
        <Col xs={24} xl={8}>
          <EChartsCard title="模块分布" option={chartOptions.pie} />
        </Col>
        <Col xs={24} xl={8}>
          <EChartsCard title="系统健康度" option={chartOptions.gauge} />
        </Col>
      </Row>

      <Card className="oa-card">
        <Descriptions
          title="当前联调状态"
          bordered
          column={{ xs: 1, md: 3 }}
          items={[
            { key: 'backend', label: '后端接口', children: 'System / AI Tasks Mock' },
            { key: 'charts', label: '图表引擎', children: 'ECharts' },
            { key: 'permissions', label: '权限模型', children: '前端 RBAC Mock' },
          ]}
        />
        <Progress percent={86} strokeColor={primaryColor} className="oa-health-progress" />
      </Card>
    </div>
  );
}

function createChartOptions(primaryColor: string): Record<'line' | 'pie' | 'gauge', EChartsOption> {
  return {
    line: {
      color: [primaryColor],
      tooltip: { trigger: 'axis' },
      grid: { left: 32, right: 20, top: 28, bottom: 28 },
      xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六'] },
      yAxis: { type: 'value' },
      series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.12 }, data: [42, 56, 48, 72, 69, 88] }],
    },
    pie: {
      color: [primaryColor, '#2fb344', '#f59f00', '#e03131', '#7048e8'],
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['48%', '72%'],
          data: [
            { name: '流程审批', value: 36 },
            { name: '财务合同', value: 22 },
            { name: '组织人事', value: 18 },
            { name: '行政资产', value: 14 },
            { name: '平台联调', value: 10 },
          ],
        },
      ],
    },
    gauge: {
      color: [primaryColor],
      series: [
        {
          type: 'gauge',
          progress: { show: true, width: 12 },
          axisLine: { lineStyle: { width: 12 } },
          pointer: { show: false },
          detail: { valueAnimation: true, formatter: '{value}%' },
          data: [{ value: 92, name: '健康度' }],
        },
      ],
    },
  };
}

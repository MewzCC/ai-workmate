'use client';

import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Dropdown,
  Empty,
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
import { approvalRecords, oaMetrics, quickEntries, timelineSeed } from '@/mock/oaDashboard';
import { can } from '@/mock/oaPermissions';
import type { ApprovalRecord, OaRole } from '@/types/oa';
import EChartsCard from './EChartsCard';
import PermissionButton from './PermissionButton';
import { OaIcon } from '@/components/OaIcon';

interface DashboardProps {
  role: OaRole;
  pageId: string;
  pageTitle: string;
  primaryColor: string;
  auditItems: Array<{ color: string; content: string }>;
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
    { title: '流程名称', dataIndex: 'name', key: 'name', ellipsis: true, minWidth: 160 },
    { title: '发起人', dataIndex: 'applicant', key: 'applicant', width: 100 },
    { title: '部门', dataIndex: 'department', key: 'department', width: 120, responsive: ['md'] },
    { title: '当前节点', dataIndex: 'node', key: 'node', width: 120, responsive: ['lg'] },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: ApprovalRecord['status']) => <Tag color={tagColor[status]}>{statusText[status]}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" type="primary" onClick={() => handleAction('处理', record)}>处理</Button>
          <Button size="small" onClick={() => handleAction('查看', record)}>查看</Button>
          <Dropdown
            menu={{
              items: [
                { key: '预审', label: '预审' },
                { key: '通过', label: '通过' },
                { key: '退回', label: '退回' },
                { key: '催办', label: '催办' },
              ],
              onClick: ({ key: action }) => handleAction(action, record),
            }}
            trigger={['click']}
          >
            <Button size="small" icon={<OaIcon name="more" />} aria-label="更多操作" />
          </Dropdown>
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
          <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi(`帮我分析 ${pageTitle} 页面当前可以自动化的操作`)}>
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
            企业级 OA 工作台，支持审批、财务、人事、资产、联调和 AI 操作。看板当前为演示数据，AI 计划和执行仅调用后端真实能力。
          </Typography.Paragraph>
        </div>
        <Space className="oa-page-title-actions" wrap={false}>
          <PermissionButton role={role} menuId="dashboard" action="export" icon={<OaIcon name="export" />} onClick={() => message.warning('真实导出能力尚未接入')}>
            导出看板
          </PermissionButton>
          <Button icon={<OaIcon name="audit" />} onClick={() => message.info('指标配置面板将在下一阶段接入')}>
            配置指标
          </Button>
          <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi('帮我预审当前列表，并输出风险排序')}>
            让 AI 预审
          </Button>
        </Space>
      </section>

      <Row gutter={[16, 16]}>
        {oaMetrics.map((metric) => (
          <Col xs={12} sm={12} md={6} key={metric.title}>
            <Card className="oa-card oa-stat-card">
              <Statistic title={metric.title} value={metric.value} suffix={metric.suffix} styles={{ content: { color: primaryColor } }} />
              <Tag color="blue">{metric.trend}</Tag>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        {quickEntries.map((entry) => (
          <Col xs={24} sm={12} xl={6} key={entry.title}>
            <Card
              className="oa-card oa-quick-card"
              hoverable
              onClick={() => onOpenAi(entry.prompt)}
              actions={[
                <Button key="start" type="link" icon={<OaIcon name="ai" />} onClick={(event) => {
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
        <Col xs={24}>
          <Card
            className="oa-card"
            title="审批列表"
            extra={
              <Input.Search
                placeholder="查询流程、发起人、部门"
                allowClear
                onSearch={(value) => setQuery(value)}
                style={{ maxWidth: 260 }}
                prefix={<OaIcon name="search" />}
              />
            }
          >
            <Table
              rowKey="id"
              columns={columns}
              dataSource={filteredRecords}
              pagination={{ pageSize: 5 }}
              scroll={{ x: 'max-content' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card className="oa-card" title="AI 执行与审计时间线">
            <Timeline items={[...auditItems, ...timelineSeed]} />
            {!can(role, 'dashboard', 'ai_execute') && (
              <Alert type="warning" showIcon title="当前角色 AI 执行能力受限，只允许查看和提交本人任务。" />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <EChartsCard title="流程趋势" option={chartOptions.line} />
        </Col>
        <Col xs={24} lg={8}>
          <EChartsCard title="模块分布" option={chartOptions.pie} />
        </Col>
        <Col xs={24} lg={8}>
          <EChartsCard title="系统健康度" option={chartOptions.gauge} />
        </Col>
      </Row>

      <Card className="oa-card">
        <Descriptions
          title="当前联调状态"
          bordered
          column={{ xs: 1, md: 3 }}
          items={[
            { key: 'backend', label: '后端接口', children: 'System / AI Tasks（JWT）' },
            { key: 'charts', label: '图表引擎', children: 'ECharts' },
            { key: 'permissions', label: '权限模型', children: '前端权限演示，后端鉴权优先' },
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
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderColor: 'transparent',
        textStyle: { color: '#fff', fontSize: 12 },
      },
      grid: { left: 36, right: 16, top: 24, bottom: 28 },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六'],
        axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.4)' } },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.16)', type: 'dashed' } },
      },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { width: 3 },
        itemStyle: { borderWidth: 2, borderColor: '#fff' },
        areaStyle: {
          opacity: 0.18,
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: primaryColor },
              { offset: 1, color: 'rgba(255, 255, 255, 0)' },
            ],
          },
        },
        emphasis: { focus: 'series' },
        data: [42, 56, 48, 72, 69, 88],
      }],
    },
    pie: {
      color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderColor: 'transparent',
        textStyle: { color: '#fff', fontSize: 12 },
        formatter: '{b}: {c} ({d}%)',
      },
      legend: {
        bottom: 4,
        left: 'center',
        type: 'scroll',
        itemWidth: 8,
        itemHeight: 8,
        itemGap: 12,
        textStyle: { fontSize: 11, color: '#64748b' },
      },
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: '34%',
          style: {
            text: '100',
            fontSize: 22,
            fontWeight: 'bold',
            fill: '#0f172a',
          },
        },
        {
          type: 'text',
          left: 'center',
          top: '46%',
          style: {
            text: '总模块',
            fontSize: 11,
            fill: '#94a3b8',
          },
        },
      ],
      series: [
        {
          type: 'pie',
          radius: ['44%', '66%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderColor: '#fff',
            borderWidth: 3,
            borderRadius: 6,
          },
          label: { show: false },
          labelLine: { show: false },
          emphasis: {
            scale: true,
            scaleSize: 8,
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(15, 23, 42, 0.24)' },
          },
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
      series: [
        {
          type: 'gauge',
          radius: '88%',
          center: ['50%', '58%'],
          startAngle: 200,
          endAngle: -20,
          progress: {
            show: true,
            width: 16,
            roundCap: true,
            itemStyle: {
              color: {
                type: 'linear',
                x: 0, y: 0, x2: 1, y2: 0,
                colorStops: [
                  { offset: 0, color: '#10b981' },
                  { offset: 0.5, color: '#34d399' },
                  { offset: 1, color: primaryColor },
                ],
              },
            },
          },
          axisLine: {
            lineStyle: {
              width: 16,
              color: [[1, 'rgba(148, 163, 184, 0.18)']],
            },
          },
          pointer: { show: false },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          anchor: { show: false },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            fontSize: 26,
            fontWeight: 'bold',
            color: '#0f172a',
            offsetCenter: [0, '8%'],
          },
          title: {
            show: true,
            offsetCenter: [0, '38%'],
            color: '#94a3b8',
            fontSize: 12,
          },
          data: [{ value: 92, name: '系统运行良好' }],
        },
      ],
    },
  };
}

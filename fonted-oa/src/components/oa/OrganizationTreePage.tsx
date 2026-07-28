'use client';

import { useEffect, useMemo, useState, Suspense, lazy } from 'react';
import {
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { hrApi, type HrDepartment, type HrEmployee, type OrganizationOverview } from '@/lib/hrApi';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';

const OrganizationGraph = lazy(() => import('./OrganizationGraph'));

export interface DepartmentNode extends HrDepartment {
  children: DepartmentNode[];
  employeeCount: number;
}

export default function OrganizationTreePage() {
  const [overview, setOverview] = useState<OrganizationOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | undefined>();
  const [keyword, setKeyword] = useState('');
  // 用于强制重新触发架构图入场动画（刷新/重置时变更）
  const [animKey, setAnimKey] = useState(0);

  const load = async () => {
    setLoading(true);
    try {
      const data = await hrApi.overview();
      setOverview(data);
      setAnimKey((k) => k + 1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '组织架构加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const departmentMap = useMemo(() => {
    const map = new Map<number, HrDepartment>();
    overview?.departments.forEach((dept) => map.set(dept.id, dept));
    return map;
  }, [overview?.departments]);

  const positionMap = useMemo(() => {
    const map = new Map<number, string>();
    overview?.positions.forEach((pos) => map.set(pos.id, pos.name));
    return map;
  }, [overview?.positions]);

  const departmentTree = useMemo<DepartmentNode[]>(() => {
    if (!overview) return [];
    const countByDept = new Map<number, number>();
    overview.employees.forEach((emp) => {
      if (emp.departmentId) {
        countByDept.set(emp.departmentId, (countByDept.get(emp.departmentId) || 0) + 1);
      }
    });
    const buildNode = (dept: HrDepartment): DepartmentNode => ({
      ...dept,
      children: overview.departments
        .filter((child) => child.parentId === dept.id)
        .map(buildNode),
      employeeCount: countByDept.get(dept.id) || 0,
    });
    return overview.departments
      .filter((dept) => !dept.parentId || !departmentMap.has(dept.parentId))
      .map(buildNode);
  }, [overview, departmentMap]);

  const stats = useMemo(() => {
    if (!overview) return { departments: 0, employees: 0, active: 0, positions: 0 };
    return {
      departments: overview.departments.length,
      employees: overview.employees.length,
      active: overview.employees.filter((e) => e.status === 1).length,
      positions: overview.positions.length,
    };
  }, [overview]);

  const filteredEmployees = useMemo(() => {
    if (!overview) return [];
    let list = overview.employees;
    if (selectedDepartmentId !== undefined) {
      const deptIds = collectDepartmentIds(departmentTree, selectedDepartmentId);
      list = list.filter((emp) => emp.departmentId && deptIds.has(emp.departmentId));
    }
    if (keyword.trim()) {
      const kw = keyword.trim().toLowerCase();
      list = list.filter(
        (emp) => emp.name.toLowerCase().includes(kw) || emp.email.toLowerCase().includes(kw),
      );
    }
    return list;
  }, [overview, selectedDepartmentId, keyword, departmentTree]);

  const columns: ColumnsType<HrEmployee> = [
    {
      title: '员工',
      key: 'employee',
      align: 'center',
      render: (_, emp) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{emp.name}</Typography.Text>
          <Typography.Text type="secondary">{emp.email}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '部门',
      dataIndex: 'departmentId',
      align: 'center',
      render: (deptId: number | undefined) =>
        deptId ? departmentMap.get(deptId)?.name || '-' : '-',
    },
    {
      title: '岗位',
      dataIndex: 'positionId',
      align: 'center',
      render: (posId: number | undefined) =>
        posId ? positionMap.get(posId) || '-' : '-',
    },
    {
      title: '直属审批人',
      dataIndex: 'approverName',
      align: 'center',
      render: (name?: string) => name || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      align: 'center',
      width: 90,
      render: (status: number) => (
        <Badge status={status === 1 ? 'success' : 'default'} text={status === 1 ? '在岗' : '停用'} />
      ),
    },
  ];

  return (
    <section className="oa-org-page">
      <header className="oa-domain-heading">
        <div>
          <Typography.Title level={3}>组织架构</Typography.Title>
          <Typography.Paragraph type="secondary">
            直观展示部门层级关系与员工分布，点击架构图中的部门卡片可筛选下方员工列表。
          </Typography.Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
          刷新
        </Button>
      </header>

      <Spin spinning={loading}>
        <Row gutter={[16, 16]} className="oa-org-stats">
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="部门总数" value={stats.departments} prefix={<OaIcon name="organization" />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="员工总数" value={stats.employees} prefix={<OaIcon name="user" />} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="在岗人数" value={stats.active} valueStyle={{ color: '#52c41a' }} />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card>
              <Statistic title="岗位类型" value={stats.positions} />
            </Card>
          </Col>
        </Row>

        <Card className="oa-org-tree-card" title="部门架构图" extra={
          <Typography.Text type="secondary" className="oa-org-tree-hint">
            Ctrl + 滚轮缩放 · 拖拽移动 · 点击节点筛选员工 · 共 {stats.departments} 个部门
          </Typography.Text>
        }>
          {departmentTree.length === 0 ? (
            <Empty description="暂无部门数据" />
          ) : (
            <Suspense fallback={<Spin tip="加载架构图..." />}>
              <OrganizationGraph
                data={departmentTree}
                selectedId={selectedDepartmentId}
                onSelect={setSelectedDepartmentId}
                animKey={animKey}
              />
            </Suspense>
          )}
        </Card>

        <Card className="oa-org-employees-card">
          <div className="oa-access-toolbar">
            <Space wrap>
              <Select
                allowClear
                placeholder="按部门筛选"
                style={{ width: 200 }}
                value={selectedDepartmentId}
                options={overview?.departments.map((dept) => ({ value: dept.id, label: dept.name })) || []}
                onChange={(value) => setSelectedDepartmentId(value)}
              />
              <Input
                allowClear
                placeholder="搜索姓名或邮箱"
                style={{ width: 240 }}
                prefix={<SearchOutlined />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
              />
              {selectedDepartmentId !== undefined && (
                <Button type="link" onClick={() => setSelectedDepartmentId(undefined)}>
                  清除部门筛选
                </Button>
              )}
            </Space>
            <Tag variant="filled">共 {filteredEmployees.length} 人</Tag>
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={filteredEmployees}
            size="middle"
            pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
          />
        </Card>
      </Spin>
    </section>
  );
}

function collectDepartmentIds(roots: DepartmentNode[], targetId: number): Set<number> {
  const ids = new Set<number>();
  const find = (nodes: DepartmentNode[]): DepartmentNode | null => {
    for (const node of nodes) {
      if (node.id === targetId) return node;
      const found = find(node.children);
      if (found) return found;
    }
    return null;
  };
  const target = find(roots);
  if (!target) return ids;
  const collect = (node: DepartmentNode) => {
    ids.add(node.id);
    node.children.forEach(collect);
  };
  collect(target);
  return ids;
}

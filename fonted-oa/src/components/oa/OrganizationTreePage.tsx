'use client';

import { useEffect, useMemo, useState, Suspense, lazy } from 'react';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Empty,
  Input,
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
import { useRouter } from '@/lib/nextCompat';
import { useTranslation } from 'react-i18next';

const OrganizationGraph = lazy(() => import('./OrganizationGraph'));

export interface DepartmentNode extends HrDepartment {
  children: DepartmentNode[];
  employeeCount: number;
}

export default function OrganizationTreePage() {
  const { t } = useTranslation();
  const router = useRouter();
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
      message.error(error instanceof Error ? error.message : t('organization.loadFailed'));
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
      title: t('organization.column.employee'),
      key: 'employee',
      width: 240,
      render: (_, emp) => (
        <div className="oa-access-user-cell">
          <Avatar size="small" src={emp.avatarUrl || undefined}>{emp.name.slice(0, 1).toUpperCase()}</Avatar>
          <div className="oa-access-user-cell__info">
            <Typography.Text strong className="oa-access-user-cell__name">{emp.name}</Typography.Text>
            <Typography.Text type="secondary" className="oa-access-user-cell__email">{emp.email}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: t('organization.column.department'),
      dataIndex: 'departmentId',
      align: 'center',
      render: (deptId: number | undefined) =>
        deptId ? departmentMap.get(deptId)?.name || '-' : '-',
    },
    {
      title: t('organization.column.position'),
      dataIndex: 'positionId',
      align: 'center',
      render: (posId: number | undefined) =>
        posId ? positionMap.get(posId) || '-' : '-',
    },
    {
      title: t('organization.column.approver'),
      dataIndex: 'approverName',
      align: 'center',
      width: 160,
      render: (name: string | undefined, emp) => {
        if (!name) return '-';
        const initial = name.slice(0, 1).toUpperCase();
        return (
          <div className="oa-access-user-cell">
            <Avatar size="small" src={emp.approverAvatarUrl || undefined}>{initial}</Avatar>
            <div className="oa-access-user-cell__info">
              <Typography.Text strong className="oa-access-user-cell__name">{name}</Typography.Text>
            </div>
          </div>
        );
      },
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      align: 'center',
      width: 90,
      render: (status: number) => (
        <Badge status={status === 1 ? 'success' : 'default'} text={status === 1 ? t('organization.status.active') : t('organization.status.inactive')} />
      ),
    },
  ];

  return (
    <section className="oa-org-page">
      <header className="oa-org-heading">
        <div className="oa-org-heading__identity">
          <span className="oa-org-heading__icon" aria-hidden="true">
            <OaIcon name="organization" size={22} />
          </span>
          <div>
            <Typography.Text className="oa-org-heading__eyebrow">
              {t('organization.eyebrow')}
            </Typography.Text>
            <Typography.Title level={3}>{t('organization.title')}</Typography.Title>
            <Typography.Paragraph type="secondary">
              {t('organization.description')}
            </Typography.Paragraph>
          </div>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
          {t('common.refresh')}
        </Button>
      </header>

      <Spin spinning={loading} wrapperClassName="oa-org-loading">
        <div className="oa-org-workspace">
          <div className="oa-org-stats" aria-label={t('organization.stats.summary')}>
            <Card>
              <Statistic title={t('organization.stats.departments')} value={stats.departments} prefix={<OaIcon name="organization" />} />
            </Card>
            <Card>
              <Statistic title={t('organization.stats.employees')} value={stats.employees} prefix={<OaIcon name="user" />} />
            </Card>
            <Card className="oa-org-stat-active">
              <Statistic title={t('organization.stats.active')} value={stats.active} prefix={<Badge status="success" />} />
            </Card>
            <Card>
              <Statistic title={t('organization.stats.positions')} value={stats.positions} prefix={<OaIcon name="employee-files" />} />
            </Card>
          </div>

          <Card
            className="oa-org-tree-card"
            title={(
              <div className="oa-org-section-title">
                <span className="oa-org-section-title__icon"><OaIcon name="organization" /></span>
                <span>
                  <strong>{t('organization.graph.cardTitle')}</strong>
                  <small>{t('organization.graph.description')}</small>
                </span>
              </div>
            )}
            extra={
              <Typography.Text type="secondary" className="oa-org-tree-hint">
                {t('organization.graph.hint', { count: stats.departments })}
              </Typography.Text>
            }
          >
            {departmentTree.length === 0 ? (
              <Empty description={t('organization.graph.empty')} />
            ) : (
              <Suspense fallback={<Spin tip={t('organization.graph.loading')} />}>
                <OrganizationGraph
                  data={departmentTree}
                  selectedId={selectedDepartmentId}
                  onSelect={setSelectedDepartmentId}
                  animKey={animKey}
                />
              </Suspense>
            )}
          </Card>

          <Card
            className="oa-org-employees-card"
            title={(
              <div className="oa-org-section-title">
                <span className="oa-org-section-title__icon"><OaIcon name="user" /></span>
                <span>
                  <strong>{t('organization.members.title')}</strong>
                  <small>{t('organization.members.description')}</small>
                </span>
              </div>
            )}
            extra={<Tag variant="filled">{t('organization.filter.totalEmployees', { count: filteredEmployees.length })}</Tag>}
          >
            <div className="oa-access-toolbar oa-org-member-toolbar">
              <Space wrap>
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder={t('organization.filter.departmentPlaceholder')}
                  className="oa-org-department-filter"
                  value={selectedDepartmentId}
                  options={overview?.departments.map((dept) => ({ value: dept.id, label: dept.name })) || []}
                  onChange={(value) => setSelectedDepartmentId(value)}
                />
                <Input
                  allowClear
                  placeholder={t('organization.filter.searchPlaceholder')}
                  className="oa-org-employee-search"
                  prefix={<SearchOutlined />}
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                />
                {selectedDepartmentId !== undefined && (
                  <Button type="link" onClick={() => setSelectedDepartmentId(undefined)}>
                    {t('organization.filter.clearDepartment')}
                  </Button>
                )}
              </Space>
            </div>
            <Table
              rowKey="id"
              rowClassName="oa-org-employee-clickable"
              columns={columns}
              dataSource={filteredEmployees}
              size="middle"
              scroll={{ x: 880 }}
              pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
              locale={{ emptyText: <Empty description={t('organization.members.empty')} /> }}
              onRow={(emp) => ({
                onClick: () => router.push(`/oa/employee-files?id=${emp.id}`),
              })}
            />
          </Card>
        </div>
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

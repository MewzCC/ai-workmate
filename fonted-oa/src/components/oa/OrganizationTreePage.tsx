'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Tag,
  Tree,
  Typography,
  message,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { OaIcon } from '@/components/OaIcon';
import { useAuth } from '@/components/auth/AuthProvider';
import {
  organizationApi,
  type OrganizationDepartment,
  type OrganizationMember,
  type OrganizationOverview,
} from '@/lib/organizationApi';

type EditorKind = 'department' | 'position' | 'member';

export default function OrganizationTreePage() {
  const { user } = useAuth();
  const [data, setData] = useState<OrganizationOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState<'chart' | 'directory'>('chart');
  const [keyword, setKeyword] = useState('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number>();
  const [editor, setEditor] = useState<EditorKind>();
  const [editingMember, setEditingMember] = useState<OrganizationMember>();
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const next = await organizationApi.overview();
      setData(next);
      setSelectedDepartmentId((current) =>
        current && next.departments.some((item) => item.id === current)
          ? current
          : next.departments.find((item) => !item.parentId)?.id || next.departments[0]?.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '组织架构加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const canManage = Boolean(
    data?.canManage
    && (user?.permissions.includes('org:manage') || user?.roles.includes('SUPER_ADMIN')),
  );
  const departmentMap = useMemo(
    () => new Map((data?.departments || []).map((item) => [item.id, item])),
    [data?.departments],
  );
  const positionMap = useMemo(
    () => new Map((data?.positions || []).map((item) => [item.id, item])),
    [data?.positions],
  );
  const visibleMembers = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    if (!query) return data?.members || [];
    return (data?.members || []).filter((member) => {
      const department = departmentMap.get(member.departmentId)?.name || '';
      const position = positionMap.get(member.positionId)?.name || '';
      return `${member.name} ${department} ${position}`.toLowerCase().includes(query);
    });
  }, [data?.members, departmentMap, keyword, positionMap]);
  const treeData = useMemo(
    () => buildDepartmentTree(data?.departments || [], visibleMembers),
    [data?.departments, visibleMembers],
  );
  const selectedDepartment = selectedDepartmentId
    ? departmentMap.get(selectedDepartmentId)
    : undefined;
  const selectedMembers = visibleMembers.filter(
    (member) => member.departmentId === selectedDepartmentId,
  );

  const openDepartment = (department?: OrganizationDepartment, parentId?: number) => {
    form.resetFields();
    form.setFieldsValue(department ? {
      ...department,
      parentId: department.parentId || undefined,
    } : { parentId });
    setEditor('department');
  };

  const openPosition = () => {
    form.resetFields();
    setEditor('position');
  };

  const openMember = (member: OrganizationMember) => {
    setEditingMember(member);
    form.setFieldsValue({
      departmentId: member.departmentId,
      positionId: member.positionId,
      approverUserId: member.approverUserId,
    });
    setEditor('member');
  };

  const save = async () => {
    const values = await form.validateFields();
    try {
      if (editor === 'department') await organizationApi.saveDepartment(values);
      if (editor === 'position') await organizationApi.savePosition(values);
      if (editor === 'member' && editingMember) {
        await organizationApi.updateMember(editingMember.id, values);
      }
      setEditor(undefined);
      setEditingMember(undefined);
      form.resetFields();
      await load();
      message.success('组织配置已更新，审批链路将实时使用最新数据');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '组织配置保存失败');
    }
  };

  const removeDepartment = async (id: number) => {
    try {
      await organizationApi.deleteDepartment(id);
      await load();
      message.success('部门已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '部门删除失败');
    }
  };

  return (
    <section className="organization-page">
      <header className="organization-hero">
        <div className="organization-hero__copy">
          <span className="organization-eyebrow">PEOPLE · STRUCTURE · APPROVAL</span>
          <Typography.Title level={2}>组织架构与审批关系</Typography.Title>
          <Typography.Paragraph>
            以组织树为主线统一维护部门、岗位和直属审批人。配置变更会实时联动请假审批候选范围。
          </Typography.Paragraph>
        </div>
        <div className="organization-hero__actions">
          <Tag color={canManage ? 'blue' : 'default'}>
            {canManage ? '可视化编辑模式' : '只读浏览模式'}
          </Tag>
          {canManage && (
            <Space wrap>
              <Button icon={<OaIcon name="add" />} onClick={() => openPosition()}>新增岗位</Button>
              <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openDepartment()}>
                新增部门
              </Button>
            </Space>
          )}
        </div>
      </header>

      {!canManage && !loading && (
        <Alert
          className="organization-readonly-alert"
          type="info"
          showIcon
          message="当前为只读视图"
          description="普通成员可以查看组织关系；编辑按钮仅对被管理员授予“组织架构管理”权限的角色显示。"
        />
      )}

      <Spin spinning={loading}>
        {!data ? <Empty description="暂无组织数据" /> : (
          <>
            <Row gutter={[16, 16]} className="organization-stats">
              <Col xs={12} lg={6}><Card><Statistic title="组织单元" value={data.departments.length} /></Card></Col>
              <Col xs={12} lg={6}><Card><Statistic title="在册成员" value={data.members.length} /></Card></Col>
              <Col xs={12} lg={6}><Card><Statistic title="岗位序列" value={data.positions.length} /></Card></Col>
              <Col xs={12} lg={6}>
                <Card><Statistic title="已配置直属审批人"
                  value={data.members.filter((item) => item.approverUserId).length}
                  suffix={`/ ${data.members.length}`} /></Card>
              </Col>
            </Row>

            <div className="organization-toolbar">
              <Input
                allowClear
                prefix={<OaIcon name="search" />}
                placeholder="搜索姓名、部门或岗位"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <Segmented
                value={mode}
                onChange={(value) => setMode(value as typeof mode)}
                options={[
                  { value: 'chart', label: '组织图' },
                  { value: 'directory', label: '树形目录' },
                ]}
              />
            </div>

            <div className="organization-workspace">
              <aside className="organization-navigator">
                <div className="organization-panel-title">
                  <div><OaIcon name="organization" /><strong>组织导航</strong></div>
                  <Typography.Text type="secondary">{data.departments.length} 个部门</Typography.Text>
                </div>
                <Tree
                  blockNode
                  defaultExpandAll
                  selectedKeys={selectedDepartmentId ? [String(selectedDepartmentId)] : []}
                  treeData={treeData}
                  onSelect={(keys) => setSelectedDepartmentId(Number(keys[0]))}
                />
              </aside>

              <main className="organization-canvas">
                {mode === 'chart' ? (
                  <DepartmentChart
                    departments={data.departments}
                    members={visibleMembers}
                    selectedId={selectedDepartmentId}
                    onSelect={setSelectedDepartmentId}
                  />
                ) : (
                  <Tree
                    className="organization-directory-tree"
                    showLine
                    defaultExpandAll
                    treeData={treeData}
                    selectedKeys={selectedDepartmentId ? [String(selectedDepartmentId)] : []}
                    onSelect={(keys) => setSelectedDepartmentId(Number(keys[0]))}
                  />
                )}
              </main>

              <aside className="organization-inspector">
                {selectedDepartment ? (
                  <>
                    <div className="organization-panel-title">
                      <div>
                        <span className="organization-department-mark">
                          {selectedDepartment.name.slice(0, 1)}
                        </span>
                        <div>
                          <strong>{selectedDepartment.name}</strong>
                          <Typography.Text type="secondary">{selectedDepartment.code}</Typography.Text>
                        </div>
                      </div>
                    </div>
                    <div className="organization-approver-card">
                      <span>部门默认审批人</span>
                      <strong>
                        {data.members.find(
                          (member) => member.id === selectedDepartment.defaultApproverUserId,
                        )?.name || '尚未配置'}
                      </strong>
                    </div>
                    {canManage && (
                      <Space wrap>
                        <Button icon={<OaIcon name="edit" />}
                          onClick={() => openDepartment(selectedDepartment)}>编辑部门</Button>
                        <Button icon={<OaIcon name="add" />}
                          onClick={() => openDepartment(undefined, selectedDepartment.id)}>新增下级</Button>
                        <Popconfirm
                          title="确认删除该部门？"
                          description="仅无下级部门且无成员时允许删除。"
                          onConfirm={() => void removeDepartment(selectedDepartment.id)}
                        >
                          <Button danger icon={<OaIcon name="delete" />}>删除</Button>
                        </Popconfirm>
                      </Space>
                    )}
                    <Typography.Title level={5} className="organization-member-heading">
                      部门成员 <Tag>{selectedMembers.length}</Tag>
                    </Typography.Title>
                    <div className="organization-member-list">
                      {selectedMembers.map((member) => (
                        <div className="organization-member" key={member.id}>
                          <Avatar>{member.name.slice(0, 1)}</Avatar>
                          <div>
                            <strong>{member.name}</strong>
                            <span>{positionMap.get(member.positionId)?.name || '未配置岗位'}</span>
                          </div>
                          {canManage && (
                            <Button type="text" shape="circle" icon={<OaIcon name="edit" />}
                              aria-label={`编辑${member.name}`} onClick={() => openMember(member)} />
                          )}
                        </div>
                      ))}
                      {!selectedMembers.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无成员" />}
                    </div>
                  </>
                ) : <Empty description="请选择部门" />}
              </aside>
            </div>
          </>
        )}
      </Spin>

      <Modal
        open={Boolean(editor)}
        title={editor === 'member' ? `调整成员 · ${editingMember?.name}` : editor === 'position' ? '岗位配置' : '部门配置'}
        okText="保存并生效"
        cancelText="取消"
        onCancel={() => { setEditor(undefined); setEditingMember(undefined); form.resetFields(); }}
        onOk={() => void save()}
      >
        <Form form={form} layout="vertical">
          {editor === 'department' && (
            <>
              <Form.Item name="code" label="部门编码" rules={[
                { required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,59}$/ },
              ]}><Input placeholder="例如 PRODUCT_CENTER" /></Form.Item>
              <Form.Item name="name" label="部门名称" rules={[{ required: true }, { max: 100 }]}>
                <Input />
              </Form.Item>
              <Form.Item name="parentId" label="上级部门">
                <Select allowClear showSearch optionFilterProp="label"
                  options={data?.departments
                    .filter((item) => item.id !== selectedDepartment?.id)
                    .map((item) => ({ value: item.id, label: item.name }))} />
              </Form.Item>
              <Form.Item name="defaultApproverUserId" label="部门默认审批人">
                <Select allowClear showSearch optionFilterProp="label"
                  options={data?.members.map((member) => ({
                    value: member.id,
                    label: `${member.name} · ${departmentMap.get(member.departmentId)?.name || '未分配部门'}`,
                  }))} />
              </Form.Item>
            </>
          )}
          {editor === 'position' && (
            <>
              <Form.Item name="code" label="岗位编码" rules={[
                { required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,59}$/ },
              ]}><Input placeholder="例如 HR_MANAGER" /></Form.Item>
              <Form.Item name="name" label="岗位名称" rules={[{ required: true }, { max: 100 }]}>
                <Input />
              </Form.Item>
            </>
          )}
          {editor === 'member' && (
            <>
              <Form.Item name="departmentId" label="所属部门" rules={[{ required: true }]}>
                <Select showSearch optionFilterProp="label"
                  options={data?.departments.map((item) => ({ value: item.id, label: item.name }))} />
              </Form.Item>
              <Form.Item name="positionId" label="岗位" rules={[{ required: true }]}>
                <Select showSearch optionFilterProp="label"
                  options={data?.positions.map((item) => ({ value: item.id, label: item.name }))} />
              </Form.Item>
              <Form.Item name="approverUserId" label="直属审批人">
                <Select allowClear showSearch optionFilterProp="label"
                  options={data?.members.filter((item) => item.id !== editingMember?.id)
                    .map((item) => ({
                      value: item.id,
                      label: `${item.name} · ${departmentMap.get(item.departmentId)?.name || '未分配部门'}`,
                    }))} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </section>
  );
}

function buildDepartmentTree(
  departments: OrganizationDepartment[],
  members: OrganizationMember[],
  parentId?: number,
): DataNode[] {
  return departments
    .filter((item) => item.parentId === parentId || (!item.parentId && parentId === undefined))
    .map((item) => ({
      key: String(item.id),
      title: (
        <span className="organization-tree-title">
          <span>{item.name}</span>
          <em>{members.filter((member) => member.departmentId === item.id).length}</em>
        </span>
      ),
      children: buildDepartmentTree(departments, members, item.id),
    }));
}

function DepartmentChart({
  departments,
  members,
  selectedId,
  onSelect,
}: {
  departments: OrganizationDepartment[];
  members: OrganizationMember[];
  selectedId?: number;
  onSelect: (id: number) => void;
}) {
  const roots = departments.filter((item) => !item.parentId);
  const renderNode = (department: OrganizationDepartment) => {
    const children = departments.filter((item) => item.parentId === department.id);
    const count = members.filter((member) => member.departmentId === department.id).length;
    return (
      <div className="organization-chart-branch" key={department.id}>
        <button
          className={`organization-chart-node ${selectedId === department.id ? 'is-selected' : ''}`}
          onClick={() => onSelect(department.id)}
        >
          <span className="organization-chart-node__icon"><OaIcon name="organization" /></span>
          <span><strong>{department.name}</strong><small>{count} 位成员</small></span>
        </button>
        {children.length > 0 && (
          <div className="organization-chart-children">{children.map(renderNode)}</div>
        )}
      </div>
    );
  };
  return roots.length
    ? <div className="organization-chart">{roots.map(renderNode)}</div>
    : <Empty description="暂无部门" />;
}

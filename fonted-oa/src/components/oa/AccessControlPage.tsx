'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  accessControlApi,
  type AccessControlOverview,
  type AccessPermission,
  type AccessRole,
  type AccessRoute,
  type AccessUser,
  type SaveRoutePayload,
} from '@/lib/accessControlApi';
import { OaIcon, oaMenuIconOptions } from '@/components/OaIcon';

const componentOptions = [
  { value: 'DASHBOARD', label: '通用 OA 页面' },
  { value: 'AI_WORKSPACE', label: 'AI 工作空间' },
  { value: 'ACCESS_CONTROL', label: '权限配置中心' },
  { value: 'TODO_LIST', label: '我的待办' },
  { value: 'LEAVE_FORM', label: '请假申请' },
  { value: 'MY_APPLICATIONS', label: '我的申请' },
  { value: 'AUDIT_CENTER', label: '审计中心' },
];

export default function AccessControlPage() {
  const [overview, setOverview] = useState<AccessControlOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedRoleCode, setSelectedRoleCode] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [savingPermissions, setSavingPermissions] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [routeModalOpen, setRouteModalOpen] = useState(false);
  const [organizationModal, setOrganizationModal] = useState<'department' | 'position'>();
  const [editingRoute, setEditingRoute] = useState<AccessRoute | null>(null);
  const [deletingOrganizationId, setDeletingOrganizationId] = useState<number | null>(null);
  const [editingUser, setEditingUser] = useState<AccessUser | null>(null);
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [savingUser, setSavingUser] = useState(false);
  const [togglingUserId, setTogglingUserId] = useState<number | null>(null);
  const [roleForm] = Form.useForm();
  const [routeForm] = Form.useForm<SaveRoutePayload>();
  const [organizationForm] = Form.useForm();
  const [userForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const data = await accessControlApi.overview();
      setOverview(data);
      const selected = data.roles.find((role) => role.code === selectedRoleCode)
        || data.roles.find((role) => role.code !== 'SUPER_ADMIN')
        || data.roles[0];
      setSelectedRoleCode(selected?.code || '');
      setSelectedPermissions(selected?.permissions || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '权限配置加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // Initial load only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const roles = overview?.roles || [];
  const selectedRole = roles.find((role) => role.code === selectedRoleCode);
  const groupedPermissions = useMemo(
    () => groupPermissions(overview?.permissions || []),
    [overview?.permissions],
  );

  const chooseRole = (role: AccessRole) => {
    setSelectedRoleCode(role.code);
    setSelectedPermissions(role.permissions);
  };

  const savePermissions = async () => {
    if (!selectedRole) return;
    setSavingPermissions(true);
    try {
      const updated = await accessControlApi.updateRolePermissions(selectedRole.code, selectedPermissions);
      setOverview((current) => current && ({
        ...current,
        roles: current.roles.map((role) => role.code === updated.code ? updated : role),
      }));
      message.success('角色权限已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '角色权限保存失败');
    } finally {
      setSavingPermissions(false);
    }
  };

  const createRole = async () => {
    const values = await roleForm.validateFields();
    try {
      const created = await accessControlApi.createRole(values);
      setRoleModalOpen(false);
      roleForm.resetFields();
      await load();
      setSelectedRoleCode(created.code);
      setSelectedPermissions([]);
      message.success('角色已创建，可立即分配页面权限');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '角色创建失败');
    }
  };

  const openRouteEditor = (route?: AccessRoute) => {
    setEditingRoute(route || null);
    routeForm.setFieldsValue(route ? {
      ...route,
      parentKey: route.parentKey || undefined,
      path: route.path || undefined,
      icon: route.icon || undefined,
      componentKey: route.componentKey || undefined,
    } : {
      routeType: 'PAGE',
      componentKey: 'DASHBOARD',
      sortOrder: 1,
      enabled: true,
    });
    setRouteModalOpen(true);
  };

  const saveRoute = async () => {
    const values = await routeForm.validateFields();
    const page = values.routeType === 'PAGE';
    try {
      await accessControlApi.saveRoute({
        ...values,
        path: page ? values.path : undefined,
        componentKey: page ? values.componentKey : undefined,
      });
      setRouteModalOpen(false);
      routeForm.resetFields();
      await load();
      message.success(page ? '页面路由已保存，请为角色勾选对应页面权限' : '菜单节点已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '路由保存失败');
    }
  };

  const saveOrganizationItem = async () => {
    const values = await organizationForm.validateFields();
    try {
      if (organizationModal === 'department') {
        await accessControlApi.saveDepartment(values);
      } else {
        await accessControlApi.savePosition(values);
      }
      setOrganizationModal(undefined);
      organizationForm.resetFields();
      await load();
      message.success(organizationModal === 'department' ? '部门已保存' : '岗位已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '组织信息保存失败');
    }
  };

  const confirmDeleteOrganization = (item: { id: number; name: string }, kind: 'department' | 'position') => {
    Modal.confirm({
      title: `确认删除${kind === 'department' ? '部门' : '岗位'}？`,
      content: `「${item.name}」将被删除。若该${kind === 'department' ? '部门' : '岗位'}下仍有用户或子节点，删除将被服务端拒绝。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setDeletingOrganizationId(item.id);
        try {
          if (kind === 'department') {
            await accessControlApi.deleteDepartment(item.id);
          } else {
            await accessControlApi.deletePosition(item.id);
          }
          await load();
          message.success(kind === 'department' ? '部门已删除' : '岗位已删除');
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
        } finally {
          setDeletingOrganizationId(null);
        }
      },
    });
  };

  const openUserEditor = (user: AccessUser) => {
    setEditingUser(user);
    userForm.setFieldsValue({
      roles: user.roles,
      departmentId: user.departmentId,
      positionId: user.positionId,
      approverUserId: user.approverUserId,
    });
    setUserModalOpen(true);
  };

  const saveUser = async () => {
    if (!editingUser) return;
    const values = await userForm.validateFields();
    setSavingUser(true);
    try {
      const roleChanged = !areArraysEqual(values.roles, editingUser.roles);
      const orgChanged = values.departmentId !== editingUser.departmentId
        || values.positionId !== editingUser.positionId
        || values.approverUserId !== editingUser.approverUserId;
      let updated = editingUser;
      if (roleChanged) {
        updated = await accessControlApi.assignUserRoles(editingUser.id, values.roles);
        setOverview((current) => current && ({
          ...current,
          users: current.users.map((item) => item.id === updated.id ? updated : item),
        }));
      }
      if (orgChanged) {
        if (!values.departmentId || !values.positionId) {
          message.warning('部门和岗位不能为空');
          setSavingUser(false);
          return;
        }
        updated = await accessControlApi.updateUserOrganization(editingUser.id, {
          departmentId: values.departmentId,
          positionId: values.positionId,
          approverUserId: values.approverUserId,
        });
        setOverview((current) => current && ({
          ...current,
          users: current.users.map((item) => item.id === updated.id ? updated : item),
        }));
      }
      setUserModalOpen(false);
      setEditingUser(null);
      userForm.resetFields();
      if (roleChanged || orgChanged) {
        message.success('用户信息已更新');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用户信息保存失败');
    } finally {
      setSavingUser(false);
    }
  };

  const toggleUserStatus = (user: AccessUser) => {
    const nextStatus = user.status === 1 ? 0 : 1;
    const action = nextStatus === 0 ? '禁用' : '启用';
    Modal.confirm({
      title: `确认${action}用户？`,
      content: `「${user.name}」将被${action}。`,
      okText: `确认${action}`,
      okType: nextStatus === 0 ? 'danger' : 'primary',
      cancelText: '取消',
      onOk: async () => {
        setTogglingUserId(user.id);
        try {
          const updated = await accessControlApi.updateUserStatus(user.id, nextStatus);
          setOverview((current) => current && ({
            ...current,
            users: current.users.map((item) => item.id === user.id ? updated : item),
          }));
          message.success(`用户已${action}`);
        } catch (error) {
          message.error(error instanceof Error ? error.message : `用户${action}失败`);
        } finally {
          setTogglingUserId(null);
        }
      },
    });
  };

  const userColumns: ColumnsType<AccessUser> = [
    {
      title: '用户',
      key: 'user',
      align: 'center',
      render: (_, user) => (
        <Space className="oa-access-identity" orientation="vertical" size={2}>
          <Typography.Text strong>{user.name}</Typography.Text>
          <Typography.Text type="secondary">{user.email}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      align: 'center',
      render: (status: number) => (
        <Badge status={status === 1 ? 'success' : 'default'} text={status === 1 ? '正常' : '停用'} />
      ),
    },
    {
      title: '角色',
      dataIndex: 'roles',
      width: 240,
      align: 'center',
      render: (roleCodes: string[]) => {
        const items = roleCodes.map((code) => roles.find((role) => role.code === code));
        const valid = items.filter((item): item is AccessRole => Boolean(item));
        if (!valid.length) return <Typography.Text type="secondary">-</Typography.Text>;
        return (
          <Space size={[4, 4]} wrap>
            {valid.map((role) => (
              <Tag key={role.code} variant="filled">{role.name}</Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '部门',
      dataIndex: 'departmentId',
      width: 140,
      align: 'center',
      render: (value: number | undefined) => (
        <Typography.Text>{overview?.departments.find((item) => item.id === value)?.name || '-'}</Typography.Text>
      ),
    },
    {
      title: '岗位',
      dataIndex: 'positionId',
      width: 140,
      align: 'center',
      render: (value: number | undefined) => (
        <Typography.Text>{overview?.positions.find((item) => item.id === value)?.name || '-'}</Typography.Text>
      ),
    },
    {
      title: '直属审批人',
      dataIndex: 'approverUserId',
      width: 120,
      align: 'center',
      render: (value: number | undefined) => (
        <Typography.Text>{overview?.users.find((item) => item.id === value)?.name || '-'}</Typography.Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      align: 'center',
      fixed: 'right',
      render: (_, user) => (
        <Space size={0}>
          <Button type="link" onClick={() => openUserEditor(user)}>编辑</Button>
          <Button
            type="link"
            danger={user.status === 1}
            loading={togglingUserId === user.id}
            onClick={() => toggleUserStatus(user)}
          >
            {user.status === 1 ? '禁用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  const routeColumns: ColumnsType<AccessRoute> = [
    {
      title: '名称 / 编码',
      key: 'route',
      align: 'center',
      render: (_, route) => (
        <Space className="oa-access-identity" orientation="vertical" size={2}>
          <Typography.Text strong>{route.name}</Typography.Text>
          <Typography.Text className="oa-access-code">{route.routeKey}</Typography.Text>
        </Space>
      ),
    },
    { title: '父级', dataIndex: 'parentKey', width: 140, align: 'center', render: (value) => value || '-' },
    {
      title: '类型',
      dataIndex: 'routeType',
      width: 90,
      align: 'center',
      render: (value) => <Tag className="oa-access-type-tag" variant="filled">{value}</Tag>,
    },
    {
      title: '路径',
      dataIndex: 'path',
      align: 'center',
      render: (value) => <span className="oa-access-path">{value || '-'}</span>,
    },
    {
      title: '组件',
      dataIndex: 'componentKey',
      width: 160,
      align: 'center',
      render: (value) => <span className="oa-access-code">{value || '-'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      align: 'center',
      render: (enabled) => <Badge status={enabled ? 'success' : 'default'} text={enabled ? '启用' : '停用'} />,
    },
    {
      title: '操作',
      width: 72,
      align: 'center',
      render: (_, route) => (
        <Tooltip title="编辑路由">
          <Button
            type="text"
            shape="circle"
            icon={<OaIcon name="edit" />}
            aria-label={`编辑${route.name}`}
            onClick={() => openRouteEditor(route)}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <section className="oa-access-page">
      <header className="oa-access-header">
        <div>
          <Typography.Title level={3}>角色、权限与动态路由</Typography.Title>
          <Typography.Paragraph type="secondary">
            权限由服务端实时解析。角色变更无需重新登录，菜单和直接 URL 访问都会同步受控。
          </Typography.Paragraph>
        </div>
        <Tag className="oa-access-service-tag" icon={<OaIcon name="access-control" />} variant="filled">
          服务端 RBAC
        </Tag>
      </header>

      <Spin spinning={loading}>
        {!overview ? <Empty description="暂无权限配置数据" /> : (
          <Tabs items={[
            {
              key: 'users',
              label: <span><OaIcon name="user" /> 用户角色</span>,
              children: (
                <Table
                  className="oa-access-table"
                  rowKey="id"
                  columns={userColumns}
                  dataSource={overview.users}
                  size="middle"
                  pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
                  scroll={{ x: 1100 }}
                />
              ),
            },
            {
              key: 'organization',
              label: <span><OaIcon name="organization" /> 组织与审批人</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Button type="primary" icon={<OaIcon name="add" />}
                      onClick={() => setOrganizationModal('department')}>
                      新增或更新部门
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    dataSource={overview.departments}
                    pagination={false}
                    columns={[
                      { title: '部门编码', dataIndex: 'code', align: 'center' as const },
                      { title: '部门名称', dataIndex: 'name', align: 'center' as const },
                      {
                        title: '默认审批人',
                        dataIndex: 'defaultApproverUserId',
                        align: 'center' as const,
                        render: (value) => overview.users.find((user) => user.id === value)?.name || '-',
                      },
                      {
                        title: '操作',
                        width: 140,
                        align: 'center' as const,
                        render: (_, item) => (
                          <Space size={0}>
                            <Button type="link" onClick={() => {
                              organizationForm.setFieldsValue(item);
                              setOrganizationModal('department');
                            }}>
                              编辑
                            </Button>
                            <Button
                              type="link"
                              danger
                              loading={deletingOrganizationId === item.id}
                              onClick={() => confirmDeleteOrganization(item, 'department')}
                            >
                              删除
                            </Button>
                          </Space>
                        ),
                      },
                    ]}
                  />
                  <Divider />
                  <div className="oa-access-toolbar">
                    <Button type="primary" icon={<OaIcon name="add" />}
                      onClick={() => setOrganizationModal('position')}>
                      新增或更新岗位
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    dataSource={overview.positions}
                    pagination={false}
                    columns={[
                      { title: '岗位编码', dataIndex: 'code', align: 'center' as const },
                      { title: '岗位名称', dataIndex: 'name', align: 'center' as const },
                      {
                        title: '操作',
                        width: 140,
                        align: 'center' as const,
                        render: (_, item) => (
                          <Space size={0}>
                            <Button type="link" onClick={() => {
                              organizationForm.setFieldsValue(item);
                              setOrganizationModal('position');
                            }}>
                              编辑
                            </Button>
                            <Button
                              type="link"
                              danger
                              loading={deletingOrganizationId === item.id}
                              onClick={() => confirmDeleteOrganization(item, 'position')}
                            >
                              删除
                            </Button>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </>
              ),
            },
            {
              key: 'roles',
              label: <span><OaIcon name="role" /> 角色权限</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Button type="primary" icon={<OaIcon name="add" />} onClick={() => setRoleModalOpen(true)}>
                      新建角色
                    </Button>
                  </div>
                  <div className="oa-role-permission-layout">
                    <nav className="oa-role-list" aria-label="角色列表">
                      {roles.map((role) => (
                        <Button key={role.code} type={role.code === selectedRoleCode ? 'primary' : 'text'}
                          block onClick={() => chooseRole(role)}>
                          {role.name}
                        </Button>
                      ))}
                    </nav>
                    <div className="oa-role-permission-editor">
                      {selectedRole ? (
                        <>
                          <div className="oa-role-permission-heading">
                            <div>
                              <Typography.Title level={5}>{selectedRole.name}</Typography.Title>
                              <Typography.Text type="secondary">{selectedRole.description}</Typography.Text>
                            </div>
                            <Typography.Text code>{selectedRole.code}</Typography.Text>
                          </div>
                          {selectedRole.code === 'SUPER_ADMIN' && (
                            <Alert type="info" showIcon title="超级管理员始终拥有全部权限" />
                          )}
                          <Checkbox.Group value={selectedPermissions}
                            disabled={selectedRole.code === 'SUPER_ADMIN'}
                            onChange={(values) => setSelectedPermissions(values as string[])}>
                            {Array.from(groupedPermissions.entries()).map(([module, items]) => (
                              <section className="oa-permission-group" key={module}>
                                <Divider titlePlacement="left">{module}</Divider>
                                <div className="oa-permission-grid">
                                  {items.map((permission) => (
                                    <Checkbox key={permission.code} value={permission.code}>
                                      <span className="oa-permission-label">
                                        <strong>{permission.name}</strong>
                                        <small>{permission.description}</small>
                                      </span>
                                    </Checkbox>
                                  ))}
                                </div>
                              </section>
                            ))}
                          </Checkbox.Group>
                          {selectedRole.code !== 'SUPER_ADMIN' && (
                            <Button type="primary" icon={<OaIcon name="save" />} loading={savingPermissions}
                              onClick={() => void savePermissions()}>
                              保存角色权限
                            </Button>
                          )}
                        </>
                      ) : <Empty description="请选择角色" />}
                    </div>
                  </div>
                </>
              ),
            },
            {
              key: 'routes',
              label: <span><OaIcon name="organization" /> 动态路由</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Alert className="oa-access-hint" type="info" showIcon
                      title="新增 PAGE 会自动创建对应页面权限；角色勾选后，该页面才会出现在菜单中。" />
                    <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openRouteEditor()}>
                      新增路由
                    </Button>
                  </div>
                  <Table
                    className="oa-access-table"
                    rowKey="routeKey"
                    columns={routeColumns}
                    dataSource={overview.routes}
                    size="middle"
                    pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
                    scroll={{ x: 920 }}
                  />
                </>
              ),
            },
          ]} />
        )}
      </Spin>

      <Modal title="新建角色" open={roleModalOpen} onCancel={() => setRoleModalOpen(false)}
        onOk={() => void createRole()} okText="创建">
        <Form form={roleForm} layout="vertical">
          <Form.Item name="code" label="角色编码"
            rules={[
              { required: true, message: '请输入角色编码' },
              { pattern: /^[A-Z][A-Z0-9_]{2,39}$/, message: '使用 3-40 位大写字母、数字或下划线' },
            ]}>
            <Input placeholder="例如 DEPARTMENT_MANAGER" />
          </Form.Item>
          <Form.Item name="name" label="角色名称" rules={[{ required: true }]}>
            <Input placeholder="例如 部门主管" />
          </Form.Item>
          <Form.Item name="description" label="职责说明" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="说明该角色的职责边界" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={organizationModal === 'department' ? '部门配置' : '岗位配置'}
        open={Boolean(organizationModal)}
        onCancel={() => { setOrganizationModal(undefined); organizationForm.resetFields(); }}
        onOk={() => void saveOrganizationItem()}
        okText="保存"
      >
        <Form form={organizationForm} layout="vertical">
          <Form.Item name="code" label="编码" rules={[
            { required: true },
            { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,59}$/, message: '使用字母、数字、下划线或连字符' },
          ]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 100 }]}>
            <Input />
          </Form.Item>
          {organizationModal === 'department' && (
            <>
              <Form.Item name="parentId" label="上级部门">
                <Select allowClear options={(overview?.departments || []).map((item) => ({
                  value: item.id, label: item.name,
                }))} />
              </Form.Item>
              <Form.Item name="defaultApproverUserId" label="部门默认审批人">
                <Select allowClear options={(overview?.users || [])
                  .filter((item) => item.status === 1)
                  .map((item) => ({ value: item.id, label: item.name }))} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <Modal title={editingRoute ? '编辑路由' : '新增路由'} open={routeModalOpen}
        onCancel={() => setRouteModalOpen(false)} onOk={() => void saveRoute()} okText="保存" width={620}>
        <Form form={routeForm} layout="vertical">
          <Form.Item name="routeKey" label="路由编码"
            rules={[
              { required: true },
              { pattern: /^[a-z][a-z0-9-]{1,59}$/, message: '使用小写字母、数字或连字符' },
            ]}>
            <Input disabled={Boolean(editingRoute)} placeholder="例如 sales-report" />
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="name" label="显示名称" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="routeType" label="节点类型" rules={[{ required: true }]}>
              <Select options={[
                { value: 'GROUP', label: '一级分组' },
                { value: 'MENU', label: '菜单目录' },
                { value: 'PAGE', label: '可访问页面' },
              ]} />
            </Form.Item>
          </div>
          <div className="oa-route-form-grid">
            <Form.Item name="parentKey" label="父级节点">
              <Select allowClear showSearch options={overview?.routes
                .filter((route) => route.routeKey !== editingRoute?.routeKey && route.routeType !== 'PAGE')
                .map((route) => ({ value: route.routeKey, label: `${route.name} (${route.routeKey})` }))} />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序" rules={[{ required: true }]}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.routeType !== next.routeType}>
            {({ getFieldValue }) => getFieldValue('routeType') === 'PAGE' && (
              <div className="oa-route-form-grid">
                <Form.Item name="path" label="页面路径"
                  rules={[
                    { required: true },
                    { pattern: /^\/oa\/[a-z][a-z0-9-]{1,59}$/, message: '格式应为 /oa/page-key' },
                  ]}>
                  <Input placeholder="/oa/sales-report" />
                </Form.Item>
                <Form.Item name="componentKey" label="页面组件" rules={[{ required: true }]}>
                  <Select options={componentOptions} />
                </Form.Item>
              </div>
            )}
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="icon" label="图标编码">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                options={oaMenuIconOptions}
                placeholder="选择语义图标"
              />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title={editingUser ? `编辑用户 - ${editingUser.name}` : '编辑用户'}
        open={userModalOpen}
        onCancel={() => { setUserModalOpen(false); setEditingUser(null); userForm.resetFields(); }}
        onOk={() => void saveUser()}
        okText="保存"
        cancelText="取消"
        confirmLoading={savingUser}
        width={520}
      >
        {editingUser && (
          <div className="oa-access-identity" style={{ marginBottom: 16 }}>
            <Typography.Text strong>{editingUser.name}</Typography.Text>
            <Typography.Text type="secondary"> {editingUser.email}</Typography.Text>
          </div>
        )}
        <Form form={userForm} layout="vertical">
          <Form.Item name="roles" label="角色" rules={[{ required: true, message: '请至少分配一个角色' }]}>
            <Select
              mode="multiple"
              options={roles.map((role) => ({ value: role.code, label: `${role.name} (${role.code})` }))}
              maxTagCount="responsive"
              placeholder="选择角色"
            />
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="departmentId" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
              <Select
                options={(overview?.departments || []).map((item) => ({ value: item.id, label: item.name }))}
                placeholder="选择部门"
              />
            </Form.Item>
            <Form.Item name="positionId" label="岗位" rules={[{ required: true, message: '请选择岗位' }]}>
              <Select
                options={(overview?.positions || []).map((item) => ({ value: item.id, label: item.name }))}
                placeholder="选择岗位"
              />
            </Form.Item>
          </div>
          <Form.Item name="approverUserId" label="直属审批人">
            <Select
              allowClear
              options={(overview?.users || [])
                .filter((item) => item.id !== editingUser?.id && item.status === 1)
                .map((item) => ({ value: item.id, label: item.name }))}
              placeholder="选择直属审批人"
            />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

function areArraysEqual(a?: string[], b?: string[]): boolean {
  const left = a ?? [];
  const right = b ?? [];
  if (left.length !== right.length) return false;
  const set = new Set(left);
  return right.every((item) => set.has(item));
}

function groupPermissions(permissions: AccessPermission[]): Map<string, AccessPermission[]> {
  return permissions.reduce((groups, permission) => {
    const values = groups.get(permission.module) || [];
    values.push(permission);
    groups.set(permission.module, values);
    return groups;
  }, new Map<string, AccessPermission[]>());
}

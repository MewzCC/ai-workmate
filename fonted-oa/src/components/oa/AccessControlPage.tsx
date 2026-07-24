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
import {
  ApartmentOutlined,
  EditOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  TeamOutlined,
} from '@ant-design/icons';
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

const componentOptions = [
  { value: 'DASHBOARD', label: '通用 OA 页面' },
  { value: 'AI_WORKSPACE', label: 'AI 工作空间' },
  { value: 'ACCESS_CONTROL', label: '权限配置中心' },
];

export default function AccessControlPage() {
  const [overview, setOverview] = useState<AccessControlOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingUserId, setSavingUserId] = useState<number | null>(null);
  const [selectedRoleCode, setSelectedRoleCode] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [savingPermissions, setSavingPermissions] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [routeModalOpen, setRouteModalOpen] = useState(false);
  const [editingRoute, setEditingRoute] = useState<AccessRoute | null>(null);
  const [roleForm] = Form.useForm();
  const [routeForm] = Form.useForm<SaveRoutePayload>();

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

  const assignRole = async (userId: number, roleCode: string) => {
    setSavingUserId(userId);
    try {
      const updated = await accessControlApi.assignUserRole(userId, roleCode);
      setOverview((current) => current && ({
        ...current,
        users: current.users.map((user) => user.id === userId ? updated : user),
      }));
      message.success('用户角色已更新，下一次请求立即生效');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '角色分配失败');
    } finally {
      setSavingUserId(null);
    }
  };

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

  const userColumns: ColumnsType<AccessUser> = [
    {
      title: '用户',
      key: 'user',
      render: (_, user) => (
        <Space className="oa-access-identity" direction="vertical" size={2}>
          <Typography.Text strong>{user.name}</Typography.Text>
          <Typography.Text type="secondary">{user.email}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: number) => (
        <Badge status={status === 1 ? 'success' : 'default'} text={status === 1 ? '正常' : '停用'} />
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 240,
      render: (roleCode: string, user) => (
        <Select
          value={roleCode}
          loading={savingUserId === user.id}
          options={roles.map((role) => ({ value: role.code, label: `${role.name} (${role.code})` }))}
          style={{ width: 210 }}
          onChange={(value) => void assignRole(user.id, value)}
        />
      ),
    },
  ];

  const routeColumns: ColumnsType<AccessRoute> = [
    {
      title: '名称 / 编码',
      key: 'route',
      render: (_, route) => (
        <Space className="oa-access-identity" direction="vertical" size={2}>
          <Typography.Text strong>{route.name}</Typography.Text>
          <Typography.Text className="oa-access-code">{route.routeKey}</Typography.Text>
        </Space>
      ),
    },
    { title: '父级', dataIndex: 'parentKey', width: 140, render: (value) => value || '-' },
    {
      title: '类型',
      dataIndex: 'routeType',
      width: 90,
      render: (value) => <Tag className="oa-access-type-tag" bordered={false}>{value}</Tag>,
    },
    {
      title: '路径',
      dataIndex: 'path',
      render: (value) => <span className="oa-access-path">{value || '-'}</span>,
    },
    {
      title: '组件',
      dataIndex: 'componentKey',
      width: 160,
      render: (value) => <span className="oa-access-code">{value || '-'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
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
            icon={<EditOutlined />}
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
        <Tag className="oa-access-service-tag" icon={<SafetyCertificateOutlined />} bordered={false}>
          服务端 RBAC
        </Tag>
      </header>

      <Spin spinning={loading}>
        {!overview ? <Empty description="暂无权限配置数据" /> : (
          <Tabs items={[
            {
              key: 'users',
              label: <span><TeamOutlined /> 用户角色</span>,
              children: (
                <Table
                  className="oa-access-table"
                  rowKey="id"
                  columns={userColumns}
                  dataSource={overview.users}
                  size="middle"
                  pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
                  scroll={{ x: 680 }}
                />
              ),
            },
            {
              key: 'roles',
              label: <span><SafetyCertificateOutlined /> 角色权限</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setRoleModalOpen(true)}>
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
                            <Alert type="info" showIcon message="超级管理员始终拥有全部权限" />
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
                            <Button type="primary" icon={<SaveOutlined />} loading={savingPermissions}
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
              label: <span><ApartmentOutlined /> 动态路由</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Alert className="oa-access-hint" type="info" showIcon
                      message="新增 PAGE 会自动创建对应页面权限；角色勾选后，该页面才会出现在菜单中。" />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openRouteEditor()}>
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
              <Select allowClear options={[
                'DashboardOutlined', 'ApartmentOutlined', 'ApiOutlined', 'SettingOutlined', 'RobotOutlined',
              ].map((value) => ({ value, label: value }))} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </section>
  );
}

function groupPermissions(permissions: AccessPermission[]): Map<string, AccessPermission[]> {
  return permissions.reduce((groups, permission) => {
    const values = groups.get(permission.module) || [];
    values.push(permission);
    groups.set(permission.module, values);
    return groups;
  }, new Map<string, AccessPermission[]>());
}

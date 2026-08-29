'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Checkbox,
  Divider,
  Drawer,
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
import { useTranslation } from 'react-i18next';
import { OaIcon, oaMenuIconOptions } from '@/components/OaIcon';

const COMPONENT_VALUES = [
  'DASHBOARD',
  'AI_WORKSPACE',
  'AI_TASK_CENTER',
  'ACCESS_CONTROL',
  'TODO_LIST',
  'LEAVE_FORM',
  'MY_APPLICATIONS',
  'AUDIT_CENTER',
  'ORG_TREE',
  'EMPLOYEE_CHANGE',
  'KNOWLEDGE_BASE',
  'SYSTEM_CONFIG',
] as const;

export default function AccessControlPage() {
  const { t } = useTranslation();
  const componentOptions = COMPONENT_VALUES.map((value) => ({
    value,
    label: t(`access.componentTypes.${value}`),
  }));
  const [overview, setOverview] = useState<AccessControlOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedRoleCode, setSelectedRoleCode] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [selectedMemberIds, setSelectedMemberIds] = useState<number[]>([]);
  const [roleWorkspaceMode, setRoleWorkspaceMode] = useState<'members' | 'permissions'>();
  const [savingPermissions, setSavingPermissions] = useState(false);
  const [savingMembers, setSavingMembers] = useState(false);
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
      message.error(error instanceof Error ? error.message : t('access.messages.loadFailed'));
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

  const openRoleWorkspace = (role: AccessRole, mode: 'members' | 'permissions') => {
    setSelectedRoleCode(role.code);
    setSelectedPermissions(role.permissions);
    setSelectedMemberIds(
      (overview?.users || [])
        .filter((user) => user.roles.includes(role.code))
        .map((user) => user.id),
    );
    setRoleWorkspaceMode(mode);
  };

  const changeSelectedMembers = (nextIds: number[]) => {
    const removedIds = selectedMemberIds.filter((userId) => !nextIds.includes(userId));
    const orphanedUser = (overview?.users || []).find(
      (user) => removedIds.includes(user.id) && user.roles.length <= 1,
    );
    if (orphanedUser) {
      message.warning(t('access.messages.userOnlyRole', { name: orphanedUser.name }));
      return;
    }
    setSelectedMemberIds(nextIds);
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
      message.success(t('access.messages.permissionsSaved'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.permissionsSaveFailed'));
    } finally {
      setSavingPermissions(false);
    }
  };

  const saveMembers = async () => {
    if (!selectedRole) return;
    setSavingMembers(true);
    try {
      const updatedOverview = await accessControlApi.updateRoleMembers(
        selectedRole.code,
        selectedMemberIds,
      );
      setOverview(updatedOverview);
      message.success(t('access.messages.membersUpdated'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.membersSaveFailed'));
    } finally {
      setSavingMembers(false);
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
      message.success(t('access.messages.roleCreated'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.roleCreateFailed'));
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
      message.success(page ? t('access.messages.pageRouteSaved') : t('access.messages.menuNodeSaved'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.routeSaveFailed'));
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
      message.success(organizationModal === 'department' ? t('access.messages.departmentSaved') : t('access.messages.positionSaved'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.orgSaveFailed'));
    }
  };

  const confirmDeleteOrganization = (item: { id: number; name: string }, kind: 'department' | 'position') => {
    Modal.confirm({
      title: kind === 'department' ? t('access.confirm.deleteDepartment') : t('access.confirm.deletePosition'),
      content: kind === 'department'
        ? t('access.confirm.deleteDepartmentContent', { name: item.name })
        : t('access.confirm.deletePositionContent', { name: item.name }),
      okText: t('access.confirm.confirmDelete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        setDeletingOrganizationId(item.id);
        try {
          if (kind === 'department') {
            await accessControlApi.deleteDepartment(item.id);
          } else {
            await accessControlApi.deletePosition(item.id);
          }
          await load();
          message.success(kind === 'department' ? t('access.messages.departmentDeleted') : t('access.messages.positionDeleted'));
        } catch (error) {
          message.error(error instanceof Error ? error.message : t('access.messages.deleteFailed'));
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
          message.warning(t('access.messages.departmentAndPositionRequired'));
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
        message.success(t('access.messages.userUpdated'));
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('access.messages.userSaveFailed'));
    } finally {
      setSavingUser(false);
    }
  };

  const toggleUserStatus = (user: AccessUser) => {
    const nextStatus = user.status === 1 ? 0 : 1;
    const isEnabling = nextStatus === 1;
    Modal.confirm({
      title: isEnabling ? t('access.confirm.enableUser') : t('access.confirm.disableUser'),
      content: isEnabling
        ? t('access.confirm.enableUserContent', { name: user.name })
        : t('access.confirm.disableUserContent', { name: user.name }),
      okText: isEnabling ? t('access.confirm.confirmEnable') : t('access.confirm.confirmDisable'),
      okType: isEnabling ? 'primary' : 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        setTogglingUserId(user.id);
        try {
          const updated = await accessControlApi.updateUserStatus(user.id, nextStatus);
          setOverview((current) => current && ({
            ...current,
            users: current.users.map((item) => item.id === user.id ? updated : item),
          }));
          message.success(isEnabling ? t('access.messages.userEnabled') : t('access.messages.userDisabled'));
        } catch (error) {
          message.error(error instanceof Error ? error.message : (isEnabling ? t('access.messages.userEnableFailed') : t('access.messages.userDisableFailed')));
        } finally {
          setTogglingUserId(null);
        }
      },
    });
  };

  const userColumns: ColumnsType<AccessUser> = [
    {
      title: t('access.columns.user'),
      key: 'user',
      width: 240,
      render: (_, user) => (
        <div className="oa-access-user-cell">
          <Avatar size="small" src={user.avatarUrl || undefined}>{user.name.slice(0, 1).toUpperCase()}</Avatar>
          <div className="oa-access-user-cell__info">
            <Typography.Text strong className="oa-access-user-cell__name">{user.name}</Typography.Text>
            <Typography.Text type="secondary" className="oa-access-user-cell__email">{user.email}</Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 90,
      align: 'center',
      render: (status: number) => (
        <Badge status={status === 1 ? 'success' : 'default'} text={status === 1 ? t('access.status.normal') : t('access.status.disabled')} />
      ),
    },
    {
      title: t('access.userModal.role'),
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
      title: t('access.columns.department'),
      dataIndex: 'departmentId',
      width: 140,
      align: 'center',
      render: (value: number | undefined) => (
        <Typography.Text>{overview?.departments.find((item) => item.id === value)?.name || '-'}</Typography.Text>
      ),
    },
    {
      title: t('access.columns.position'),
      dataIndex: 'positionId',
      width: 140,
      align: 'center',
      render: (value: number | undefined) => (
        <Typography.Text>{overview?.positions.find((item) => item.id === value)?.name || '-'}</Typography.Text>
      ),
    },
    {
      title: t('access.columns.directApprover'),
      dataIndex: 'approverUserId',
      width: 160,
      render: (value: number | undefined) => {
        const approver = overview?.users.find((item) => item.id === value);
        if (!approver) return <Typography.Text type="secondary">-</Typography.Text>;
        const deptName = overview?.departments.find((d) => d.id === approver.departmentId)?.name;
        return (
          <div className="oa-access-user-cell">
            <Avatar size="small" src={approver.avatarUrl || undefined}>{approver.name.slice(0, 1).toUpperCase()}</Avatar>
            <div className="oa-access-user-cell__info">
              <Typography.Text strong className="oa-access-user-cell__name">{approver.name}</Typography.Text>
              <Typography.Text type="secondary" className="oa-access-user-cell__email">{deptName || approver.email}</Typography.Text>
            </div>
          </div>
        );
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 140,
      align: 'center',
      fixed: 'right',
      render: (_, user) => (
        <Space size={0}>
          <Button type="link" onClick={() => openUserEditor(user)}>{t('common.edit')}</Button>
          <Button
            type="link"
            danger={user.status === 1}
            loading={togglingUserId === user.id}
            onClick={() => toggleUserStatus(user)}
          >
            {user.status === 1 ? t('common.disable') : t('common.enable')}
          </Button>
        </Space>
      ),
    },
  ];

  const routeColumns: ColumnsType<AccessRoute> = [
    {
      title: t('access.columns.routeNameCode'),
      key: 'route',
      align: 'center',
      render: (_, route) => (
        <Space className="oa-access-identity" orientation="vertical" size={2}>
          <Typography.Text strong>{route.name}</Typography.Text>
          <Typography.Text className="oa-access-code">{route.routeKey}</Typography.Text>
        </Space>
      ),
    },
    { title: t('access.columns.parent'), dataIndex: 'parentKey', width: 140, align: 'center', render: (value) => value || '-' },
    {
      title: t('access.columns.type'),
      dataIndex: 'routeType',
      width: 90,
      align: 'center',
      render: (value) => <Tag className="oa-access-type-tag" variant="filled">{value}</Tag>,
    },
    {
      title: t('access.columns.path'),
      dataIndex: 'path',
      align: 'center',
      render: (value) => <span className="oa-access-path">{value || '-'}</span>,
    },
    {
      title: t('access.columns.component'),
      dataIndex: 'componentKey',
      width: 160,
      align: 'center',
      render: (value) => <span className="oa-access-code">{value || '-'}</span>,
    },
    {
      title: t('common.status'),
      dataIndex: 'enabled',
      width: 80,
      align: 'center',
      render: (enabled) => <Badge status={enabled ? 'success' : 'default'} text={enabled ? t('common.enable') : t('access.status.disabled')} />,
    },
    {
      title: t('common.actions'),
      width: 72,
      align: 'center',
      render: (_, route) => (
        <Tooltip title={t('access.editRouteTooltip')}>
          <Button
            type="text"
            shape="circle"
            icon={<OaIcon name="edit" />}
            aria-label={t('access.editRouteAriaLabel', { name: route.name })}
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
          <Typography.Title level={3}>{t('access.header.title')}</Typography.Title>
          <Typography.Paragraph type="secondary">
            {t('access.header.description')}
          </Typography.Paragraph>
        </div>
        <Space>
          <Tag className="oa-access-service-tag" icon={<OaIcon name="access-control" />} variant="filled">
            {t('access.header.serverRbacTag')}
          </Tag>
          <Button type="primary" icon={<OaIcon name="add" />} onClick={() => setRoleModalOpen(true)}>
            {t('access.header.createRole')}
          </Button>
        </Space>
      </header>

      <Spin spinning={loading}>
        {!overview ? <Empty description={t('access.empty')} /> : (
          <Tabs defaultActiveKey="roles" items={[
            {
              key: 'roles',
              label: <span><OaIcon name="role" /> {t('access.tabs.roles')}</span>,
              children: (
                <div className="oa-role-card-grid">
                  {roles.map((role) => {
                    const members = overview.users.filter((user) => user.roles.includes(role.code));
                    return (
                      <Card className="oa-role-card" key={role.code}>
                        <div className="oa-role-card-heading">
                          <div className="oa-role-card-icon"><OaIcon name="role" /></div>
                          <div className="oa-role-card-title">
                            <Space size={8} wrap>
                              <Typography.Title level={5}>{role.name}</Typography.Title>
                              {role.builtin && <Tag variant="filled">{t('access.roleCard.builtin')}</Tag>}
                            </Space>
                            <Typography.Text className="oa-access-code">{role.code}</Typography.Text>
                          </div>
                        </div>
                        <Typography.Paragraph className="oa-role-card-description" type="secondary">
                          {role.description}
                        </Typography.Paragraph>
                        <div className="oa-role-card-summary">
                          <div><strong>{members.length}</strong><span>{t('access.roleCard.membersCount')}</span></div>
                          <div><strong>{role.permissions.length}</strong><span>{t('access.roleCard.permissionsCount')}</span></div>
                        </div>
                        <div className="oa-role-member-preview">
                          <Avatar.Group max={{ count: 4 }}>
                            {members.map((user) => (
                              <Tooltip key={user.id} title={`${user.name} · ${user.email}`}>
                                <Avatar src={user.avatarUrl || undefined}>{user.name.slice(0, 1).toUpperCase()}</Avatar>
                              </Tooltip>
                            ))}
                          </Avatar.Group>
                          <Typography.Text type="secondary">
                            {members.length ? t('access.roleCard.associatedUsers', { count: members.length }) : t('access.roleCard.noMembers')}
                          </Typography.Text>
                        </div>
                        <div className="oa-role-card-actions">
                          <Button icon={<OaIcon name="user" />}
                            onClick={() => openRoleWorkspace(role, 'members')}>
                            {t('access.roleCard.manageMembers')}
                          </Button>
                          <Button type="primary" ghost icon={<OaIcon name="access-control" />}
                            onClick={() => openRoleWorkspace(role, 'permissions')}>
                            {t('access.roleCard.configurePermissions')}
                          </Button>
                        </div>
                      </Card>
                    );
                  })}
                </div>
              ),
            },
            {
              key: 'organization',
              label: <span><OaIcon name="organization" /> {t('access.tabs.organization')}</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Button type="primary" icon={<OaIcon name="add" />}
                      onClick={() => setOrganizationModal('department')}>
                      {t('access.organization.addOrUpdateDepartment')}
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    dataSource={overview.departments}
                    pagination={false}
                    columns={[
                      { title: t('access.columns.departmentCode'), dataIndex: 'code', align: 'center' as const },
                      { title: t('access.columns.departmentName'), dataIndex: 'name', align: 'center' as const },
                      {
                        title: t('access.columns.defaultApprover'),
                        dataIndex: 'defaultApproverUserId',
                        align: 'center' as const,
                        render: (value) => overview.users.find((user) => user.id === value)?.name || '-',
                      },
                      {
                        title: t('common.actions'),
                        width: 140,
                        align: 'center' as const,
                        render: (_, item) => (
                          <Space size={0}>
                            <Button type="link" onClick={() => {
                              organizationForm.setFieldsValue(item);
                              setOrganizationModal('department');
                            }}>
                              {t('common.edit')}
                            </Button>
                            <Button
                              type="link"
                              danger
                              loading={deletingOrganizationId === item.id}
                              onClick={() => confirmDeleteOrganization(item, 'department')}
                            >
                              {t('common.delete')}
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
                      {t('access.organization.addOrUpdatePosition')}
                    </Button>
                  </div>
                  <Table
                    rowKey="id"
                    dataSource={overview.positions}
                    pagination={false}
                    columns={[
                      { title: t('access.columns.positionCode'), dataIndex: 'code', align: 'center' as const },
                      { title: t('access.columns.positionName'), dataIndex: 'name', align: 'center' as const },
                      {
                        title: t('common.actions'),
                        width: 140,
                        align: 'center' as const,
                        render: (_, item) => (
                          <Space size={0}>
                            <Button type="link" onClick={() => {
                              organizationForm.setFieldsValue(item);
                              setOrganizationModal('position');
                            }}>
                              {t('common.edit')}
                            </Button>
                            <Button
                              type="link"
                              danger
                              loading={deletingOrganizationId === item.id}
                              onClick={() => confirmDeleteOrganization(item, 'position')}
                            >
                              {t('common.delete')}
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
              key: 'users',
              label: <span><OaIcon name="user" /> {t('access.tabs.users')}</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Alert type="info" showIcon
                      title={t('access.usersAlert')} />
                  </div>
                  <Table
                    className="oa-access-table"
                    rowKey="id"
                    columns={userColumns}
                    dataSource={overview.users}
                    size="middle"
                    pagination={{ pageSize: 10, hideOnSinglePage: true, showSizeChanger: false }}
                    scroll={{ x: 1100 }}
                  />
                </>
              ),
            },
            {
              key: 'routes',
              label: <span><OaIcon name="organization" /> {t('access.tabs.routes')}</span>,
              children: (
                <>
                  <div className="oa-access-toolbar">
                    <Alert className="oa-access-hint" type="info" showIcon
                      title={t('access.routesAlert')} />
                    <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openRouteEditor()}>
                      {t('access.addRoute')}
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

      <Drawer
        className="oa-role-workspace-drawer"
        title={selectedRole ? (
          <div>
            <Typography.Text strong>{selectedRole.name}</Typography.Text>
            <Typography.Text className="oa-access-code"> · {selectedRole.code}</Typography.Text>
          </div>
        ) : t('access.drawer.roleWorkspace')}
        open={Boolean(roleWorkspaceMode)}
        size={640}
        onClose={() => setRoleWorkspaceMode(undefined)}
        extra={roleWorkspaceMode && (
          <Tag variant="filled">{roleWorkspaceMode === 'members' ? t('access.drawer.membersManagement') : t('access.drawer.permissionsConfig')}</Tag>
        )}
        footer={selectedRole && (
          <div className="oa-role-workspace-footer">
            <Button onClick={() => setRoleWorkspaceMode(undefined)}>{t('common.cancel')}</Button>
            {roleWorkspaceMode === 'members' ? (
              <Button type="primary" icon={<OaIcon name="save" />} loading={savingMembers}
                onClick={() => void saveMembers()}>
                {t('access.drawer.saveMembers')}
              </Button>
            ) : selectedRole.code !== 'SUPER_ADMIN' && (
              <Button type="primary" icon={<OaIcon name="save" />} loading={savingPermissions}
                onClick={() => void savePermissions()}>
                {t('access.drawer.savePermissions')}
              </Button>
            )}
          </div>
        )}
      >
        {selectedRole && roleWorkspaceMode === 'members' && (
          <div className="oa-role-workspace">
            <Alert
              type="info"
              showIcon
              title={t('access.drawer.membersAlert')}
            />
            <div className="oa-role-workspace-summary">
              <span>{t('access.drawer.currentlySelected')}</span>
              <strong>{selectedMemberIds.length}</strong>
              <span>{t('access.roleCard.membersCount')}</span>
            </div>
            <Form layout="vertical">
              <Form.Item label={t('access.drawer.roleMembersLabel')}>
                <Select
                  mode="multiple"
                  showSearch
                  allowClear
                  optionFilterProp="label"
                  value={selectedMemberIds}
                  maxTagCount="responsive"
                  placeholder={t('access.drawer.searchMembersPlaceholder')}
                  onChange={changeSelectedMembers}
                  options={overview?.users
                    .filter((user) => user.status === 1 || selectedMemberIds.includes(user.id))
                    .map((user) => ({
                      value: user.id,
                      label: `${user.name} · ${user.email}${user.status === 1 ? '' : t('access.drawer.disabledOptionSuffix')}`,
                    }))}
                />
              </Form.Item>
            </Form>
            <div className="oa-role-member-list">
              {(overview?.users || [])
                .filter((user) => selectedMemberIds.includes(user.id))
                .map((user) => (
                  <div className="oa-role-member-row" key={user.id}>
                    <Avatar src={user.avatarUrl || undefined}>{user.name.slice(0, 1).toUpperCase()}</Avatar>
                    <div>
                      <Typography.Text strong>{user.name}</Typography.Text>
                      <Typography.Text type="secondary">{user.email}</Typography.Text>
                    </div>
                    <Badge status={user.status === 1 ? 'success' : 'default'}
                      text={user.status === 1 ? t('access.status.normal') : t('access.status.disabled')} />
                  </div>
                ))}
            </div>
          </div>
        )}

        {selectedRole && roleWorkspaceMode === 'permissions' && (
          <div className="oa-role-workspace">
            {selectedRole.code === 'SUPER_ADMIN' && (
              <Alert type="info" showIcon title={t('access.drawer.superAdminAlert')} />
            )}
            <div className="oa-role-workspace-summary">
              <span>{t('access.drawer.currentlySelected')}</span>
              <strong>{selectedPermissions.length}</strong>
              <span>{t('access.roleCard.permissionsCount')}</span>
            </div>
            <Checkbox.Group
              value={selectedPermissions}
              disabled={selectedRole.code === 'SUPER_ADMIN'}
              onChange={(values) => setSelectedPermissions(values as string[])}
            >
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
          </div>
        )}
      </Drawer>

      <Modal title={t('access.roleModal.title')} open={roleModalOpen} onCancel={() => setRoleModalOpen(false)}
        onOk={() => void createRole()} okText={t('common.create')}>
        <Form form={roleForm} layout="vertical">
          <Form.Item name="code" label={t('access.roleModal.roleCode')}
            rules={[
              { required: true, message: t('access.roleModal.roleCodeRequired') },
              { pattern: /^[A-Z][A-Z0-9_]{2,39}$/, message: t('access.roleModal.roleCodePattern') },
            ]}>
            <Input placeholder={t('access.roleModal.roleCodePlaceholder')} />
          </Form.Item>
          <Form.Item name="name" label={t('access.roleModal.roleName')} rules={[{ required: true }]}>
            <Input placeholder={t('access.roleModal.roleNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('access.roleModal.responsibilityDescription')} rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder={t('access.roleModal.responsibilityPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={organizationModal === 'department' ? t('access.organizationModal.departmentConfig') : t('access.organizationModal.positionConfig')}
        open={Boolean(organizationModal)}
        onCancel={() => { setOrganizationModal(undefined); organizationForm.resetFields(); }}
        onOk={() => void saveOrganizationItem()}
        okText={t('common.save')}
      >
        <Form form={organizationForm} layout="vertical">
          <Form.Item name="code" label={t('access.organizationModal.code')} rules={[
            { required: true },
            { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,59}$/, message: t('access.organizationModal.codePattern') },
          ]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label={t('common.name')} rules={[{ required: true }, { max: 100 }]}>
            <Input />
          </Form.Item>
          {organizationModal === 'department' && (
            <>
              <Form.Item name="parentId" label={t('access.organizationModal.parentDepartment')}>
                <Select allowClear options={(overview?.departments || []).map((item) => ({
                  value: item.id, label: item.name,
                }))} />
              </Form.Item>
              <Form.Item name="defaultApproverUserId" label={t('access.organizationModal.departmentDefaultApprover')}>
                <Select allowClear options={(overview?.users || [])
                  .filter((item) => item.status === 1)
                  .map((item) => ({ value: item.id, label: item.name }))} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <Modal title={editingRoute ? t('access.routeModal.editTitle') : t('access.routeModal.addTitle')} open={routeModalOpen}
        onCancel={() => setRouteModalOpen(false)} onOk={() => void saveRoute()} okText={t('common.save')} width={620}>
        <Form form={routeForm} layout="vertical">
          <Form.Item name="routeKey" label={t('access.routeModal.routeCode')}
            rules={[
              { required: true },
              { pattern: /^[a-z][a-z0-9-]{1,59}$/, message: t('access.routeModal.routeCodePattern') },
            ]}>
            <Input disabled={Boolean(editingRoute)} placeholder={t('access.routeModal.routeCodePlaceholder')} />
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="name" label={t('access.routeModal.displayName')} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="routeType" label={t('access.routeModal.nodeType')} rules={[{ required: true }]}>
              <Select options={[
                { value: 'GROUP', label: t('access.routeModal.routeTypes.GROUP') },
                { value: 'MENU', label: t('access.routeModal.routeTypes.MENU') },
                { value: 'PAGE', label: t('access.routeModal.routeTypes.PAGE') },
              ]} />
            </Form.Item>
          </div>
          <div className="oa-route-form-grid">
            <Form.Item name="parentKey" label={t('access.routeModal.parentNode')}>
              <Select allowClear showSearch options={overview?.routes
                .filter((route) => route.routeKey !== editingRoute?.routeKey && route.routeType !== 'PAGE')
                .map((route) => ({ value: route.routeKey, label: `${route.name} (${route.routeKey})` }))} />
            </Form.Item>
            <Form.Item name="sortOrder" label={t('access.routeModal.sortOrder')} rules={[{ required: true }]}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </div>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.routeType !== next.routeType}>
            {({ getFieldValue }) => getFieldValue('routeType') === 'PAGE' && (
              <div className="oa-route-form-grid">
                <Form.Item name="path" label={t('access.routeModal.pagePath')}
                  rules={[
                    { required: true },
                    { pattern: /^\/oa\/[a-z][a-z0-9-]{1,59}$/, message: t('access.routeModal.pagePathPattern') },
                  ]}>
                  <Input placeholder="/oa/sales-report" />
                </Form.Item>
                <Form.Item name="componentKey" label={t('access.routeModal.pageComponent')} rules={[{ required: true }]}>
                  <Select options={componentOptions} />
                </Form.Item>
              </div>
            )}
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="icon" label={t('access.routeModal.iconCode')}>
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                options={oaMenuIconOptions.map((option) => ({
                  value: option.value,
                  label: t(option.labelKey),
                }))}
                placeholder={t('access.routeModal.iconPlaceholder')}
              />
            </Form.Item>
            <Form.Item name="enabled" label={t('common.enable')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title={editingUser ? t('access.userModal.editTitleWithName', { name: editingUser.name }) : t('access.userModal.editTitle')}
        open={userModalOpen}
        onCancel={() => { setUserModalOpen(false); setEditingUser(null); userForm.resetFields(); }}
        onOk={() => void saveUser()}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
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
          <Form.Item name="roles" label={t('access.userModal.role')} rules={[{ required: true, message: t('access.userModal.roleRequired') }]}>
            <Select
              mode="multiple"
              options={roles.map((role) => ({ value: role.code, label: `${role.name} (${role.code})` }))}
              maxTagCount="responsive"
              placeholder={t('access.userModal.rolePlaceholder')}
            />
          </Form.Item>
          <div className="oa-route-form-grid">
            <Form.Item name="departmentId" label={t('access.columns.department')} rules={[{ required: true, message: t('access.userModal.departmentRequired') }]}>
              <Select
                options={(overview?.departments || []).map((item) => ({ value: item.id, label: item.name }))}
                placeholder={t('access.userModal.departmentPlaceholder')}
              />
            </Form.Item>
            <Form.Item name="positionId" label={t('access.columns.position')} rules={[{ required: true, message: t('access.userModal.positionRequired') }]}>
              <Select
                options={(overview?.positions || []).map((item) => ({ value: item.id, label: item.name }))}
                placeholder={t('access.userModal.positionPlaceholder')}
              />
            </Form.Item>
          </div>
          <Form.Item name="approverUserId" label={t('access.columns.directApprover')}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={(overview?.users || [])
                .filter((item) => item.id !== editingUser?.id && item.status === 1)
                .map((item) => {
                  const deptName = overview?.departments.find((d) => d.id === item.departmentId)?.name;
                  return {
                    value: item.id,
                    label: `${item.name} · ${item.email}${deptName ? ` · ${deptName}` : ''}`,
                  };
                })}
              placeholder={t('access.userModal.directApproverPlaceholder')}
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

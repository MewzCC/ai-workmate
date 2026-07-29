import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AccessControlPage from './AccessControlPage';

const overview = {
  users: [
    {
      id: 1,
      name: '测试管理员',
      email: 'admin@example.com',
      role: 'SUPER_ADMIN',
      roles: ['SUPER_ADMIN'],
      status: 1,
      permissionVersion: 1,
    },
    {
      id: 2,
      name: '人事专员',
      email: 'hr@example.com',
      role: 'HR_MANAGER',
      roles: ['HR_MANAGER'],
      status: 1,
      permissionVersion: 1,
    },
    {
      id: 3,
      name: '停用成员',
      email: 'disabled@example.com',
      role: 'HR_MANAGER',
      roles: ['HR_MANAGER', 'EMPLOYEE'],
      status: 0,
      permissionVersion: 1,
    },
  ],
  roles: [
    {
      code: 'SUPER_ADMIN',
      name: '超级管理员',
      description: '拥有全部权限',
      builtin: true,
      permissions: ['access:manage', 'route:access-control'],
    },
    {
      code: 'HR_MANAGER',
      name: '人事经理',
      description: '维护组织与人员权限',
      builtin: false,
      permissions: ['hr:read'],
    },
  ],
  permissions: [
    {
      code: 'hr:read',
      name: '查看组织人事',
      module: '组织人事',
      description: '查看组织和员工档案',
    },
    {
      code: 'access:manage',
      name: '管理角色权限',
      module: '系统设置',
      description: '配置用户、角色与权限',
    },
  ],
  routes: [],
  departments: [],
  positions: [],
};

const apiMocks = vi.hoisted(() => ({
  overview: vi.fn(),
  updateRolePermissions: vi.fn(),
  updateRoleMembers: vi.fn(),
}));

vi.mock('@/lib/accessControlApi', () => ({
  accessControlApi: {
    overview: apiMocks.overview,
    updateRolePermissions: apiMocks.updateRolePermissions,
    updateRoleMembers: apiMocks.updateRoleMembers,
  },
}));

describe('AccessControlPage', () => {
  afterEach(cleanup);

  beforeEach(() => {
    apiMocks.overview.mockReset();
    apiMocks.updateRolePermissions.mockReset();
    apiMocks.updateRoleMembers.mockReset();
    apiMocks.overview.mockResolvedValue(overview);
    apiMocks.updateRolePermissions.mockImplementation(
      async (roleCode: string, permissionCodes: string[]) => ({
        ...overview.roles.find((role) => role.code === roleCode),
        permissions: permissionCodes,
      }),
    );
    apiMocks.updateRoleMembers.mockResolvedValue(overview);
  });

  it('restores the four access-control work areas', async () => {
    render(<AccessControlPage />);

    expect(await screen.findByText('角色、权限与动态路由')).toBeTruthy();
    expect(screen.getByRole('tab', { name: /角色工作台/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /组织与审批人/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /用户批量配置/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /动态路由/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /新建角色/ })).toBeTruthy();
  });

  it('configures permissions in the side workspace', async () => {
    render(<AccessControlPage />);

    const roleHeading = await screen.findByRole('heading', { name: '人事经理' });
    const roleCard = roleHeading.closest('.oa-role-card');
    expect(roleCard).toBeTruthy();
    fireEvent.click(within(roleCard as HTMLElement).getByRole('button', { name: /配置权限/ }));

    fireEvent.click(screen.getByRole('checkbox', { name: /管理角色权限/ }));
    fireEvent.click(screen.getByRole('button', { name: /保存权限/ }));

    await waitFor(() => {
      expect(apiMocks.updateRolePermissions).toHaveBeenCalledWith(
        'HR_MANAGER',
        expect.arrayContaining(['hr:read', 'access:manage']),
      );
    });
  });

  it('opens searchable multi-member management and keeps disabled members removable', async () => {
    render(<AccessControlPage />);

    const roleHeading = await screen.findByRole('heading', { name: '人事经理' });
    const roleCard = roleHeading.closest('.oa-role-card');
    fireEvent.click(within(roleCard as HTMLElement).getByRole('button', { name: /管理成员/ }));

    expect(await screen.findByText(/可按姓名或邮箱搜索并选择多人/)).toBeTruthy();
    expect(screen.getByRole('combobox')).toBeTruthy();
    expect(screen.getByText('停用成员')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /保存成员/ }));

    await waitFor(() => {
      expect(apiMocks.updateRoleMembers).toHaveBeenCalledWith('HR_MANAGER', [2, 3]);
    });
  });
});

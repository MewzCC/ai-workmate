import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import type { EmployeeDetail, EmployeeDocument, OrganizationOverview } from '@/lib/hrApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ overview: vi.fn(), detail: vi.fn(), listEmployeeDocuments: vi.fn(), uploadEmployeeDocument: vi.fn(), downloadEmployeeDocument: vi.fn() }));
const auth = vi.hoisted(() => ({ me: vi.fn() }));
vi.mock('@/lib/hrApi', () => ({ hrApi: api }));
vi.mock('@/lib/authApi', async (load) => {
  const original = await load<typeof import('@/lib/authApi')>();
  return { ...original, authApi: { ...original.authApi, me: auth.me } };
});
import EmployeeFilePage from './EmployeeFilePage';

const overview: OrganizationOverview = { departments: [], positions: [], employees: [1, 2].map((id) => ({ id,
  name: id === 1 ? '员工甲' : '员工乙', email: `employee${id}@example.invalid`, role: 'EMPLOYEE', status: 1 })) };
function detail(id: number): EmployeeDetail { return { id, name: id === 1 ? '员工甲' : '员工乙', email: `employee${id}@example.invalid`,
  role: 'EMPLOYEE', status: 1, avatarUrl: null, createdAt: null, employmentHistory: [], recentActivities: [],
  attendance: { totalDays: 0, normalDays: 0, lateDays: 0, earlyLeaveDays: 0, lateAndEarlyDays: 0, missingClockDays: 0 } }; }
function documentFor(id: number): EmployeeDocument { return { id, employeeUserId: id, documentType: 'PROFILE', displayName: `档案-${id}.pdf`,
  mimeType: 'application/pdf', fileSize: 100, uploadedByUserId: 7, contentUrl: `/api/hr/employees/${id}/documents/${id}/content`, createdAt: '2026-10-21T10:00:00' }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done; }); return { promise, resolve }; }
function user(version = 1) { return { id: 7, tenantId: 1, name: 'Test', email: 'test@example.invalid', role: 'EMPLOYEE', roles: ['EMPLOYEE'],
  permissions: ['hr:read', 'hr:manage'], dataScopes: ['SELF'], permissionVersion: version }; }
function Snapshot() { return <><output aria-label="employee-context">{JSON.stringify(usePageAgentContextSnapshot())}</output><output aria-label="auth-version">{useAuth().user?.permissionVersion}</output></>; }
function renderPage(path = '/oa/employee-files') { return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App><PageAgentContextProvider pageId="employee-files"><EmployeeFilePage /><Snapshot /></PageAgentContextProvider></App></AuthProvider></MemoryRouter>); }
beforeEach(() => {
  vi.clearAllMocks();
  auth.me.mockResolvedValue(user());
  api.overview.mockResolvedValue(overview);
  api.detail.mockImplementation(async (id: number) => detail(id));
  api.listEmployeeDocuments.mockResolvedValue([]);
});
afterEach(cleanup);

it('publishes only the selected visible employee after validating URL selection', async () => {
  renderPage('/oa/employee-files?id=999');
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('employee-context').textContent || '{}')).toEqual({ employeeId: 1 }));
  expect(api.detail).not.toHaveBeenCalledWith(999);
  fireEvent.click(screen.getByText('员工乙'));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('employee-context').textContent || '{}')).toEqual({ employeeId: 2 }));
});

it('never displays old employee documents when a slower request finishes after switching', async () => {
  const oldDocuments = deferred<EmployeeDocument[]>();
  api.listEmployeeDocuments.mockImplementation((id: number) => id === 1 ? oldDocuments.promise : Promise.resolve([documentFor(2)]));
  renderPage();
  await waitFor(() => expect(api.listEmployeeDocuments).toHaveBeenCalledWith(1));
  fireEvent.click(screen.getByText('员工乙'));
  await screen.findByText('档案-2.pdf');
  await act(async () => oldDocuments.resolve([documentFor(1)]));
  expect(screen.queryByText('档案-1.pdf')).toBeNull();
  expect(screen.getByText('档案-2.pdf')).toBeTruthy();
});

it('reloads visible employees and clears old details and context after a live permission change', async () => {
  api.listEmployeeDocuments.mockImplementation(async (id: number) => [documentFor(id)]);
  renderPage();
  await screen.findByText('档案-1.pdf');
  api.overview.mockResolvedValue({ ...overview, employees: [overview.employees[1]] });
  auth.me.mockResolvedValue(user(2));
  fireEvent.focus(window);
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('2'));
  await waitFor(() => expect(screen.queryByText('档案-1.pdf')).toBeNull());
  await screen.findByText('档案-2.pdf');
  expect(JSON.parse(screen.getByLabelText('employee-context').textContent || '{}')).toEqual({ employeeId: 2 });
});

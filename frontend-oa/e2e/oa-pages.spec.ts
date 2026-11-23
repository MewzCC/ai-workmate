import { expect, test, type Page } from '@playwright/test';
import { enabledOaRoutes, navigationFixture, type OaRouteFixture } from './fixtures/oaRoutes';
import { ENABLED_PAGE_COMPONENT_KEYS } from '../src/types/oa';

const allPermissions = [
  ...enabledOaRoutes.map((route) => `route:${route.routeKey}`),
  'access:manage', 'data:export', 'dictionary:manage', 'tenant:config:manage',
  'data:permission:manage', 'ai:permission:manage', 'workbench:finance:manage',
  'workbench:integration:manage', 'workbench:settings:manage',
  'todo:read',
];

const user = {
  id: 7001,
  name: '回归测试管理员',
  email: 'oa-regression@example.invalid',
  tenantId: 77,
  role: 'SUPER_ADMIN',
  roles: ['SUPER_ADMIN'],
  avatarUrl: null,
  permissions: allPermissions,
  dataScopes: ['ALL'],
  permissionVersion: 1,
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'zh-CN');
    window.localStorage.setItem('workmeta-oa-open-tabs', '[]');
  });
});

test('42 个启用页面在真实浏览器中均可进入且接口失败时不白屏', async ({ page }) => {
  expect(enabledOaRoutes).toHaveLength(42);
  expect(new Set(enabledOaRoutes.map((route) => route.componentKey)))
    .toEqual(new Set(ENABLED_PAGE_COMPONENT_KEYS));
});

test('独立入口在当前视口内显示登录表单和详情页面', async ({ page }) => {
  await page.route('**/api/**', route => json(route, 401, null));
  await page.goto('/oa/auth');
  await expect(page.locator('.account-submit').first()).toBeVisible();
  await expectBodyWithinViewport(page);
  await mockOaBackend(page);
  for (const path of ['/oa/approval-tasks/1', '/oa/knowledge-bases/1']) {
    await page.goto(path);
    await expect(page.locator('.oa-page-transition > *').first()).toBeVisible();
    await expectBodyWithinViewport(page);
    expect(await page.locator('.oa-content').evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
  }
});

for (const route of enabledOaRoutes) {
  test(`${route.routeKey} -> ${route.componentKey}：接口失败时不白屏`, async ({ page }) => {
    await mockOaBackend(page);
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.goto(`/oa/${route.routeKey}`);
    await expect(page.locator('.oa-shell')).toBeVisible();
    await expect(page.locator('.oa-page-transition > *').first()).toBeVisible();
    await expect(page.getByTestId('unsupported-component')).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(`/oa/${route.routeKey}$`));
    await expectBodyWithinViewport(page);

    const overflow = await page.locator('.oa-content').evaluate((element) => ({
      width: element.scrollWidth - element.clientWidth,
      clipped: Array.from(element.querySelectorAll('input:not([type=hidden]), button')).filter((control) => {
        const bounds = control.getBoundingClientRect();
        if (!bounds.width || !bounds.height || !control.checkVisibility()) return false;
        for (let parent = control.parentElement; parent && parent !== element; parent = parent.parentElement) {
          if (parent.matches('.ant-table, .ant-picker, .ant-select, .ant-tabs-nav, .ant-select-selector, .ant-switch')) return false;
          const style = getComputedStyle(parent);
          if (style.overflowX !== 'hidden' && style.overflowY !== 'hidden') continue;
          const container = parent.getBoundingClientRect();
          if (style.overflowX === 'hidden' && bounds.right > container.right + 2
            || style.overflowY === 'hidden' && bounds.bottom > container.bottom + 2) return true;
        }
        return false;
      }).map((control) => control.textContent || control.getAttribute('placeholder') || control.getAttribute('aria-label') || control.tagName),
    }));
    expect(overflow.width).toBeLessThanOrEqual(1);
    expect(overflow.clipped).toEqual([]);
    const detached = await page.locator('.oa-content .ant-card').evaluateAll(cards =>
      cards.flatMap(card => {
        const bottom = card.getBoundingClientRect().bottom;
        return [...card.querySelectorAll('tr, .ant-pagination, .ant-form-item, .ant-empty')].filter(item => {
          if (item.closest('.ant-card') !== card || !item.checkVisibility()
            || item.getBoundingClientRect().bottom <= bottom + 2) return false;
          for (let parent = item.parentElement; parent && parent !== card.parentElement; parent = parent.parentElement) {
            if (getComputedStyle(parent).overflowY !== 'visible' && parent.getBoundingClientRect().bottom <= bottom + 2) return false;
          }
          return true;
        }).map(item => [card.className, item.className]);
      }));
    expect(detached).toEqual([]);
    await expect(page.locator('.oa-workspace-table .ant-table-body .ant-table-placeholder')).toHaveCount(0);
    expect(pageErrors).toEqual([]);
  });
}

for (const routeKey of ['audit-center', 'messages']) {
  test(`${routeKey} 长列表的末行和分页都在卡片内`, async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 1440, height: 700 });
    await mockOaBackend(page, { extraPermissions: ['notification:read:self'] });
    const records = Array.from({ length: 20 }, (_, index) => ({
      id: index + 1, createdAt: '2026-11-20T09:00:00', actorName: '测试',
      resourceType: 'TEST', resourceId: index, action: 'UPDATE', result: 'SUCCESS',
      summary: '审计记录', title: '系统通知', content: '消息内容', type: 'system', read: true,
    }));
    await page.route('**/api/notifications/unread-count', route => json(route, 200, { unread: 0 }));
    const endpoint = routeKey === 'messages' ? 'notifications' : 'audit-records';
    await page.route(`**/api/${endpoint}?**`, route => json(route, 200, { records, page: 1, size: 20, total: 40 }));
    await page.goto(`/oa/${routeKey}`);
    const card = page.locator('.oa-domain-list-card');
    const body = card.locator('.ant-table-body');
    await expect(body.locator('tr[data-row-key]')).toHaveCount(20);
    await body.locator('tr[data-row-key]').last().scrollIntoViewIfNeeded();
    expect(await body.evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1);
    const last = await body.locator('tr[data-row-key]').last().boundingBox();
    const pager = await card.locator('.ant-pagination').boundingBox();
    const bounds = await card.boundingBox();
    expect(last!.y + last!.height).toBeLessThanOrEqual(pager!.y + 1);
    expect(pager!.y + pager!.height).toBeLessThanOrEqual(bounds!.y + bounds!.height);
    await page.setViewportSize({ width: 980, height: 540 });
    await expect.poll(async () => (await body.boundingBox())!.height).toBeGreaterThanOrEqual(220);
    const header = card.locator('.ant-table-sticky-holder');
    await body.locator('tr[data-row-key]').nth(6).scrollIntoViewIfNeeded();
    const headerTop = (await header.boundingBox())!.y;
    await body.locator('tr[data-row-key]').nth(14).scrollIntoViewIfNeeded();
    await expect(header).toBeInViewport();
    expect((await header.boundingBox())!.y).toBeCloseTo(headerTop, 0);
    const bodyBox = (await body.boundingBox())!;
    expect((await card.locator('.ant-pagination').boundingBox())!.y - bodyBox.y - bodyBox.height).toBeGreaterThanOrEqual(16);
  });
}

test('异常考勤长列表滚动后可在卡片内看到分页', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 650 });
  await mockOaBackend(page);
  await page.route('**/api/attendance/exceptions?**', route => json(route, 200, {
    records: Array.from({ length: 10 }, (_, i) => ({
      id: i + 1, userName: '测试', clockDate: '2026-11-20', status: 'LATE', lateMinutes: 10,
    })), total: 20, page: 1, size: 10,
  }));
  await page.goto('/oa/attendance-exception');
  const card = page.locator('.oa-attendance-card--fill');
  await expect(card.locator('tr[data-row-key]')).toHaveCount(10);
  await card.locator('.ant-pagination').scrollIntoViewIfNeeded();
  const pager = await card.locator('.ant-pagination').boundingBox();
  const bounds = await card.boundingBox();
  expect(pager!.y + pager!.height).toBeLessThanOrEqual(bounds!.y + bounds!.height);
});

test('组织统计下方直接显示成员，表格底部保留内边距', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 700 });
  await mockOaBackend(page, { extraPermissions: ['hr:read'] });
  await page.route('**/api/hr/organization', route => json(route, 200, {
    departments: [{ id: 1, name: '总部' }], positions: [],
    employees: Array.from({ length: 13 }, (_, i) => ({
      id: i + 1, name: `成员${i}`, email: 'member@example.invalid', departmentId: 1, status: 1,
    })),
  }));
  await page.goto('/oa/org-tree');
  const card = page.locator('.oa-org-stats + .oa-org-employees-card');
  await expect(card.locator('tr[data-row-key]')).toHaveCount(10);
  const table = card.locator('.ant-table-wrapper');
  const body = table.locator('.ant-table-body');
  await body.locator('tr[data-row-key]').last().scrollIntoViewIfNeeded();
  expect(await body.evaluate(el => el.scrollHeight - el.clientHeight)).toBeLessThanOrEqual(1);
  const bounds = await card.boundingBox();
  const list = await table.boundingBox();
  expect(bounds!.y + bounds!.height - list!.y - list!.height).toBeGreaterThanOrEqual(16);
  await page.locator('.oa-org-heading button').first().click();
  await expect(page.getByRole('dialog').locator('.oa-org-graph')).toBeVisible();
});

test('数据权限按工作区宽度排列策略库和绑定表格', async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await mockOaBackend(page);
  await page.route('**/api/admin/data-permissions', route => json(route, 200, {
    policies: [], roleBindings: [], userExceptions: [], departments: [],
    roles: [{ code: 'SUPER_ADMIN', name: '超级管理员', description: '管理员' }],
    users: [{ id: 1, name: '测试用户', email: 'member@example.invalid' }],
  }));
  await page.setViewportSize({ width: 1000, height: 700 });
  await page.goto('/oa/data-permission');
  const policy = page.locator('.oa-data-permission-policies');
  const bindings = page.locator('.oa-data-permission-bindings');
  await expect(bindings.getByText('超级管理员')).toBeVisible();
  const left = (await policy.boundingBox())!;
  expect((await bindings.boundingBox())!.y).toBeGreaterThanOrEqual(left.y + left.height);
  for (const label of ['角色绑定', '用户例外']) {
    await bindings.getByText(label, { exact: true }).click();
    const row = bindings.locator('tr[data-row-key]');
    expect((await row.locator('td').first().boundingBox())!.width).toBeGreaterThanOrEqual(170);
    await expect(row.locator('.ant-select')).toBeVisible();
    await expectBodyWithinViewport(page);
  }
  await page.setViewportSize({ width: 1800, height: 900 });
  await expect.poll(async () => Math.abs((await policy.boundingBox())!.y - (await bindings.boundingBox())!.y)).toBeLessThan(2);
  await page.setViewportSize({ width: 320, height: 640 });
  const mobile = bindings.locator('.oa-mobile-card');
  await expect(mobile.getByText('测试用户')).toBeVisible();
  await mobile.locator('.ant-select').click();
  await expect(page.locator('.ant-select-dropdown:visible')).toBeVisible();
  await page.keyboard.press('Escape');
  await expectBodyWithinViewport(page);
});

test('租户配置页头、摘要和表单在不同窗口高度下完整显示', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route('**/api/admin/tenant-config', (route) => json(route, 200, {
    id: 1, tenantName: 'AI WorkMate', tenantShortName: 'WorkMate',
    locale: 'zh-CN', timezone: 'Asia/Shanghai', fiscalYearStartMonth: 1,
    approvalEnabled: true, attendanceEnabled: true, assetEnabled: true,
    meetingEnabled: true, visitorEnabled: true, sealEnabled: true,
    defaultApprovalDays: 3, expenseCurrency: 'CNY', passwordMinLength: 8,
    sessionTimeoutMinutes: 120, version: 0, updatedAt: '2026-11-19T12:00:00Z', canManage: true,
  }));
  const cards = page.locator('.oa-tenant-section');
  for (const viewport of [
    { width: 1200, height: 435 }, { width: 1440, height: 800 },
    { width: 1024, height: 768 }, { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/oa/tenant-config');
    await expect(cards).toHaveCount(4);
    const heading = page.locator('.oa-tenant-hero').getByRole('heading');
    const summary = page.locator('.oa-tenant-summary').getByText('WorkMate', { exact: true });
    await expect(heading).toBeInViewport({ ratio: 1 });
    await expect(summary).toBeInViewport({ ratio: 1 });
    const headerOverflow = await page.locator('.oa-tenant-hero, .oa-tenant-summary').evaluateAll(
      (elements) => elements.map((element) => {
        const bounds = element.getBoundingClientRect();
        return Math.max(...Array.from(element.children, (child) => {
          const rect = child.getBoundingClientRect();
          return Math.max(bounds.top - rect.top, rect.bottom - bounds.bottom);
        }));
      }),
    );
    for (const distance of headerOverflow) expect(distance).toBeLessThanOrEqual(1);
    const layout = await cards.evaluateAll((elements) => elements.map((card) => {
      const bounds = card.getBoundingClientRect();
      const actions = card.querySelector('.oa-tenant-actions')!.getBoundingClientRect();
      return {
        top: bounds.top,
        actionsBottom: actions.bottom,
        overflow: Math.max(actions.bottom - bounds.bottom, actions.right - bounds.right),
      };
    }));
    for (const card of layout) expect(card.overflow).toBeLessThanOrEqual(1);
    if (viewport.width > 980) {
      for (const [left, right] of [[layout[0], layout[1]], [layout[2], layout[3]]]) {
        expect(Math.abs(left.top - right.top)).toBeLessThanOrEqual(1);
        expect(Math.abs(left.actionsBottom - right.actionsBottom)).toBeLessThanOrEqual(1);
      }
    }
    const lastActions = cards.last().locator('.oa-tenant-actions');
    await lastActions.scrollIntoViewIfNeeded();
    await expect(lastActions).toBeInViewport({ ratio: 1 });
    await lastActions.getByRole('button').last().click({ trial: true });
    await expectBodyWithinViewport(page);
  }
});

test('知识库详情在卡片内滚动，切换标签后仍可使用各面板', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route('**/api/knowledge/bases/12', (route) => json(route, 200, {
    id: 12, name: '布局测试知识库', description: '知识库详情测试说明', docCount: 3, chunkCount: 314,
    embeddingProvider: 'api', embeddingModel: 'vendor/layout-embedding-model',
    rerankModel: 'vendor/layout-rerank-model', chunkSize: 1000, chunkOverlap: 120, denseTopK: 5, sparseTopK: 5,
    createdAt: '2026-11-19T12:00:00Z', updatedAt: '2026-11-20T12:00:00Z',
  }));
  await page.route('**/api/knowledge/embedding-status', (route) => json(route, 200, { enabled: true }));
  await page.route(/\/api\/knowledge\/documents\?/, (route) => json(route, 200, { records: [], total: 0, page: 1, size: 20 }));
  await page.route(/\/api\/ai\/models\?/, (route) => json(route, 200, []));

  await page.setViewportSize({ width: 1450, height: 600 });
  await page.goto('/oa/knowledge-bases/12');
  const card = page.locator('.oa-knowledge-detail > .oa-domain-card');
  const overview = card.getByRole('tab', { name: '概览' });
  await expect(overview).toBeInViewport();
  const panel = card.getByRole('tabpanel');
  await panel.hover();
  await page.mouse.wheel(0, 5000);
  await expect(card.getByText('稀疏检索数量', { exact: true })).toBeInViewport();
  await expect(overview).toBeInViewport({ ratio: 1 });
  const bounds = await card.boundingBox();
  const content = await panel.boundingBox();
  expect(content!.y + content!.height).toBeLessThanOrEqual(bounds!.y + bounds!.height + 1);

  await card.getByRole('tab', { name: '文档管理' }).click();
  await expect(panel.getByRole('button', { name: /上传/ }).first()).toBeInViewport();
  await card.getByRole('tab', { name: '知识库查询' }).click();
  await expect(panel.getByRole('textbox', { name: /输入检索问题/ })).toBeInViewport();
  await card.getByRole('tab', { name: '设置' }).click();
  const save = panel.getByRole('button', { name: '保存设置' });
  await save.scrollIntoViewIfNeeded();
  await expect(save).toBeInViewport();
  await expect(card.getByRole('tab', { name: '设置' })).toBeInViewport();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  const model = card.getByText('vendor/layout-rerank-model', { exact: true });
  await model.scrollIntoViewIfNeeded();
  await expect(model).toBeInViewport();
  await expectBodyWithinViewport(page);
});

test('AI 操作权限的筛选、角色选择和保存按钮都留在卡片内', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  const tools = Array.from({ length: 32 }, (_, index) => ({
    code: `report.layout.${index + 1}`, name: `Layout report ${index + 1}`,
    description: '读取测试报表摘要。', riskLevel: 'L0', sideEffect: 'NONE', confirmationPolicy: 'NONE',
    requiredPermissions: [], pageIds: ['dashboard'], platformEnabled: true, tenantEnabled: true, effectiveEnabled: false,
  }));
  const roleName = '跨部门报表审批管理员';
  let ready = false;
  await page.route('**/api/admin/ai-operation-permissions', (route) => json(route, 200, {
    runtime: { agentEnabled: ready, planningEnabled: ready, executionEnabled: false, writeToolsEnabled: false },
    tenantPolicy: { enabled: false, writeToolsEnabled: false }, tools,
    roles: ['超级管理员', '系统管理员', '流程管理员', '财务管理员', roleName].map((name, index) => ({
      code: `ROLE_${index}`, name, description: '测试角色说明', builtin: false, immutable: index === 0,
      eligibleToolCodes: tools.map((tool) => tool.code), toolCodes: [],
    })),
  }));

  for (const { width, height } of [
    { width: 1450, height: 700 }, { width: 1450, height: 860 },
    { width: 1060, height: 700 }, { width: 390, height: 844 },
  ]) {
    await page.setViewportSize({ width, height });
    await page.goto('/oa/ai-permission');
    const toolbar = page.locator('.oa-ai-permission-toolbar');
    const search = toolbar.getByRole('textbox');
    const list = page.locator('.oa-ai-permission-tool-list');
    await expect(list.locator('article')).toHaveCount(32);
    const filters = (await toolbar.boundingBox())!;
    const gap = (await list.boundingBox())!.y - filters.y - filters.height;
    expect(gap).toBeGreaterThanOrEqual(0);
    await search.fill('Layout report 32');
    await expect(list.locator('article')).toHaveCount(1);
    await expect(list.getByText('Layout report 32')).toBeVisible();
    await search.fill('no matching report');
    await expect(list.locator('.ant-empty-description')).toHaveText('暂无数据');

    const roles = page.locator('.oa-ai-permission-roles');
    await roles.getByRole('combobox', { name: '角色工具授权' }).click();
    await page.locator('.ant-select-item-option').getByText(roleName, { exact: true }).click();
    await expect(roles.getByRole('heading', { name: roleName })).toBeVisible();
    await roles.getByRole('button', { name: '保存角色授权' }).click({ trial: true });
    expect(await page.locator('.oa-content').evaluate((content) => content.scrollWidth - content.clientWidth)).toBeLessThanOrEqual(1);
    await expectBodyWithinViewport(page);
    ready = true;
  }
});

test('打卡按钮悬停时在亮色和夜间主题保留主题渐变', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['attendance:clock'] });
  await page.route('**/api/attendance/today-status', (route) => json(route, 200, {
    clockDate: '2026-11-20', canClockIn: true, canClockOut: false, lateMinutes: 0, earlyLeaveMinutes: 0,
  }));
  await page.route(/\/api\/attendance\/records\?/, (route) => json(route, 200, { records: [], total: 0, page: 1, size: 50 }));
  await page.route('**/api/attendance/settings', (route) => json(route, 200, {
    workStartTime: '09:00', workEndTime: '18:00', startFlexMinutes: 0, endFlexMinutes: 0, flexLinked: false,
  }));
  await page.goto('/oa/attendance-clock');
  const button = page.locator('.oa-clock-circle');
  for (const dark of [false, true]) {
    if (dark) {
      await page.evaluate(() => window.localStorage.setItem('workmeta-oa-theme', 'home-night'));
      await page.reload();
    }
    await page.mouse.move(0, 0);
    await expect(button).toBeEnabled();
    const restingBackground = await button.evaluate((element) => getComputedStyle(element).backgroundImage);
    await button.hover();
    await expect(button).toHaveCSS('background-image', /^radial-gradient\(/);
    await expect(button).not.toHaveCSS('background-image', restingBackground);
    await expect(button).toHaveCSS('filter', 'none');
    await expect(button.locator('.oa-clock-circle__label')).toHaveCSS('text-shadow', 'none');
    await expect(button.locator('.oa-clock-circle__hint')).toHaveCSS('text-shadow', 'none');
  }
});

test('入转调离的桌面表头和分页保持可见', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['hr:read'] });
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route('**/api/hr/organization', (route) => json(route, 200, { employees: [], departments: [], positions: [] }));
  let populated = false;
  await page.route(/\/api\/hr\/employee-changes\?/, (route) => json(route, 200, {
    records: populated ? Array.from({ length: 20 }, (_, index) => ({
      id: index + 1, employeeName: `Change employee ${index + 1}`, employeeEmail: 'employee@example.invalid',
      changeType: 'TRANSFER', status: 'PENDING', effectiveDate: '2026-11-20', reviewApproverName: 'Reviewer',
    })) : [],
    total: populated ? 40 : 0, page: 1, size: 20,
  }));
  await page.setViewportSize({ width: 1450, height: 700 });
  await page.goto('/oa/employee-change');
  const card = page.locator('.employee-change-table-card');
  const header = card.locator('.ant-table-header');
  await expect(card.getByText('暂无员工变动申请')).toBeInViewport();
  await expect(card.getByRole('columnheader').first()).toBeInViewport({ ratio: 1 });

  populated = true;
  await page.reload();
  const pager = card.locator('.ant-pagination');
  await expect(card.getByText('Change employee 1', { exact: true })).toBeInViewport();
  await pager.scrollIntoViewIfNeeded();
  await expect(pager).toBeInViewport({ ratio: 1 });
  await pager.locator('li[title="2"]').click({ trial: true });
  const headerTop = (await header.boundingBox())!.y;
  await card.locator('.ant-table-body').hover();
  await page.mouse.wheel(0, 5000);
  await expect(card.getByText('Change employee 20', { exact: true })).toBeInViewport();
  await expect(header).toBeInViewport({ ratio: 1 });
  expect((await header.boundingBox())!.y).toBeCloseTo(headerTop, 0);
  await expect(pager).toBeInViewport({ ratio: 1 });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  const last = card.locator('.oa-mobile-card').getByText('Change employee 20', { exact: true });
  await last.scrollIntoViewIfNeeded();
  await expect(last).toBeInViewport();
  await expectBodyWithinViewport(page);
});

test('员工档案的长名单在桌面独立滚动，手机可滚动查看档案', async ({ page }) => {
  await mockOaBackend(page);
  const employees = Array.from({ length: 20 }, (_, index) => ({
    id: index + 1, name: `Layout employee ${index + 1}`, email: `employee-${index + 1}@example.invalid`,
    role: 'EMPLOYEE', status: 1,
  }));
  await page.route('**/api/hr/organization', (route) => json(route, 200, { employees, departments: [], positions: [] }));
  await page.route(/\/api\/hr\/employees\/\d+$/, (route) => {
    const id = Number(new URL(route.request().url()).pathname.split('/').pop());
    return json(route, 200, {
      ...employees[id - 1], employmentHistory: [], recentActivities: [],
      attendance: { totalDays: 0, normalDays: 0, lateDays: 0, earlyLeaveDays: 0, lateAndEarlyDays: 0, missingClockDays: 0 },
    });
  });
  await page.route('**/api/hr/employees/*/documents', (route) => json(route, 200, []));
  const card = page.locator('.oa-employee-file__list-card');
  const list = card.locator('.oa-employee-file__list-body');
  const last = list.getByText('Layout employee 20', { exact: true });
  const detail = page.locator('.oa-employee-file__overview-name');

  await page.setViewportSize({ width: 1440, height: 700 });
  await page.goto('/oa/employee-files');
  await expect(detail).toHaveText('Layout employee 1');
  const top = await card.evaluate((element) => element.closest('.oa-content')!.scrollTop);
  await list.hover({ position: { x: 25, y: 50 } });
  await page.mouse.wheel(0, 5000);
  await expect(last).toBeInViewport();
  await expect(card.locator('.oa-employee-file__search')).toBeInViewport();
  expect(await card.evaluate((element) => element.closest('.oa-content')!.scrollTop)).toBe(top);
  await last.click();
  await expect(detail).toHaveText('Layout employee 20');

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/oa/employee-files');
  await last.click();
  await expect(detail).toHaveText('Layout employee 20');
  await detail.scrollIntoViewIfNeeded();
  await expect(detail).toBeInViewport();
  await expectBodyWithinViewport(page);
});

test('请假申请表单与流程预览在桌面顶端对齐，手机可滚动阅读', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['leave:create'] });
  await page.route('**/api/leave-applications/approval-context', (route) => json(route, 200, {
    applicantName: 'Layout employee', departmentName: 'Layout department', approverConfigured: false,
  }));
  await page.route(/\/api\/leave-applications\/approver-candidates\?/, (route) => json(route, 200, {
    records: [], total: 0, page: 1, size: 20,
  }));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 1440, height: 760 });
  await page.goto('/oa/leave-application');
  const form = page.locator('.leave-form-card');
  const preview = page.locator('.leave-workflow-panel');
  await expect(preview.locator('.leave-workflow-panel__identity').getByText('Layout employee', { exact: true })).toBeVisible();
  await form.evaluate((card) => {
    const content = card.closest('.oa-content')!;
    content.scrollTop += card.getBoundingClientRect().top - content.getBoundingClientRect().top - 24;
  });
  await expect.poll(() => page.locator('.leave-form-card, .leave-workflow-panel').evaluateAll(
    ([form, preview]) => Math.abs(form.getBoundingClientRect().top - preview.getBoundingClientRect().top),
  )).toBeLessThanOrEqual(1);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  const policy = preview.locator('.leave-workflow-panel__policy');
  await policy.scrollIntoViewIfNeeded();
  await expect(policy).toBeInViewport();
  const formBottom = (await form.boundingBox())!;
  expect((await preview.boundingBox())!.y).toBeGreaterThan(formBottom.y + formBottom.height);
  await expectBodyWithinViewport(page);
});

test('AI 任务中心的空态和分页留在卡片内', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route(/\/api\/ai\/tasks\?/, (route) => {
    const empty = new URL(route.request().url()).searchParams.has('status');
    return json(route, 200, {
      records: empty ? [] : Array.from({ length: 20 }, (_, index) => ({
        taskId: `layout-task-${index + 1}`, pageId: 'dashboard', status: 'SUCCEEDED',
        riskLevel: 'L0', createdAt: '2026-11-19T12:00:00Z', updatedAt: '2026-11-19T12:00:00Z',
      })),
      total: empty ? 0 : 40, page: 1, size: 20,
    });
  });
  for (const viewport of [{ width: 1450, height: 700 }, { width: 1024, height: 570 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/oa/ai-tasks');
    const card = page.locator('.agent-task-center__card');
    await expect(card.getByText('layout-task-1', { exact: true })).toBeVisible();

    if (viewport.width === 1450) {
      const table = card.locator('.ant-table-body');
      const pager = card.locator('.ant-pagination');
      await pager.scrollIntoViewIfNeeded();
      await expect(pager).toBeInViewport({ ratio: 1 });
      await pager.locator('li[title="2"]').click({ trial: true });
      await table.hover();
      await page.mouse.wheel(0, 5000);
      await expect(card.getByText('layout-task-20', { exact: true })).toBeInViewport();
      await expect(card.locator('.ant-table-thead')).toBeInViewport();
      await expect(pager).toBeInViewport({ ratio: 1 });
    }

    await card.getByRole('combobox').click();
    await page.locator('.ant-select-item-option').getByText('已接收', { exact: true }).click();
    const empty = card.getByText('当前筛选下暂无 AI 任务');
    await expect(empty).toBeVisible();
    if (viewport.width > 720) {
      await expect(card.locator('.ant-table-body')).toHaveCount(0);
      if (viewport.width === 1450) {
        const content = card.locator('.ant-table-content');
        expect(await content.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
        const errorHeader = card.getByRole('columnheader', { name: '错误码' });
        expect(await errorHeader.evaluate(el => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
      }
    }
    if (viewport.width !== 1450) await empty.scrollIntoViewIfNeeded();
    await expect(empty).toBeInViewport();
    const overflow = await empty.evaluate((text) => {
      const box = text.closest('.ant-empty')!.getBoundingClientRect();
      const cardBounds = text.closest('.agent-task-center__card')!.getBoundingClientRect();
      return box.bottom - cardBounds.bottom;
    });
    expect(overflow).toBeLessThanOrEqual(1);
    await expectBodyWithinViewport(page);
  }
});

test('发起审批的同排模板卡片及操作按钮对齐', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['approval:create', 'leave:create'] });
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route(/\/api\/approval-config\/application-templates\?/, (route) => json(route, 200, {
    records: [{ formId: 1, formKey: 'business-trip', formName: '出差申请单', description: '出差行程与预算申请模板',
      version: 1, fields: [], process: { id: 1, processKey: 'travel', processName: '出差审批', version: 1, nodes: [] } }],
    total: 1, page: 1, size: 20,
  }));
  await page.setViewportSize({ width: 1440, height: 800 });
  await page.goto('/oa/approval-start');
  const cards = page.locator('.approval-start-section').first().locator('.approval-template-card');
  await expect(cards).toHaveCount(2);
  await cards.first().hover();
  await expect.poll(() => cards.evaluateAll(([first, second]) => Math.max(
    Math.abs(first.getBoundingClientRect().top - second.getBoundingClientRect().top),
    Math.abs(first.querySelector('button')!.getBoundingClientRect().bottom -
      second.querySelector('button')!.getBoundingClientRect().bottom),
  ))).toBeLessThanOrEqual(1);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(cards).toHaveCount(2);
  for (const button of await cards.getByRole('button').all()) {
    await button.scrollIntoViewIfNeeded();
    await expect(button).toBeInViewport({ ratio: 1 });
  }
  await expectBodyWithinViewport(page);
});

test('审批中心切换筛选和标签时保留同一权限下的全局统计', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['approval:read'] });
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  let count = 7;
  let statsRequests = 0;
  let releaseStats!: () => void;
  let statsGate = new Promise<void>((resolve) => { releaseStats = resolve; });
  let releaseFilter!: () => void;
  const filteredList = new Promise<void>((resolve) => { releaseFilter = resolve; });
  await page.route('**/api/approval-tasks/stats', async (route) => {
    statsRequests += 1;
    await statsGate;
    await json(route, 200, [{ status: 'PENDING', count }]);
  });
  await page.route(/\/api\/approval-tasks\?/, async (route) => {
    if (new URL(route.request().url()).searchParams.has('status')) await filteredList;
    await json(route, 200, { records: [], total: 0, page: 1, size: 20 });
  });
  await page.goto('/oa/approval-list');
  const metrics = page.locator('.approval-metric-strip strong');
  await expect(metrics).toHaveText(['—', '—', '—', '—', '—']);
  releaseStats();
  await expect(metrics).toHaveText(['7', '0', '0', '0', '7']);
  const requestsBeforeFilter = statsRequests;
  await Promise.all([
    page.waitForRequest((request) => new URL(request.url()).searchParams.get('status') === 'APPROVED'),
    page.locator('.ant-segmented-item-label').getByText('已通过', { exact: true }).click(),
  ]);
  await expect(metrics).toHaveText(['7', '0', '0', '0', '7']);
  expect(statsRequests).toBe(requestsBeforeFilter);
  releaseFilter();
  await expect(page.getByText('当前筛选下暂无审批单')).toBeVisible();
  count = 8;
  await page.locator('.leave-list-toolbar').getByRole('button', { name: '刷新' }).click();
  await expect(metrics).toHaveText(['8', '0', '0', '0', '8']);

  statsGate = new Promise<void>((resolve) => { releaseStats = resolve; });
  count = 9;
  await page.locator('[data-tab-key="dashboard"]').click();
  await expect(metrics).toHaveCount(0);
  await page.locator('[data-tab-key="approval-list"]').click();
  await expect(metrics).toHaveText(['8', '0', '0', '0', '8']);
  releaseStats();
  await expect(metrics).toHaveText(['9', '0', '0', '0', '9']);

  let currentUser = { ...user, permissions: [...allPermissions, 'approval:read'] };
  await page.route('**/api/auth/me', (route) => json(route, 200, currentUser));
  for (const scope of [{ permissionVersion: 2 }, { id: user.id + 1 }]) {
    currentUser = { ...currentUser, ...scope };
    count += 1;
    statsGate = new Promise<void>((resolve) => { releaseStats = resolve; });
    await page.evaluate(() => window.dispatchEvent(new Event('oa-permissions-changed')));
    await expect(metrics).toHaveText(['—', '—', '—', '—', '—']);
    releaseStats();
    await expect(metrics).toHaveText([String(count), '0', '0', '0', String(count)]);
  }
});

test('审批中心完整显示统计数字，且表格和筛选空态留在卡片内', async ({ page }) => {
  await mockOaBackend(page, { extraPermissions: ['approval:read'] });
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  await page.route('**/api/approval-tasks/stats', (route) => json(route, 200, [{ status: 'PENDING', count: 40 }]));
  let responseGate: Promise<void>;
  await page.route(/\/api\/approval-tasks\?/, async (route) => {
    const filtered = new URL(route.request().url()).searchParams.get('status') === 'APPROVED';
    if (filtered) await responseGate;
    const records = filtered ? [] : Array.from({ length: 20 }, (_, index) => ({
      id: index + 1, applicantName: `Approval applicant ${index + 1}`,
      leaveType: 'ANNUAL', startDate: '2026-11-19', endDate: '2026-11-19',
      startPeriod: 'AM', endPeriod: 'PM', durationDays: 1, status: 'PENDING',
    }));
    return json(route, 200, { records, total: filtered ? 0 : 40, page: 1, size: 20 });
  });

  for (const viewport of [{ width: 1450, height: 700 }, { width: 1024, height: 550 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/oa/approval-list');
    const metrics = page.locator('.approval-metric-strip');
    await expect(metrics.getByText('40', { exact: true })).toHaveCount(2);
    const overflow = await metrics.evaluate((strip) => {
      const bottom = strip.getBoundingClientRect().bottom;
      return Math.max(...Array.from(strip.querySelectorAll('strong, small'), (text) => text.getBoundingClientRect().bottom - bottom));
    });
    expect(overflow).toBeLessThanOrEqual(1);

    const card = page.locator('.leave-list-card');
    if (viewport.width === 1450) {
      await card.locator('.ant-pagination').scrollIntoViewIfNeeded();
      await card.locator('.ant-table-body').hover();
      await page.mouse.wheel(0, 5000);
      await expect(card.getByText('Approval applicant 20', { exact: true })).toBeInViewport();
      await expect(card.locator('.ant-pagination')).toBeInViewport({ ratio: 1 });
      await expect(card.locator('.ant-table-sticky-holder')).toBeInViewport();
    }
    await expect(card.getByText('Approval applicant 1', { exact: true })).toBeVisible();
    let finish!: () => void;
    responseGate = new Promise(resolve => { finish = resolve; });
    const requested = page.waitForRequest(request => request.url().includes('status=APPROVED'));
    await card.locator('.ant-segmented-item-label').getByText('已通过', { exact: true }).click();
    await requested;
    const empty = card.getByText('当前筛选下暂无审批单');
    try {
      await expect(card.getByText('Approval applicant 1', { exact: true })).toBeVisible();
      await expect(empty).toHaveCount(0);
    } finally { finish(); }
    await expect(empty).toBeVisible();
    if (viewport.width !== 1450) await empty.scrollIntoViewIfNeeded();
    await expect(empty).toBeInViewport();
    const emptyOverflow = await empty.evaluate((element) =>
      element.getBoundingClientRect().bottom - element.closest('.leave-list-card')!.getBoundingClientRect().bottom,
    );
    expect(emptyOverflow).toBeLessThanOrEqual(1);
    await expectBodyWithinViewport(page);
  }
});

for (const [pageId, resource, prefix] of [
  ['form-engine', 'forms', 'form'],
  ['process-config', 'processes', 'process'],
  ['approval-rules', 'rules', 'rule'],
] as const) {
  test(`${pageId} 的配置列表在卡片内滚动，筛选控件保持同排`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 700 });
    await mockOaBackend(page, { extraPermissions: ['approval:read', 'approval:manage'] });
    const longKey = 'expense-application-international-reimbursement';
    await page.route(new RegExp(`/api/approval-config/${resource}\\?`), (route) => json(route, 200, {
      records: Array.from({ length: 20 }, (_, index) => ({
        id: index + 1,
        [`${prefix}Key`]: index === 0 ? longKey : `record-${index + 1}`,
        [`${prefix}Name`]: `Layout record ${index + 1}`,
        status: 'ENABLED', version: 1, canEdit: true, canDelete: true,
        updatedAt: '2026-11-19T12:00:00Z', schemaJson: '{"fields":[]}', nodeJson: '[]',
        ruleType: 'AMOUNT_THRESHOLD', priority: 0, conditionJson: '{}', actionJson: '{}',
      })),
      total: 40, page: 1, size: 20,
    }));
    await page.goto(`/oa/${pageId}`);
    const card = page.locator('.leave-list-card');
    const toolbar = card.locator('.leave-list-toolbar');
    const key = card.getByRole('button', { name: longKey, exact: true });
    await expect(key).toBeInViewport();
    const centered = await toolbar.locator('.ant-input-search, .ant-select, .ant-space button')
      .evaluateAll((controls) => controls.map((control) => {
        const rect = control.getBoundingClientRect();
        return rect.top + rect.height / 2;
      }));
    expect(centered).toHaveLength(3);
    expect(Math.max(...centered) - Math.min(...centered)).toBeLessThanOrEqual(2);
    const keyOverflow = await key.evaluate((button) => {
      const cell = button.closest('td')!.getBoundingClientRect();
      return button.getBoundingClientRect().right - cell.right;
    });
    expect(keyOverflow).toBeLessThanOrEqual(1);
    const header = card.locator('.ant-table-thead');
    const pager = card.locator('.ant-pagination');
    await expect(header).toBeInViewport();
    await pager.scrollIntoViewIfNeeded();
    await expect(pager).toBeInViewport({ ratio: 1 });
    await pager.locator('li[title="2"]').click({ trial: true });
    await card.locator('.ant-table-body').hover();
    await page.mouse.wheel(0, 5000);
    await expect(card.getByText('record-20', { exact: true })).toBeInViewport();
    await expect(header).toBeInViewport();
    await expect(pager).toBeInViewport({ ratio: 1 });
    await toolbar.scrollIntoViewIfNeeded();
    await expect(toolbar).toBeInViewport({ ratio: 1 });

    await page.setViewportSize({ width: 1024, height: 768 });
    await page.reload();
    const table = card.locator('.ant-table-body');
    await expect(table.locator('tbody tr[data-row-key]').first().locator('td').last().getByRole('button')).toHaveCount(3);
    for (const atEnd of [false, true]) {
      const actionLayout = await table.evaluate((scroller, atEnd) => {
        scroller.scrollLeft = atEnd ? scroller.scrollWidth : 0;
        const header = Array.from(scroller.parentElement!.querySelectorAll('thead th:not(.ant-table-cell-scrollbar)')).at(-1)!.getBoundingClientRect();
        const cell = scroller.querySelector('tbody tr[data-row-key] td:last-child')!;
        const bounds = cell.getBoundingClientRect();
        const right = scroller.getBoundingClientRect().left + scroller.clientWidth;
        return {
          scrollLeft: scroller.scrollLeft,
          columnOffset: Math.abs(header.right - bounds.right),
          rightEdgeOffset: Math.abs(right - bounds.right),
          buttonOverflow: Math.max(...Array.from(cell.querySelectorAll('button'), (button) => {
            const rect = button.getBoundingClientRect();
            return Math.max(bounds.left - rect.left, rect.right - bounds.right);
          })),
        };
      }, atEnd);
      if (atEnd) expect(actionLayout.scrollLeft).toBeGreaterThan(0);
      expect(actionLayout.columnOffset).toBeLessThanOrEqual(1);
      expect(actionLayout.rightEdgeOffset).toBeLessThanOrEqual(2);
      expect(actionLayout.buttonOverflow).toBeLessThanOrEqual(1);
    }

    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload();
    const mobileCards = card.locator('.oa-mobile-cards');
    await expect(mobileCards.getByRole('button', { name: longKey, exact: true })).toBeVisible();
    const last = mobileCards.getByText('record-20', { exact: true });
    await last.scrollIntoViewIfNeeded();
    await expect(last).toBeInViewport();
    await expectBodyWithinViewport(page);
  });
}

test('预算中心可滚动查看末行和分页', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 });
  await mockOaBackend(page);
  await page.route(/\/api\/budgets\?/, (route) => json(route, 200, {
    records: Array.from({ length: 20 }, (_, index) => ({
      id: index + 1, code: `B-${index + 1}`, name: `Layout budget ${index + 1}`, fiscalYear: 2026,
      ownerUserId: 1, ownerLabel: 'Finance', currency: 'CNY', totalAmount: 1000,
      occupiedAmount: 0, spentAmount: 0, availableAmount: 1000, utilizationPercent: 0,
      warningThreshold: 90, alertLevel: 'NORMAL', status: 'ACTIVE', allowedTransitions: [],
      version: 0, updatedAt: '2026-11-19T12:00:00Z', canManage: false,
    })),
    stats: { totalPlans: 40, activePlans: 40, warningPlans: 0, totalAmount: 40000,
      occupiedAmount: 0, spentAmount: 0, availableAmount: 40000 },
    total: 40, page: 1, size: 20, canManage: false,
  }));
  await page.goto('/oa/budget');
  const list = page.locator('.oa-budget-list-card');
  await expect(list.getByText('Layout budget 1', { exact: true })).toBeInViewport();
  await list.locator('.ant-pagination').scrollIntoViewIfNeeded();
  await list.locator('.ant-table-body').hover();
  await page.mouse.wheel(0, 5000);
  await expect(list.getByText('Layout budget 20', { exact: true })).toBeInViewport();
  await expect(list.locator('.ant-pagination')).toBeInViewport({ ratio: 1 });
  const verticalScrollers = await list.evaluate(card => [...card.querySelectorAll('*')].filter(el =>
    /auto|scroll/.test(getComputedStyle(el).overflowY) && el.scrollHeight > el.clientHeight + 1
  ).map(el => el.className));
  expect(verticalScrollers).toEqual([]);
  await page.route(/\/api\/budgets\?/, route => json(route, 200, {
    records: [], total: 0, page: 1, size: 20, canManage: false,
    stats: { totalPlans: 0, activePlans: 0, warningPlans: 0, totalAmount: 0, occupiedAmount: 0, spentAmount: 0, availableAmount: 0 },
  }));
  await page.reload();
  await expect(list.locator('.ant-table-placeholder')).toBeVisible();
  expect(await list.evaluate(card => [...card.querySelectorAll('*')].filter(el =>
    /auto|scroll/.test(getComputedStyle(el).overflowY) && el.scrollHeight > el.clientHeight + 1
  ).length)).toBe(0);
});

test('空待办展示真实空态且页面填充可用工作区', async ({ page }) => {
  await mockOaBackend(page, { emptyTodos: true });
  await page.goto('/oa/todo');

  await expect(page.locator('.oa-shell')).toBeVisible();
  await expect(page.locator('.ant-empty')).toBeVisible();
  const dimensions = await page.locator('.oa-page-transition').evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return { height: rect.height, viewportHeight: window.innerHeight };
  });
  expect(dimensions.height).toBeGreaterThan(Math.min(560, dimensions.viewportHeight * 0.6));
  await expectBodyWithinViewport(page);
});

test('权限快照变化后移除失权页面并回到仍可访问页面', async ({ page }) => {
  const state = { restricted: false };
  await mockOaBackend(page, { state });
  await page.goto('/oa/access-control');
  await expect(page).toHaveURL(/\/oa\/access-control$/);

  state.restricted = true;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));

  await expect(page).toHaveURL(/\/oa\/dashboard$/);
  await expect(page.getByTestId('unsupported-component')).toHaveCount(0);
});

test('角色搜索保持独立单行，并按名称或编码筛选卡片', async ({ page }) => {
  await mockOaBackend(page);
  await page.route('**/api/admin/access-control', (route) => json(route, 200, {
    users: [],
    roles: [
      { code: 'SUPER_ADMIN', name: '超级管理员', builtin: true, permissions: [] },
      { code: 'EMPLOYEE', name: '普通员工', builtin: true, permissions: [] },
    ],
    permissions: [], routes: [], departments: [], positions: [],
  }));
  await page.addInitScript(() => {
    window.localStorage.setItem('workmeta-oa-theme', 'home-night');
    window.localStorage.setItem('workmeta-locale', 'zh-CN');
  });
  await page.goto('/oa/access-control');

  const search = page.getByRole('textbox', { name: '搜索角色' });
  const admin = page.getByRole('heading', { name: '超级管理员' });
  const employee = page.getByRole('heading', { name: '普通员工' });
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 375, height: 812 },
    { width: 320, height: 700 },
  ]) {
    await page.setViewportSize(viewport);
    await expect(admin).toBeVisible();
    await expect(employee).toBeVisible();
    const layout = await page.getByRole('tabpanel', { name: '角色工作台' }).evaluate((panel) => {
      const search = panel.querySelector('.oa-role-search')!.getBoundingClientRect();
      const card = panel.querySelector('.oa-role-card')!.getBoundingClientRect();
      return { searchHeight: search.height, searchWidth: search.width, gap: card.top - search.bottom };
    });
    expect(layout.searchHeight).toBeLessThan(60);
    expect(layout.searchWidth).toBeLessThanOrEqual(320);
    expect(layout.gap).toBeGreaterThan(0);
    await expectBodyWithinViewport(page);
  }

  await search.fill(' employee ');
  await expect(employee).toBeVisible();
  await expect(admin).toHaveCount(0);
  await search.fill('超级');
  await expect(admin).toBeVisible();
  await expect(employee).toHaveCount(0);
  await search.fill('unmatched');
  await expect(page.getByText('没有匹配的角色')).toBeVisible();
  await page.locator('.oa-role-search .ant-input-clear-icon').click();
  await expect(admin).toBeVisible();
  await expect(employee).toBeVisible();
});

test('系统设置的 AI 配置目录集中展示三项配置入口', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => {
    window.localStorage.setItem('workmeta-locale', 'zh-CN');
    window.localStorage.setItem('workmeta-oa-theme', 'home-night');
  });
  await page.route('**/api/admin/ai-configuration/channels', (route) => json(route, 200, []));
  const storedPreferences = {
    model: 'default', maxContextRounds: 8, stream: true, forcePdfOcr: false, initialized: true, ocrModelRef: 'default',
  };
  await page.route('**/api/settings/chat', (route) => {
    if (route.request().method() === 'PUT') Object.assign(storedPreferences, route.request().postDataJSON());
    return json(route, 200, storedPreferences);
  });
  await page.route(/\/api\/ai\/models(?:\?.*)?$/, (route) => {
    const category = new URL(route.request().url()).searchParams.get('category');
    return json(route, 200, category === 'OCR' ? [{ id: '2::reader', category: 'OCR' }] : []);
  });
  await page.goto('/oa/dashboard');

  const sidebar = page.locator('.oa-sider');
  await sidebar.getByRole('menuitem', { name: '系统设置', exact: true }).click();
  await sidebar.getByRole('menuitem', { name: 'AI 配置', exact: true }).click();
  await expect(sidebar.getByRole('menuitem', { name: 'AI 操作权限', exact: true })).toBeVisible();
  await expect(sidebar.getByRole('menuitem', { name: '知识库管理', exact: true })).toBeVisible();
  await sidebar.getByRole('menuitem', { name: '模型与偏好', exact: true }).click();

  await expect(page).toHaveURL(/\/oa\/system-config$/);
  await expect(page.getByRole('heading', { name: '模型与偏好' })).toBeVisible();
  const channels = page.getByRole('tab', { name: 'AI 渠道', exact: true });
  const preferences = page.getByRole('tab', { name: 'AI 使用设置', exact: true });
  const rounds = page.getByRole('spinbutton', { name: '最大上下文轮数' });
  await expect(channels).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('tab', { name: '调用分析' })).toBeVisible();
  await expect(rounds).toBeHidden();
  await preferences.click();
  await expect(rounds).toHaveValue('8');
  await rounds.fill('12');
  await channels.click();
  await expect(rounds).toBeHidden();
  await preferences.click();
  await expect(rounds).toHaveValue('12');
  await page.getByRole('combobox', { name: 'OCR 模型' }).click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: /^2::reader$/ }).click();
  await page.getByRole('button', { name: '保存设置' }).click();
  await expect.poll(() => storedPreferences.ocrModelRef).toBe('2::reader');
  for (const viewport of [{ width: 375, height: 812 }, { width: 320, height: 700 }]) {
    await page.setViewportSize(viewport);
    await expect(preferences).toBeVisible();
    await expect(rounds).toBeVisible();
    await expectBodyWithinViewport(page);
  }
  await page.setViewportSize({ width: 1280, height: 720 });
  await page.reload();
  await expect(sidebar.getByRole('menuitem', { name: '模型与偏好', exact: true })).toBeVisible();
});

test('重新打开 OCR 下拉能看到刚保存到渠道的模型', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  let configuration = {
    baseUrl: 'https://models.example/v1', model: 'chat-model', version: 1, keyConfigured: true,
    models: ['chat-model', 'reader'].map(id => ({
      id, category: 'CHAT', contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false,
    })),
  };
  const channel = () => ({ id: 1, name: '测试渠道', configuration });
  await page.route('**/api/admin/ai-configuration/channels', (route) => json(route, 200, [channel()]));
  await page.route('**/api/admin/ai-configuration/channels/1', (route) => {
    const { models } = route.request().postDataJSON().configuration;
    configuration = { ...configuration, models, version: 2 };
    return json(route, 200, channel());
  });
  const savedPreferences = {
    model: 'default', maxContextRounds: 8, stream: true, forcePdfOcr: false, initialized: true, ocrModelRef: 'default',
  };
  await page.route('**/api/settings/chat', (route) => {
    if (route.request().method() === 'PUT') Object.assign(savedPreferences, route.request().postDataJSON());
    return json(route, 200, savedPreferences);
  });
  let catalogRequests = 0;
  await page.route(/\/api\/ai\/models(?:\?.*)?$/, (route) => {
    const category = new URL(route.request().url()).searchParams.get('category') ?? 'CHAT';
    if (category === 'OCR') catalogRequests += 1;
    return json(route, 200, configuration.models.filter(model => model.category === category)
      .map(model => ({ ...model, id: category === 'CHAT' ? model.id : `1::${model.id}` })));
  });

  await page.goto('/oa/system-config');
  const settings = page.getByRole('tab', { name: 'AI 使用设置', exact: true });
  await settings.click();
  const modelSelect = page.getByRole('combobox', { name: 'OCR 模型', exact: true });
  await expect.poll(() => catalogRequests).toBeGreaterThan(0);
  await modelSelect.click();
  await expect(page.locator('.ant-select-item-option-content').filter({ hasText: '使用服务端默认' })).toBeVisible();
  await expect(page.locator('.ant-select-item-option-content').filter({ hasText: '请先在 AI 渠道勾选并保存对应分类的模型' })).toBeVisible();
  await expect(page.locator('.ant-select-item-option-content').filter({ hasText: '1::reader' })).toHaveCount(0);
  await modelSelect.press('Escape');

  await page.getByRole('tab', { name: 'AI 渠道', exact: true }).click();
  await page.getByRole('button', { name: '配置渠道' }).click();
  const drawer = page.getByRole('dialog');
  await drawer.getByRole('combobox', { name: 'reader 的分类' }).click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: /^OCR 模型$/ }).click();
  await drawer.getByRole('button', { name: /^保\s*存$/ }).click();
  await expect.poll(() => configuration.models.find(model => model.id === 'reader')?.category).toBe('OCR');
  await drawer.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(drawer).toBeHidden();

  await settings.click();
  await modelSelect.click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: /^1::reader$/ }).click({ timeout: 5000 });
  await page.getByRole('button', { name: '保存设置', exact: true }).click();
  await expect.poll(() => savedPreferences.ocrModelRef).toBe('1::reader');
});

test('渠道抽屉展示完整分类名称并自动保留默认对话模型', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => {
    window.localStorage.setItem('workmeta-locale', 'zh-CN');
    window.localStorage.setItem('workmeta-oa-theme', 'home-night');
  });
  const models = [
    { id: 'first-chat', contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false },
    { id: 'current-chat', category: 'CHAT', contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false },
    { id: 'vector', category: 'EMBEDDING', contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false },
  ];
  const configuration = { baseUrl: 'https://models.example/v1', model: 'current-chat', version: 1, models, keyConfigured: true };
  await page.route('**/api/admin/ai-configuration/channels', (route) =>
    json(route, 200, [{ id: 1, name: '测试渠道', configuration }]));
  const saves: unknown[] = [];
  await page.route('**/api/admin/ai-configuration/channels/1', (route) => {
    const payload = route.request().postDataJSON();
    saves.push(payload.configuration.model);
    return json(route, 200, { id: 1, name: payload.name, configuration: { ...configuration, ...payload.configuration } });
  });
  await page.goto('/oa/system-config');
  const card = page.locator('.oa-channel-card');
  const counts = card.locator('.oa-channel-model-counts > div');
  await expect(card.getByText('默认模型', { exact: true })).toHaveCount(0);
  await expect(card.getByText('已启用 3 个模型', { exact: true })).toBeVisible();
  await expect(counts).toHaveCount(8);
  await expect(counts.filter({ hasText: '对话模型' }).locator('dd')).toHaveText('2');
  await expect(counts.filter({ hasText: '嵌入模型' }).locator('dd')).toHaveText('1');
  await expect(counts.filter({ hasText: 'OCR 模型' }).locator('dd')).toHaveText('0');
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 540, height: 820 },
    { width: 375, height: 812 },
    { width: 320, height: 700 },
  ]) {
    await page.setViewportSize(viewport);
    await expect(counts.last()).toBeVisible();
    const heights = await counts.evaluateAll(elements => elements.map(element => element.getBoundingClientRect().height));
    expect(Math.max(...heights) - Math.min(...heights)).toBeLessThanOrEqual(1);
    await expectBodyWithinViewport(page);
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('button', { name: '配置渠道' }).click();
  const drawer = page.getByRole('dialog');
  await expect(drawer.getByText('默认模型', { exact: true })).toHaveCount(0);
  await expect(drawer.getByRole('textbox', { name: '输入上游模型 ID' })).toHaveCount(0);
  await expect(drawer.getByRole('button', { name: '添加模型' })).toHaveCount(0);
  await expect(drawer.getByRole('button', { name: '从上游获取' })).toBeVisible();
  const category = drawer.getByRole('combobox', { name: 'first-chat 的分类', exact: true });
  for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }, { width: 320, height: 700 }]) {
    await page.setViewportSize(viewport);
    await category.click();
    const label = page.locator('.ant-select-item-option-content').filter({ hasText: /^嵌入模型（Embedding）$/ });
    await expect(label).toBeVisible();
    expect(await label.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    await category.press('Escape');
  }
  await drawer.getByRole('button', { name: /^保\s*存$/ }).click();
  await expect.poll(() => saves).toEqual(['current-chat']);
  await drawer.getByRole('checkbox', { name: 'current-chat' }).uncheck();
  await drawer.getByRole('button', { name: /^保\s*存$/ }).click();
  await expect.poll(() => saves).toEqual(['current-chat', 'first-chat']);
  await expect(counts.filter({ hasText: '对话模型' }).locator('dd')).toHaveText('1');
  await expect(card.getByText('已启用 2 个模型', { exact: true })).toHaveCount(1);
});

test('知识库搜索独立于卡片网格，并能筛选名称和描述', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => {
    window.localStorage.setItem('workmeta-locale', 'zh-CN');
    window.localStorage.setItem('workmeta-oa-theme', 'home-night');
  });
  await page.route('**/api/knowledge/bases', (route) => json(route, 200, [
    { id: 101, name: '项目资料', description: '研发参考', docCount: 2, chunkCount: 5, updatedAt: '2026-11-19T12:00:00' },
    { id: 102, name: '组织手册', description: '入职说明', docCount: 1, chunkCount: 3, updatedAt: '2026-11-19T12:00:00' },
  ]));
  await page.goto('/oa/knowledge-base');

  const search = page.getByRole('textbox', { name: '搜索知识库' });
  const cards = page.locator('.oa-knowledge-base-grid > .ant-card');
  await expect(cards).toHaveCount(2);
  for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }, { width: 320, height: 700 }]) {
    await page.setViewportSize(viewport);
    const bounds = await page.locator('.oa-knowledge-base-search').evaluate(element => {
      const input = element.getBoundingClientRect();
      const grid = element.nextElementSibling!.getBoundingClientRect();
      return { height: input.height, width: input.width, gap: grid.top - input.bottom };
    });
    expect(bounds.height).toBeLessThan(60);
    expect(bounds.width).toBeLessThanOrEqual(320);
    expect(bounds.gap).toBeGreaterThan(0);
    await expectBodyWithinViewport(page);
  }

  await search.fill('项目');
  await expect(cards).toHaveText(/项目资料/);
  await search.fill('入职');
  await expect(cards).toHaveText(/组织手册/);
  await search.fill('无匹配关键词');
  await expect(page.getByText('没有匹配的知识库')).toBeVisible();
  await page.locator('.oa-knowledge-base-search .ant-input-clear-icon').click();
  await expect(cards).toHaveCount(2);
});

test('新建知识库保存指定的 Embedding 和 Rerank 渠道模型', async ({ page }) => {
  await mockOaBackend(page);
  await page.addInitScript(() => window.localStorage.setItem('workmeta-locale', 'zh-CN'));
  const created: unknown[] = [];
  await page.route('**/api/knowledge/bases', (route) => {
    if (route.request().method() !== 'POST') return json(route, 200, []);
    created.push(route.request().postDataJSON());
    return json(route, 200, { id: 101, name: '向量测试库' });
  });
  await page.route(/\/api\/ai\/models\?category=(EMBEDDING|RERANK)$/, (route) => {
    const embedding = new URL(route.request().url()).searchParams.get('category') === 'EMBEDDING';
    return json(route, 200, [{ id: embedding ? '2::embedding-model' : '3::reranker' }]);
  });
  await page.goto('/oa/knowledge-base');
  await page.getByRole('button', { name: '新建知识库' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: '知识库名称' }).fill('向量测试库');
  await dialog.getByRole('combobox', { name: /嵌入模型/ }).click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: /^2::embedding-model$/ }).click();
  await dialog.getByRole('combobox', { name: /重排序模型/ }).click();
  await page.locator('.ant-select-item-option-content').filter({ hasText: /^3::reranker$/ }).click();
  await dialog.getByRole('button', { name: /^创\s*建$/ }).click();
  await expect.poll(() => created).toEqual([expect.objectContaining({
    name: '向量测试库', embeddingModelRef: '2::embedding-model', rerankModelRef: '3::reranker',
  })]);
});

for (const viewport of [
  { name: '1440×900', width: 1440, height: 900 },
  { name: '1920×1080', width: 1920, height: 1080 },
  { name: '2560×1080', width: 2560, height: 1080 },
  { name: '390×844', width: 390, height: 844 },
]) {
  test(`${viewport.name} 下代表性页面无视口截断`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await mockOaBackend(page, { emptyTodos: true });
    for (const routeKey of ['dashboard', 'todo', 'meeting-room', 'access-control']) {
      await page.goto(`/oa/${routeKey}`);
      await expect(page.locator('.oa-shell')).toBeVisible();
      await expect(page.locator('.oa-page-transition > *').first()).toBeVisible();
      await expectBodyWithinViewport(page);
    }
  });
}

async function mockOaBackend(
  page: Page,
  options: { emptyTodos?: boolean; state?: { restricted: boolean }; extraPermissions?: string[] } = {},
) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/me') {
      const restricted = options.state?.restricted ?? false;
      await json(route, 200, {
        ...user,
        permissions: restricted ? ['route:dashboard'] : [...user.permissions, ...(options.extraPermissions ?? [])],
        permissionVersion: restricted ? 2 : 1,
      });
      return;
    }
    if (path === '/api/navigation') {
      const routes: readonly OaRouteFixture[] = options.state?.restricted
        ? enabledOaRoutes.filter((item) => item.routeKey === 'dashboard')
        : enabledOaRoutes;
      await json(route, 200, navigationFixture(routes));
      return;
    }
    if (path === '/api/profile/wallpaper') {
      await json(route, 200, { wallpaperUrl: null });
      return;
    }
    if (options.emptyTodos && path === '/api/todos') {
      await json(route, 200, { records: [], total: 0, page: 1, size: 20 });
      return;
    }
    await json(route, 503, null, '回归测试注入的服务不可用');
  });
}

async function json(
  route: Parameters<Parameters<Page['route']>[1]>[0],
  status: number,
  data: unknown,
  message = '成功',
) {
  await route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({ code: status, message, data }),
  });
}

async function expectBodyWithinViewport(page: Page) {
  const dimensions = await page.evaluate(() => ({
    bodyWidth: document.body.scrollWidth,
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: document.documentElement.clientWidth,
  }));
  expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewportWidth + 1);
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth + 1);
}

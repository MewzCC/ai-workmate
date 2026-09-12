import { expect, test, type Page } from '@playwright/test';
import { enabledOaRoutes, navigationFixture, type OaRouteFixture } from './fixtures/oaRoutes';
import { ENABLED_PAGE_COMPONENT_KEYS } from '../src/types/oa';

const allPermissions = [
  ...enabledOaRoutes.map((route) => `route:${route.routeKey}`),
  'access:manage', 'data:export', 'dictionary:manage', 'tenant:config:manage',
  'data:permission:manage', 'ai:permission:manage', 'workbench:finance:manage',
  'workbench:integration:manage', 'workbench:settings:manage',
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

test('41 个启用页面在真实浏览器中均可进入且接口失败时不白屏', async ({ page }) => {
  expect(enabledOaRoutes).toHaveLength(41);
  expect(new Set(enabledOaRoutes.map((route) => route.componentKey)))
    .toEqual(new Set(ENABLED_PAGE_COMPONENT_KEYS));
  await mockOaBackend(page);
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));

  for (const route of enabledOaRoutes) {
    await test.step(`${route.routeKey} -> ${route.componentKey}`, async () => {
      await page.goto(`/oa/${route.routeKey}`);
      await expect(page.locator('.oa-shell')).toBeVisible();
      await expect(page.locator('.oa-page-transition > *').first()).toBeVisible();
      await expect(page.getByTestId('unsupported-component')).toHaveCount(0);
      await expect(page).toHaveURL(new RegExp(`/oa/${route.routeKey}$`));
      await expectBodyWithinViewport(page);
    });
  }

  expect(pageErrors).toEqual([]);
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
  options: { emptyTodos?: boolean; state?: { restricted: boolean } } = {},
) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/me') {
      const restricted = options.state?.restricted ?? false;
      await json(route, 200, {
        ...user,
        permissions: restricted ? ['route:dashboard'] : user.permissions,
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

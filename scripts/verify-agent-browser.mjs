import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { mkdir, writeFile } from 'node:fs/promises';
import { verifyAssetLifecycle } from './verify-asset-lifecycle-browser.mjs';

// This driver only runs against the owned loopback backend started by AgentLiveBrowserIT.
const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(repository, 'frontend-oa', 'package.json'));
const { chromium } = require('playwright');
const { createServer } = require('vite');
const apiPort = Number(process.env.AGENT_BROWSER_API_PORT);
assert(Number.isInteger(apiPort) && apiPort > 0 && apiPort < 65536);
const uiPort = Number(process.env.AGENT_BROWSER_UI_PORT);
assert(Number.isInteger(uiPort) && uiPort > 0 && uiPort < 65536);
const cookie = process.env.AGENT_BROWSER_COOKIE;
const applicant = process.env.AGENT_BROWSER_APPLICANT;
const notice = process.env.AGENT_BROWSER_NOTICE;
const chatMarker = process.env.AGENT_BROWSER_CHAT_MARKER;
const cancellableTask = process.env.AGENT_BROWSER_CANCELLABLE_TASK;
const leaveWrite = process.env.AGENT_BROWSER_MODE === 'leave-write';
const allPages = process.env.AGENT_BROWSER_MODE === 'pages';
const identitySwitch = process.env.AGENT_BROWSER_MODE === 'identity';
const permissionRefresh = process.env.AGENT_BROWSER_MODE === 'permissions';
const recovery = process.env.AGENT_BROWSER_MODE === 'recovery';
const assetLifecycle = process.env.AGENT_BROWSER_MODE === 'asset-lifecycle';
const workspaceWrite = leaveWrite && process.env.AGENT_BROWSER_SURFACE === 'workspace';
const leaveReason = process.env.AGENT_BROWSER_LEAVE_REASON;
const leaveDate = process.env.AGENT_BROWSER_LEAVE_DATE;
assert(cookie?.startsWith('oa_browser_it='));
assert(allPages || identitySwitch || permissionRefresh || recovery || assetLifecycle || (leaveWrite ? /^browser-leave-[a-f0-9-]{36}$/.test(leaveReason) && /^\d{4}-\d{2}-\d{2}$/.test(leaveDate)
  : applicant && notice && chatMarker?.startsWith('browser-chat-') && /^[a-f0-9-]{36}$/.test(cancellableTask)));
const artifacts = path.join(repository, 'output', 'playwright', assetLifecycle ? 'asset-lifecycle-browser' : recovery ? 'agent-recovery-browser' : permissionRefresh ? 'agent-permission-browser' : identitySwitch ? 'agent-identity-browser' : allPages ? 'oa-all-pages' : workspaceWrite ? 'agent-leave-workspace-browser' : leaveWrite ? 'agent-leave-browser' : 'agent-read-browser');
let server, browser, page, phase = 'startup';
let pageErrors = 0;
const responses = [];
const writeRequests = [];
const pendingResources = new Map();
const resourceCategory = request => {
  const root = new URL(request.url()).pathname.match(/^\/api\/([a-z-]+)/)?.[1];
  return root ? `api:${root}` : `resource:${request.resourceType()}`;
};
try {
  server = await createServer({
    configFile: path.join(repository, 'frontend-oa', 'vite.config.ts'),
    root: path.join(repository, 'frontend-oa'),
    logLevel: 'silent',
    server: { host: '127.0.0.1', port: uiPort, strictPort: true,
      proxy: { '/api': { target: `http://127.0.0.1:${apiPort}` } } },
  });
  await server.listen();
  const address = server.httpServer.address();
  assert(address && typeof address !== 'string');
  assert.equal(address.port, uiPort); // Exactly the origin whitelisted by this test's backend.
  const base = `http://127.0.0.1:${address.port}`;
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
  await context.addCookies([{ name: 'oa_browser_it', value: cookie.slice(cookie.indexOf('=') + 1),
    domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Strict', secure: false }]);
  await context.addInitScript(() => { localStorage.setItem('workmeta-locale', 'zh-CN'); });
  page = await context.newPage();
  page.setDefaultTimeout(15_000);
  page.on('pageerror', () => { pageErrors += 1; });
  page.on('request', (request) => {
    const category = resourceCategory(request);
    pendingResources.set(category, (pendingResources.get(category) ?? 0) + 1);
    const match = new URL(request.url()).pathname.match(/^\/api\/ai\/tasks\/[^/]+\/(execute|confirmation-token)$/);
    if (match) writeRequests.push(match[1]); // Never retain headers, body or credentials.
  });
  const resourceFinished = request => {
    const category = resourceCategory(request);
    pendingResources.set(category, Math.max(0, (pendingResources.get(category) ?? 0) - 1));
  };
  page.on('requestfinished', resourceFinished);
  page.on('requestfailed', resourceFinished);
  page.on('response', (response) => {
    const url = new URL(response.url());
    if (/^\/api\/(?:chat\/stream|ai\/(?:intents|tasks\/plan|tasks\/[^/]+\/(?:execute|cancel|confirmation-token)))$/.test(url.pathname)) {
      responses.push({ action: url.pathname.endsWith('/stream') ? 'chat' : url.pathname.endsWith('/cancel') ? 'cancel' : url.pathname.endsWith('/execute') ? 'execute' : url.pathname.endsWith('/confirmation-token') ? 'confirm' : url.pathname.endsWith('/plan') ? 'plan' : 'intent', status: response.status() });
    }
  });
  await mkdir(artifacts, { recursive: true });
  if (assetLifecycle) {
    phase = 'asset lifecycle';
    await verifyAssetLifecycle({ page, context, base, artifacts });
    assert.equal(pageErrors, 0); assert.equal(writeRequests.length, 0);
  } else if (recovery) {
    const waitingTask = process.env.AGENT_BROWSER_WAITING_TASK, queuedTask = process.env.AGENT_BROWSER_QUEUED_TASK;
    const unknownTask = process.env.AGENT_BROWSER_UNKNOWN_TASK;
    const waitingTitle = process.env.AGENT_BROWSER_WAITING_TITLE, queuedTitle = process.env.AGENT_BROWSER_QUEUED_TITLE;
    const unknownTitle = process.env.AGENT_BROWSER_UNKNOWN_TITLE;
    for (const task of [waitingTask, queuedTask, unknownTask]) assert(/^[a-f0-9-]{36}$/.test(task));
    for (const title of [waitingTitle, queuedTitle, unknownTitle]) assert(/^recovery-(waiting|queued|unknown)-[a-f0-9-]{36}$/.test(title));
    const reopen = async title => {
      await page.goto(`${base}/oa/ai-workspace`);
      await page.locator('.ai-session-list').getByText(title, { exact: true }).click();
    };
    const taskState = async task => {
      const response = await context.request.get(`${base}/api/ai/tasks/${task}`);
      assert.equal(response.status(), 200);
      return (await response.json()).data.status;
    };
    const planCard = page.locator('.ai-operation-plan');
    const statusCard = page.locator('.ai-operation-status');
    phase = 'recovery waiting confirmation after refresh';
    await reopen(waitingTitle);
    await planCard.getByText('计划尚未执行', { exact: true }).waitFor();
    await page.reload();
    await page.locator('.ai-session-list').getByText(waitingTitle, { exact: true }).click();
    await planCard.getByText('计划尚未执行', { exact: true }).waitFor();
    assert.equal(await taskState(waitingTask), 'WAITING_CONFIRMATION');
    assert.equal(writeRequests.length, 0);
    phase = 'recovery cancellation never delivered';
    let cancelAttempts = 0;
    await page.route(`**/api/ai/tasks/${waitingTask}/cancel`, async route => {
      cancelAttempts += 1;
      await route.abort('failed'); // No server command was delivered.
    });
    await planCard.getByRole('button', { name: '放弃计划', exact: true }).click();
    await page.locator('.ant-message-error').waitFor();
    assert.equal(await taskState(waitingTask), 'WAITING_CONFIRMATION');
    assert.equal(await planCard.count(), 1);
    assert.equal(cancelAttempts, 1);
    phase = 'recovery cancellation committed but response lost';
    await page.unroute(`**/api/ai/tasks/${waitingTask}/cancel`);
    await page.route(`**/api/ai/tasks/${waitingTask}/cancel`, async route => {
      cancelAttempts += 1;
      const response = await route.fetch();
      assert.equal(response.status(), 200);
      await route.abort('failed'); // Real cancellation committed; only delivery is lost.
    });
    await planCard.getByRole('button', { name: '放弃计划', exact: true }).click();
    await statusCard.getByText(waitingTask, { exact: true }).waitFor();
    assert.equal(await taskState(waitingTask), 'CANCELLED');
    assert.equal(await planCard.count(), 0);
    assert.equal(cancelAttempts, 2);
    await statusCard.getByRole('button', { name: '刷新任务与结果', exact: true }).click();
    assert.equal(cancelAttempts, 2);
    console.log('Browser acceptance: pending plan survived reload and undelivered cancellation; one lost real cancellation response was reconciled without retry');
    phase = 'recovery queued task after refresh';
    await reopen(queuedTitle);
    await statusCard.getByText(queuedTask, { exact: true }).waitFor();
    await page.reload();
    await page.locator('.ai-session-list').getByText(queuedTitle, { exact: true }).click();
    await statusCard.getByText(queuedTask, { exact: true }).waitFor();
    assert.equal(await taskState(queuedTask), 'QUEUED');
    assert.equal(writeRequests.length, 0);
    await page.getByRole('button', { name: '停止生成', exact: true }).click();
    await statusCard.getByText('已取消', { exact: true }).waitFor();
    assert.equal(await taskState(queuedTask), 'CANCELLED');
    phase = 'recovery uncertain terminal result';
    await reopen(unknownTitle);
    await statusCard.getByText(unknownTask, { exact: true }).waitFor();
    await statusCard.getByText('结果待核对', { exact: true }).waitFor();
    await statusCard.getByText('执行结果尚不确定，业务可能已生效。请核对任务记录与业务页面，不要重复执行。', { exact: true }).waitFor();
    assert.equal(await planCard.count(), 0);
    assert.equal(await page.getByRole('button', { name: '确认并执行', exact: true }).count(), 0);
    await page.reload();
    await page.locator('.ai-session-list').getByText(unknownTitle, { exact: true }).click();
    await statusCard.getByText(unknownTask, { exact: true }).waitFor();
    await statusCard.getByRole('button', { name: '刷新任务与结果', exact: true }).click();
    assert.equal(await taskState(unknownTask), 'PARTIALLY_SUCCEEDED');
    assert.equal(writeRequests.length, 0);
    assert.equal(pageErrors, 0);
    for (const viewport of [{ width: 1440, height: 900 }, { width: 1920, height: 1080 }, { width: 2560, height: 1440 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(viewport);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      if (viewport.width <= 720) {
        // Wait for the existing sidebar margin transition, so screenshots measure settled mobile layout.
        await page.waitForFunction(() => Math.abs(document.querySelector('.ai-chat-window').getBoundingClientRect().left) <= 1);
      }
      const clippedWorkspaceElements = await page.locator('.ai-chat-header, .ai-composer-wrap, .ai-message, .ai-message-avatar, .ai-operation-status, .ai-operation-status .ant-typography').evaluateAll(elements => {
        const viewport = document.documentElement.clientWidth;
        return elements.filter(element => {
          const bounds = element.getBoundingClientRect();
          return bounds.left < -1 || bounds.right > viewport + 1;
        }).map(element => element.className);
      });
      assert.deepEqual(clippedWorkspaceElements, [], `Restored task content must fit ${viewport.width}x${viewport.height}`);
      await page.screenshot({ path: path.join(artifacts, `unknown-result-${viewport.width}x${viewport.height}.png`) });
    }
    console.log('Browser acceptance: queued task recovered without enqueueing; unknown terminal state remained visible without confirmation or repeated execution');
  } else if (permissionRefresh) {
    const adminCookie = process.env.AGENT_BROWSER_ADMIN_COOKIE;
    const readerRole = process.env.AGENT_BROWSER_READER_ROLE;
    assert(adminCookie?.startsWith('oa_browser_it=') && /^HTTP_IT_[A-F0-9]{24}$/.test(readerRole));
    assert(/^permission-notice-[a-f0-9-]{36}$/.test(notice));
    const permissionPath = `${base}/api/admin/access-control/roles/${readerRole}/permissions`;
    const initialPermissions = ['route:messages', 'route:dashboard', 'notification:read:self'];
    const update = codes => context.request.put(permissionPath, {
      headers: { Cookie: adminCookie, Origin: base }, data: { permissionCodes: codes },
    });
    phase = 'permissions initial read';
    await page.goto(`${base}/oa/messages`);
    const privateRow = page.locator('.oa-notification-title').filter({ hasText: notice });
    await privateRow.waitFor();
    const unauthorized = await context.request.put(permissionPath, {
      headers: { Origin: base }, data: { permissionCodes: initialPermissions },
    });
    assert.equal(unauthorized.status(), 403);
    const unknown = await update(['route:messages', 'agent:tool:missing.query']);
    assert.equal(unknown.status(), 400);
    phase = 'permissions delayed authorized read';
    let release, markCaptured, holdNext = true;
    const released = new Promise(resolve => { release = resolve; });
    const captured = new Promise(resolve => { markCaptured = resolve; });
    const pending = [];
    await page.route(/\/api\/notifications\?/, async route => {
      if (!holdNext) return route.continue();
      holdNext = false;
      const response = await route.fetch();
      assert.equal(response.status(), 200);
      markCaptured();
      const delivery = released.then(() => route.fulfill({ response })).catch(() => undefined);
      pending.push(delivery); await delivery;
    });
    await page.getByRole('button', { name: '刷新', exact: true }).click();
    let timer;
    try {
      await Promise.race([captured, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Authorized read was not captured')), 15000); })]);
    } finally { clearTimeout(timer); }
    phase = 'permissions revoke through actual administrator API';
    assert.equal((await update(['route:messages', 'route:dashboard'])).status(), 200);
    assert.equal((await context.request.get(`${base}/api/notifications?page=1&size=20`)).status(), 403);
    const revoked = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.status() === 200);
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    assert(!(await (await revoked).json()).data.permissions.includes('notification:read:self'));
    await page.getByText('当前角色无权限执行该操作', { exact: true }).waitFor();
    assert.equal(await privateRow.count(), 0);
    assert.equal(await page.getByRole('button', { name: '全部已读', exact: true }).count(), 0);
    release(); await Promise.all(pending); await page.waitForLoadState('networkidle');
    assert.equal(await privateRow.count(), 0);
    console.log('Browser acceptance: real permission revocation denied the API, removed cached private rows and ignored the delayed authorized response');
    phase = 'permissions restore without relogin';
    assert.equal((await update(initialPermissions)).status(), 200);
    const restored = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.status() === 200);
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    assert((await (await restored).json()).data.permissions.includes('notification:read:self'));
    await privateRow.waitFor();
    assert.equal((await context.request.get(`${base}/api/notifications?page=1&size=20`)).status(), 200);
    assert.equal(writeRequests.length, 0);
    assert.equal(pageErrors, 0);
    await page.screenshot({ path: path.join(artifacts, 'permission-restored.png') });
    console.log('Browser acceptance: restored hierarchical permission reloaded real data without relogin; non-admin and unregistered authority changes were rejected');
  } else if (identitySwitch) {
    const secondCookie = process.env.AGENT_BROWSER_SECOND_COOKIE;
    const secondUser = Number(process.env.AGENT_BROWSER_SECOND_USER);
    const delayedConversation = process.env.AGENT_BROWSER_DELAYED_CONVERSATION;
    const firstTitle = process.env.AGENT_BROWSER_FIRST_TITLE, delayedTitle = process.env.AGENT_BROWSER_DELAYED_TITLE;
    const secondTitle = process.env.AGENT_BROWSER_SECOND_TITLE;
    const firstMessage = process.env.AGENT_BROWSER_FIRST_MESSAGE, delayedMessage = process.env.AGENT_BROWSER_DELAYED_MESSAGE;
    const secondMessage = process.env.AGENT_BROWSER_SECOND_MESSAGE;
    assert(secondCookie?.startsWith('oa_browser_it=') && Number.isSafeInteger(secondUser) && secondUser > 0);
    assert(/^[1-9][0-9]*$/.test(delayedConversation));
    assert([firstTitle, delayedTitle, secondTitle, firstMessage, delayedMessage, secondMessage].every(value => typeof value === 'string' && value.length < 100));
    let release, markCaptured, captured = 0;
    const pending = [];
    const released = new Promise(resolve => { release = resolve; });
    const capturedBoth = new Promise(resolve => { markCaptured = resolve; });
    // Metadata is real. Image delivery is deliberately blocked; this test does not exercise MinIO.
    await page.route('**/api/profile/wallpaper/content*', route => route.abort('blockedbyclient'));
    const profileWrites = [];
    page.on('request', request => {
      if (new URL(request.url()).pathname.startsWith('/api/profile') && !['GET', 'OPTIONS'].includes(request.method())) profileWrites.push(request.method());
    });
    await page.route(new RegExp(`/api/conversations/${delayedConversation}/messages$`), async route => {
      const response = await route.fetch(); // The actual authenticated response is delayed, never fabricated.
      assert.equal(response.status(), 200);
      captured++;
      if (captured >= 2) markCaptured();
      const delivery = released.then(() => route.fulfill({ response })).catch(() => undefined); // Aborting an old read is also safe.
      pending.push(delivery);
      await delivery;
    });
    phase = 'identity initial account';
    await page.goto(`${base}/oa/ai-workspace`);
    await page.locator('.oa-wallpaper-layer').waitFor();
    await page.locator('.ai-session-item').filter({ hasText: firstTitle }).click();
    await page.locator('.ai-message-assistant').filter({ hasText: firstMessage }).waitFor();
    await page.locator('.ai-session-item').filter({ hasText: delayedTitle }).click();
    let captureTimer;
    try {
      await Promise.race([capturedBoth, new Promise((_, reject) => { captureTimer = setTimeout(() => reject(new Error('Delayed read was not captured')), 15000); })]);
    } finally { clearTimeout(captureTimer); }
    await page.locator('.ai-composer textarea').fill('private unsent draft from the first account');
    phase = 'identity capture real old authentication failures';
    let authCaptured = 0, markAuthCaptured;
    const capturedAuth = new Promise(resolve => { markAuthCaptured = resolve; });
    await page.route(/\/api\/notifications\?late-auth=(401|403)$/, async route => {
      const expected = Number(new URL(route.request().url()).searchParams.get('late-auth'));
      const headers = await route.request().allHeaders();
      if (expected === 401) headers.cookie = 'oa_browser_it=invalid-synthetic-session';
      const response = await route.fetch({ headers }); // Real backend rejection; never fabricate success or an auth body.
      assert.equal(response.status(), expected);
      authCaptured++; if (authCaptured === 2) markAuthCaptured();
      const delivery = released.then(() => route.fulfill({ response }));
      pending.push(delivery); await delivery;
    });
    const oldAuthResults = page.evaluate(async () => {
      const { request } = await import('/oa/src/lib/oaApi.ts');
      return Promise.all([401, 403].map(async status => {
        try { await request(`/notifications?late-auth=${status}`); return 0; }
        catch (error) { return error.status; }
      }));
    });
    void oldAuthResults.catch(() => undefined); // Later assertions still await the original promise.
    try {
      await Promise.race([capturedAuth, new Promise((_, reject) => { captureTimer = setTimeout(() => reject(new Error('Old auth failures were not captured')), 15000); })]);
    } finally { clearTimeout(captureTimer); }
    let markWallpaperCaptured, releaseWallpaper;
    const wallpaperCaptured = new Promise(resolve => { markWallpaperCaptured = resolve; });
    const wallpaperReleased = new Promise(resolve => { releaseWallpaper = resolve; });
    await page.route('**/api/profile/wallpaper', async route => {
      const response = await route.fetch(); assert.equal(response.status(), 200);
      assert.equal((await response.json()).data.wallpaperUrl, null);
      markWallpaperCaptured(); await wallpaperReleased; await route.fulfill({ response });
    });
    const localLegacy = 'data:image/png;base64,c3ludGhldGlj';
    await page.evaluate(value => localStorage.setItem('workmeta-oa-wallpaper', value), localLegacy);
    phase = 'identity account switch without reload';
    await context.addCookies([{ name: 'oa_browser_it', value: secondCookie.slice(secondCookie.indexOf('=') + 1),
      domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Strict', secure: false }]);
    const refreshed = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.status() === 200);
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    assert.equal((await (await refreshed).json()).data.id, secondUser);
    await page.locator('.ai-session-item').filter({ hasText: secondTitle }).waitFor();
    try {
      await Promise.race([wallpaperCaptured, new Promise((_, reject) => { captureTimer = setTimeout(() => reject(new Error('Replacement wallpaper metadata was not captured')), 15000); })]);
    } finally { clearTimeout(captureTimer); }
    assert.equal(await page.locator('.oa-wallpaper-layer').count(), 0); // New metadata has not been delivered yet.
    releaseWallpaper();
    assert.equal(await page.locator('.ai-session-item').filter({ hasText: firstTitle }).count(), 0);
    assert.equal(await page.locator('.ai-composer textarea').inputValue(), '');
    phase = 'identity delayed response after switch';
    release();
    await Promise.all(pending);
    assert.deepEqual(await oldAuthResults, [401, 403]);
    phase = 'identity replacement network settled';
    await page.waitForLoadState('networkidle');
    assert.equal(await page.locator('.oa-wallpaper-layer').count(), 0);
    assert.equal(profileWrites.length, 0);
    assert.equal(await page.evaluate(() => localStorage.getItem('workmeta-oa-wallpaper')), localLegacy);
    assert.equal(await page.locator('.ai-message-assistant').filter({ hasText: firstMessage }).count(), 0);
    assert.equal(await page.locator('.ai-message-assistant').filter({ hasText: delayedMessage }).count(), 0);
    phase = 'identity replacement conversation read';
    await page.locator('.ai-session-item').filter({ hasText: secondTitle }).click();
    await page.locator('.ai-message-assistant').filter({ hasText: secondMessage }).waitFor();
    const forbidden = await context.request.get(`${base}/api/conversations/${delayedConversation}/messages`);
    assert.equal(forbidden.status(), 403);
    console.log('Browser acceptance: account switch discarded old messages, unsent draft and delayed real HTTP responses; foreign conversation read was denied');
    console.log('Browser acceptance: delayed real 401 and 403 from the old request scope did not expire the new account');
    console.log('Browser acceptance: account-owned wallpaper metadata was removed before replacement read delivery; unowned local legacy image was retained without upload');
    phase = 'identity same-account permission refresh';
    await page.locator('.ai-composer textarea').fill('current account draft retained during refresh');
    const sameAccount = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/me' && response.status() === 200);
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    assert.equal((await (await sameAccount).json()).data.id, secondUser);
    assert.equal(await page.locator('.ai-composer textarea').inputValue(), 'current account draft retained during refresh');
    await page.locator('.ai-message-assistant').filter({ hasText: secondMessage }).waitFor();
    assert.equal(responses.filter(response => ['cancel', 'execute', 'confirm'].includes(response.action)).length, 0);
    assert.equal(pageErrors, 0);
    await page.screenshot({ path: path.join(artifacts, 'identity-isolation.png') });
    console.log('Browser acceptance: same-account refresh preserved the current draft and messages without implicit task cancellation or execution');
  } else if (allPages) {
    const pageIds = JSON.parse(process.env.AGENT_BROWSER_PAGE_IDS);
    assert(Array.isArray(pageIds) && pageIds.length === 42 && pageIds.every(id => /^[a-z][a-z0-9-]{1,59}$/.test(id)));
    const failures = [];
    page.on('response', response => {
      const url = new URL(response.url());
      if (url.pathname.startsWith('/api/') && response.status() >= 400)
        failures.push({ route: url.pathname, status: response.status() });
    });
    const results = [];
    for (const viewport of [{ width: 1440, height: 900 }, { width: 1920, height: 1080 }, { width: 2560, height: 1440 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(viewport);
      for (const id of pageIds) {
        phase = `page ${id} at ${viewport.width}x${viewport.height}`;
        console.log(`Browser acceptance: checking page=${id}; viewport=${viewport.width}x${viewport.height}`);
        const beforeErrors = pageErrors, beforeFailures = failures.length;
        try {
          await page.goto(`${base}/oa/${id}`);
          await page.locator('.oa-page-transition').waitFor({ state: 'visible' });
          await page.waitForLoadState('networkidle');
          assert.equal(new URL(page.url()).pathname, `/oa/${id}`);
          assert.equal(await page.getByTestId('unsupported-component').count(), 0);
          assert((await page.locator('.oa-page-transition').innerText()).trim().length > 0);
          assert.equal(pageErrors, beforeErrors);
          assert.equal(failures.length, beforeFailures);
          const dimensions = await page.evaluate(() => ({ width: window.innerWidth, content: document.documentElement.scrollWidth }));
          assert(dimensions.content <= dimensions.width + 2);
          if (['meeting-room', 'platform-observability'].includes(id))
            await page.screenshot({ path: path.join(artifacts, `${id}-${viewport.width}.png`) });
          results.push({ pageId: id, ...viewport, passed: true });
        } catch (error) {
          results.push({ pageId: id, ...viewport, passed: false, error: error instanceof Error ? error.name : 'Unknown',
            pageErrors: pageErrors - beforeErrors, failedReads: failures.slice(beforeFailures) });
          console.log(`Browser acceptance: page check FAILED page=${id}; viewport=${viewport.width}x${viewport.height}`);
        }
        await writeFile(path.join(artifacts, 'results.json'), JSON.stringify({ complete: false, results, pageErrors, failedReads: failures }, null, 2));
      }
    }
    await writeFile(path.join(artifacts, 'results.json'), JSON.stringify({ complete: true, results, pageErrors, failedReads: failures }, null, 2));
    assert(results.every(result => result.passed));
    console.log(`Browser acceptance: ${results.length} real page/viewport checks passed without page errors or failed read APIs`);
  } else if (leaveWrite) {
    phase = 'leave page load';
    await page.goto(`${base}/oa/${workspaceWrite ? 'ai-workspace' : 'leave-application'}`);
    if (!workspaceWrite) await page.getByRole('button', { name: '打开 AI 操作面板', exact: true }).click();
    const drawer = page.locator('.oa-ai-operation-drawer');
    const composer = workspaceWrite ? page.locator('.ai-composer textarea') : drawer.getByRole('textbox');
    const planCard = workspaceWrite ? page.locator('.ai-operation-plan') : drawer;
    const planned = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/ai/tasks/plan', { timeout: 45_000 });
    await composer.fill(`请帮我直接提交${leaveDate}上午至当天下午的事假申请，原因填写：${leaveReason}，使用系统为我配置的审批人。`);
    if (workspaceWrite) await composer.press('Enter');
    else await drawer.getByRole('button', { name: /发送 \/ 生成计划/ }).click();
    const planResponse = await planned;
    assert.equal(planResponse.status(), 200);
    const plan = (await planResponse.json()).data;
    assert.equal(plan.status, 'WAITING_CONFIRMATION');
    assert.equal(plan.confirmationRequired, true);
    assert.equal(plan.steps.length, 1);
    assert.equal(plan.steps[0].toolCode, 'leave.apply');
    assert.equal(plan.steps[0].arguments.reason, leaveReason);
    assert.equal(plan.steps[0].arguments.startDate, leaveDate);
    assert.equal(await composer.inputValue(), '');
    const noWrites = async () => {
      assert.equal(writeRequests.length, 0);
      const mine = await context.request.get(`${base}/api/leave-applications/mine`);
      assert.equal(mine.status(), 200);
      const data = (await mine.json()).data;
      assert.equal(Array.isArray(data) ? data.length : data.total, 0);
    };
    phase = 'leave cancelled confirmation';
    await planCard.getByRole('button', { name: '确认并执行', exact: true }).click();
    const modal = page.getByRole('dialog', { name: workspaceWrite ? '确认执行这项 OA 操作？' : '确认执行 AI 计划' });
    await modal.getByText(leaveReason, { exact: true }).waitFor();
    await noWrites();
    await modal.getByRole('button', { name: /取\s*消/, exact: true }).click();
    await modal.waitFor({ state: 'hidden' });
    await noWrites();
    console.log('Browser acceptance: leave plan and cancelled confirmation caused no write or confirmation request');
    phase = 'leave confirmed execution';
    await planCard.getByRole('button', { name: '确认并执行', exact: true }).click();
    await modal.getByRole('button', { name: workspaceWrite ? '确认并执行' : '确认执行', exact: true }).click();
    const result = workspaceWrite ? page.locator('.ai-message-assistant').filter({ hasText: /已提交请假申请.*等待审批/ }) : drawer.getByText(/已提交请假申请.*等待审批/);
    await result.first().waitFor({ timeout: 45_000 });
    await modal.waitFor({ state: 'hidden' });
    assert.equal(responses.filter((r) => r.action === 'confirm' && r.status === 200).length, 1);
    assert.equal(responses.filter((r) => r.action === 'execute' && r.status === 202).length, 1);
    assert.deepEqual(writeRequests, ['confirmation-token', 'execute']);
    if (workspaceWrite) {
      assert.equal(await page.locator('.ai-operation-plan').count(), 0);
      assert.equal(await composer.inputValue(), '');
      await page.reload();
      await page.locator('.ai-session-item').click();
      await result.first().waitFor();
      assert.equal(await page.locator('.ai-message-assistant').count(), 1);
      await result.getByRole('button', { name: '前往对应业务页面', exact: true }).click();
      await page.waitForURL('**/oa/my-applications');
      await page.getByRole('tab').filter({ hasText: 'AI 工作空间' }).first().click();
      await page.waitForURL('**/oa/ai-workspace');
      await result.first().waitFor();
      assert.deepEqual(writeRequests, ['confirmation-token', 'execute']);
      assert.equal(responses.filter((r) => r.action === 'plan').length, 1);
      assert.equal(responses.filter((r) => r.action === 'intent').length, 1);
      assert.equal(responses.filter((r) => r.action === 'intent' && r.status === 200).length, 1);
      assert.equal(responses.filter((r) => r.action === 'chat').length, 0);
      assert.equal(pageErrors, 0);
      await page.screenshot({ path: path.join(artifacts, 'confirmed-result.png') });
      console.log('Browser acceptance: workspace actual confirmed leave, persisted conversation and business navigation passed');
    } else {
    await page.waitForURL('**/oa/my-applications');
    await page.screenshot({ path: path.join(artifacts, 'confirmed-result.png') });
    phase = 'leave result route persistence';
    await drawer.getByRole('textbox').fill('尚未发送的后续请求');
    await drawer.getByRole('button', { name: /^(Close|关闭)$/ }).click();
    await page.getByRole('menuitem').filter({ hasText: 'AI 工作空间' }).click();
    await page.waitForURL('**/oa/ai-workspace');
    assert.equal(await page.getByRole('button', { name: '打开 AI 操作面板', exact: true }).count(), 0);
    await page.getByRole('tab').filter({ hasText: '请假申请' }).first().click();
    await page.waitForURL('**/oa/leave-application');
    await page.getByRole('button', { name: '打开 AI 操作面板', exact: true }).click();
    await drawer.getByText(/已提交请假申请.*等待审批/).first().waitFor();
    assert.equal(await drawer.getByRole('textbox').inputValue(), '尚未发送的后续请求');
    assert.equal(responses.filter((r) => r.action === 'plan').length, 1);
    assert.equal(responses.filter((r) => r.action === 'execute').length, 1);
    assert.deepEqual(writeRequests, ['confirmation-token', 'execute']);
    assert.equal(pageErrors, 0);
    console.log('Browser acceptance: one confirmed leave write, readable actual result, cleared input and route persistence passed');
    }
  } else {
  phase = 'dashboard load';
  await page.goto(`${base}/oa/dashboard`);
  phase = 'drawer open';
  await page.getByRole('button', { name: '打开 AI 操作面板', exact: true }).click();
  const drawer = page.locator('.oa-ai-operation-drawer');
  phase = 'drawer input';
  await drawer.getByRole('textbox').fill('查一下我的待办。');
  phase = 'drawer send';
  await drawer.getByRole('button', { name: /发送 \/ 生成计划/ }).click();
  phase = 'drawer automatic result';
  await drawer.locator('.oa-ai-result-card').getByText(applicant, { exact: false }).first().waitFor({ timeout: 45_000 });
  await page.waitForURL('**/oa/todo');
  assert.equal(await drawer.getByRole('textbox').inputValue(), '');
  await page.screenshot({ path: path.join(artifacts, 'drawer-result.png') });
  console.log('Browser acceptance: drawer actual result, automatic navigation and cleared input passed');

  phase = 'drawer route persistence-close';
  await drawer.getByRole('textbox').fill('下一条尚未发送');
  await drawer.getByRole('button', { name: /^(Close|关闭)$/ }).click();
  phase = 'drawer route persistence-switch';
  await page.getByRole('tab').filter({ hasText: '企业驾驶舱' }).first().click();
  await page.waitForURL('**/oa/dashboard');
  await page.getByRole('button', { name: '打开 AI 操作面板', exact: true }).click();
  await drawer.locator('.oa-ai-result-card').getByText(applicant, { exact: false }).first().waitFor();
  assert.equal(await drawer.getByRole('textbox').inputValue(), '下一条尚未发送');
  console.log('Browser acceptance: drawer messages, result and unsent draft survived route switch');

  phase = 'workspace normal chat';
  await page.goto(`${base}/oa/ai-workspace`);
  const composer = page.locator('.ai-composer textarea');
  const submit = async (input) => {
    await page.getByRole('button', { name: '发送消息', exact: true }).waitFor();
    await composer.fill(input);
    await composer.press('Enter');
  };
  const chat = async (input, index) => {
    const streamed = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/chat/stream', { timeout: 45_000 });
    await submit(input);
    assert.equal((await streamed).status(), 200);
    await page.locator('.ai-message-assistant').nth(index).filter({ hasText: chatMarker }).waitFor({ timeout: 45_000 });
    await page.locator('.ai-message-content-streaming').waitFor({ state: 'hidden', timeout: 45_000 });
    assert.equal(await composer.inputValue(), '');
  };
  await chat(`你好，请记住短语“${chatMarker}”，原样回复这个短语即可。`, 0);
  await chat('刚才我让你记住的短语是什么？请原样复述。', 1);
  console.log('Browser acceptance: actual streaming chat and server conversation context passed');

  phase = 'workspace actual read';
  await submit('请帮我查询我的通知。');
  const reply = page.locator('.ai-message-assistant').filter({ hasText: notice });
  await reply.first().waitFor({ timeout: 45_000 });
  assert.equal(await composer.inputValue(), '');
  phase = 'workspace permission read';
  await submit('请查询我自己的实时角色和权限码。');
  await page.locator('.ai-message-assistant').nth(3).filter({ hasText: 'user-permission:read:self' }).waitFor({ timeout: 45_000 });
  assert.equal(await composer.inputValue(), '');
  phase = 'workspace chat after governed operation';
  await chat('再复述我最开始让你记住的短语。', 4);
  phase = 'workspace persisted conversation';
  await page.reload();
  await page.locator('.ai-session-item').click(); // Reopen the actual saved conversation, not browser storage.
  await page.locator('.ai-message-assistant').nth(4).filter({ hasText: chatMarker }).waitFor();
  await page.locator('.ai-message-assistant').nth(2).filter({ hasText: notice }).waitFor();
  await page.locator('.ai-message-assistant').nth(3).filter({ hasText: 'user-permission:read:self' }).waitFor();
  assert.equal(await page.locator('.ai-message-assistant').count(), 5);
  console.log('Browser acceptance: permission query, return to normal chat and persisted mixed conversation passed');
  await page.screenshot({ path: path.join(artifacts, 'workspace-result.png') });
  await reply.getByRole('button', { name: '前往对应业务页面', exact: true }).click();
  await page.waitForURL('**/oa/messages');
  phase = 'actual task cancellation';
  await page.goto(`${base}/oa/ai-tasks`);
  const row = page.locator(`tr[data-row-key="${cancellableTask}"]`);
  await row.getByRole('button', { name: /取\s*消/, exact: true }).click();
  await page.getByRole('button', { name: '确认取消', exact: true }).click();
  await row.getByText('已取消', { exact: true }).waitFor();
  await page.getByRole('button', { name: '确认取消', exact: true }).waitFor({ state: 'hidden' });
  assert.equal(await row.getByRole('button', { name: /取\s*消/, exact: true }).count(), 0);
  await page.screenshot({ path: path.join(artifacts, 'cancelled-task.png') });
  assert.equal(pageErrors, 0);
  assert.equal(responses.filter((item) => item.action === 'plan' && item.status === 200).length, 3);
  assert.equal(responses.filter((item) => item.action === 'execute' && item.status === 202).length, 3);
  assert.equal(responses.filter((item) => item.action === 'intent' && item.status === 200).length, 5);
  assert.equal(responses.filter((item) => item.action === 'chat' && item.status === 200).length, 3);
  assert.equal(responses.filter((item) => item.action === 'cancel' && item.status === 200).length, 1);
  console.log('Browser acceptance: workspace actual result, business link, cleared input and zero page errors passed');
  console.log('Browser acceptance: task centre confirmed cancellation through the real owned API');
  }
} catch (error) {
  // Do not print exception payloads, cookies, response bodies, model settings or DOM dumps.
  if (page) await page.screenshot({ path: path.join(artifacts, 'failure.png') }).catch(() => undefined);
  console.log(`Browser acceptance: FAILED phase=${phase}; route=${page ? new URL(page.url()).pathname : 'none'}; error=${error instanceof Error ? error.name : 'Unknown'}; API statuses=${JSON.stringify(responses)}`);
  console.log(`Browser acceptance: pending resource categories=${JSON.stringify([...pendingResources].filter(([, count]) => count > 0))}`);
  process.exitCode = 1;
} finally {
  try { await browser?.close(); }
  finally { await server?.close(); }
}

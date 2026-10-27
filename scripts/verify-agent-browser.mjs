import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { mkdir } from 'node:fs/promises';

// This driver only runs against the owned loopback backend started by AgentLiveBrowserIT.
const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(repository, 'fonted-oa', 'package.json'));
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
const workspaceWrite = leaveWrite && process.env.AGENT_BROWSER_SURFACE === 'workspace';
const leaveReason = process.env.AGENT_BROWSER_LEAVE_REASON;
const leaveDate = process.env.AGENT_BROWSER_LEAVE_DATE;
assert(cookie?.startsWith('oa_browser_it='));
assert(leaveWrite ? /^browser-leave-[a-f0-9-]{36}$/.test(leaveReason) && /^\d{4}-\d{2}-\d{2}$/.test(leaveDate)
  : applicant && notice && chatMarker?.startsWith('browser-chat-') && /^[a-f0-9-]{36}$/.test(cancellableTask));
const artifacts = path.join(repository, 'output', 'playwright', workspaceWrite ? 'agent-leave-workspace-browser' : leaveWrite ? 'agent-leave-browser' : 'agent-read-browser');
let server, browser, page, phase = 'startup';
let pageErrors = 0;
const responses = [];
const writeRequests = [];
try {
  server = await createServer({
    configFile: path.join(repository, 'fonted-oa', 'vite.config.ts'),
    root: path.join(repository, 'fonted-oa'),
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
    const match = new URL(request.url()).pathname.match(/^\/api\/ai\/tasks\/[^/]+\/(execute|confirmation-token)$/);
    if (match) writeRequests.push(match[1]); // Never retain headers, body or credentials.
  });
  page.on('response', (response) => {
    const url = new URL(response.url());
    if (/^\/api\/(?:chat\/stream|ai\/(?:intents|tasks\/plan|tasks\/[^/]+\/(?:execute|cancel|confirmation-token)))$/.test(url.pathname)) {
      responses.push({ action: url.pathname.endsWith('/stream') ? 'chat' : url.pathname.endsWith('/cancel') ? 'cancel' : url.pathname.endsWith('/execute') ? 'execute' : url.pathname.endsWith('/confirmation-token') ? 'confirm' : url.pathname.endsWith('/plan') ? 'plan' : 'intent', status: response.status() });
    }
  });
  await mkdir(artifacts, { recursive: true });
  if (leaveWrite) {
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
  process.exitCode = 1;
} finally {
  try { await browser?.close(); }
  finally { await server?.close(); }
}

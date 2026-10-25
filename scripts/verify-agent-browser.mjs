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
const cookie = process.env.AGENT_BROWSER_COOKIE;
const applicant = process.env.AGENT_BROWSER_APPLICANT;
const notice = process.env.AGENT_BROWSER_NOTICE;
assert(cookie?.startsWith('oa_browser_it=') && applicant && notice);
const artifacts = path.join(repository, 'output', 'playwright', 'agent-read-browser');
let server, browser, page, phase = 'startup';
let pageErrors = 0;
const responses = [];
try {
  server = await createServer({
    configFile: path.join(repository, 'fonted-oa', 'vite.config.ts'),
    root: path.join(repository, 'fonted-oa'),
    logLevel: 'silent',
    server: { host: '127.0.0.1', port: 0, strictPort: true,
      proxy: { '/api': { target: `http://127.0.0.1:${apiPort}` } } },
  });
  await server.listen();
  const address = server.httpServer.address();
  assert(address && typeof address !== 'string');
  const base = `http://127.0.0.1:${address.port}`;
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
  await context.addCookies([{ name: 'oa_browser_it', value: cookie.slice(cookie.indexOf('=') + 1),
    domain: '127.0.0.1', path: '/', httpOnly: true, sameSite: 'Strict', secure: false }]);
  await context.addInitScript(() => { localStorage.setItem('workmeta-locale', 'zh-CN'); });
  page = await context.newPage();
  page.setDefaultTimeout(15_000);
  page.on('pageerror', () => { pageErrors += 1; });
  page.on('response', (response) => {
    const url = new URL(response.url());
    if (/^\/api\/ai\/(?:intents|tasks\/plan|tasks\/[^/]+\/execute)$/.test(url.pathname)) {
      responses.push({ action: url.pathname.endsWith('/execute') ? 'execute' : url.pathname.endsWith('/plan') ? 'plan' : 'intent', status: response.status() });
    }
  });
  await mkdir(artifacts, { recursive: true });
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

  phase = 'workspace actual read';
  await page.goto(`${base}/oa/ai-workspace`);
  const composer = page.locator('.ai-composer textarea');
  await composer.fill('请帮我查询我的通知。');
  await composer.press('Enter');
  const reply = page.locator('.ai-message-assistant').filter({ hasText: notice });
  await reply.first().waitFor({ timeout: 45_000 });
  assert.equal(await composer.inputValue(), '');
  await page.screenshot({ path: path.join(artifacts, 'workspace-result.png') });
  await reply.getByRole('button', { name: '前往对应业务页面', exact: true }).click();
  await page.waitForURL('**/oa/messages');
  assert.equal(pageErrors, 0);
  assert.equal(responses.filter((item) => item.action === 'plan' && item.status === 200).length, 2);
  assert.equal(responses.filter((item) => item.action === 'execute' && item.status === 202).length, 2);
  assert.equal(responses.filter((item) => item.action === 'intent' && item.status === 200).length, 1);
  console.log('Browser acceptance: workspace actual result, business link, cleared input and zero page errors passed');
} catch (error) {
  // Do not print exception payloads, cookies, response bodies, model settings or DOM dumps.
  if (page) await page.screenshot({ path: path.join(artifacts, 'failure.png') }).catch(() => undefined);
  console.log(`Browser acceptance: FAILED phase=${phase}; route=${page ? new URL(page.url()).pathname : 'none'}; error=${error instanceof Error ? error.name : 'Unknown'}; API statuses=${JSON.stringify(responses)}`);
  process.exitCode = 1;
} finally {
  try { await browser?.close(); }
  finally { await server?.close(); }
}

import assert from 'node:assert/strict';
import path from 'node:path';

/** Called only by the owned loopback browser fixture; no live user session is used. */
export async function verifyAssetLifecycle({ page, context, base, artifacts }) {
  const assetId = Number(process.env.AGENT_BROWSER_ASSET_ID);
  const departmentA = Number(process.env.AGENT_BROWSER_DEPARTMENT_A), departmentB = Number(process.env.AGENT_BROWSER_DEPARTMENT_B);
  const ownerA = Number(process.env.AGENT_BROWSER_OWNER_A), ownerB = Number(process.env.AGENT_BROWSER_OWNER_B);
  const code = process.env.AGENT_BROWSER_ASSET_CODE;
  for (const id of [assetId, departmentA, departmentB, ownerA, ownerB]) assert(Number.isSafeInteger(id) && id > 0);
  assert(/^lifecycle-[a-f0-9-]{36}$/.test(code));
  const assetPath = `/api/admin-assets/assets/${assetId}`;
  let phase = 'initial page', pendingRelease;
  const writes = [];
  page.on('response', response => {
    const request = response.request(), url = new URL(response.url());
    if (url.pathname.startsWith(assetPath) && ['POST', 'PUT'].includes(request.method())) {
      writes.push({ action: url.pathname.slice(assetPath.length) || 'metadata', status: response.status() });
    }
  });
  const read = async () => {
    const response = await context.request.get(`${base}${assetPath}`);
    assert.equal(response.status(), 200); return (await response.json()).data;
  };
  const row = page.locator(`tr[data-row-key="${assetId}"]`);
  const dialog = title => page.getByRole('dialog', { name: title, exact: true });
  const select = async (modal, label, text) => {
    await modal.getByLabel(label, { exact: true }).click();
    await page.locator('.ant-select-dropdown:visible').getByText(text, { exact: true }).click();
  };
  try {
    await page.goto(`${base}/oa/asset-ledger`);
    await row.getByText(code, { exact: true }).waitFor();
    const me = await context.request.get(`${base}/api/auth/me`);
    assert.equal(me.status(), 200);
    const permissions = (await me.json()).data.permissions;
    assert(permissions.includes('asset:write') && permissions.includes('assets:read'));
    assert(!permissions.includes('hr:read') && !permissions.includes('hr:manage') && !permissions.includes('access:manage'));

    phase = 'real metadata save delivered after a new form opens';
    let committed;
    const committedSignal = new Promise(resolve => { committed = resolve; });
    const release = new Promise(resolve => { pendingRelease = resolve; });
    await page.route(`**${assetPath}`, async route => {
      if (route.request().method() !== 'PUT') { await route.continue(); return; }
      const response = await route.fetch(); assert.equal(response.status(), 200);
      console.log('Browser acceptance: delayed real metadata write committed');
      committed(); await release; await route.fulfill({ response });
    });
    await row.getByRole('button', { name: '编辑', exact: true }).click();
    const editor = dialog('编辑资产');
    await editor.getByLabel('备注', { exact: true }).fill('Lifecycle edited metadata');
    await editor.getByRole('button', { name: /保\s*存/, exact: true }).click();
    phase = 'waiting for real metadata commit';
    await committedSignal;
    phase = 'closing editor while response is delayed';
    await editor.getByRole('button', { name: /关\s*闭/, exact: true }).click();
    phase = 'opening replacement create form';
    await page.getByRole('button', { name: '新增资产', exact: true }).click();
    const create = dialog('新增资产');
    await create.getByLabel('资产名称', { exact: true }).fill('Unsaved replacement draft');
    const refreshed = page.waitForResponse(response => new URL(response.url()).pathname === '/api/admin-assets/assets' && response.request().method() === 'GET');
    phase = 'receiving old save after new draft';
    pendingRelease(); await refreshed;
    assert.equal(await create.isVisible(), true);
    assert.equal(await create.getByLabel('资产名称', { exact: true }).inputValue(), 'Unsaved replacement draft');
    await create.getByRole('button', { name: /取\s*消/, exact: true }).click();
    await page.unroute(`**${assetPath}`);
    let current = await read(); assert.equal(current.version, 1);
    assert.equal(current.remark, 'Lifecycle edited metadata');
    await row.getByRole('button', { name: '资产详情', exact: true }).click();
    const detail = dialog(`${code} · Lifecycle asset`);
    await detail.waitFor();
    const operation = async ({ action, title, suffix, status, configure }) => {
      phase = `operation ${suffix}`;
      const previous = current.version;
      await detail.getByRole('button', { name: action, exact: true }).click();
      const modal = dialog(title); await modal.waitFor();
      if (configure) await configure(modal);
      const response = page.waitForResponse(result => new URL(result.url()).pathname === `${assetPath}/${suffix}` && result.request().method() === 'POST');
      await modal.getByRole('button', { name: /确\s*定/, exact: true }).click();
      assert.equal((await response).status(), 200);
      await modal.waitFor({ state: 'hidden' });
      current = await read(); assert.equal(current.version, previous + 1); assert.equal(current.status, status);
      assert.equal(current.history.length, current.version - 1);
    };
    await operation({ action: '办理领用', title: '资产领用', suffix: 'claim', status: 'IN_USE', configure: async modal => {
      await select(modal, '目标部门', 'Lifecycle department A'); await select(modal, '目标保管人', 'Lifecycle owner A');
    } });
    assert.equal(current.departmentId, departmentA); assert.equal(current.ownerUserId, ownerA);

    phase = 'version conflict refuses duplicate mutation';
    const conflict = await context.request.post(`${base}${assetPath}/return`, { data: { version: 0 } });
    assert.equal(conflict.status(), 409); assert.equal((await read()).version, current.version);

    await operation({ action: '跨部门调拨', title: '资产调拨', suffix: 'transfer', status: 'IN_USE', configure: async modal => {
      await select(modal, '目标部门', 'Lifecycle department B'); await select(modal, '目标保管人', 'Lifecycle owner B');
    } });
    assert.equal(current.departmentId, departmentB); assert.equal(current.ownerUserId, ownerB);
    await operation({ action: '办理归还', title: '资产归还', suffix: 'return', status: 'IDLE' });
    assert.equal(current.ownerUserId, null);
    await operation({ action: '登记维修', title: '登记资产维修', suffix: 'repairs', status: 'REPAIRING', configure: modal => modal.getByLabel('操作原因', { exact: true }).fill('Synthetic maintenance') });
    await operation({ action: '完成维修', title: '完成资产维修', suffix: 'repairs/complete', status: 'IDLE', configure: modal => modal.getByLabel('操作原因', { exact: true }).fill('Synthetic maintenance completed') });
    await operation({ action: '登记盘点', title: '登记资产盘点', suffix: 'inventories', status: 'IDLE' });
    assert.equal(current.history[0].inventoryResult, 'MATCH');
    await operation({ action: '办理报废', title: '办理资产报废', suffix: 'scrap', status: 'SCRAPPED', configure: modal => modal.getByLabel('操作原因', { exact: true }).fill('Synthetic disposal') });
    for (const action of ['办理领用', '跨部门调拨', '登记维修', '登记盘点']) assert.equal(await detail.getByRole('button', { name: action, exact: true }).isDisabled(), true);
    assert.equal(await detail.locator('.ant-timeline-item').count(), 7);
    assert.deepEqual(writes.filter(item => item.status === 200).map(item => item.action), ['metadata', '/claim', '/transfer', '/return', '/repairs', '/repairs/complete', '/inventories', '/scrap']);
    // Diagnostic capture only: closing animation may still be visible; assertions above prove business state.
    await page.screenshot({ path: path.join(artifacts, 'asset-lifecycle-complete.png') });
    console.log('Browser acceptance: real asset metadata race, claim, transfer, return, repair, inventory, scrap and stale-version rejection passed');
  } catch (error) {
    console.log(`Browser acceptance: asset phase=${phase}; error=${error instanceof Error ? error.name : 'Unknown'}`);
    throw error;
  } finally { pendingRelease?.(); }
}

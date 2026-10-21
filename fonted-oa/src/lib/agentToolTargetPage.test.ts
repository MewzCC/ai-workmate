import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { agentToolTargetPage, agentToolTargetRecordId, resolveAgentToolTargetPath } from './agentToolTargetPage';
import type { OaMenuItem } from '@/types/oa';

function backendToolCodes(): string[] {
  const source = readFileSync(resolve(
    process.cwd(), '../backend/src/main/java/com/aiworkmate/agent/registry/ToolCode.java',
  ), 'utf8');
  return [...source.matchAll(/^\s*[A-Z][A-Z0-9_]*\("([^"]+)"/gm)].map((match) => match[1]);
}

function backendOaPages(): Set<string> {
  const source = readFileSync(resolve(
    process.cwd(), '../backend/src/main/java/com/aiworkmate/oa/page/OaPage.java',
  ), 'utf8');
  return new Set([...source.matchAll(/^\s*[A-Z][A-Z0-9_]*\("([^"]+)"/gm)].map((match) => match[1]));
}

describe('Agent tool business navigation', () => {
  const menus: OaMenuItem[] = ['employee-files', 'asset-ledger', 'dashboard'].map((id) => ({
    id, name: id, type: 'page', visible: true, sort: 1, path: `/oa/${id}`,
  }));
  it('takes only explicit supported resource IDs from actual completed output', () => {
    expect(agentToolTargetRecordId('hr.employee.query', { id: 23, url: 'https://invalid.example' })).toBe(23);
    for (const code of ['asset.create', 'asset.update', 'asset.claim', 'asset.return', 'asset.transfer',
      'asset.repair.start', 'asset.repair.complete', 'asset.inventory', 'asset.scrap']) {
      expect(agentToolTargetRecordId(code, { assetId: 45 })).toBe(45);
    }
    expect(agentToolTargetRecordId('asset.query', { items: [{ id: 1 }], assetId: 1 })).toBeUndefined();
    expect(agentToolTargetRecordId('legacy.unknown', { id: 23, assetId: 45 })).toBeUndefined();
  });
  it('rejects malformed IDs without coercing strings, arrays or unsafe numbers', () => {
    for (const id of [0, -1, 1.2, Number.MAX_SAFE_INTEGER + 1, Infinity, NaN, '23', 'https://invalid.example', null, {}]) {
      expect(agentToolTargetRecordId('hr.employee.query', { id })).toBeUndefined();
    }
    expect(agentToolTargetRecordId('hr.employee.query', [{ id: 23 }])).toBeUndefined();
    expect(agentToolTargetRecordId('hr.employee.query', null)).toBeUndefined();
  });
  it('builds record locations only for supported internal pages after live menu authorization', () => {
    expect(resolveAgentToolTargetPath('employee-files', 23, menus)).toBe('/oa/employee-files?id=23');
    expect(resolveAgentToolTargetPath('asset-ledger', 45, menus)).toBe('/oa/asset-ledger?id=45');
    expect(resolveAgentToolTargetPath('employee-files', 23, menus.filter((menu) => menu.id !== 'employee-files'))).toBeNull();
    expect(resolveAgentToolTargetPath('employee-files', 23, [{ ...menus[0], visible: false }])).toBeNull();
    expect(resolveAgentToolTargetPath('employee-files', 23, [{ ...menus[0], type: 'group' }])).toBeNull();
    expect(resolveAgentToolTargetPath('dashboard', 23, menus)).toBeNull();
    expect(resolveAgentToolTargetPath('asset-ledger', NaN, menus)).toBeNull();
  });
  it('keeps module navigation internal and refuses arbitrary URLs in route configuration', () => {
    expect(resolveAgentToolTargetPath('dashboard', undefined, menus)).toBe('/oa/dashboard');
    for (const path of ['https://invalid.example', '//invalid.example/oa', '/oa/../auth', '/oa/dashboard?url=x', 'javascript:alert(1)']) {
      expect(resolveAgentToolTargetPath('dashboard', undefined, [{ ...menus[2], path }])).toBeNull();
    }
  });
  it('assigns every registered business tool to an explicit page', () => {
    const pages = backendOaPages();
    for (const toolCode of backendToolCodes()) {
      if (toolCode.startsWith('agentTask.')) continue;
      const targetPage = agentToolTargetPage(toolCode);
      expect(targetPage, toolCode).not.toBe('ai-tasks');
      expect(pages.has(targetPage), `${toolCode} -> ${targetPage}`).toBe(true);
    }
  });

  it('uses task center only for task inspection or unknown historical tools', () => {
    expect(agentToolTargetPage('attendance.clock')).toBe('attendance-clock');
    expect(agentToolTargetPage('dashboard.overview.query')).toBe('dashboard');
    expect(agentToolTargetPage('observability.overview.query')).toBe('platform-observability');
    expect(agentToolTargetPage('agentTask.mine.query')).toBe('ai-tasks');
    expect(agentToolTargetPage('agentTask.cancel')).toBe('ai-tasks');
    expect(agentToolTargetPage('legacy.unknown')).toBe('ai-tasks');
  });
});

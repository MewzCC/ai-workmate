import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { agentToolTargetPage } from './agentToolTargetPage';

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
  it('assigns every registered business tool to an explicit page', () => {
    const pages = backendOaPages();
    for (const toolCode of backendToolCodes()) {
      if (toolCode === 'agentTask.mine.query') continue;
      const targetPage = agentToolTargetPage(toolCode);
      expect(targetPage, toolCode).not.toBe('ai-tasks');
      expect(pages.has(targetPage), `${toolCode} -> ${targetPage}`).toBe(true);
    }
  });

  it('uses task center only for task inspection or unknown historical tools', () => {
    expect(agentToolTargetPage('agentTask.mine.query')).toBe('ai-tasks');
    expect(agentToolTargetPage('legacy.unknown')).toBe('ai-tasks');
  });
});

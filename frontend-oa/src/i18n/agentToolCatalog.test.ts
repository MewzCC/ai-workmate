import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { agentToolTranslationKey } from '../lib/agentToolPresentation';
import enAiPermission from './locales/en-US/aiPermission';
import zhAiPermission from './locales/zh-CN/aiPermission';

const TOOL_CODE_SOURCE = resolve(
  process.cwd(),
  '../backend/src/main/java/com/aiworkmate/agent/registry/ToolCode.java',
);

function backendToolTranslationKeys(): string[] {
  const source = readFileSync(TOOL_CODE_SOURCE, 'utf8');
  return [...source.matchAll(/^\s*[A-Z][A-Z0-9_]*\("([^"]+)"/gm)]
    .map((match) => agentToolTranslationKey(match[1]))
    .sort();
}

function localizedToolKeys(catalog: typeof zhAiPermission): string[] {
  return Object.keys(catalog.tools).sort();
}

describe('Agent 工具双语目录', () => {
  it('与后端 ToolCode 代码上界保持完全一致', () => {
    const expectedKeys = backendToolTranslationKeys();

    expect(expectedKeys.length).toBeGreaterThan(0);
    expect(localizedToolKeys(zhAiPermission)).toEqual(expectedKeys);
    expect(localizedToolKeys(enAiPermission)).toEqual(expectedKeys);
  });

  it('每个工具在两种语言中都有名称和安全范围说明', () => {
    for (const key of backendToolTranslationKeys()) {
      const zhTool = zhAiPermission.tools[key as keyof typeof zhAiPermission.tools];
      const enTool = enAiPermission.tools[key as keyof typeof enAiPermission.tools];

      expect(zhTool.name.trim()).not.toBe('');
      expect(zhTool.description.trim()).not.toBe('');
      expect(enTool.name.trim()).not.toBe('');
      expect(enTool.description.trim()).not.toBe('');
    }
  });
});

import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { COMPONENT_KEYS, ENABLED_PAGE_COMPONENT_KEYS } from '@/types/oa';
import { OA_PAGE_REGISTRY } from './OaPageRenderer';

const readBackendPageManifest = () => {
  const source = readFileSync(resolve(
    process.cwd(),
    '..',
    'backend',
    'src',
    'main',
    'java',
    'com',
    'aiworkmate',
    'oa',
    'page',
    'OaPage.java',
  ), 'utf8');

  return [...source.matchAll(/^\s*[A-Z][A-Z0-9_]*\("([^"]+)",\s*"([^"]+)"\)[,;]/gm)]
    .map((match) => ({ routeKey: match[1], componentKey: match[2] }));
};

describe('OA page component registry', () => {
  it('registers every known component key including the disabled legacy fallback', () => {
    expect(Object.keys(OA_PAGE_REGISTRY).sort()).toEqual([...COMPONENT_KEYS].sort());
  });

  it('keeps the generic workbench fallback out of enabled page choices', () => {
    expect(ENABLED_PAGE_COMPONENT_KEYS).not.toContain('WORKBENCH_MODULE');
    expect(ENABLED_PAGE_COMPONENT_KEYS.every((key) => Boolean(OA_PAGE_REGISTRY[key]))).toBe(true);
  });

  it('matches the backend code-owned OA page manifest exactly', () => {
    const backendPages = readBackendPageManifest();
    const backendRouteKeys = backendPages.map(({ routeKey }) => routeKey);
    const backendComponentKeys = backendPages.map(({ componentKey }) => componentKey);

    expect(backendPages).toHaveLength(41);
    expect(new Set(backendRouteKeys).size).toBe(backendRouteKeys.length);
    expect(new Set(backendComponentKeys).size).toBe(backendComponentKeys.length);
    expect([...backendComponentKeys].sort()).toEqual([...ENABLED_PAGE_COMPONENT_KEYS].sort());
    expect(Object.keys(OA_PAGE_REGISTRY)
      .filter((key) => key !== 'WORKBENCH_MODULE')
      .sort()).toEqual([...backendComponentKeys].sort());
  });
});

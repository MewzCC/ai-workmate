import { describe, expect, it } from 'vitest';
import { COMPONENT_KEYS, ENABLED_PAGE_COMPONENT_KEYS } from '@/types/oa';
import { OA_PAGE_REGISTRY } from './OaPageRenderer';

describe('OA page component registry', () => {
  it('registers every known component key including the disabled legacy fallback', () => {
    expect(Object.keys(OA_PAGE_REGISTRY).sort()).toEqual([...COMPONENT_KEYS].sort());
  });

  it('keeps the generic workbench fallback out of enabled page choices', () => {
    expect(ENABLED_PAGE_COMPONENT_KEYS).not.toContain('WORKBENCH_MODULE');
    expect(ENABLED_PAGE_COMPONENT_KEYS.every((key) => Boolean(OA_PAGE_REGISTRY[key]))).toBe(true);
  });
});

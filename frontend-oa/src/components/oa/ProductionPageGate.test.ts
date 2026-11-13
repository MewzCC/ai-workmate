import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const OA_COMPONENT_DIR = join(process.cwd(), 'src', 'components', 'oa');
const PRODUCTION_EXTENSIONS = ['.ts', '.tsx'];

function productionSources(root: string): Array<{ path: string; source: string }> {
  return readdirSync(root).flatMap((name) => {
    const path = join(root, name);
    if (statSync(path).isDirectory()) return productionSources(path);
    if (!PRODUCTION_EXTENSIONS.some((extension) => name.endsWith(extension))
      || name.includes('.test.') || name.includes('.spec.')) return [];
    return [{ path, source: readFileSync(path, 'utf8') }];
  });
}

describe('production OA page gate', () => {
  const sources = productionSources(OA_COMPONENT_DIR);

  it('does not import mock business data into production pages', () => {
    const violations = sources.filter(({ source }) => /(?:@\/mock|src\/mock|from\s+['"][^'"]*mock)/.test(source));
    expect(violations.map(({ path }) => path)).toEqual([]);
  });

  it('uses Ant Design Button for OA business actions', () => {
    const violations = sources.filter(({ source }) => /<button\b/.test(source));
    expect(violations.map(({ path }) => path)).toEqual([]);
  });

  it('does not attach fake success feedback directly to click handlers', () => {
    const violations = sources.filter(({ source }) => /onClick\s*=\s*\{[^\n]*(?:message|notification)\.success\s*\(/.test(source));
    expect(violations.map(({ path }) => path)).toEqual([]);
  });

  it('does not ship clickable coming-soon placeholders', () => {
    const violations = sources.filter(({ source }) => /coming[ -]?soon|comingSoon|下一阶段接入|待开发/i.test(source));
    expect(violations.map(({ path }) => path)).toEqual([]);
  });
});

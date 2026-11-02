import i18n from '@/i18n';
import { AuthResponseScopeError, captureAuthDataScope } from './authEvents';

/** Loads a bounded catalogue/range completely or fails visibly; never presents a truncated first page as complete. */
export async function collectPagedRecords<T>(load: (page: number, size: number) => Promise<{ records: T[]; total: number }>): Promise<T[]> {
  const rows: T[] = [];
  const current = captureAuthDataScope();
  let total: number | undefined;
  for (let page = 1; page <= 1000; page++) {
    if (!current()) throw new AuthResponseScopeError();
    const result = await load(page, 100);
    if (!current()) throw new AuthResponseScopeError();
    if (!result || !Array.isArray(result.records) || result.records.length > 100
        || !Number.isSafeInteger(result.total) || result.total < 0
        || (total !== undefined && total !== result.total))
      throw new Error(i18n.t('common.incompletePageData'));
    total = result.total;
    rows.push(...result.records);
    if (rows.length > total) throw new Error(i18n.t('common.incompletePageData'));
    if (rows.length === total) return rows;
    if (result.records.length === 0) break;
  }
  throw new Error(i18n.t('common.incompletePageData'));
}

import { useCallback, useEffect, useRef, useState } from 'react';
import { Select, Spin } from 'antd';
import type { SelectProps } from 'antd';
import type { BusinessUserOption } from '@/lib/adminAssetsApi';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';

/** Domain-supplied, permission-filtered candidates; never fetches the global employee directory. */
export function BusinessUserSelect({ value, onChange, query, active, scopeKey, placeholder, disabled, ...fieldProps }: {
  value?: number; onChange?: (value: number | undefined) => void; active: boolean; scopeKey: string; placeholder?: string;
  query: (params: { keyword?: string; page: number; size: number }) => Promise<{ records: BusinessUserOption[]; total: number }>;
  disabled?: boolean;
} & Pick<SelectProps<number>, 'id' | 'aria-label' | 'aria-describedby' | 'aria-invalid' | 'onBlur' | 'onFocus'>) {
  const [search, setSearch] = useState('');
  const [options, setOptions] = useState<BusinessUserOption[]>([]);
  const [optionsScope, setOptionsScope] = useState('');
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [failed, setFailed] = useState(false);
  const request = useRef(0);
  const scope = `${scopeKey}:${active}:${search}`;
  const currentScope = useRef<string | undefined>(scope); currentScope.current = scope;
  const busy = useRef(false);
  useEffect(() => { setSearch(''); }, [scopeKey, active]);
  const load = useCallback(async (nextPage: number) => {
    if (!active) return;
    const ticket = ++request.current;
    busy.current = true; setLoading(true); setFailed(false);
    try {
      const result = await query({ keyword: search.trim() || undefined, page: nextPage, size: 20 });
      if (ticket !== request.current || currentScope.current !== scope) return;
      setOptions(previous => nextPage === 1 ? result.records : [...new Map([...previous, ...result.records].map(item => [item.id, item])).values()]);
      setOptionsScope(scope); setPage(nextPage); setTotal(result.total);
    } catch (error) {
      if (ticket !== request.current || currentScope.current !== scope) return;
      if (nextPage === 1) { setOptions([]); setTotal(0); }
      setFailed(true); message.error(formatOaApiError(error));
    } finally {
      if (ticket === request.current && currentScope.current === scope) { busy.current = false; setLoading(false); }
    }
  }, [active, query, search, scope]);
  useEffect(() => {
    currentScope.current = scope;
    setOptions([]); setTotal(0); setPage(1); busy.current = false; setLoading(false);
    const timer = window.setTimeout(() => { void load(1); }, search ? 250 : 0);
    return () => { window.clearTimeout(timer); request.current += 1; currentScope.current = undefined; };
  }, [load, scope, search]);
  return <Select key={`${scopeKey}:${active}`} {...fieldProps} value={value} onChange={onChange} showSearch allowClear filterOption={false} placeholder={placeholder}
    disabled={!active || disabled ? true : undefined} loading={loading} onSearch={setSearch} searchValue={search}
    options={(optionsScope === scope ? options : []).map(item => ({ value: item.id, label: item.name }))}
    notFoundContent={loading ? <Spin size="small" /> : undefined}
    onOpenChange={open => { if (open && failed && !busy.current) void load(1); }}
    onPopupScroll={event => {
      const target = event.currentTarget;
      if (!busy.current && options.length < total && target.scrollTop + target.clientHeight >= target.scrollHeight - 24)
        void load(page + 1);
    }} />;
}

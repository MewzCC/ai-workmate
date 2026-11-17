import dayjs from 'dayjs';

export const TODO_BUSINESS_TYPES = ['ALL', 'LEAVE_APPLICATION', 'GENERIC_APPROVAL', 'VISITOR_BOOKING', 'SEAL_USAGE'] as const;
export const TODO_STATUSES = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'] as const;
export interface TodoQueryFilters {
  businessType: typeof TODO_BUSINESS_TYPES[number]; status: typeof TODO_STATUSES[number];
  page: number; size: number; from?: string; to?: string;
}
export function requireTodoQuery(input: Readonly<Record<string, unknown>> | Readonly<Partial<TodoQueryFilters>>): TodoQueryFilters {
  const businessType = input.businessType ?? 'ALL', status = input.status === '' ? 'ALL' : input.status ?? 'PENDING';
  const page = input.page ?? 1, size = input.size ?? 20;
  if (!TODO_BUSINESS_TYPES.some(value => value === businessType) || !TODO_STATUSES.some(value => value === status)
      || typeof page !== 'number' || !Number.isSafeInteger(page) || page < 1 || page > 10000
      || typeof size !== 'number' || !Number.isSafeInteger(size) || size < 1 || size > 50) throw new Error('PAGE_UI_FIELD_REJECTED');
  const date = (value: unknown): string | undefined => {
    if (value == null || value === '') return undefined;
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) value += 'T00:00:00';
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/.test(value)
        || !dayjs(value).isValid() || !value.startsWith(dayjs(value).format('YYYY-MM-DDTHH:mm'))) throw new Error('PAGE_UI_FIELD_REJECTED');
    const seconds = value.length === 16 ? `${value}:00` : value;
    return seconds.includes('.') ? seconds.replace(/0+$/, '').replace(/\.$/, '') : seconds;
  };
  const from = date(input.from), to = date(input.to);
  if (from && to && dayjs(from).isAfter(dayjs(to))) throw new Error('PAGE_UI_FIELD_REJECTED');
  return { businessType: businessType as TodoQueryFilters['businessType'], status: status as TodoQueryFilters['status'], page, size, from, to };
}
export function todoQueryFromLocation(params: URLSearchParams): TodoQueryFilters {
  const number = (key: string) => {
    const value = params.get(key); if (value == null) return undefined;
    if (!/^[1-9]\d*$/.test(value)) throw new Error('PAGE_UI_FIELD_REJECTED'); return Number(value);
  };
  if (params.has('kind') && !['all', 'leave', 'generic'].includes(params.get('kind')!)) throw new Error('PAGE_UI_FIELD_REJECTED');
  const kind = params.get('kind'), explicit = params.get('businessType');
  const forced = kind === 'leave' ? 'LEAVE_APPLICATION' : kind === 'generic' ? 'GENERIC_APPROVAL' : undefined;
  if (forced && explicit && forced !== explicit) throw new Error('PAGE_UI_FIELD_REJECTED');
  return requireTodoQuery({ businessType: forced ?? explicit ?? undefined, status: params.get('status') ?? undefined,
    page: number('page'), size: number('size'), from: params.get('from') ?? undefined, to: params.get('to') ?? undefined });
}
/** Fixed route and bounded filter values; never accepts a URL from a tool result. */
export function todoQueryPath(input: TodoQueryFilters): string {
  const query = requireTodoQuery(input), params = new URLSearchParams();
  if (query.businessType !== 'ALL') params.set('businessType', query.businessType);
  if (query.status !== 'PENDING') params.set('status', query.status);
  if (query.from) params.set('from', query.from); if (query.to) params.set('to', query.to);
  if (query.page !== 1) params.set('page', String(query.page)); if (query.size !== 20) params.set('size', String(query.size));
  return `/oa/todo${params.size ? `?${params}` : ''}`;
}

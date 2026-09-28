'use client';

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { PageContextSchema } from '@/types/oa';

export type PageAgentContextValue = string | number | boolean;
export type PageAgentContextSnapshot = Readonly<Record<string, PageAgentContextValue>>;

const EMPTY_CONTEXT: PageAgentContextSnapshot = Object.freeze({});
const FIELD_NAME_PATTERN = /^[a-z][A-Za-z0-9]{0,39}$/;
const FORBIDDEN_FIELD_PARTS = [
  'userid', 'tenantid', 'role', 'permission', 'datascope',
  'url', 'uri', 'sql', 'filepath', 'path', 'script', 'classname', 'beanname',
] as const;
const MAX_FIELDS = 32;
const MAX_STRING_LENGTH = 2000;
const MAX_CONTEXT_BYTES = 16 * 1024;

interface PageAgentContextController {
  pageId: string;
  snapshot: PageAgentContextSnapshot;
  publish: (sourceId: string, context: Record<string, unknown>) => void;
  remove: (sourceId: string) => void;
}

const PageAgentContextState = createContext<PageAgentContextController | null>(null);

function byteLength(value: unknown): number {
  const serialized = JSON.stringify(value);
  return typeof TextEncoder === 'undefined'
    ? serialized.length
    : new TextEncoder().encode(serialized).length;
}

/**
 * Browser-side guardrail for untrusted page state. The backend remains the
 * authoritative filter and applies the exact per-page schema again.
 */
export function sanitizePageAgentContext(
  context: Record<string, unknown>,
  schema?: PageContextSchema,
): PageAgentContextSnapshot {
  const safe: Record<string, PageAgentContextValue> = {};
  const entries = Object.entries(context).sort(([left], [right]) => left.localeCompare(right));
  const allowedFields = schema
    ? new Map(schema.fields.map((field) => [field.name, field] as const))
    : null;
  const maxBytes = schema ? Math.min(schema.maxBytes, MAX_CONTEXT_BYTES) : MAX_CONTEXT_BYTES;

  for (const [name, value] of entries) {
    if (Object.keys(safe).length >= MAX_FIELDS || !FIELD_NAME_PATTERN.test(name)) continue;
    const normalizedName = name.toLowerCase();
    if (FORBIDDEN_FIELD_PARTS.some((part) => normalizedName.includes(part))) continue;

    const field = allowedFields?.get(name);
    if (allowedFields && !field) continue;

    const stringLimit = Math.min(field?.maxLength ?? MAX_STRING_LENGTH, MAX_STRING_LENGTH);
    const validString = typeof value === 'string'
      && value.length <= stringLimit
      && (!field || field.valueType === 'STRING');
    const validNumber = typeof value === 'number'
      && Number.isFinite(value)
      && (!field || field.valueType === 'NUMBER');
    const validBoolean = typeof value === 'boolean'
      && (!field || field.valueType === 'BOOLEAN');
    if (!validString && !validNumber && !validBoolean) continue;

    const candidate = { ...safe, [name]: value as PageAgentContextValue };
    if (byteLength(candidate) > maxBytes) continue;
    safe[name] = value as PageAgentContextValue;
  }

  return Object.freeze(safe);
}

function mergeSources(sources: Map<string, PageAgentContextSnapshot>): PageAgentContextSnapshot {
  const merged: Record<string, unknown> = {};
  sources.forEach((context) => Object.assign(merged, context));
  return sanitizePageAgentContext(merged);
}

export function PageAgentContextProvider({ pageId, children }: { pageId: string; children: ReactNode }) {
  return (
    <PageAgentContextScope key={pageId} pageId={pageId}>
      {children}
    </PageAgentContextScope>
  );
}

function PageAgentContextScope({ pageId, children }: { pageId: string; children: ReactNode }) {
  const sourcesRef = useRef(new Map<string, PageAgentContextSnapshot>());
  const [snapshot, setSnapshot] = useState<PageAgentContextSnapshot>(EMPTY_CONTEXT);

  const publish = useCallback((sourceId: string, context: Record<string, unknown>) => {
    sourcesRef.current.set(sourceId, sanitizePageAgentContext(context));
    setSnapshot(mergeSources(sourcesRef.current));
  }, []);

  const remove = useCallback((sourceId: string) => {
    if (!sourcesRef.current.delete(sourceId)) return;
    setSnapshot(mergeSources(sourcesRef.current));
  }, []);

  const value = useMemo<PageAgentContextController>(() => ({
    pageId,
    snapshot,
    publish,
    remove,
  }), [pageId, publish, remove, snapshot]);

  return <PageAgentContextState.Provider value={value}>{children}</PageAgentContextState.Provider>;
}

/**
 * Publishes one page fragment. Multiple page components may contribute
 * independent filters; fragments are removed automatically on unmount.
 */
export function usePublishPageAgentContext(context: Record<string, unknown>): void {
  const controller = useContext(PageAgentContextState);
  const publish = controller?.publish;
  const remove = controller?.remove;
  const sourceId = useId();
  const fingerprint = JSON.stringify(sanitizePageAgentContext(context));
  const stableContext = useMemo<Record<string, unknown>>(
    () => JSON.parse(fingerprint) as Record<string, unknown>,
    [fingerprint],
  );

  useEffect(() => {
    if (!publish || !remove) return undefined;
    publish(sourceId, stableContext);
    return () => remove(sourceId);
  }, [publish, remove, sourceId, stableContext]);
}

export function usePageAgentContextSnapshot(): PageAgentContextSnapshot {
  return useContext(PageAgentContextState)?.snapshot ?? EMPTY_CONTEXT;
}

/** 将页面筛选上下文送到持久挂载的 AI 入口，切页时不保留旧页面上下文。 */
export function PageAgentContextObserver({ onChange }: { onChange: (snapshot: PageAgentContextSnapshot) => void }) {
  const snapshot = usePageAgentContextSnapshot();
  useEffect(() => onChange(snapshot), [onChange, snapshot]);
  return null;
}

import type { PageUiCommandCode } from '@/types/oa';

export const PAGE_UI_COMMAND_CODES: readonly PageUiCommandCode[] = [
  'ui.navigate', 'ui.applyFilter', 'ui.openDetail', 'ui.openCreateForm',
  'ui.fillForm', 'ui.previewSubmission', 'ui.refreshPage',
];

const PAGE_ID_PATTERN = /^[a-z][a-z0-9-]{1,59}$/;
const FORBIDDEN_KEYS = new Set([
  'userId', 'tenantId', 'role', 'roles', 'permission', 'permissions', 'dataScope',
  'url', 'uri', 'sql', 'file', 'filePath', 'path', 'script', 'className', 'beanName',
  '__proto__', 'prototype', 'constructor', 'token', 'password', 'secret',
].map(name => name.toLowerCase()));

type Scalar = string | number | boolean | null;
export interface UiFormContext { sessionId: string; recordId?: number; version?: number }

export type PageUiCommand =
  | { code: 'ui.navigate'; pageId: string; payload: { targetPageId: string } }
  | { code: 'ui.applyFilter'; pageId: string; payload: { filters: Record<string, Scalar> } }
  | { code: 'ui.openDetail'; pageId: string; payload: { recordId: string | number } }
  | { code: 'ui.openCreateForm'; pageId: string; payload: Record<string, never> }
  | { code: 'ui.fillForm'; pageId: string; payload: { values: Record<string, Scalar>; formContext?: UiFormContext } }
  | { code: 'ui.previewSubmission'; pageId: string; payload: { formContext?: UiFormContext } }
  | { code: 'ui.refreshPage'; pageId: string; payload: Record<string, never> };

export interface PageUiCommandPolicy {
  currentPageId: string;
  allowedCommands: readonly PageUiCommandCode[];
  allowedTargetPageIds: readonly string[];
}

export type PageUiCommandHandlers = Partial<{
  [Code in PageUiCommandCode]: (command: Extract<PageUiCommand, { code: Code }>) => void | Promise<void>;
}>;

export function requirePageUiCommandCodes(input: unknown): PageUiCommandCode[] {
  if (!Array.isArray(input) || input.some((code) => typeof code !== 'string'
    || !PAGE_UI_COMMAND_CODES.includes(code as PageUiCommandCode))) fail();
  return [...new Set(input)] as PageUiCommandCode[];
}

export async function executePageUiCommand(
  input: unknown,
  policy: PageUiCommandPolicy,
  handlers: PageUiCommandHandlers,
): Promise<PageUiCommand> {
  const command = parsePageUiCommand(input, policy);
  const handler = handlers[command.code] as ((value: PageUiCommand) => void | Promise<void>) | undefined;
  if (!handler) throw new Error('PAGE_UI_COMMAND_UNHANDLED');
  await handler(command);
  return command;
}

export function parsePageUiCommand(input: unknown, policy: PageUiCommandPolicy): PageUiCommand {
  if (!isObject(input) || !hasOnlyKeys(input, ['code', 'pageId', 'payload'])) fail();
  if (JSON.stringify(input).length > 4096 || containsForbiddenKey(input)) fail();
  const code = input.code;
  const pageId = input.pageId;
  const payload = input.payload;
  if (typeof code !== 'string' || typeof pageId !== 'string' || !PAGE_ID_PATTERN.test(pageId)) fail();
  if (pageId !== policy.currentPageId || !policy.allowedCommands.includes(code as PageUiCommandCode)) fail();
  if (!isObject(payload)) fail();

  if (code === 'ui.navigate') {
    if (!hasOnlyKeys(payload, ['targetPageId']) || typeof payload.targetPageId !== 'string'
      || !policy.allowedTargetPageIds.includes(payload.targetPageId)) fail();
    return { code, pageId, payload: { targetPageId: payload.targetPageId } };
  }
  if (code === 'ui.applyFilter') {
    if (!hasOnlyKeys(payload, ['filters']) || !isScalarMap(payload.filters)) fail();
    return { code, pageId, payload: { filters: { ...payload.filters } } };
  }
  if (code === 'ui.openDetail') {
    if (!hasOnlyKeys(payload, ['recordId']) || !isRecordId(payload.recordId)) fail();
    return { code, pageId, payload: { recordId: payload.recordId } };
  }
  if (code === 'ui.fillForm') {
    if (!Object.keys(payload).every((key) => ['values', 'formContext'].includes(key)) || !isScalarMap(payload.values)
        || payload.formContext !== undefined && !isUiFormContext(payload.formContext)) fail();
    return { code, pageId, payload: { values: { ...payload.values }, ...(payload.formContext ? { formContext: payload.formContext as UiFormContext } : {}) } };
  }
  if (code === 'ui.previewSubmission') {
    if (!Object.keys(payload).every((key) => key === 'formContext') || payload.formContext !== undefined && !isUiFormContext(payload.formContext)) fail();
    return { code, pageId, payload: payload.formContext ? { formContext: payload.formContext as UiFormContext } : {} };
  }
  if (code === 'ui.openCreateForm' || code === 'ui.refreshPage') {
    if (!hasOnlyKeys(payload, [])) fail();
    return { code, pageId, payload: {} } as PageUiCommand;
  }
  fail();
}

export function isUiFormContext(value: unknown): value is UiFormContext {
  if (!isObject(value) || !Object.keys(value).every((key) => ['sessionId', 'recordId', 'version'].includes(key))
      || typeof value.sessionId !== 'string' || !/^[a-zA-Z0-9_-]{16,64}$/.test(value.sessionId)) return false;
  if (value.recordId === undefined && value.version === undefined) return true;
  return typeof value.recordId === 'number' && Number.isSafeInteger(value.recordId) && value.recordId > 0
    && typeof value.version === 'number' && Number.isInteger(value.version) && value.version >= 0 && value.version <= 2147483646;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
    && Object.getPrototypeOf(value) === Object.prototype;
}

function hasOnlyKeys(value: Record<string, unknown>, allowed: string[]): boolean {
  const keys = Object.keys(value);
  return keys.length === allowed.length && keys.every((key) => allowed.includes(key));
}

function isScalarMap(value: unknown): value is Record<string, Scalar> {
  if (!isObject(value)) return false;
  const entries = Object.entries(value);
  return entries.length <= 50 && entries.every(([key, item]) => /^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(key)
    && (item === null || ['string', 'number', 'boolean'].includes(typeof item))
    && (typeof item !== 'string' || item.length <= 500)
    && (typeof item !== 'number' || Number.isFinite(item)));
}

function isRecordId(value: unknown): value is string | number {
  return (typeof value === 'number' && Number.isSafeInteger(value) && value > 0)
    || (typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(value));
}

function containsForbiddenKey(value: unknown): boolean {
  if (Array.isArray(value)) return value.some(containsForbiddenKey);
  if (!isObject(value)) return false;
  return Object.entries(value).some(([key, item]) => FORBIDDEN_KEYS.has(key.toLowerCase()) || containsForbiddenKey(item));
}

function fail(): never {
  throw new Error('PAGE_UI_COMMAND_REJECTED');
}

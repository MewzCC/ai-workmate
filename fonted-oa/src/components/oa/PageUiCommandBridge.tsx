import { createContext, useCallback, useContext, useEffect, useId, useMemo, useRef, type ReactNode } from 'react';
import { getPageCapabilities } from '@/lib/oaApi';
import { getNavigation } from '@/lib/navigationApi';
import { flattenPages, toMenuItem } from '@/lib/navigationTree';
import { executePageUiCommand, type PageUiCommand, type PageUiCommandHandlers, type UiFormContext } from '@/lib/pageUiCommands';
import { preparedPageUiCommand } from '@/lib/preparedPageUiCommand';
import type { AgentTaskDetail, PageUiCommandCode } from '@/types/oa';
import { usePageAgentPageId, usePublishPageAgentContext } from './PageAgentContext';
import i18n from '@/i18n';
import { captureAuthDataScope } from '@/lib/authEvents';

interface Registration {
  pageId: string; codes: PageUiCommandCode[]; execute: (command: PageUiCommand) => void | Promise<void>;
  formContext: () => UiFormContext | null;
  viewKey: () => string | undefined;
  views?: () => readonly string[];
  selectView?: (view: string, filters: Readonly<Record<string, unknown>>) => void | Promise<void>;
  selectedView?: () => string;
}
interface Bridge {
  register: (id: string, registration: Registration) => () => void;
  applyTask: (detail: AgentTaskDetail) => Promise<boolean>;
}
const Context = createContext<Bridge | null>(null);

export function PageUiCommandBridge({ pageId, navigate, refresh, children }: {
  pageId: string; navigate: (pageId: string, recordId?: number) => boolean; refresh: () => void; children: ReactNode;
}) {
  const registrations = useRef(new Map<string, Registration>());
  const latest = useRef({ pageId, navigate, refresh });
  const pageRevision = useRef(0);
  if (latest.current.pageId !== pageId) pageRevision.current++;
  latest.current = { pageId, navigate, refresh };
  const applied = useRef(new Set<string>());
  const createdForms = useRef(new Map<string, Map<string, UiFormContext>>());
  const selectedViews = useRef(new Map<string, Map<string, string>>());
  const busy = useRef(false);
  const disposed = useRef(false);
  const inFlightTasks = useRef(new Set<string>());
  useEffect(() => {
    const ownedRegistrations = registrations.current;
    const ownedRevision = pageRevision;
    disposed.current = false;
    return () => { disposed.current = true; ownedRevision.current++; ownedRegistrations.clear(); };
  }, []);
  const register = useCallback((id: string, registration: Registration) => {
    registrations.current.set(id, registration);
    return () => { if (registrations.current.get(id) === registration) registrations.current.delete(id); };
  }, []);

  const applyTask = useCallback(async (detail: AgentTaskDetail) => {
    const steps = detail.steps.filter((step) => step.toolCode === 'page.ui.prepare');
    if (!steps.length) return false;
    if (detail.steps.length > 3 || new Set(steps.map(step => step.sequence)).size !== steps.length)
      throw new Error(i18n.t('oa.ai.uiNotApplied'));
    if (inFlightTasks.current.has(detail.taskId)) return false;
    if (detail.status !== 'SUCCEEDED' || detail.steps.some((step) => step.status !== 'SUCCEEDED'))
      throw new Error(i18n.t('oa.ai.uiNotApplied'));
    if (busy.current) throw new Error(i18n.t('oa.ai.uiBusy'));
    busy.current = true;
    inFlightTasks.current.add(detail.taskId);
    let changed = false;
    const authCurrent = captureAuthDataScope();
    let expectedRevision = pageRevision.current;
    const assertCurrent = () => {
      if (disposed.current || !authCurrent() || pageRevision.current !== expectedRevision)
        throw new Error(i18n.t('oa.ai.uiNotApplied'));
    };
    const navigateTo = async (targetPageId: string) => {
      assertCurrent();
      const previousPage = latest.current.pageId;
      if (!latest.current.navigate(targetPageId)) throw new Error(i18n.t('oa.ai.uiNotApplied'));
      const deadline = Date.now() + 5000;
      while (!disposed.current && authCurrent() && latest.current.pageId !== targetPageId && Date.now() < deadline) {
        if (pageRevision.current > expectedRevision) throw new Error(i18n.t('oa.ai.uiNotApplied'));
        await new Promise<void>((resolve) => window.setTimeout(resolve, 25));
      }
      const nextRevision = expectedRevision + (previousPage === targetPageId ? 0 : 1);
      if (latest.current.pageId !== targetPageId || pageRevision.current !== nextRevision)
        throw new Error(i18n.t('oa.ai.uiNotApplied'));
      expectedRevision = nextRevision;
      assertCurrent();
    };
    try {
      assertCurrent();
      if (!createdForms.current.has(detail.taskId) && createdForms.current.size >= 100)
        throw new Error(i18n.t('oa.ai.uiSessionLimit'));
      if (!createdForms.current.has(detail.taskId)) createdForms.current.set(detail.taskId, new Map());
      const created = createdForms.current.get(detail.taskId)!;
      for (const step of steps) {
        assertCurrent();
        const receipt = `${detail.taskId}:${step.sequence}`;
        if (applied.current.has(receipt)) continue;
        let command = preparedPageUiCommand(step.result);
        const [navigation, capability] = await Promise.all([getNavigation(), getPageCapabilities(command.pageId)]);
        assertCurrent();
        const pages = flattenPages(navigation.map(toMenuItem));
        if (!pages.some((page) => page.id === command.pageId) || capability.pageId !== command.pageId
            || !capability.tools.some(tool => tool.code === 'page.ui.prepare') || !capability.uiCommands.includes(command.code))
          throw new Error(i18n.t('oa.ai.uiNotApplied'));
        if (latest.current.pageId !== command.pageId) {
          await navigateTo(command.pageId);
        }
        assertCurrent();
        if (disposed.current || latest.current.pageId !== command.pageId) throw new Error(i18n.t('oa.ai.uiNotApplied'));
        if (command.code === 'ui.applyFilter' && command.payload.filters.view !== undefined) {
          if (!capability.uiCommands.includes('ui.applyFilter')) throw new Error(i18n.t('oa.ai.uiNotApplied'));
          const view = command.payload.filters.view;
          if (typeof view !== 'string' || !/^[a-z][a-z0-9-]{0,39}$/.test(view)) throw new Error(i18n.t('oa.ai.uiNotApplied'));
          const deadline = Date.now() + 3000;
          let selectors: Registration[];
          do {
            assertCurrent();
            selectors = [...registrations.current.values()].filter(item => item.pageId === command.pageId && item.selectView && item.views?.().includes(view));
            if (selectors.length) break;
            await new Promise<void>(resolve => window.setTimeout(resolve, 25));
          } while (!disposed.current && latest.current.pageId === command.pageId && Date.now() < deadline);
          if (disposed.current || latest.current.pageId !== command.pageId || selectors.length !== 1) throw new Error(i18n.t('oa.ai.uiNotApplied'));
          await selectors[0].selectView!(view, command.payload.filters);
          assertCurrent();
          do {
            assertCurrent();
            const current = [...registrations.current.values()].filter(item => item.pageId === command.pageId && item.selectView);
            if (current.length === 1 && current[0].selectedView?.() === view) break;
            await new Promise<void>(resolve => window.setTimeout(resolve, 25));
          } while (!disposed.current && latest.current.pageId === command.pageId && Date.now() < deadline);
          const current = [...registrations.current.values()].filter(item => item.pageId === command.pageId && item.selectView);
          if (disposed.current || latest.current.pageId !== command.pageId || current.length !== 1 || current[0].selectedView?.() !== view || !current[0].viewKey())
            throw new Error(i18n.t('oa.ai.uiStaleView'));
          if (!selectedViews.current.has(detail.taskId)) selectedViews.current.set(detail.taskId, new Map());
          selectedViews.current.get(detail.taskId)!.set(command.pageId, current[0].viewKey()!);
          const filters = { ...command.payload.filters }; delete filters.view;
          if (Object.keys(filters).length === 0) { applied.current.add(receipt); changed = true; continue; }
          command = { ...command, payload: { filters } };
        }
        const handlers: PageUiCommandHandlers = {
          'ui.navigate': (value) => navigateTo(value.payload.targetPageId),
          'ui.refreshPage': () => latest.current.refresh(),
        };
        const deadline = Date.now() + 3000;
        let owners: Registration[];
        do {
          assertCurrent();
          owners = [...registrations.current.values()].filter((item) => item.pageId === command.pageId && item.codes.includes(command.code));
          if (owners.length || command.code === 'ui.navigate' || command.code === 'ui.refreshPage' || command.code === 'ui.openDetail') break;
          await new Promise<void>((resolve) => window.setTimeout(resolve, 50));
        } while (!disposed.current && Date.now() < deadline && latest.current.pageId === command.pageId);
        if (disposed.current || owners.length > 1 || latest.current.pageId !== command.pageId) throw new Error(i18n.t('oa.ai.uiNotApplied'));
        const pageRegistrations = [...registrations.current.values()].filter(item => item.pageId === command.pageId);
        const selectorViews = pageRegistrations.filter(item => item.selectView).map(item => item.viewKey()).filter((key): key is string => Boolean(key));
        const views = [...new Set(selectorViews.length ? selectorViews : pageRegistrations.map(item => item.viewKey()).filter((key): key is string => Boolean(key)))];
        if (command.code !== 'ui.navigate' && command.code !== 'ui.refreshPage' && views.length > 0) {
          const snapshot = detail.pageContext && typeof detail.pageContext === 'object' && !Array.isArray(detail.pageContext)
            ? detail.pageContext as Record<string, unknown> : {};
          const expected = selectedViews.current.get(detail.taskId)?.get(command.pageId)
            ?? (detail.pageId === command.pageId ? snapshot.uiViewKey : undefined);
          if (views.length !== 1 || expected !== views[0]) throw new Error(i18n.t('oa.ai.uiStaleView'));
        }
        const priorForm = owners[0]?.formContext() ?? null;
        if (command.code === 'ui.fillForm' || command.code === 'ui.previewSubmission') {
          const expected = created.get(command.pageId) ?? command.payload.formContext;
          if (!expected || !priorForm || !sameForm(expected, priorForm)) throw new Error(i18n.t('oa.ai.uiStaleForm'));
          command = command.code === 'ui.fillForm'
            ? { ...command, payload: { values: command.payload.values, formContext: expected } }
            : { ...command, payload: { formContext: expected } };
        }
        if (owners.length === 1) {
          // This callback only reaches a registered state handler; there is no submit command.
          Object.assign(handlers, { [command.code]: (value: PageUiCommand) => {
            assertCurrent();
            if (![...registrations.current.values()].includes(owners[0])) throw new Error(i18n.t('oa.ai.uiNotApplied'));
            return owners[0].execute(value);
          } });
        } else if (command.code === 'ui.openDetail') {
          handlers['ui.openDetail'] = (value) => {
            const id = Number(value.payload.recordId);
            if (!Number.isSafeInteger(id) || id < 1 || !latest.current.navigate(command.pageId, id))
              throw new Error(i18n.t('oa.ai.uiNotApplied'));
          };
        }
        await executePageUiCommand(command, { currentPageId: latest.current.pageId,
          allowedCommands: capability.uiCommands, allowedTargetPageIds: pages.map((page) => page.id) }, handlers);
        assertCurrent();
        if (command.code === 'ui.openCreateForm') {
          const deadline = Date.now() + 2000;
          let opened: UiFormContext | null = null;
          do {
            assertCurrent();
            opened = owners[0]?.formContext() ?? null;
            if (opened && opened.sessionId !== priorForm?.sessionId) break;
            await new Promise<void>((resolve) => window.setTimeout(resolve, 20));
          } while (!disposed.current && Date.now() < deadline && latest.current.pageId === command.pageId);
          if (!opened || opened.recordId !== undefined || opened.sessionId === priorForm?.sessionId
              || disposed.current || latest.current.pageId !== command.pageId) throw new Error(i18n.t('oa.ai.uiStaleForm'));
          created.set(command.pageId, opened);
        }
        applied.current.add(receipt);
        changed = true;
      }
      return changed;
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) throw new Error(i18n.t('oa.ai.uiFormInvalid'));
      if (error instanceof Error && !error.message.startsWith('PAGE_UI_')) throw error;
      throw new Error(i18n.t('oa.ai.uiNotApplied'));
    } finally { busy.current = false; inFlightTasks.current.delete(detail.taskId); }
  }, []);

  const value = useMemo(() => ({ register, applyTask }), [register, applyTask]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function usePageUiCommandBridge() { return useContext(Context); }

/** Only explicitly registered controls may be reached. Permission changes update the live handler reference. */
export function useRegisterPageUiCommands(handlers: PageUiCommandHandlers, formContext: UiFormContext | null = null, viewKey?: string): void {
  const bridge = useContext(Context);
  const pageId = usePageAgentPageId();
  const id = useId();
  const current = useRef(handlers);
  current.current = handlers;
  const currentForm = useRef(formContext);
  currentForm.current = formContext;
  const currentView = useRef(viewKey);
  currentView.current = viewKey;
  usePublishPageAgentContext({ uiViewKey: ['my-applications', 'knowledge-base', 'dictionary'].includes(pageId ?? '') ? undefined : viewKey });
  const codesKey = Object.keys(handlers).sort().join(',');
  useEffect(() => {
    if (!bridge || !pageId) return;
    const codes = codesKey ? codesKey.split(',') as PageUiCommandCode[] : [];
    return bridge.register(id, { pageId, codes, formContext: () => currentForm.current, viewKey: () => currentView.current, execute: async (command) => {
      const handler = current.current[command.code] as ((command: PageUiCommand) => void | Promise<void>) | undefined;
      if (!handler) throw new Error(i18n.t('oa.ai.uiNotApplied'));
      await handler(command);
    } });
  }, [bridge, pageId, id, codesKey]);
}

function sameForm(expected: UiFormContext, actual: UiFormContext): boolean {
  return expected.sessionId === actual.sessionId && expected.recordId === actual.recordId && expected.version === actual.version;
}

/** Explicit view selection changes local presentation only; no domain mutation callback is accepted. */
export function useRegisterPageUiViews(view: string, views: readonly string[], selectView: (view: string, filters: Readonly<Record<string, unknown>>) => void | Promise<void>, scopeKey = '', enabled = true): void {
  const bridge = useContext(Context), pageId = usePageAgentPageId(), id = useId();
  const current = useRef({ view, views, selectView, scopeKey }); current.current = { view, views, selectView, scopeKey };
  usePublishPageAgentContext({ uiViewKey: pageId && enabled ? `${pageId}:${scopeKey}:${view}` : undefined });
  useEffect(() => {
    if (!bridge || !pageId || !enabled) return;
    return bridge.register(id, { pageId, codes: [], execute: () => {}, formContext: () => null,
      viewKey: () => `${pageId}:${current.current.scopeKey}:${current.current.view}`, selectedView: () => current.current.view, views: () => current.current.views,
      selectView: (value, filters) => current.current.selectView(value, filters) });
  }, [bridge, pageId, id, enabled]);
}

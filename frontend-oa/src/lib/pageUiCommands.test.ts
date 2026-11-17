import { describe, expect, it, vi } from 'vitest';
import { executePageUiCommand, parsePageUiCommand, requirePageUiCommandCodes } from './pageUiCommands';

const policy = {
  currentPageId: 'todo',
  allowedCommands: ['ui.navigate', 'ui.applyFilter', 'ui.openDetail', 'ui.refreshPage'] as const,
  allowedTargetPageIds: ['dashboard', 'todo'],
};

describe('pageUiCommands', () => {
  it('accepts only the fixed protocol command catalog', () => {
    expect(requirePageUiCommandCodes(['ui.navigate', 'ui.navigate'])).toEqual(['ui.navigate']);
    expect(() => requirePageUiCommandCodes(['ui.executeScript'])).toThrow('PAGE_UI_COMMAND_REJECTED');
  });

  it('dispatches only an explicitly registered current-page command', async () => {
    const refresh = vi.fn();
    const command = await executePageUiCommand(
      { code: 'ui.refreshPage', pageId: 'todo', payload: {} },
      policy,
      { 'ui.refreshPage': refresh },
    );
    expect(command.code).toBe('ui.refreshPage');
    expect(refresh).toHaveBeenCalledOnce();
  });

  it('allows navigation only to a server-authorized page id', () => {
    expect(parsePageUiCommand(
      { code: 'ui.navigate', pageId: 'todo', payload: { targetPageId: 'dashboard' } }, policy,
    ).code).toBe('ui.navigate');
    expect(() => parsePageUiCommand(
      { code: 'ui.navigate', pageId: 'todo', payload: { targetPageId: 'access-control' } }, policy,
    )).toThrow('PAGE_UI_COMMAND_REJECTED');
  });

  it.each([
    { code: 'ui.refreshPage', pageId: 'dashboard', payload: {} },
    { code: 'ui.fillForm', pageId: 'todo', payload: { values: { reason: 'x' } } },
    { code: 'ui.openDetail', pageId: 'todo', payload: { recordId: '../secret' } },
    { code: 'ui.applyFilter', pageId: 'todo', payload: { filters: { tenantId: 9 } } },
    { code: 'ui.refreshPage', pageId: 'todo', payload: { url: 'https://example.com' } },
    { code: 'ui.refreshPage', pageId: 'todo', payload: {}, extra: true },
  ])('rejects cross-page, ungranted or unsafe payload %#', (command) => {
    expect(() => parsePageUiCommand(command, policy)).toThrow('PAGE_UI_COMMAND_REJECTED');
  });

  it('fails closed when a valid command has no registered page handler', async () => {
    await expect(executePageUiCommand(
      { code: 'ui.refreshPage', pageId: 'todo', payload: {} }, policy, {},
    )).rejects.toThrow('PAGE_UI_COMMAND_UNHANDLED');
  });
});

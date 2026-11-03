import { expect, it } from 'vitest';
import { agentToolTargetPage, agentToolTargetRecordId, resolveAgentToolTargetPath } from './agentToolTargetPage';

it('navigates metadata reads through fixed business routes and authorized menus', () => {
  expect(agentToolTargetPage('approval.process.definition.query')).toBe('process-config');
  expect(agentToolTargetPage('approval.application.template.query')).toBe('approval-start');
  expect(agentToolTargetPage('approval.task.participants.query')).toBe('approval-list');
  expect(agentToolTargetRecordId('approval.process.definition.query', { processId: 12, url: 'https://invalid.example' })).toBe(12);
  expect(resolveAgentToolTargetPath('process-config', 12, [])).toBeNull();
  expect(resolveAgentToolTargetPath('process-config', 12, [{ id: 'process-config', name: 'Synthetic process page', type: 'page', visible: true, sort: 1 }]))
    .toBe('/oa/process-config?id=12');
  expect(agentToolTargetRecordId('approval.process.definition.query', { processId: '12' })).toBeUndefined();
  expect(agentToolTargetRecordId('approval.application.template.query', { items: [{ formId: 12 }] })).toBeUndefined();
});

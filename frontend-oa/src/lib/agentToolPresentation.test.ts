import { describe, expect, it } from 'vitest';
import { agentToolTranslationKey, formatAgentPlanArgument, isSensitiveAgentPlanArgument } from './agentToolPresentation';

describe('agent tool presentation', () => {
  it('maps a code-owned tool code to its translation resource key', () => {
    expect(agentToolTranslationKey('approval.application.createDraft'))
      .toBe('approval_application_createDraft');
  });

  it('redacts credential-like fields and bounds nested previews', () => {
    expect(formatAgentPlanArgument('confirmationToken', 'one-time-secret', '已隐藏'))
      .toBe('已隐藏');
    expect(formatAgentPlanArgument('password', 'unsafe', '已隐藏')).toBe('已隐藏');
    expect(isSensitiveAgentPlanArgument('confirmationToken')).toBe(true);
    expect(isSensitiveAgentPlanArgument('applicationId')).toBe(false);
    expect(formatAgentPlanArgument('reason', '正常事由', '已隐藏')).toBe('正常事由');
    expect(formatAgentPlanArgument('items', { content: 'x'.repeat(400) }, '已隐藏'))
      .toHaveLength(241);
  });
});

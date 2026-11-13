export function agentToolTranslationKey(toolCode: string): string {
  return toolCode.replaceAll('.', '_');
}

const SENSITIVE_ARGUMENT_KEY = /(authorization|cookie|credential|password|secret|token|api[-_]?key)/i;
const MAX_ARGUMENT_PREVIEW_LENGTH = 240;

export function isSensitiveAgentPlanArgument(key: string): boolean {
  return SENSITIVE_ARGUMENT_KEY.test(key);
}

export function formatAgentPlanArgument(
  key: string,
  value: unknown,
  redactedText: string,
): string {
  if (isSensitiveAgentPlanArgument(key)) return redactedText;
  if (value === null) return 'null';
  if (value === undefined) return '-';

  let serialized: string;
  if (typeof value === 'string') {
    serialized = value;
  } else if (typeof value === 'number' || typeof value === 'boolean') {
    serialized = String(value);
  } else {
    try {
      serialized = JSON.stringify(value);
    } catch {
      return '-';
    }
  }

  return serialized.length > MAX_ARGUMENT_PREVIEW_LENGTH
    ? `${serialized.slice(0, MAX_ARGUMENT_PREVIEW_LENGTH)}…`
    : serialized;
}

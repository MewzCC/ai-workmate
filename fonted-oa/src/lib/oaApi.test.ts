import { describe, expect, it } from 'vitest';
import { formatOaApiError, OaApiError } from './oaApi';

describe('OA API error mapping', () => {
  it('preserves stable error code, status and trace id', () => {
    const error = new OaApiError('数据已更新', 409, 'VERSION_CONFLICT', 'request-1', 'trace-1');
    expect(error.status).toBe(409);
    expect(error.errorCode).toBe('VERSION_CONFLICT');
    expect(error.retryable).toBe(false);
    expect(formatOaApiError(error)).toContain('trace-1');
  });

  it('does not expose unknown error details', () => {
    expect(formatOaApiError(new Error('secret'))).toBe('请求失败，请稍后重试');
  });
});

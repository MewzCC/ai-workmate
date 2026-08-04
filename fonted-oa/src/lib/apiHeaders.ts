import i18n from '@/i18n';

/**
 * 构造请求头，统一注入 Accept-Language 与 X-Request-Id。
 *
 * <p>所有 API 客户端必须通过此工具构造 headers，确保后端按当前 i18next 语言解析错误消息。
 * 禁止在业务 API 客户端中手写 headers 而遗漏 Accept-Language。
 */
export function buildApiHeaders(json = true, extra?: HeadersInit): HeadersInit {
  const result: Record<string, string> = {
    'X-Request-Id': crypto.randomUUID().replaceAll('-', ''),
    'Accept-Language': i18n.language || 'zh-CN',
  };
  if (json) result['Content-Type'] = 'application/json';
  if (extra) {
    const merged = extra as Record<string, string>;
    Object.assign(result, merged);
  }
  return result;
}

'use client';

import type { MessageInstance } from 'antd/es/message/interface';

// 全局 message 实例桥接
// 在 Providers 中通过 App.useApp() 初始化
// 其他文件从本模块导入 message，替代 antd 静态 message
// 这样可消费 ConfigProvider 动态主题，消除 antd v6 静态方法警告

let instance: MessageInstance | null = null;

export function setMessageInstance(msg: MessageInstance) {
  instance = msg;
}

type Args<T> = T extends (...a: infer A) => unknown ? A : never;

function delegate<T extends keyof MessageInstance>(method: T) {
  return (...args: Args<MessageInstance[T]>) => {
    if (instance) {
      (instance[method] as (...a: unknown[]) => void)(...args);
    } else {
      // fallback: 实例未初始化时输出到控制台
      console.warn(`[message.${String(method)}] instance not ready`, args);
    }
  };
}

export const message = {
  success: delegate('success'),
  error: delegate('error'),
  info: delegate('info'),
  warning: delegate('warning'),
  loading: delegate('loading'),
  open: delegate('open'),
  destroy: delegate('destroy'),
} as unknown as MessageInstance;

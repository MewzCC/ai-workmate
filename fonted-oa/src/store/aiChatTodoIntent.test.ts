import { describe, expect, it } from 'vitest';
import { isMyTodoQuery } from './aiChatStore';

describe('isMyTodoQuery', () => {
  it('routes explicit personal todo reads', () => {
    expect(isMyTodoQuery('查一下我的待办')).toBe(true);
    expect(isMyTodoQuery('我的待办有哪些？')).toBe(true);
  });

  it('does not turn normal chat or write requests into a read task', () => {
    expect(isMyTodoQuery('怎么处理待办？')).toBe(false);
    expect(isMyTodoQuery('帮我审批我的待办')).toBe(false);
    expect(isMyTodoQuery('查询其他人的待办')).toBe(false);
  });
});

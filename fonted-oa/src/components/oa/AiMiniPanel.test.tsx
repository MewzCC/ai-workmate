import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AiMiniPanel from './AiMiniPanel';

function dispatchPointerEvent(
  element: Element,
  type: 'pointerdown' | 'pointermove' | 'pointerup',
  properties: { pointerId: number; clientX?: number; clientY?: number; button?: number },
) {
  const event = new MouseEvent(type, {
    bubbles: true,
    button: properties.button ?? 0,
    clientX: properties.clientX,
    clientY: properties.clientY,
  });
  Object.defineProperty(event, 'pointerId', { value: properties.pointerId });
  fireEvent(element, event);
}

describe('AiMiniPanel', () => {
  afterEach(cleanup);

  beforeEach(() => {
    window.localStorage.clear();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
  });

  it('opens AI suggestions without starting a drag', () => {
    const onOpenAi = vi.fn();
    render(<AiMiniPanel onOpenAi={onOpenAi} />);
    const actionButton = screen.getByRole('button', { name: /生成建议/ });

    dispatchPointerEvent(actionButton, 'pointerdown', {
      button: 0,
      pointerId: 3,
      clientX: 1000,
      clientY: 650,
    });
    dispatchPointerEvent(actionButton, 'pointerup', {
      pointerId: 3,
      clientX: 1000,
      clientY: 650,
    });
    fireEvent.click(actionButton);

    expect(onOpenAi).toHaveBeenCalledWith('帮我总结当前工作台的风险和下一步动作');
  });

  it('drags the whole panel and stores the new position', () => {
    const onOpenAi = vi.fn();
    render(<AiMiniPanel onOpenAi={onOpenAi} />);
    const panel = document.querySelector('.oa-ai-mini') as HTMLDivElement;
    const actionButton = screen.getByRole('button', { name: /生成建议/ });
    panel.getBoundingClientRect = vi.fn(() => ({
      x: 946,
      y: 564,
      left: 946,
      top: 564,
      right: 1176,
      bottom: 704,
      width: 230,
      height: 140,
      toJSON: () => ({}),
    }));

    dispatchPointerEvent(panel, 'pointerdown', {
      button: 0,
      pointerId: 7,
      clientX: 966,
      clientY: 584,
    });
    dispatchPointerEvent(panel, 'pointermove', {
      pointerId: 7,
      clientX: 420,
      clientY: 320,
    });
    dispatchPointerEvent(panel, 'pointerup', { pointerId: 7 });
    fireEvent.click(actionButton);

    expect(panel.style.left).toBe('400px');
    expect(panel.style.top).toBe('300px');
    expect(onOpenAi).not.toHaveBeenCalled();
    expect(window.localStorage.getItem('workmeta-oa-ai-mini-position')).toBe(
      JSON.stringify({ x: 400, y: 300 }),
    );
  });

  it('keeps the panel inside the visible viewport', () => {
    render(<AiMiniPanel onOpenAi={vi.fn()} />);
    const panel = document.querySelector('.oa-ai-mini') as HTMLDivElement;
    panel.getBoundingClientRect = vi.fn(() => ({
      x: 900,
      y: 500,
      left: 900,
      top: 500,
      right: 1130,
      bottom: 640,
      width: 230,
      height: 140,
      toJSON: () => ({}),
    }));

    dispatchPointerEvent(panel, 'pointerdown', {
      button: 0,
      pointerId: 9,
      clientX: 920,
      clientY: 520,
    });
    dispatchPointerEvent(panel, 'pointermove', {
      pointerId: 9,
      clientX: -100,
      clientY: -100,
    });

    expect(panel.style.left).toBe('12px');
    expect(panel.style.top).toBe('12px');
  });
});

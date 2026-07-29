'use client';

import {
  useEffect,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { createPortal } from 'react-dom';
import { Button, Card, Space, Tag } from 'antd';
import { OaIcon } from '@/components/OaIcon';

interface AiMiniPanelProps {
  onOpenAi: (prompt?: string) => void;
}

interface PanelPosition {
  x: number;
  y: number;
}

interface DragState {
  pointerId: number;
  offsetX: number;
  offsetY: number;
  startX: number;
  startY: number;
  moved: boolean;
}

const PANEL_POSITION_STORAGE_KEY = 'workmeta-oa-ai-mini-position';
const PANEL_WIDTH = 230;
const VIEWPORT_GAP = 12;
const DRAG_THRESHOLD = 4;

function clampPosition(position: PanelPosition, width: number, height: number): PanelPosition {
  return {
    x: Math.min(
      Math.max(VIEWPORT_GAP, position.x),
      Math.max(VIEWPORT_GAP, window.innerWidth - width - VIEWPORT_GAP),
    ),
    y: Math.min(
      Math.max(VIEWPORT_GAP, position.y),
      Math.max(VIEWPORT_GAP, window.innerHeight - height - VIEWPORT_GAP),
    ),
  };
}

function readStoredPosition(): PanelPosition | null {
  try {
    const stored = window.localStorage.getItem(PANEL_POSITION_STORAGE_KEY);
    if (!stored) return null;
    const position = JSON.parse(stored) as Partial<PanelPosition>;
    return Number.isFinite(position.x) && Number.isFinite(position.y)
      ? { x: position.x as number, y: position.y as number }
      : null;
  } catch {
    return null;
  }
}

export default function AiMiniPanel({ onOpenAi }: AiMiniPanelProps) {
  const [mounted, setMounted] = useState(false);
  const [position, setPosition] = useState<PanelPosition | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const dragStateRef = useRef<DragState | null>(null);
  const positionRef = useRef<PanelPosition | null>(null);
  const suppressClickRef = useRef(false);

  useEffect(() => setMounted(true), []);

  useEffect(() => {
    if (!mounted || !panelRef.current) return;
    const rect = panelRef.current.getBoundingClientRect();
    const initialPosition = readStoredPosition() ?? {
      x: window.innerWidth - rect.width - 24,
      y: window.innerHeight - rect.height - 96,
    };
    const nextPosition = clampPosition(initialPosition, rect.width, rect.height);
    positionRef.current = nextPosition;
    setPosition(nextPosition);
  }, [mounted]);

  useEffect(() => {
    if (!mounted) return;
    const keepPanelInViewport = () => {
      const rect = panelRef.current?.getBoundingClientRect();
      if (!rect) return;
      setPosition((current) => {
        const nextPosition = current
          ? clampPosition(current, rect.width, rect.height)
          : current;
        positionRef.current = nextPosition;
        return nextPosition;
      });
    };
    window.addEventListener('resize', keepPanelInViewport);
    return () => window.removeEventListener('resize', keepPanelInViewport);
  }, [mounted]);

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!panelRef.current || event.button !== 0) return;
    const rect = panelRef.current.getBoundingClientRect();
    dragStateRef.current = {
      pointerId: event.pointerId,
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top,
      startX: event.clientX,
      startY: event.clientY,
      moved: false,
    };
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const dragState = dragStateRef.current;
    const panel = panelRef.current;
    if (!dragState || !panel || dragState.pointerId !== event.pointerId) return;
    if (!dragState.moved) {
      const distance = Math.hypot(
        event.clientX - dragState.startX,
        event.clientY - dragState.startY,
      );
      if (distance < DRAG_THRESHOLD) return;
      dragState.moved = true;
      event.currentTarget.setPointerCapture?.(event.pointerId);
      panel.classList.add('oa-ai-mini-dragging');
    }
    const rect = panel.getBoundingClientRect();
    const nextPosition = clampPosition({
      x: event.clientX - dragState.offsetX,
      y: event.clientY - dragState.offsetY,
    }, rect.width, rect.height);
    positionRef.current = nextPosition;
    setPosition(nextPosition);
  };

  const finishDragging = (event: ReactPointerEvent<HTMLDivElement>) => {
    const dragState = dragStateRef.current;
    if (dragState?.pointerId !== event.pointerId) return;
    dragStateRef.current = null;
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    panelRef.current?.classList.remove('oa-ai-mini-dragging');
    if (dragState.moved && positionRef.current) {
      suppressClickRef.current = true;
      window.localStorage.setItem(
        PANEL_POSITION_STORAGE_KEY,
        JSON.stringify(positionRef.current),
      );
      window.setTimeout(() => {
        suppressClickRef.current = false;
      }, 0);
    }
  };

  const handleClickCapture = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (!suppressClickRef.current) return;
    event.preventDefault();
    event.stopPropagation();
    suppressClickRef.current = false;
  };

  if (!mounted) return null;
  return createPortal(
    <Card
      ref={panelRef}
      className="oa-ai-mini"
      size="small"
      aria-label="可拖动的 AI 快捷卡片"
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={finishDragging}
      onPointerCancel={finishDragging}
      onClickCapture={handleClickCapture}
      style={{
        position: 'fixed',
        left: position?.x,
        top: position?.y,
        right: position ? 'auto' : 24,
        bottom: position ? 'auto' : 96,
        width: PANEL_WIDTH,
        zIndex: 1050,
      }}
    >
      <Space className="oa-ai-mini-content" orientation="vertical" size={8}>
        <div
          className="oa-ai-mini-drag-handle"
        >
          <Tag color="purple">AI 快捷卡片</Tag>
          <span className="oa-ai-mini-drag-hint" aria-hidden="true">
            <span className="oa-ai-mini-grip" />
            拖动
          </span>
        </div>
        <strong>需要我接手当前流程吗？</strong>
        <Button type="primary" icon={<OaIcon name="ai" />} onClick={() => onOpenAi('帮我总结当前工作台的风险和下一步动作')}>
          生成建议
        </Button>
      </Space>
    </Card>,
    document.body
  );
}

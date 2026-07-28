'use client';

import { useEffect, useRef } from 'react';
import { Graph as G6Graph, NodeEvent } from '@antv/g6';
import type { NodeData } from '@antv/g6';
import type { DepartmentNode } from './OrganizationTreePage';

interface OrganizationGraphProps {
  data: DepartmentNode[];
  selectedId?: number;
  onSelect: (id: number) => void;
  animKey?: number | string;
}

/* ------------------------------------------------------------------ *
 * 主题令牌读取
 * 从 Ant Design ConfigProvider 注入的 CSS 变量中读取当前主题色，
 * 让架构图配色随主题切换自动适配，而非写死固定色值。
 * ------------------------------------------------------------------ */
interface ThemeTokens {
  primary: string;
  surface: string;
  card: string;
  text: string;
  muted: string;
  border: string;
  isDark: boolean;
}

function readThemeTokens(): ThemeTokens {
  const styles = getComputedStyle(document.documentElement);
  const get = (name: string, fallback: string) =>
    styles.getPropertyValue(name).trim() || fallback;
  const primary = get('--oa-primary', '#1677ff');
  const surface = get('--oa-surface', '#f4f7fb');
  const card = get('--oa-card', '#ffffff');
  const text = get('--oa-text', '#111827');
  const muted = get('--oa-muted', '#64748b');
  const border = get('--oa-border', 'rgba(15, 23, 42, 0.08)');
  // 简单判断深色主题：card 的亮度低于 0.5
  const isDark = luminance(card) < 0.45;
  return { primary, surface, card, text, muted, border, isDark };
}

/* ------------------------------------------------------------------ *
 * 颜色工具：hex → rgb → hsl，支持调亮 / 调暗 / 透明度叠加
 * ------------------------------------------------------------------ */
function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  const full =
    clean.length === 3
      ? clean
          .split('')
          .map((c) => c + c)
          .join('')
      : clean;
  const num = parseInt(full, 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  let h = 0;
  let s = 0;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r:
        h = (g - b) / d + (g < b ? 6 : 0);
        break;
      case g:
        h = (b - r) / d + 2;
        break;
      default:
        h = (r - g) / d + 4;
    }
    h /= 6;
  }
  return [h * 360, s * 100, l * 100];
}

function hslToHex(h: number, s: number, l: number): string {
  h /= 360;
  s /= 100;
  l /= 100;
  const hue2rgb = (p: number, q: number, t: number) => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  let r: number, g: number, b: number;
  if (s === 0) {
    r = g = b = l;
  } else {
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    r = hue2rgb(p, q, h + 1 / 3);
    g = hue2rgb(p, q, h);
    b = hue2rgb(p, q, h - 1 / 3);
  }
  const toHex = (v: number) =>
    Math.round(v * 255)
      .toString(16)
      .padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function luminance(color: string): number {
  // 支持 hex 和 rgba
  let r = 0, g = 0, b = 0;
  if (color.startsWith('#')) {
    [r, g, b] = hexToRgb(color);
  } else if (color.startsWith('rgb')) {
    const m = color.match(/[\d.]+/g);
    if (m) {
      r = +m[0];
      g = +m[1];
      b = +m[2];
    }
  }
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}

/**
 * 基于主题色派生层级色板
 * - stroke: 主题色本身，随层级递增逐步降低饱和度 + 提升亮度（视觉上变淡）
 * - fill: 白色 / 卡片底色叠加极淡的主题色调
 */
interface LevelPalette {
  stroke: string;
  fill: string;
}

function deriveLevelPalette(primary: string, isDark: boolean, card: string): LevelPalette[] {
  const [r, g, b] = hexToRgb(primary);
  const [h, s, l] = rgbToHsl(r, g, b);

  const palettes: LevelPalette[] = [];
  // 4 个层级：根 → 叶
  const levels = [
    { satDelta: 0, lightDelta: 0 }, // level 0
    { satDelta: -8, lightDelta: +4 }, // level 1
    { satDelta: -16, lightDelta: +8 }, // level 2
    { satDelta: -24, lightDelta: +12 }, // level 3+
  ];

  for (const lv of levels) {
    const strokeS = Math.max(20, s + lv.satDelta);
    const strokeL = isDark
      ? Math.max(35, l + lv.lightDelta - 5)
      : Math.min(55, l + lv.lightDelta);
    const stroke = hslToHex(h, strokeS, strokeL);

    const fillL = isDark ? 18 : 97;
    const fillS = Math.max(15, s - 30);
    const fill = hslToHex(h, fillS, fillL);

    palettes.push({ stroke, fill });
  }

  // 深色模式下 fill 用半透明叠加
  if (isDark) {
    palettes.forEach((p) => {
      p.fill = card;
    });
  }

  return palettes;
}

function countDescendants(n: DepartmentNode): number {
  return n.children.reduce((sum, child) => sum + 1 + countDescendants(child), 0);
}

interface NodeCustomData {
  name: string;
  code?: string;
  employeeCount: number;
  childrenCount: number;
  descendantsCount: number;
  level: number;
  isVirtual?: boolean;
  isRoot?: boolean;
  selected?: boolean;
}

interface FlatNode {
  id: string;
  data: NodeCustomData;
}

interface FlatEdge {
  source: string;
  target: string;
}

function buildFlatData(roots: DepartmentNode[], selectedId?: number): {
  nodes: FlatNode[];
  edges: FlatEdge[];
} {
  const nodes: FlatNode[] = [];
  const edges: FlatEdge[] = [];
  const isSelected = (id: string | number) =>
    selectedId !== undefined && String(selectedId) === String(id);

  const makeNode = (
    id: string,
    name: string,
    code: string | undefined,
    employeeCount: number,
    childrenCount: number,
    descendantsCount: number,
    level: number,
    isVirtual: boolean,
    isRoot: boolean,
  ): FlatNode => ({
    id,
    data: {
      name,
      code,
      employeeCount,
      childrenCount,
      descendantsCount,
      level,
      isVirtual,
      isRoot,
      selected: isSelected(id),
    },
  });

  const traverse = (node: DepartmentNode, level: number) => {
    const id = String(node.id);
    nodes.push(
      makeNode(
        id,
        node.name,
        node.code,
        node.employeeCount,
        node.children.length,
        countDescendants(node),
        level,
        false,
        level === 0,
      ),
    );
    node.children.forEach((child) => {
      edges.push({ source: id, target: String(child.id) });
      traverse(child, level + 1);
    });
  };

  if (roots.length === 1) {
    traverse(roots[0], 0);
  } else if (roots.length > 1) {
    const virtualId = '__virtual_root__';
    nodes.push(
      makeNode(
        virtualId,
        '集团',
        '',
        roots.reduce((s, r) => s + r.employeeCount, 0),
        roots.length,
        roots.reduce((s, r) => s + countDescendants(r), 0),
        -1,
        true,
        false,
      ),
    );
    roots.forEach((root) => {
      edges.push({ source: virtualId, target: String(root.id) });
      traverse(root, 0);
    });
  }

  return { nodes, edges };
}

export default function OrganizationGraph({
  data,
  selectedId,
  onSelect,
  animKey,
}: OrganizationGraphProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onSelectRef = useRef(onSelect);
  const selectedIdRef = useRef(selectedId);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  useEffect(() => {
    selectedIdRef.current = selectedId;
  }, [selectedId]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    if (data.length === 0) return undefined;

    let graph: G6Graph | null = null;
    let destroyed = false;
    // 保存 wheel 监听器引用，便于在 cleanup 中移除
    let wheelHandler: ((e: WheelEvent) => void) | null = null;

    const rafId = requestAnimationFrame(() => {
      if (destroyed || !containerRef.current) return;

      const currentContainer = containerRef.current;
      const w = currentContainer.clientWidth;
      const h = currentContainer.clientHeight;
      if (w === 0 || h === 0) return;

      const tokens = readThemeTokens();
      const palette = deriveLevelPalette(tokens.primary, tokens.isDark, tokens.card);

      // 节点尺寸
      const SIZES = [
        { w: 220, h: 72 },
        { w: 200, h: 64 },
        { w: 190, h: 60 },
        { w: 180, h: 56 },
      ];
      const VIRTUAL_SIZE = { w: 240, h: 80 };

      const getPalette = (level: number, isVirtual: boolean) => {
        if (isVirtual) {
          // 虚拟根用最深的主色
          return { stroke: tokens.primary, fill: palette[0].fill };
        }
        return palette[Math.min(Math.max(level, 0), palette.length - 1)];
      };

      const getSize = (level: number, isVirtual: boolean) => {
        if (isVirtual) return VIRTUAL_SIZE;
        return SIZES[Math.min(Math.max(level, 0), SIZES.length - 1)];
      };

      const { nodes, edges } = buildFlatData(data, selectedIdRef.current);

      const getNodeStyle = (datum: NodeData): Record<string, unknown> => {
        const d = (datum.data || {}) as unknown as NodeCustomData;
        const selected = !!d.selected;
        const isVirtual = !!d.isVirtual;
        const isRoot = !!d.isRoot;
        const level = d.level;

        const pal = getPalette(level, isVirtual);
        const size = getSize(level, isVirtual);

        // 徽标
        const badges: Record<string, unknown>[] = [];
        badges.push({
          text: `${d.employeeCount}人`,
          placement: 'right-top',
          fill: '#ffffff',
          background: true,
          backgroundFill: pal.stroke,
          backgroundRadius: 10,
          backgroundOpacity: 1,
          padding: [2, 8, 2, 8],
          fontSize: 11,
          fontWeight: 600,
          offsetY: -3,
        });
        if (d.childrenCount > 0) {
          badges.push({
            text: `${d.childrenCount} 子部门`,
            placement: 'right-bottom',
            fill: tokens.isDark ? '#a6adbb' : '#595959',
            background: true,
            backgroundFill: tokens.isDark
              ? 'rgba(148, 163, 184, 0.15)'
              : 'rgba(15, 23, 42, 0.06)',
            backgroundRadius: 8,
            backgroundOpacity: 1,
            padding: [1, 7, 1, 7],
            fontSize: 10,
            offsetY: 3,
          });
        }

        const strokeColor = selected ? tokens.primary : pal.stroke;
        const fillColor = selected
          ? tokens.isDark
            ? 'rgba(139, 92, 246, 0.18)'
            : 'rgba(22, 119, 255, 0.10)'
          : pal.fill;

        return {
          size: [size.w, size.h],
          fill: fillColor,
          stroke: strokeColor,
          lineWidth: selected ? 2 : 1.5,
          radius: 10,
          shadowColor: selected
            ? (tokens.isDark
                ? 'rgba(139, 92, 246, 0.35)'
                : 'rgba(22, 119, 255, 0.22)')
            : (tokens.isDark
                ? 'rgba(0, 0, 0, 0.3)'
                : 'rgba(15, 23, 42, 0.06)'),
          shadowBlur: selected ? 16 : 8,
          shadowOffsetX: 0,
          shadowOffsetY: selected ? 4 : 3,
          labelText: d.name,
          labelFill: tokens.isDark ? '#f8fafc' : '#1f2937',
          labelFontSize: isVirtual ? 16 : isRoot ? 15 : 14,
          labelFontWeight: 600,
          labelPlacement: 'center',
          halo: false,
          badge: badges.length > 0,
          badges,
        };
      };

      const edgeColor = tokens.isDark
        ? 'rgba(148, 163, 184, 0.25)'
        : 'rgba(100, 116, 139, 0.3)';
      const edgeActiveColor = tokens.primary;

      try {
        graph = new G6Graph({
          container: currentContainer,
          width: w,
          height: h,
          autoFit: 'view',
          padding: [40, 40, 40, 40],
          data: {
            nodes: nodes as unknown as never[],
            edges: edges as unknown as never[],
          },
          node: {
            type: 'rect',
            style: getNodeStyle,
            state: {
              selected: {
                lineWidth: 2,
                stroke: tokens.primary,
                fill: tokens.isDark
                  ? 'rgba(139, 92, 246, 0.18)'
                  : 'rgba(22, 119, 255, 0.10)',
                shadowColor: tokens.isDark
                  ? 'rgba(139, 92, 246, 0.35)'
                  : 'rgba(22, 119, 255, 0.22)',
                shadowBlur: 16,
                shadowOffsetY: 4,
              },
              active: {
                lineWidth: 1.8,
                stroke: tokens.primary,
                shadowBlur: 12,
                shadowOffsetY: 4,
              },
            },
            animation: {
              enter: [
                {
                  fields: ['opacity'],
                  duration: 600,
                  easing: 'easeQuad',
                },
              ],
              update: [
                {
                  fields: [
                    'opacity',
                    'lineWidth',
                    'stroke',
                    'fill',
                    'shadowBlur',
                    'haloStrokeOpacity',
                  ],
                  duration: 300,
                  easing: 'easeQuad',
                },
              ],
            },
          },
          edge: {
            type: 'cubic-vertical',
            style: {
              stroke: edgeColor,
              lineWidth: 1.2,
              endArrow: true,
              endArrowSize: 5,
              endArrowFill: edgeColor,
            },
            state: {
              active: {
                stroke: edgeActiveColor,
                lineWidth: 2,
                endArrowFill: edgeActiveColor,
              },
            },
            animation: {
              enter: [
                {
                  fields: ['opacity'],
                  duration: 800,
                  easing: 'easeQuad',
                  delay: 300,
                },
              ],
              update: [
                {
                  fields: ['stroke', 'lineWidth'],
                  duration: 300,
                  easing: 'easeQuad',
                },
              ],
            },
          },
          layout: {
            type: 'dagre',
            rankdir: 'TB',
            nodesep: 56,
            ranksep: 96,
            nodeSize: [240, 80],
          },
          behaviors: [
            'drag-canvas',
            {
              type: 'zoom-canvas',
              trigger: ['Control'],
              // 关闭 G6 默认的 preventDefault：它会在所有滚轮事件上阻止默认行为，
              // 导致用户不按 Ctrl 时也无法用滚轮滚动页面。
              // 改为下方自定义监听器，仅在按住 Ctrl 时阻止默认行为（避免浏览器原生页面缩放）。
              preventDefault: false,
            },
            'collapse-expand',
            {
              type: 'hover-activate',
              degree: 1,
              state: 'active',
              inactiveState: undefined,
            },
            {
              type: 'click-select',
              state: 'selected',
              multiple: false,
            },
          ],
          animation: {
            duration: 600,
            easing: 'easeQuad',
          },
        });

        graph.on(NodeEvent.CLICK, ((event: { target?: { id?: string } }) => {
          const id = Number(event.target?.id);
          if (!Number.isNaN(id)) {
            onSelectRef.current(id);
          }
        }) as (event: unknown) => void);

        // 仅在按住 Ctrl 时阻止滚轮默认行为（避免浏览器原生页面缩放），
        // 不按 Ctrl 时放行，让页面正常滚动。
        wheelHandler = (e: WheelEvent) => {
          if (e.ctrlKey) e.preventDefault();
        };
        currentContainer.addEventListener('wheel', wheelHandler, { passive: false });

        graph
          .render()
          .then(() => {
            if (!destroyed && graph) {
              try {
                graph.fitView({ direction: 'both' });
              } catch (e) {
                console.error('[OrganizationGraph] fitView error:', e);
              }
            }
          })
          .catch((err: unknown) => {
            console.error('[OrganizationGraph] render failed:', err);
          });
      } catch (e) {
        console.error('[OrganizationGraph] init error:', e);
      }
    });

    return () => {
      destroyed = true;
      cancelAnimationFrame(rafId);
      if (container && wheelHandler) {
        container.removeEventListener('wheel', wheelHandler);
      }
      if (graph) {
        try {
          graph.destroy();
        } catch (e) {
          console.error('[OrganizationGraph] destroy error:', e);
        }
        graph = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, animKey]);

  return <div ref={containerRef} className="oa-org-graph" />;
}

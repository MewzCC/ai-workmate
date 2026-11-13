import { useEffect, useRef, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';

/** Native SVG cursor stays precise; only its contextual sticker follows with easing. */
export default function KineticPointer({ surface }: { surface: RefObject<HTMLDivElement> }) {
  const sticker = useRef<HTMLDivElement>(null);
  const { t } = useTranslation();
  useEffect(() => {
    const page = surface.current;
    const label = sticker.current;
    if (!page || !label) return;
    const fine = window.matchMedia('(hover: hover) and (pointer: fine)');
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0, shown = false, x = 0, y = 0, targetX = 0, targetY = 0;
    let mode = '', tone = '', dragging = false;
    const hide = () => {
      shown = false; dragging = false; label.classList.remove('is-visible', 'is-pressed'); cancelAnimationFrame(frame); frame = 0;
    };
    const paint = () => {
      frame = 0;
      if (!shown) return;
      const ease = reduce.matches ? 1 : .27;
      x += (targetX - x) * ease; y += (targetY - y) * ease;
      label.style.transform = `translate3d(${x + 25}px,${y + 24}px,0)`;
      if (Math.abs(x-targetX)+Math.abs(y-targetY) > .3) frame = requestAnimationFrame(paint);
    };
    const update = (event: PointerEvent) => {
      if (!fine.matches || event.pointerType !== 'mouse') { hide(); return; }
      const target = event.target instanceof Element ? event.target : null;
      if (!target || !page.contains(target)) { hide(); return; }
      targetX = Math.min(event.clientX, window.innerWidth - 150);
      targetY = Math.min(event.clientY, window.innerHeight - 70);
      const nextMode = dragging ? 'hold' : target.closest('.bm-drag-card') ? 'drag' : target.closest('.bm-gravity-lab canvas') ? 'scatter' : target.closest('a,button') ? 'go' : 'idle';
      const nextTone = target.closest('.bm-trust,.bm-gravity-lab,.bm-ticker,.bm-design-switch,.bm-rail-panel-1,.bm-drag-1') ? 'blue' : 'yellow';
      if (nextMode !== mode) { mode = nextMode; label.dataset.mode = mode; }
      if (nextTone !== tone) { tone = nextTone; label.dataset.tone = tone; }
      if (!shown) { x=targetX; y=targetY; shown=true; label.classList.add('is-visible'); }
      if (!frame) frame=requestAnimationFrame(paint);
    };
    const down = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse' || !fine.matches) return;
      dragging = event.target instanceof Element && Boolean(event.target.closest('.bm-drag-card'));
      label.classList.add('is-pressed');
      update(event);
    };
    const up = (event: PointerEvent) => {
      dragging=false; label.classList.remove('is-pressed'); update(event);
    };
    const key = () => { hide(); };
    page.addEventListener('pointermove',update,{passive:true});
    page.addEventListener('pointerdown',down,{passive:true});
    page.addEventListener('pointerleave',hide);
    window.addEventListener('pointerup',up,{passive:true});
    window.addEventListener('pointercancel',hide);
    window.addEventListener('blur',hide);
    window.addEventListener('scroll',hide,{passive:true});
    window.addEventListener('resize',hide);
    window.addEventListener('keydown',key);
    fine.addEventListener('change',hide);
    return () => {
      cancelAnimationFrame(frame);
      page.removeEventListener('pointermove',update); page.removeEventListener('pointerdown',down); page.removeEventListener('pointerleave',hide);
      window.removeEventListener('pointerup',up); window.removeEventListener('pointercancel',hide);
      window.removeEventListener('blur',hide); window.removeEventListener('scroll',hide); window.removeEventListener('resize',hide); window.removeEventListener('keydown',key);
      fine.removeEventListener('change',hide);
    };
  },[surface]);
  return createPortal(<div ref={sticker} className="bm-pointer-sticker" aria-hidden="true">
    <span className="bm-pointer-spark">✳</span>
    <span className="bm-pointer-label bm-pointer-scatter">{t('marketing.kinetic.cursorScatter')}</span>
    <span className="bm-pointer-label bm-pointer-drag">{t('marketing.kinetic.cursorDrag')}</span>
    <span className="bm-pointer-label bm-pointer-hold">{t('marketing.kinetic.cursorHold')}</span>
    <span className="bm-pointer-label bm-pointer-go">{t('marketing.kinetic.cursorGo')}<span>↗</span></span>
  </div>, document.body);
}

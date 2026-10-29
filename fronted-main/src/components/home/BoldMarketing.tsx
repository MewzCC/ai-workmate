import { lazy, Suspense, useEffect, useRef, useState, type CSSProperties, type PointerEvent, type KeyboardEvent } from 'react';
import { ArrowDown, ArrowRight, ArrowUpRight, Check, ChevronUp, FileText, Grip, MessageSquare, Pause, Play, Plus, RotateCcw, ShieldCheck, Sparkles, Users } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import KineticPointer from './KineticPointer';
import IdeaGravity from './IdeaGravity';

import useKineticStage from './useKineticStage';
import './bold-marketing.css';

const LiquidSculpture = lazy(() => import('./LiquidSculpture'));
type Direction = 'liquid' | 'kinetic';

function DraggableCard({ index, title, text, label, arrangement }: { index: number; title: string; text: string; label: string; arrangement: number }) {
  const element = useRef<HTMLDivElement>(null);
  const position = useRef({x:0,y:0});
  const drag = useRef<{x:number;y:number;pointer:number}|null>(null);
  const move = (x: number, y: number) => {
    position.current = {x:Math.max(-180,Math.min(180,x)),y:Math.max(-130,Math.min(130,y))};
    element.current?.style.setProperty('--dx', position.current.x+'px');
    element.current?.style.setProperty('--dy', position.current.y+'px');
  };
  const start = (event: PointerEvent<HTMLDivElement>) => {
    if(event.button !== 0) return;
    drag.current={x:event.clientX-position.current.x,y:event.clientY-position.current.y,pointer:event.pointerId};
    event.currentTarget.setPointerCapture(event.pointerId);
    event.currentTarget.classList.add('is-dragging');
  };
  const update = (event: PointerEvent<HTMLDivElement>) => {
    if(drag.current) move(event.clientX-drag.current.x,event.clientY-drag.current.y);
  };
  const stop = (event: PointerEvent<HTMLDivElement>) => {
    if(event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    drag.current=null; event.currentTarget.classList.remove('is-dragging');
  };
  const key = (event: KeyboardEvent<HTMLDivElement>) => {
    const offset: Record<string,[number,number]> = {ArrowLeft:[-15,0],ArrowRight:[15,0],ArrowUp:[0,-15],ArrowDown:[0,15]};
    if(offset[event.key]) {event.preventDefault();move(position.current.x+offset[event.key][0],position.current.y+offset[event.key][1]);}
  };
  const poses = [[[0,0,0],[0,0,0],[0,0,0]], [[-32,18,5],[-16,-35,-8],[40,-24,8]], [[30,-14,-7],[-36,28,5],[15,20,-5]]];
  const [homeX, homeY, tilt] = poses[arrangement % poses.length][index];
  const cardStyle = { '--home-x': homeX + 'px', '--home-y': homeY + 'px', '--tilt': ([-9,12,-6][index] + tilt) + 'deg' } as CSSProperties;
  const Icon = [MessageSquare,FileText,Users][index];
  return <div ref={element} style={cardStyle} tabIndex={0} role="group" aria-label={`${title}. ${label}`} className={`bm-drag-card bm-drag-${index}`} onPointerDown={start} onPointerMove={update} onPointerUp={stop} onPointerCancel={stop} onLostPointerCapture={() => {drag.current=null;element.current?.classList.remove('is-dragging');}} onKeyDown={key}>
    <div className="bm-drag-header"><span>0{index+1}</span><Grip size={17}/><span aria-hidden="true">↗</span></div>
    <Icon className="bm-card-icon" strokeWidth={1.3}/><h3>{title}</h3><p>{text}</p><span className="bm-card-bottom" aria-hidden="true">✳</span>
  </div>;
}

export default function BoldMarketing() {
  const { t } = useTranslation();
  const initial = new URLSearchParams(window.location.search).get('design');
  const [direction,setDirection] = useState<Direction>(initial === 'liquid' ? 'liquid' : 'kinetic');
  const [paused,setPaused] = useState(false);
  const [scene,setScene] = useState(0);
  const [shuffle,setShuffle] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const detonate = useKineticStage(root, direction === 'kinetic');
  const concepts = t('marketing.concepts',{returnObjects:true}) as string[];
  const titles = t(`marketing.${direction}.title`,{returnObjects:true}) as string[];
  const story = t('marketing.liquid.storyTitle',{returnObjects:true}) as string[];
  const railTitles = t('marketing.kinetic.railTitles',{returnObjects:true}) as string[];
  const railTexts = t('marketing.kinetic.railTexts',{returnObjects:true}) as string[];
  const railWords = t('marketing.kinetic.railWords',{returnObjects:true}) as string[];
  const cardTitles = t('marketing.kinetic.cardTitles',{returnObjects:true}) as string[];
  const cardTexts = t('marketing.kinetic.cardTexts',{returnObjects:true}) as string[];
  const scenes = t('marketing.scenarios',{returnObjects:true}) as string[];
  const prompts = t('marketing.prompts',{returnObjects:true}) as string[];
  const tasks = t('marketing.tasks',{returnObjects:true}) as string[][];
  const trust = t('marketing.trust',{returnObjects:true}) as {title:string;text:string}[];
  const end = t('marketing.endTitle',{returnObjects:true}) as string[];
  const nav = t('marketing.nav',{returnObjects:true}) as string[];

  useEffect(() => {
    const node=root.current;
    if(!node) return;
    const motion=window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame=0;
    const paint = () => {
      frame=0;
      const height=window.innerHeight;
      const hero=node.querySelector<HTMLElement>('.bm-hero');
      const narrative=node.querySelector<HTMLElement>('.bm-narrative');
      const rail=node.querySelector<HTMLElement>('.bm-rail');
      const heroProgress = Math.min(1,Math.max(0,-(hero?.getBoundingClientRect().top ?? 0)/height));
      node.style.setProperty('--hero-progress',String(motion.matches ? 0 : heroProgress));
      if(narrative) {
        const box=narrative.getBoundingClientRect();
        const progress=Math.min(1,Math.max(0,-box.top/Math.max(1,box.height-height)));
        node.style.setProperty('--story-progress',String(motion.matches ? 1 : progress));
      }
      if(rail) {
        const box=rail.getBoundingClientRect();
        const progress=Math.min(1,Math.max(0,-box.top/Math.max(1,box.height-height)));
        node.style.setProperty('--rail-progress',String(motion.matches ? 0 : progress));
      }
    };
    const scroll=()=>{if(!frame)frame=requestAnimationFrame(paint);};
    const reveal=new IntersectionObserver(entries=>entries.forEach(entry=>{
      if(entry.isIntersecting){entry.target.classList.add('bm-in');reveal.unobserve(entry.target);}
    }),{threshold:.15});
    node.querySelectorAll('.bm-reveal').forEach(el=>reveal.observe(el));
    window.addEventListener('scroll',scroll,{passive:true});
    window.addEventListener('resize',scroll);
    motion.addEventListener('change',scroll);
    paint();
    return ()=>{cancelAnimationFrame(frame);reveal.disconnect();window.removeEventListener('scroll',scroll);window.removeEventListener('resize',scroll);motion.removeEventListener('change',scroll);};
  },[direction]);
  const choose = (next: Direction) => {
    setDirection(next);
    const url=new URL(window.location.href);url.searchParams.set('design',next);url.hash='';
    window.history.replaceState(null,'',url);window.scrollTo({top:0,behavior:'auto'});
  };
  const enter = () => {window.location.href=import.meta.env.VITE_OA_URL||`http://${window.location.hostname}:3001/oa/`;};
  const cta = (className='') => <button type="button" onClick={enter} className={`bm-cta ${className}`}><span>{t('marketing.enter')}</span><ArrowUpRight/></button>;
  return <div ref={root} className={`bm-site bm-${direction}`} key={direction}>
    <div className="bm-design-switch" role="group" aria-label={t('marketing.conceptLabel')}>{(['liquid','kinetic'] as const).map((item,i)=><button type="button" key={item} onClick={()=>choose(item)} aria-pressed={direction===item}><span>0{i+1}</span>{concepts[i]}<span className="bm-switch-dot"/></button>)}</div>
    <header className="bm-nav"><a href="#" className="bm-brand" aria-label={t('marketing.brand')}><span className="bm-brand-symbol" aria-hidden="true">w.</span><strong>{t('marketing.brand')}</strong></a><nav>{['perspective','experience','trust'].map((id,i)=><a href={`#${id}`} key={id}>{nav[i]}</a>)}{direction === 'kinetic' && <a href="#gravity">{t('marketing.kinetic.labNav')}</a>}</nav><div className="bm-nav-right"><LanguageSwitcher/>{cta('bm-nav-cta')}</div></header>
    <main>
      {direction==='liquid' ? <>
        <section className="bm-hero bm-liquid-hero">
          <div className="bm-watermark" aria-hidden="true">BEYOND.</div>
          <Suspense fallback={<div className="bm-sculpture"><div className="bm-metal-fallback"/></div>}><LiquidSculpture paused={paused}/></Suspense>
          <div className="bm-liquid-hero-top"><span className="bm-micro">{t('marketing.liquid.eyebrow')}</span><span className="bm-coordinates" aria-hidden="true">35° 41′ 22″ N / ∞</span></div>
          <div className="bm-liquid-title"><h1>{titles.map((line,i)=><span key={line} style={{'--line-index':i} as CSSProperties}>{line}</span>)}</h1><div><p>{t('marketing.liquid.intro')}</p>{cta()}</div></div>
          <div className="bm-hero-base"><a href="#perspective"><ArrowDown size={16}/>{t('marketing.explore')}</a><div><span>{t('marketing.liquid.hint')}</span><button type="button" aria-label={t(paused?'marketing.liquid.resume':'marketing.liquid.pause')} onClick={()=>setPaused(!paused)}>{paused?<Play size={15}/>:<Pause size={15}/>}</button></div></div>
          <span className="bm-side-code" aria-hidden="true">HUMAN × KNOWLEDGE × INTELLIGENCE</span>
        </section>
        <section id="perspective" className="bm-narrative">
          <div className="bm-narrative-sticky"><span className="bm-micro">{t('marketing.liquid.storyLabel')}</span><div className="bm-story-title">{story.map((word,i)=><span key={word} className={`bm-story-word bm-story-word-${i}`}>{word}</span>)}</div><div className="bm-story-bottom"><span className="bm-chapter" aria-hidden="true">01 — 02</span><p>{t('marketing.liquid.storyBody')}</p></div><div className="bm-story-orbit" aria-hidden="true"><i/><i/><i/></div></div>
        </section>
        <section className="bm-liquid-manifesto bm-reveal"><span aria-hidden="true">✳</span><p>{t('marketing.liquid.experiment')}</p><ArrowDown strokeWidth={1}/></section>
      </> : <>
        <section className="bm-hero bm-kinetic-hero">
          <div className="bm-hero-registration" aria-hidden="true"><i/><i/><i/><i/></div><div className="bm-kinetic-overline"><span>{t('marketing.kinetic.eyebrow')}</span><span aria-hidden="true">EST. NOW / NO LIMITS</span></div>
          <h1 className="bm-kinetic-title" aria-label={titles.join('')}>{titles.map((line,i)=><span key={line} className={`bm-kinetic-line bm-kinetic-line-${i}`}>{Array.from(line).map((glyph,index)=><span className="bm-title-glyph" key={index}>{glyph === ' ' ? '\u00a0' : glyph}</span>)}<span className="bm-title-punctuation" aria-hidden="true">{i===0?'↗':'✳'}</span></span>)}</h1>
          <div className="bm-playground" key={shuffle}>{cardTitles.map((title,i)=><DraggableCard key={i} index={i} arrangement={shuffle} title={title} text={cardTexts[i]} label={t('marketing.kinetic.dragLabel')}/>)}</div>
          <div className="bm-hero-edition" aria-hidden="true"><span>IDEA DEPT.</span><span>W—M / 002</span><div className="bm-barcode"/></div><button type="button" className="bm-explosion-sticker" onClick={event=>detonate(event.currentTarget)} aria-label={t('marketing.kinetic.heroIgnite')}><span>{t('marketing.kinetic.sticker')}</span><small>{t('marketing.kinetic.clickMe')}</small></button>
          <div className="bm-hero-doodle" aria-hidden="true"><svg viewBox="0 0 250 120" fill="none"><path d="M10 85C42 12 89 26 78 60C67 95 41 35 100 40C139 45 140 100 180 64C193 53 202 39 216 29M195 30L220 23L218 47" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/></svg><span>{t('marketing.kinetic.doodle')}</span></div><div className="bm-kinetic-bottom"><div><p>{t('marketing.kinetic.intro')}</p><button type="button" className="bm-remix" onClick={()=>setShuffle(shuffle+1)}><RotateCcw size={14}/>{t('marketing.kinetic.reset')}</button></div><div className="bm-kinetic-action"><span>{t('marketing.kinetic.hint')}</span>{cta()}</div></div>
          <a className="bm-kinetic-scroll" href="#perspective"><ArrowDown size={18}/>{t('marketing.explore')}</a>
        </section>
        <div className="bm-ticker" aria-hidden="true"><div>{Array.from({length:4},(_,i)=><span key={i}>GOOD IDEAS DON'T WAIT <b>✳</b> MAKE ROOM FOR MORE <b>↗</b></span>)}</div></div>
        <section id="perspective" className="bm-rail"><div className="bm-rail-sticky"><div className="bm-rail-progress" aria-hidden="true"><span/></div><span className="bm-rail-label">{t('marketing.kinetic.railLabel')}<ArrowRight size={18}/></span><div className="bm-rail-track">{railTitles.map((title,i)=>{const Icon=[MessageSquare,FileText,Users][i];return <article className={`bm-rail-panel bm-rail-panel-${i}`} key={title}><div className="bm-rail-number">0{i+1}<span>/ 03</span></div><div className="bm-rail-word" aria-hidden="true">{railWords[i]}</div><div className="bm-rail-panel-detail" aria-hidden="true">IDEAS / IN MOTION <span>✳</span></div><div className="bm-rail-art" aria-hidden="true"><div className="bm-paper-satellites">{[0,1,2,3].map(piece=><i key={piece} style={{'--piece':piece} as CSSProperties}/>)}</div><Icon strokeWidth={1}/><span>{i===0?'?':i===1?'↗':'✳'}</span></div><div className="bm-rail-copy"><h2>{title}</h2><p>{railTexts[i]}</p></div></article>;})}</div></div></section>
      </>}
      {direction === 'kinetic' && <IdeaGravity/>}
      <section id="experience" className="bm-experience bm-reveal"><div className="bm-experience-heading"><span className="bm-micro">{t('marketing.productLabel')}</span><h2>{t('marketing.productTitle')}</h2><div className="bm-scenarios" role="group" aria-label={t('marketing.taskLabel')}>{scenes.map((label,i)=><button type="button" key={label} onClick={()=>setScene(i)} aria-pressed={scene===i}><span>0{i+1}</span>{label}<ArrowUpRight size={15}/></button>)}</div></div><div className="bm-demo-window"><div className="bm-demo-tape" aria-hidden="true"/><div className="bm-demo-header"><span className="bm-demo-dots"><i/><i/><i/></span><span>{t('marketing.brand')}</span><Plus size={16}/></div><div className="bm-demo-body" key={scene}><div className="bm-demo-prompt"><span className="bm-you-icon"><Users size={16}/></span><p>{prompts[scene]}</p></div><div className="bm-demo-assistant"><Sparkles size={21}/><span>{t('marketing.assistant')}</span><span className="bm-demo-live" aria-hidden="true"/></div><div className="bm-demo-plan"><span className="bm-plan-label">{t('marketing.draft')}<FileText size={16}/></span>{tasks[scene].map((task,i)=><div className="bm-demo-task" key={task}><span>{i===2?<span className="bm-task-dot"/>:<Check size={13}/>}</span><p>{task}</p><small>0{i+1}</small></div>)}<div className="bm-demo-confirm"><ShieldCheck size={14}/>{t('marketing.review')}<ArrowRight size={16}/></div></div></div><span className="bm-demo-cross" aria-hidden="true">+</span></div></section>
      <section id="trust" className="bm-trust bm-reveal"><div className="bm-trust-title"><span className="bm-micro">{t('marketing.trustLabel')}</span><h2>{t('marketing.trustTitle')}</h2><div className="bm-trust-seal" aria-hidden="true"><ShieldCheck strokeWidth={.8}/><span>HUMAN<br/>IN CONTROL</span></div></div><div className="bm-trust-items">{trust.map((item,i)=><article key={item.title}><span>0{i+1}</span><div><h3>{item.title}</h3><p>{item.text}</p></div><ArrowUpRight size={23}/></article>)}</div></section>
      {direction === 'kinetic' && <div className="bm-stamp-strip"><span>✳</span><p>{t('marketing.kinetic.stamp')}</p><span>↗</span></div>}<section className="bm-ending"><div className="bm-ending-top"><span className="bm-micro">{t('marketing.endText')}</span><a href="#">{t('marketing.top')}<ChevronUp size={17}/></a></div><h2>{end.map(line=><span key={line}>{line}</span>)}</h2>{cta('bm-ending-cta')}<div className="bm-ending-word" aria-hidden="true">{direction==='liquid'?'TOGETHER.':'LET’S GO!'}<ArrowUpRight/></div></section>
    </main>
    {direction === 'kinetic' && <KineticPointer surface={root}/>}
    <footer className="bm-footer"><span>{t('marketing.footer')}</span><span aria-hidden="true">HUMAN + AI = MORE.</span><span>© {new Date().getFullYear()}</span></footer>
  </div>;
}

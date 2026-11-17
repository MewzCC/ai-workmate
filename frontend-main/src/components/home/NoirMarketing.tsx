import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowRight, ArrowUpRight, Check, Pause, Play, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import ThemeToggle from '@/components/ThemeToggle';
import exterior from './assets/w-signature-exterior.png';
import detail from './assets/w-signature-detail.png';
import './architecture-marketing.css';

interface Workflow { title:string; subtitle:string; description:string; prompt:string; steps:string[] }
interface Principle { title:string; body:string }

export default function NoirMarketing({ onSwitch }: { onSwitch:()=>void }) {
  const { t }=useTranslation();
  const root=useRef<HTMLDivElement>(null);
  const [loaded,setLoaded]=useState(false);
  const [paused,setPaused]=useState(false);
  const [active,setActive]=useState(0);
  const nav=t('architecture.nav',{returnObjects:true}) as string[];
  const title=t('architecture.title',{returnObjects:true}) as string[];
  const vision=t('architecture.visionTitle',{returnObjects:true}) as string[];
  const workTitle=t('architecture.workTitle',{returnObjects:true}) as string[];
  const workflows=t('architecture.workflows',{returnObjects:true}) as Workflow[];
  const principles=t('architecture.trust',{returnObjects:true}) as Principle[];
  const scene=workflows[active];
  const enter=()=>{window.location.href=import.meta.env.VITE_OA_URL||`http://${window.location.hostname}:3001/oa/`;};
  useEffect(()=>{
    const node=root.current;
    if(!node)return;
    const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');
    const observer=new IntersectionObserver(entries=>entries.forEach(entry=>{
      if(entry.isIntersecting){entry.target.classList.add('am-visible');observer.unobserve(entry.target);}
    }),{threshold:.12});
    node.querySelectorAll('[data-am-reveal]').forEach(element=>observer.observe(element));
    let frame=0;
    const paint=()=>{
      frame=0;
      const hero=node.querySelector<HTMLElement>('.am-hero');
      const progress=Math.max(0,Math.min(1,-(hero?.getBoundingClientRect().top??0)/window.innerHeight));
      node.style.setProperty('--am-scroll',String(reduced.matches?0:progress));
    };
    const scroll=()=>{if(!frame)frame=requestAnimationFrame(paint);};
    window.addEventListener('scroll',scroll,{passive:true});window.addEventListener('resize',scroll);reduced.addEventListener('change',scroll);paint();
    return()=>{observer.disconnect();cancelAnimationFrame(frame);window.removeEventListener('scroll',scroll);window.removeEventListener('resize',scroll);reduced.removeEventListener('change',scroll);};
  },[]);
  const cta=(light=false)=><button type="button" className={`am-button ${light?'am-button-light':''}`} onClick={enter}><span>{t('architecture.enter')}</span><ArrowUpRight size={18}/></button>;
  return <div ref={root} className={`am-site ${paused?'am-paused':''}`}>
    <header className="am-header">
      <a href="#home" className="am-brand" aria-label={t('marketing.brand')}><span aria-hidden="true">w.</span><strong>{t('marketing.brand')}</strong></a>
      <nav>{['vision','work','trust'].map((id,i)=><a href={`#${id}`} key={id}>{nav[i]}</a>)}</nav>
      <div className="am-header-actions"><ThemeToggle theme="night" onChange={onSwitch}/><LanguageSwitcher/>{cta()}</div>
    </header>
    <main>
      <section id="home" className="am-hero">
        <div className={`am-scene ${loaded?'am-ready':''}`}>
          <img className="am-scene-image" src={exterior} width="1672" height="941" alt={t('architecture.imageAlt')} fetchPriority="high" onLoad={()=>setLoaded(true)}/>
          <img className="am-light-pass" src={exterior} width="1672" height="941" alt="" aria-hidden="true"/>
          <div className="am-mist"/>
        </div>
        <div className="am-hero-shade"/>
        <div className="am-hero-copy">
          <p className="am-eyebrow"><i/>{t('architecture.eyebrow')}</p>
          <h1>{title.map(line=><span className="am-title-line" key={line}><span>{line}</span></span>)}</h1>
          <p className="am-introduction">{t('architecture.introduction')}</p>
          <div className="am-hero-actions">{cta(true)}<a className="am-text-link" href="#vision"><span>{t('architecture.discover')}</span><ArrowRight size={15}/></a></div>
        </div>
        <div className="am-hero-foot">
          <a href="#vision" className="am-scroll" aria-label={t('architecture.discover')}><span aria-hidden="true">01 — 03</span><ArrowDown size={16}/></a>
          <span className="am-edition">{t('architecture.edition')}</span>
          <div className="am-visual-caption"><span>{t('architecture.concept')}</span><button type="button" onClick={()=>setPaused(!paused)} aria-label={t(paused?'architecture.resume':'architecture.pause')} aria-pressed={paused}>{paused?<Play size={13}/>:<Pause size={13}/>}</button></div>
        </div>
      </section>
      <section id="vision" className="am-vision">
        <div className="am-section-line"><span>{t('architecture.visionLabel')}</span><span aria-hidden="true">HUMAN / KNOWLEDGE / AI</span></div>
        <div className="am-vision-grid">
          <figure className="am-vision-image" data-am-reveal="image"><img src={detail} width="1672" height="941" alt={t('architecture.detailAlt')} loading="lazy"/><figcaption>{t('architecture.visionNote')}</figcaption></figure>
          <div className="am-vision-copy" data-am-reveal="copy"><h2>{vision.map(line=><span key={line}>{line}</span>)}</h2><p>{t('architecture.visionBody')}</p><div className="am-vision-points">{(t('architecture.visionDetails',{returnObjects:true}) as string[]).map((item,i)=><div key={item}><span>0{i+1}</span><p>{item}</p><ArrowUpRight size={16}/></div>)}</div></div>
        </div>
      </section>
      <section id="work" className="am-work">
        <div className="am-section-line"><span>{t('architecture.workLabel')}</span><span aria-hidden="true">CONTEXT → DIRECTION</span></div>
        <div className="am-work-heading" data-am-reveal><h2>{workTitle.map(line=><span key={line}>{line}</span>)}</h2><p>{t('architecture.workBody')}</p></div>
        <div className="am-work-grid">
          <div className="am-workflows" role="tablist" aria-label={t('architecture.tabsAria')} aria-orientation="vertical">{workflows.map((item,i)=><button type="button" role="tab" id={`am-tab-${i}`} key={i} tabIndex={active===i?0:-1} aria-selected={active===i} aria-controls="am-work-panel" onClick={()=>setActive(i)} onKeyDown={event=>{
            const offset=event.key==='ArrowDown'?1:event.key==='ArrowUp'?-1:0;
            if(offset){event.preventDefault();const next=(i+offset+workflows.length)%workflows.length;setActive(next);root.current?.querySelector<HTMLButtonElement>(`#am-tab-${next}`)?.focus();}
          }}><span className="am-tab-number">0{i+1}</span><div><small>{item.subtitle}</small><h3>{item.title}</h3>{active===i&&<p>{item.description}</p>}</div><ArrowUpRight size={18}/></button>)}</div>
          <div className="am-work-panel" role="tabpanel" id="am-work-panel" aria-labelledby={`am-tab-${active}`} tabIndex={0}>
            <div className="am-panel-top"><span><i/>{t('architecture.demo')}</span><span aria-hidden="true">W / 0{active+1}</span></div>
            <div className="am-plan" key={active}><p className="am-plan-prompt">{scene.prompt}</p><div className="am-plan-label"><span>{t('architecture.draft')}</span><span aria-hidden="true">↳</span></div>{scene.steps.map((step,i)=><div className="am-plan-step" key={step}><span>0{i+1}</span><p>{step}</p><Check size={14}/></div>)}<div className="am-plan-review"><ShieldCheck size={16}/><span>{t('architecture.review')}</span></div></div>
            <p className="am-demo-note">{t('architecture.demoNote')}</p>
          </div>
        </div>
      </section>
      <section className="am-interlude"><img src={detail} width="1672" height="941" alt={t('architecture.detailAlt')} loading="lazy" data-am-reveal="scene"/><div className="am-interlude-shade"/><h2 data-am-reveal>{(t('architecture.interlude',{returnObjects:true}) as string[]).map(line=><span key={line}>{line}</span>)}</h2></section>
      <section id="trust" className="am-trust">
        <div className="am-section-line"><span>{t('architecture.trustLabel')}</span><ShieldCheck size={18}/></div>
        <h2 data-am-reveal>{t('architecture.trustTitle')}</h2>
        <div className="am-trust-grid">{principles.map((item,i)=><article data-am-reveal key={item.title}><span>0{i+1}</span><h3>{item.title}</h3><p>{item.body}</p></article>)}</div>
      </section>
      <section className="am-finale"><div className="am-section-line"><span>{t('architecture.finaleLabel')}</span><a href="#home">{t('architecture.back')}<ArrowUpRight size={14}/></a></div><div className="am-final-heading" data-am-reveal><h2>{(t('architecture.finaleTitle',{returnObjects:true}) as string[]).map(line=><span key={line}>{line}</span>)}</h2>{cta(true)}</div><div className="am-wordmark" aria-hidden="true">WORKMATE<span>↗</span></div></section>
    </main>
    <footer className="am-footer"><span>{t('marketing.brand')} / {t('architecture.footer')}</span><span>{t('architecture.footerNote')}</span><button type="button" onClick={onSwitch}>{t('architecture.other')}<ArrowUpRight size={13}/></button><span>© {new Date().getFullYear()}</span></footer>
  </div>;
}

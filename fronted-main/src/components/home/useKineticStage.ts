import { useEffect, useRef, type RefObject } from 'react';

type Scrap = {x:number;y:number;vx:number;vy:number;angle:number;spin:number;width:number;height:number;life:number;color:string;shape:number};

/** Finite paper burst, with independent animation channels so card dragging stays intact. */
export default function useKineticStage(surface: RefObject<HTMLDivElement>, enabled: boolean) {
  const launch = useRef<(target: HTMLElement) => void>(()=>{});
  useEffect(()=>{
    const page=surface.current;
    const hero=page?.querySelector<HTMLElement>('.bm-kinetic-hero');
    if(!enabled||!page||!hero)return;
    const layer=document.createElement('canvas');
    layer.className='bm-confetti-layer'; layer.setAttribute('aria-hidden','true');
    hero.appendChild(layer);
    const context=layer.getContext('2d');
    if(!context){layer.remove();return;}
    const reduced=window.matchMedia('(prefers-reduced-motion: reduce)');
    let width=0,height=0,frame=0,last=0,age=0;
    let scraps:Scrap[]=[];
    const animations=new Set<Animation>();
    const palette=['#1427e8','#f7a3e0','#ff6b3a','#fffdf3'];
    const resize=()=>{
      const box=hero.getBoundingClientRect();width=box.width;height=box.height;
      const ratio=Math.min(window.devicePixelRatio,1.5);
      layer.width=Math.round(width*ratio);layer.height=Math.round(height*ratio);
      context.setTransform(ratio,0,0,ratio,0,0);
    };
    const observer=new ResizeObserver(resize);observer.observe(hero);resize();
    const animate=(element:Element,keyframes:Keyframe[],options:KeyframeAnimationOptions)=>{
      const animation=element.animate(keyframes,options);
      animations.add(animation);
      animation.onfinish=()=>animations.delete(animation);
    };
    const tick=(now:number)=>{
      frame=0;
      const step=Math.min(2.5,(now-last)/16.67||1);last=now;age+=step;
      context.clearRect(0,0,width,height);
      for(const paper of scraps){
        paper.vy+=.16*step;paper.vx*=Math.pow(.992,step);paper.x+=paper.vx*step;paper.y+=paper.vy*step;
        paper.angle+=paper.spin*step;paper.life-=step;
        if(paper.life<=0)continue;
        context.save();context.translate(paper.x,paper.y);context.rotate(paper.angle);
        context.globalAlpha=Math.min(1,paper.life/25);context.fillStyle=paper.color;
        const flip=Math.cos(age*.075+paper.spin*10);
        context.scale(Math.abs(flip)*.7+.3,1);
        if(paper.shape===0){context.fillRect(-paper.width/2,-paper.height/2,paper.width,paper.height);context.strokeStyle='#1427e8';context.lineWidth=.7;context.strokeRect(-paper.width/2,-paper.height/2,paper.width,paper.height);}
        else if(paper.shape===1){context.beginPath();context.moveTo(0,-paper.height);context.lineTo(paper.width,paper.height*.5);context.lineTo(-paper.width*.5,0);context.closePath();context.fill();}
        else {context.fillRect(-paper.width,-2,paper.width*2,4);context.fillRect(-2,-paper.height,4,paper.height*2);}
        context.restore();
      }
      scraps=scraps.filter(paper=>paper.life>0&&paper.y<height+100);
      if(scraps.length)frame=requestAnimationFrame(tick);
      else context.clearRect(0,0,width,height);
    };
    launch.current=(target)=>{
      // Complete or cancel a previous burst before starting a new finite sequence.
      for(const animation of animations)animation.cancel();
      animations.clear();
      if(reduced.matches){
        animate(target,[{outlineOffset:'3px'},{outlineOffset:'10px'},{outlineOffset:'3px'}],{duration:250});
        return;
      }
      const heroBox=hero.getBoundingClientRect(),buttonBox=target.getBoundingClientRect();
      const origin={x:buttonBox.left+buttonBox.width/2-heroBox.left,y:buttonBox.top+buttonBox.height/2-heroBox.top};
      scraps=Array.from({length:85},(_,index)=>{
        const angle=(index/85)*Math.PI*2;
        const speed=4+Math.random()*12;
        return {x:origin.x,y:origin.y,vx:Math.cos(angle)*speed,vy:Math.sin(angle)*speed-5,angle:Math.random()*6,spin:(Math.random()-.5)*.22,width:5+Math.random()*13,height:8+Math.random()*20,life:80+Math.random()*45,color:palette[index%palette.length],shape:index%5===0?2:index%4===0?1:0};
      });
      age=0;last=performance.now();
      if(!frame)frame=requestAnimationFrame(tick);
      hero.querySelectorAll('.bm-title-glyph').forEach((glyph,index)=>{
        const offset=Math.sin(index*2.4)*55;
        animate(glyph,[{translate:'0 0',rotate:'0deg'},{translate:`${offset}px ${-40-Math.abs(offset)}px`,rotate:`${Math.sin(index)*23}deg`,offset:.24},{translate:'0 7px',rotate:'-2deg',offset:.8},{translate:'0 0',rotate:'0deg'}],{duration:1100,delay:index*18,easing:'cubic-bezier(.2,.7,.2,1)'});
      });
      hero.querySelectorAll('.bm-drag-card').forEach((card,index)=>{
        animate(card,[{translate:'0 0',rotate:'0deg'},{translate:`${(index-1)*40}px -65px`,rotate:`${(index-1)*17}deg`,offset:.3},{translate:'0 10px',rotate:'2deg',offset:.8},{translate:'0 0',rotate:'0deg'}],{duration:1300,delay:index*70,easing:'cubic-bezier(.2,.7,.2,1)'});
      });
      animate(target,[{rotate:'0deg',scale:'1'},{rotate:'-18deg',scale:'.8',offset:.12},{rotate:'38deg',scale:'1.15',offset:.45},{rotate:'0deg',scale:'1'}],{duration:950,easing:'cubic-bezier(.2,.7,.2,1)'});
    };
    const stop=()=>{
      cancelAnimationFrame(frame);frame=0;scraps=[];context.clearRect(0,0,width,height);
      for(const animation of animations)animation.cancel();
      animations.clear();
    };
    const onVisibility=()=>{if(document.hidden)stop();};
    reduced.addEventListener('change',stop);document.addEventListener('visibilitychange',onVisibility);
    return()=>{stop();observer.disconnect();layer.remove();launch.current=()=>{};reduced.removeEventListener('change',stop);document.removeEventListener('visibilitychange',onVisibility);};
  },[surface,enabled]);
  return (target:HTMLElement)=>launch.current(target);
}

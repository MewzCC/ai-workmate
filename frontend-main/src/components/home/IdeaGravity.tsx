import { useEffect, useRef, useState } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';

type Dot = {x:number;y:number;homeX:number;homeY:number;vx:number;vy:number;size:number;color:number};
type Wave = {x:number;y:number;radius:number;age:number;strength:number};
type Spark = {x:number;y:number;vx:number;vy:number;life:number;maxLife:number};
export default function IdeaGravity() {
  const canvas=useRef<HTMLCanvasElement>(null);
  const explode=useRef<()=>void>(()=>{});
  const [count,setCount]=useState(0);
  const { t }=useTranslation();
  useEffect(()=>{
    const surface=canvas.current;
    const context=surface?.getContext('2d');
    if(!surface||!context)return;
    const reduce=window.matchMedia('(prefers-reduced-motion: reduce)');
    const pointer={x:-9999,y:-9999,vx:0,vy:0,stamp:0,down:false};
    let dots:Dot[]=[],waves:Wave[]=[],sparks:Spark[]=[];
    let width=0,height=0,frame=0,visible=false,last=0,recovery=0,charge=0;
    const colors=['#e9ff52','#f7a3e0','#fffdf3'];
    const blast=(x:number,y:number,power=1)=>{
      if(reduce.matches)return;
      recovery=45;
      waves.push({x,y,radius:8,age:0,strength:power});
      if(waves.length>4)waves.shift();
      for(const dot of dots){
        const dx=dot.x-x,dy=dot.y-y,distance=Math.max(Math.hypot(dx,dy),1);
        const falloff=Math.max(.12,1-distance/Math.max(width*.65,500));
        const impulse=(22+Math.random()*28)*power*falloff;
        dot.vx+=dx/distance*impulse-dy/distance*power*7;
        dot.vy+=dy/distance*impulse+dx/distance*power*7;
      }
      for(let i=0;i<65;i++){
        const angle=Math.random()*Math.PI*2,speed=(5+Math.random()*16)*power,life=20+Math.random()*25;
        sparks.push({x,y,vx:Math.cos(angle)*speed,vy:Math.sin(angle)*speed,life,maxLife:life});
      }
      if(sparks.length>200)sparks=sparks.slice(-200);
    };
    const render=(step:number,staticFrame=false)=>{
      if(staticFrame||reduce.matches)context.clearRect(0,0,width,height);
      else {context.globalCompositeOperation='destination-out';context.fillStyle='rgba(0,0,0,.28)';context.fillRect(0,0,width,height);context.globalCompositeOperation='source-over';}
      const pace=Math.min(30,Math.hypot(pointer.vx,pointer.vy));
      const radius=Math.min(width*.37,175+pace*4);
      charge=pointer.down?Math.min(1,charge+.022*step):Math.max(0,charge-.07*step);
      recovery=Math.max(0,recovery-step);
      for(const dot of dots){
        const previousX=dot.x,previousY=dot.y;
        if(!reduce.matches&&!staticFrame){
          const dx=dot.x-pointer.x,dy=dot.y-pointer.y,distance=Math.hypot(dx,dy);
          if(distance<radius&&distance>.5){
            const weight=1-distance/radius;
            const radial=pointer.down ? -3.8-charge*3 : 1.5+pace*.3;
            const swirl=pointer.down ? 3.5+charge*4 : Math.max(.5,2.4-pace*.06);
            dot.vx+=(dx/distance*radial-dy/distance*swirl)*weight*step;
            dot.vy+=(dy/distance*radial+dx/distance*swirl)*weight*step;
            if(!pointer.down){dot.vx+=pointer.vx*.2*weight;dot.vy+=pointer.vy*.2*weight;}
          }
          const spring=pointer.down ? .005 : recovery>0 ? .0025 : .015;
          dot.vx+=(dot.homeX-dot.x)*spring*step;dot.vy+=(dot.homeY-dot.y)*spring*step;
          const friction=Math.pow(recovery>0?.955:.925,step);
          dot.vx=Math.max(-58,Math.min(58,dot.vx*friction));dot.vy=Math.max(-58,Math.min(58,dot.vy*friction));
          dot.x=Math.max(-width*.35,Math.min(width*1.35,dot.x+dot.vx*step));
          dot.y=Math.max(-height*.4,Math.min(height*1.4,dot.y+dot.vy*step));
        }
        const velocity=Math.hypot(dot.vx,dot.vy);
        const tint=velocity>20?'#ff9867':velocity>7?'#f7a3e0':colors[dot.color];
        if(velocity>1.5&&!reduce.matches&&!staticFrame){
          context.strokeStyle=tint;context.globalAlpha=Math.min(.7,velocity*.045);
          context.lineWidth=dot.size*.55;
          context.beginPath();context.moveTo(previousX-dot.vx*.7,previousY-dot.vy*.7);context.lineTo(dot.x,dot.y);context.stroke();
        }
        context.globalAlpha=1;context.fillStyle=tint;
        const size=dot.size*(1+Math.min(velocity,25)*.018);
        context.fillRect(dot.x-size/2,dot.y-size/2,size,size);
      }
      if(!reduce.matches&&!staticFrame){
        for(const wave of waves){
          wave.age+=step;wave.radius+=(16+wave.strength*5)*step;
          const alpha=Math.max(0,1-wave.age/36);
          context.globalAlpha=alpha*.75;context.strokeStyle='#f7a3e0';context.lineWidth=2;
          context.beginPath();context.arc(wave.x,wave.y,wave.radius,0,Math.PI*2);context.stroke();
          context.globalAlpha=alpha*.25;context.strokeStyle='#e9ff52';
          context.beginPath();context.arc(wave.x,wave.y,wave.radius*.78,0,Math.PI*2);context.stroke();
        }
        waves=waves.filter(wave=>wave.age<36);
        for(const spark of sparks){
          spark.life-=step;spark.x+=spark.vx*step;spark.y+=spark.vy*step;spark.vx*=.97;spark.vy*=.97;
          context.globalAlpha=Math.max(0,spark.life/spark.maxLife);context.strokeStyle='#fffdf3';context.lineWidth=1;
          context.beginPath();context.moveTo(spark.x-spark.vx*1.7,spark.y-spark.vy*1.7);context.lineTo(spark.x,spark.y);context.stroke();
        }
        sparks=sparks.filter(spark=>spark.life>0);
        if(pointer.down){
          context.globalAlpha=.4+charge*.4;context.strokeStyle='#fffdf3';context.lineWidth=1.5;
          context.beginPath();context.arc(pointer.x,pointer.y,24+charge*50,-Math.PI/2,-Math.PI/2+Math.PI*2*Math.max(.05,charge));context.stroke();
          context.globalAlpha=.3;context.strokeStyle='#f7a3e0';
          context.beginPath();context.arc(pointer.x,pointer.y,80+charge*55,0,Math.PI*2);context.stroke();
        }
      }
      context.globalAlpha=1;
      pointer.vx*=Math.pow(.8,step);pointer.vy*=Math.pow(.8,step);
    };
    const build=()=>{
      const rect=surface.getBoundingClientRect();width=rect.width;height=rect.height;
      const ratio=Math.min(window.devicePixelRatio,1.5);
      surface.width=Math.round(width*ratio);surface.height=Math.round(height*ratio);
      context.setTransform(ratio,0,0,ratio,0,0);
      const mask=document.createElement('canvas');mask.width=Math.round(width);mask.height=Math.round(height);
      const ink=mask.getContext('2d');if(!ink||width<1||height<1)return;
      const fontSize=Math.min(width*.205,240);
      ink.font=`900 ${fontSize}px Inter, sans-serif`;ink.textAlign='center';ink.textBaseline='middle';
      ink.fillText('MAKE IT',width*.5,height*.35);ink.fillText('HAPPEN',width*.5,height*.35+fontSize*.86);
      const pixels=ink.getImageData(0,0,mask.width,mask.height).data;
      dots=[];waves=[];sparks=[];pointer.down=false;charge=0;recovery=0;
      const spacing=width<720?7:9;
      for(let y=8;y<mask.height;y+=spacing)for(let x=8;x<mask.width;x+=spacing){
        if(pixels[(y*mask.width+x)*4+3]>160)dots.push({x,y,homeX:x,homeY:y,vx:0,vy:0,size:spacing*.36,color:Math.random()>.93?1:Math.random()>.985?2:0});
      }
      render(1,true);
    };
    const tick=(now:number)=>{
      frame=requestAnimationFrame(tick);
      if(!visible||document.hidden||reduce.matches){last=now;return;}
      if(now-last<30)return;
      const step=Math.min(2,(now-last)/33);last=now;render(step);
    };
    const position=(event:PointerEvent)=>{
      const rect=surface.getBoundingClientRect(),x=event.clientX-rect.left,y=event.clientY-rect.top;
      const now=performance.now(),elapsed=Math.max(8,now-pointer.stamp);
      if(pointer.x>-1000&&elapsed<140){
        pointer.vx=Math.max(-40,Math.min(40,(x-pointer.x)*16/elapsed));
        pointer.vy=Math.max(-40,Math.min(40,(y-pointer.y)*16/elapsed));
      }
      pointer.x=x;pointer.y=y;pointer.stamp=now;
    };
    const move=(event:PointerEvent)=>{if(event.pointerType==='mouse'){position(event);}};
    const down=(event:PointerEvent)=>{
      if(event.button!==0||reduce.matches)return;
      position(event);
      if(event.pointerType!=='mouse'){blast(pointer.x,pointer.y,1.2);return;}
      pointer.down=true;charge=0;surface.setPointerCapture(event.pointerId);
    };
    const up=(event:PointerEvent)=>{
      if(!pointer.down)return;
      position(event);pointer.down=false;blast(pointer.x,pointer.y,1.15+charge*1.5);
      if(surface.hasPointerCapture(event.pointerId))surface.releasePointerCapture(event.pointerId);
    };
    const leave=()=>{if(!pointer.down){pointer.x=-9999;pointer.y=-9999;pointer.vx=0;pointer.vy=0;}};
    const cancel=()=>{pointer.down=false;pointer.x=-9999;pointer.y=-9999;charge=0;};
    const preference=()=>{cancel();build();};
    explode.current=()=>{blast(width*.5,height*.48,2);};
    const resize=new ResizeObserver(build);resize.observe(surface);
    const intersection=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;if(!visible)cancel();});
    intersection.observe(surface);
    surface.addEventListener('pointermove',move,{passive:true});surface.addEventListener('pointerleave',leave);
    surface.addEventListener('pointerdown',down);surface.addEventListener('pointerup',up);
    surface.addEventListener('pointercancel',cancel);surface.addEventListener('lostpointercapture',cancel);
    window.addEventListener('blur',cancel);reduce.addEventListener('change',preference);
    build();frame=requestAnimationFrame(tick);
    return()=>{
      cancelAnimationFrame(frame);resize.disconnect();intersection.disconnect();
      surface.removeEventListener('pointermove',move);surface.removeEventListener('pointerleave',leave);
      surface.removeEventListener('pointerdown',down);surface.removeEventListener('pointerup',up);
      surface.removeEventListener('pointercancel',cancel);surface.removeEventListener('lostpointercapture',cancel);
      window.removeEventListener('blur',cancel);reduce.removeEventListener('change',preference);explode.current=()=>{};
    };
  },[]);
  return <section id="gravity" className="bm-gravity-lab">
    <div className="bm-lab-top"><span>{t('marketing.kinetic.labLabel')}</span><span aria-hidden="true">EXPERIMENT / 003 <span>✳</span></span></div>
    <canvas ref={canvas} aria-hidden="true"/>
    <div className="bm-lab-orbit" aria-hidden="true"><i/><i/></div>
    <div className="bm-lab-bottom"><div><h2>{t('marketing.kinetic.labTitle')}</h2><p>{t('marketing.kinetic.labHint')}</p></div><button type="button" className="bm-ignite-button" onClick={()=>{explode.current();setCount(value=>value+1);}}><span>{t('marketing.kinetic.ignite')}</span><span className="bm-ignite-number">{String(count).padStart(2,'0')}</span><ArrowUpRight size={22}/></button></div>
  </section>;
}

import { useEffect, useRef, useState } from 'react';

/** A self-contained ray-marched metal sculpture; no remote textures or runtime dependency. */
export default function LiquidSculpture({ paused }: { paused: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [fallback, setFallback] = useState(false);
  const pausedValue = useRef(paused);
  useEffect(() => { pausedValue.current = paused; }, [paused]);
  useEffect(() => {
    const surface = canvas.current;
    if (!surface) return;
    const gl = surface.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' });
    if (!gl) { setFallback(true); return; }
    const vertexSource = 'attribute vec2 position; void main(){ gl_Position=vec4(position,0.,1.); }';
    const fragmentSource = `
precision highp float;
uniform vec2 resolution;
uniform vec2 pointer;
uniform float time;
uniform float progress;
mat2 rot(float a){return mat2(cos(a),-sin(a),sin(a),cos(a));}
float shape(vec3 p){
 p.xz=rot(time*.16+pointer.x*.35)*p.xz;
 p.yz=rot(.5+pointer.y*.3+progress*.7)*p.yz;
 p.xy=rot(-.37)*p.xy;
 float a=atan(p.z,p.x);
 float radius=1.48+.19*cos(a*3.+time*.27);
 vec2 q=vec2(length(p.xz)-radius,p.y+.41*sin(a*3.+time*.27));
 return length(q)-(.43+.09*cos(a*3.+1.2));
}
vec3 normal(vec3 p){
 vec2 e=vec2(.002,0.);
 return normalize(vec3(shape(p+e.xyy)-shape(p-e.xyy),shape(p+e.yxy)-shape(p-e.yxy),shape(p+e.yyx)-shape(p-e.yyx)));
}
vec3 environment(vec3 r){
 vec3 c=vec3(.035,.04,.044);
 float band=pow(max(0.,1.-abs(r.y-.43)),25.);
 float edge=pow(max(0.,dot(r,normalize(vec3(-.7,.5,1.)))),12.);
 float strip=pow(max(0.,1.-abs(r.x-.3)),60.)*smoothstep(-.6,.5,r.y);
 float soft=pow(max(0.,dot(r,normalize(vec3(.4,-.5,.6)))),7.);
 c+=vec3(.9,.96,1.)*band*1.8+vec3(1.)*edge*2.4+vec3(1.)*strip*2.2;
 c+=vec3(.19,.24,.29)*soft;
 return c;
}
void main(){
 vec2 uv=(gl_FragCoord.xy-.5*resolution)/resolution.y;
 vec3 ro=vec3(0.,.5,6.5);
 vec3 rd=normalize(vec3(uv*2.6,-4.));
 ro.x+=pointer.x*.18;
 float travel=0.; float dist=0.;
 for(int i=0;i<76;i++){
   dist=shape(ro+rd*travel);
   if(dist<.002||travel>11.)break;
   travel+=dist*.8;
 }
 vec3 col=vec3(.025,.027,.029);
 col+=.012*exp(-length(uv)*2.);
 if(travel<11.&&dist<.01){
   vec3 p=ro+rd*travel;
   vec3 n=normal(p);
   vec3 reflected=reflect(rd,n);
   float fresnel=pow(1.-max(0.,dot(n,-rd)),4.);
   float diffuse=max(0.,dot(n,normalize(vec3(-.7,1.,2.))));
   float ao=clamp(shape(p+n*.3)/.3,.25,1.);
   col=environment(reflected)*(.73+.27*fresnel)*ao;
   col+=vec3(.11,.12,.13)*diffuse;
   col+=vec3(.28)*fresnel;
   col=col/(col+vec3(.65));
   col=pow(col,vec3(.85));
 }
 float vignette=1.-.22*dot(uv,uv);
 gl_FragColor=vec4(col*vignette,1.);
}`;
    const makeShader = (type: number, source: string) => {
      const shader = gl.createShader(type);
      if (!shader) return null;
      gl.shaderSource(shader, source); gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) { gl.deleteShader(shader); return null; }
      return shader;
    };
    const vertex = makeShader(gl.VERTEX_SHADER, vertexSource);
    const fragment = makeShader(gl.FRAGMENT_SHADER, fragmentSource);
    const program = gl.createProgram();
    if (!vertex || !fragment || !program) {
      if (vertex) gl.deleteShader(vertex);
      if (fragment) gl.deleteShader(fragment);
      if (program) gl.deleteProgram(program);
      setFallback(true); return;
    }
    gl.attachShader(program, vertex); gl.attachShader(program, fragment); gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
      setFallback(true); return;
    }
    gl.useProgram(program);
    const buffer = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]), gl.STATIC_DRAW);
    const position = gl.getAttribLocation(program, 'position');
    gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
    const locations = { resolution: gl.getUniformLocation(program,'resolution'), pointer: gl.getUniformLocation(program,'pointer'), time: gl.getUniformLocation(program,'time'), progress: gl.getUniformLocation(program,'progress') };
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0, last = 0, elapsed = 0, visible = true;
    let px = 0, py = 0, tx = 0, ty = 0;
    const draw = () => {
      px += (tx-px)*.045; py += (ty-py)*.045;
      gl.uniform2f(locations.resolution, surface.width, surface.height);
      gl.uniform2f(locations.pointer, px, py);
      gl.uniform1f(locations.time, elapsed);
      gl.uniform1f(locations.progress, Math.min(1,window.scrollY/window.innerHeight));
      gl.drawArrays(gl.TRIANGLES,0,6);
    };
    const resize = () => {
      const box = surface.getBoundingClientRect();
      const ratio = Math.min(window.devicePixelRatio, 1);
      surface.width = Math.max(1, Math.round(Math.min(box.width,1200)*ratio));
      surface.height = Math.max(1, Math.round(surface.width*box.height/Math.max(box.width,1)));
      gl.viewport(0,0,surface.width,surface.height); draw();
    };
    const observer = new ResizeObserver(resize); observer.observe(surface);
    const intersection = new IntersectionObserver(entries => { visible=entries[0].isIntersecting; });
    intersection.observe(surface);
    const pointerMove = (event: PointerEvent) => {
      if(reduce.matches || pausedValue.current) return;
      tx=(event.clientX/window.innerWidth-.5)*2; ty=(event.clientY/window.innerHeight-.5)*2;
    };
    const tick = (now: number) => {
      frame=requestAnimationFrame(tick);
      if(pausedValue.current || reduce.matches || !visible || document.hidden) {last=now;return;}
      if(now-last<33) return;
      elapsed+=Math.min((now-last)/1000,.05); last=now; draw();
    };
    const lost = (event: Event) => { event.preventDefault(); cancelAnimationFrame(frame); setFallback(true); };
    surface.addEventListener('webglcontextlost',lost);
    window.addEventListener('pointermove',pointerMove,{passive:true});
    resize(); frame=requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame); observer.disconnect(); intersection.disconnect();
      window.removeEventListener('pointermove',pointerMove);
      surface.removeEventListener('webglcontextlost',lost);
      gl.deleteBuffer(buffer); gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
    };
  }, []);
  return <div className="bm-sculpture" aria-hidden="true">{fallback ? <div className="bm-metal-fallback"/> : <canvas ref={canvas}/>}</div>;
}

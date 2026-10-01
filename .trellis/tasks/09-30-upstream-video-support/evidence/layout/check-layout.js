async page => {
 const external=[];
 await page.context().route('**/*',route=>{
   const url=route.request().url();
   if(url.startsWith('http://127.0.0.1:18762/'))return route.continue();
   external.push(url);return route.abort();
 });
 await page.goto('http://127.0.0.1:18762/');
 await page.evaluate(()=>window.ready);
 const results=[];
 for(const theme of ['light','dark']){
  await page.evaluate(async theme=>{const css=await fetch('style_'+theme+'.css').then(r=>r.text());document.querySelector('#theme').remove();const style=document.createElement('style');style.id='theme';style.textContent=css;document.head.append(style);},theme);
  for(const width of [320,360,768]){
   await page.setViewportSize({width,height:900});
   await page.evaluate(()=>document.querySelectorAll('.collapse').forEach(e=>e.style.display='none'));
   const hidden=await page.locator('.collapse video').evaluateAll(vs=>vs.every(v=>v.getBoundingClientRect().width===0));
   await page.locator('button').evaluateAll(bs=>bs.forEach(b=>b.click()));
   const cases=await page.locator('video').evaluateAll(vs=>vs.map(v=>{
    const r=v.getBoundingClientRect(),p=v.parentElement,pr=p.getBoundingClientRect(),s=getComputedStyle(p);
    const available=p.clientWidth-parseFloat(s.paddingLeft)-parseFloat(s.paddingRight);
    const before=p.querySelector('.before').getBoundingClientRect(),after=p.querySelector('.after').getBoundingClientRect();
    return {name:v.closest('section').dataset.case,width:r.width,height:r.height,intrinsic:[v.videoWidth,v.videoHeight],available,
     fits:r.width<=available+1,ratio:Math.abs(r.width/r.height-v.videoWidth/v.videoHeight)<0.01,
     natural:r.width<=v.videoWidth+1,block:before.bottom<=r.top+1&&after.top>=r.bottom-1};
   }));
   const audio=await page.locator('audio').evaluate(e=>{const s=getComputedStyle(e);return {width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height,display:s.display};});
   const bodyOverflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
   results.push({theme,width,hidden,cases,audio,bodyOverflow});
   await page.screenshot({path:'.trellis/tasks/09-30-upstream-video-support/evidence/layout/'+theme+'-'+width+'.png',fullPage:true});
  }
 }
 return {browser:await page.context().browser().version(),externalRequests:external,results};
}

async page => {
 const html="<video src='https://page.example/attachments/a&#39;b.mp4?token=$1&amp;x=2#t=1' controls='controls'></video>";
 const dom=await page.evaluate(html=>{
  const node=new DOMParser().parseFromString(html,'text/html').querySelector('video');
  return {src:node.getAttribute('src'),attributes:[...node.attributes].map(a=>a.name)};
 },html);
 if(dom.src!=="https://page.example/attachments/a'b.mp4?token=$1&x=2#t=1"||dom.attributes.join(',')!=='src,controls')throw Error('DOM attribute mismatch');
 for(const kind of ['landscape','portrait','small']){
  const downloadPromise=page.waitForEvent('download');
  await page.evaluate(kind=>{const a=document.createElement('a');a.href=document.querySelector('[data-case="'+kind+'-normal"] video').src;a.download=kind+'.webm';a.click();},kind);
  await (await downloadPromise).saveAs('.trellis/tasks/09-30-upstream-video-support/evidence/layout/'+kind+'.webm');
 }
 const audioBaseline=await page.evaluate(()=>{
  const audio=document.querySelector('audio');const before=audio.getBoundingClientRect();
  const videoRule=[...document.styleSheets].flatMap(s=>[...s.cssRules]).find(r=>r.selectorText==='video');
  const original=videoRule.style.cssText;videoRule.style.cssText='';const after=audio.getBoundingClientRect();videoRule.style.cssText=original;
  return {before:[before.width,before.height],withoutVideoRule:[after.width,after.height],unchanged:before.width===after.width&&before.height===after.height};
 });
 return {dom,audioBaseline};
}

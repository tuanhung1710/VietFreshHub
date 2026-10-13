// Run the order MockMvc tests first to generate HTML fixtures. Uses installed Playwright; does not connect to an app database.
const fs = require('fs');
const path = require('path');
const http = require('http');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async()=>{
 const root=process.cwd(); const ui=path.join(root,'.document-analysis/fe052/ui');
 const types={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.jpg':'image/jpeg','.png':'image/png','.svg':'image/svg+xml'};
 const server=http.createServer((req,res)=>{
  const uri=new URL(req.url,'http://localhost').pathname;
  const filename=uri.endsWith('.html')?path.join(ui,path.basename(uri)):path.join(root,'src/main/resources/static',uri);
  if(!filename.startsWith(root)||!fs.existsSync(filename)||!fs.statSync(filename).isFile()){res.writeHead(404);res.end();return;}
  res.writeHead(200,{'Content-Type':types[path.extname(filename)]||'application/octet-stream'});res.end(fs.readFileSync(filename));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || undefined,args:['--disable-background-networking','--disable-component-update']});
 const results=[];
 try {
  const context=await browser.newContext({locale:"vi-VN"}); const page=await context.newPage();
  await page.route('**/*',route=>new URL(route.request().url()).origin===base?route.continue():route.abort());
  for(const width of [360,390,430,600,760,850,960,1000,1280,1440,1920]){
   await page.setViewportSize({width,height:1000});
   for(const name of ['history','empty-history','invalid-filter','detail-cod','detail-refunded','detail-multishop']){
    await page.goto(base+'/'+name+'.html');
    const layout=await page.evaluate(()=>({viewport:innerWidth,scroll:document.documentElement.scrollWidth,
     overflowing:[...document.querySelectorAll('main *')].filter(el=>{const r=el.getBoundingClientRect();return r.width>0&&r.right>innerWidth+1;}).map(el=>({tag:el.tagName,class:el.className,right:Math.round(el.getBoundingClientRect().right)}))}));
    results.push({name,width,...layout});
    if([360,1440,1920].includes(width)){
     await page.screenshot({path:path.join(ui,name+'-'+width+'.png'),fullPage:true});
     if(name==='detail-refunded'&&width===360)await page.locator('aside.summary').screenshot({path:path.join(ui,'detail-refunded-summary-360.png')});
    }
   }
  }
  fs.writeFileSync(path.join(ui,'layout-results.json'),JSON.stringify(results,null,2));
  const bad=results.filter(r=>r.scroll>r.viewport||r.overflowing.length>0);
  console.log(JSON.stringify({cases:results.length,failures:bad},null,2));
  if(bad.length)process.exitCode=1;
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exit(1);});

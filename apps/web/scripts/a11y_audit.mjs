import puppeteer from 'puppeteer';
import {AxePuppeteer} from '@axe-core/puppeteer';
const base=process.env.PULSE_BASE_URL||'http://localhost:5173';
const pages=['/','/academy','/dashboard','/devices','/billing','/founder'];
const browser=await puppeteer.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
let failed=false;
try{for(const path of pages){const page=await browser.newPage();const res=await page.goto(base+path,{waitUntil:'networkidle0',timeout:60000}).catch(e=>{console.error(path,e.message);failed=true;return null});if(!res)continue;const results=await new AxePuppeteer(page).withTags(['wcag2a','wcag2aa']).analyze();console.log(JSON.stringify({page:path,violations:results.violations.length,passes:results.passes.length},null,2));if(results.violations.length){failed=true;for(const v of results.violations)console.error(v.id,v.impact,v.help,v.nodes.map(n=>n.target).join(','));}await page.close();}}finally{await browser.close();}
process.exit(failed?1:0);

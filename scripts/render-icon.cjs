const {chromium}=require('../web/node_modules/playwright');
const fs=require('node:fs');const path=require('node:path');
(async()=>{
 const [output,png]=process.argv.slice(2);
 if(!output||!png)throw new Error('Usage: node render-icon.cjs icon.ico icon.png');
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:256,height:256}});
  const svg=fs.readFileSync(path.join(__dirname,'../web/public/assets/brand/boltunya.svg'),'utf8');
  await page.setContent(`<style>html,body{margin:0;background:transparent}svg{width:256px;height:256px;display:block}</style>${svg}`);
  const data=await page.screenshot({omitBackground:true});
  fs.mkdirSync(path.dirname(png),{recursive:true});fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(png,data);
  const header=Buffer.alloc(22);header.writeUInt16LE(1,2);header.writeUInt16LE(1,4);header.writeUInt16LE(1,10);header.writeUInt16LE(32,12);header.writeUInt32LE(data.length,14);header.writeUInt32LE(22,18);
  fs.writeFileSync(output,Buffer.concat([header,data]));
 }finally{await browser.close();}
})().catch(error=>{console.error(error.message);process.exitCode=1;});

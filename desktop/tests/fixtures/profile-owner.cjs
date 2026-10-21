const {createServer} = require('node:http');
const {writeFileSync} = require('node:fs');
const {join} = require('node:path');
const server = createServer((request,response)=>{setTimeout(()=>{response.setHeader('Content-Type','application/json');response.end('{}');},Number(process.argv[3]||0));});
server.listen(0,'127.0.0.1',()=>{writeFileSync(join(process.argv[2],'launch.url'),`http://127.0.0.1:${server.address().port}/?key=synthetic-migration`);process.send('ready');});

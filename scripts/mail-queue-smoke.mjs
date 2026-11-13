import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.argv[2]??'http://127.0.0.1:8080',cookies=new Map();
async function request(path,method='GET',body,form=false) {
 const headers={};
 if(method!=='GET') {const csrf=await request('/api/auth/csrf'); headers[csrf.body.headerName]=csrf.body.token;}
 headers.Cookie=[...cookies].map(([k,v])=>k+'='+v).join('; ');
 if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
 const response=await fetch(base+path,{method,headers,redirect:'manual',body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
 for(const cookie of response.headers.getSetCookie()){const[k,...v]=cookie.split(';')[0].split('=');if(v.join('='))cookies.set(k,v.join('='));else cookies.delete(k);}
 const text=await response.text();return{status:response.status,body:text?JSON.parse(text):null};
}
async function expect(path,method,body,status,form=false){const r=await request(path,method,body,form);assert.equal(r.status,status,method+' '+path+': '+JSON.stringify(r.body));return r.body;}
const config=await expect('/api/public/config','GET',undefined,200);assert.equal(config.demo,true);assert.equal(config.mailMode,'demo','Teste apenas com mensagens simuladas.');
await expect('/api/admin/emails','GET',undefined,401);
const email=randomUUID()+'@example.test',password='frase exclusiva para fila 2030';
await expect('/api/auth/register','POST',{requestKey:randomUUID(),businessName:'Fila fictícia de e-mails',category:'Teste',address:'Online fictício',zoneId:'America/Sao_Paulo',ownerName:'Ana Teste',email,password,demoAcknowledged:true},201);
const inbox=await expect('/api/auth/demo-mailbox','GET',undefined,200);assert.equal(inbox[0].purpose,'VERIFY');
await expect('/api/auth/login','POST',{username:email,password},204,true);
const monitor=await expect('/api/admin/emails','GET',undefined,200);
assert.equal(monitor.mode,'demo');assert.equal(monitor.counts.ACCEPTED,1);
assert.equal(monitor.messages[0].status,'ACCEPTED');
assert.ok(!JSON.stringify(monitor).includes(email));assert.ok(!JSON.stringify(monitor).includes('token='));assert.ok(!JSON.stringify(monitor).includes('payload'));
await expect('/api/admin/emails/'+monitor.messages[0].id+'/retry','POST',undefined,409);
assert.equal((await expect('/api/admin/emails?status=FAILED','GET',undefined,200)).messages.length,0);
await expect('/api/admin/emails?status=invalid','GET',undefined,400);
await expect('/api/auth/logout','POST',undefined,204);
await expect('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
const other=await expect('/api/admin/emails','GET',undefined,200);
assert.ok(!other.messages.some(item=>item.id===monitor.messages[0].id));
await expect('/api/admin/emails/'+monitor.messages[0].id+'/retry','POST',undefined,404);
await expect('/api/auth/logout','POST',undefined,204);
console.log('OK: fila persistente simulada, acompanhamento privado por empresa, filtros, proteção contra repetição indevida e isolamento do histórico, sem exposição de links ou destinatários.');

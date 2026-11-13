import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.argv[2]??'http://127.0.0.1:8080';
function client() {
  const cookies=new Map();
  async function request(path,method='GET',body,form=false) {
    const headers={};
    if(method!=='GET') { const csrf=await request('/api/auth/csrf'); assert.equal(csrf.status,200); headers[csrf.body.headerName]=csrf.body.token; }
    headers.Cookie=[...cookies].map(([k,v])=>k+'='+v).join('; ');
    if(body) headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
    const response=await fetch(base+path,{method,headers,redirect:'manual',body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
    for(const cookie of response.headers.getSetCookie()) {
      const [key,...parts]=cookie.split(';')[0].split('='), value=parts.join('=');
      if(value) {
        if(key==='AGENDA_DEMO_MAILBOX') {
          assert.match(cookie,/HttpOnly/i); assert.match(cookie,/SameSite=Strict/i);
          if(base.startsWith('https://'))assert.match(cookie,/; Secure/i);
        }
        cookies.set(key,value);
      } else cookies.delete(key);
    }
    const text=await response.text(); return {status:response.status,body:text?JSON.parse(text):null};
  }
  async function expect(path,method,body,status,form=false) {
    const result=await request(path,method,body,form);
    assert.equal(result.status,status,method+' '+path+': '+JSON.stringify(result.body)); return result.body;
  }
  return {expect};
}
const first=client(), second=client(), stranger=client();
const config=await first.expect('/api/public/config','GET',undefined,200);
assert.equal(config.demo,true); assert.equal(config.mailMode,'demo','Este roteiro só pode usar a caixa de teste, sem envio real.');
const email=randomUUID()+'@example.test', password='frase original de teste 2030', replacement='outra frase exclusiva de teste 2030';
await first.expect('/api/auth/register','POST',{requestKey:randomUUID(),businessName:'Teste fictício de acesso',category:'Teste',address:'Online fictício',
 zoneId:'America/Sao_Paulo',ownerName:'Ana Teste',email,password,demoAcknowledged:true},201);
assert.deepEqual(await stranger.expect('/api/auth/demo-mailbox','GET',undefined,200),[]);
let inbox=await first.expect('/api/auth/demo-mailbox','GET',undefined,200);
const verify=inbox.find(item=>item.purpose==='VERIFY'); assert.ok(verify);
const verification=new URL(verify.actionUrl).hash.split('token=')[1];
await first.expect('/api/auth/password/reset','POST',{token:verification,password:replacement},400);
for(const instance of [first,second])await instance.expect('/api/auth/login','POST',{username:email,password},204,true);
assert.equal((await first.expect('/api/admin/business','GET',undefined,200)).emailVerified,false);
const unknown=await stranger.expect('/api/auth/password/request','POST',{email:'unknown@example.test'},202);
const known=await first.expect('/api/auth/password/request','POST',{email},202); assert.deepEqual(known,unknown);
inbox=await first.expect('/api/auth/demo-mailbox','GET',undefined,200);
const reset=inbox.find(item=>item.purpose==='RESET'); assert.ok(reset);
const token=new URL(reset.actionUrl).hash.split('token=')[1];
await first.expect('/api/auth/password/reset','POST',{token,password:replacement},200);
for(const instance of [first,second])await instance.expect('/api/auth/me','GET',undefined,401);
await first.expect('/api/auth/password/reset','POST',{token,password:replacement},400);
await first.expect('/api/auth/email/confirm','POST',{token:verification},400);
await first.expect('/api/auth/login','POST',{username:email,password},401,true);
await first.expect('/api/auth/login','POST',{username:email,password:replacement},204,true);
assert.equal((await first.expect('/api/admin/business','GET',undefined,200)).emailVerified,true);
await first.expect('/api/auth/logout','POST',undefined,204);
inbox=await first.expect('/api/auth/demo-mailbox','GET',undefined,200);
assert.ok(inbox.some(item=>item.purpose==='CHANGED')); assert.ok(!JSON.stringify(inbox).includes(replacement));
console.log('OK: caixa de teste privada, recuperação genérica, propósito e uso único dos links, troca da senha, confirmação do e-mail, revogação de duas sessões e notificação sem senha.');

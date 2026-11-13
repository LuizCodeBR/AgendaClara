import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
const phase=process.argv[2],base=process.argv[3]??'http://127.0.0.1:8080',file=process.argv[4];
assert.ok(file&&['before','after'].includes(phase),'Informe before|after, URL e arquivo privado temporário.');
const fixture=phase==='after'?JSON.parse(fs.readFileSync(file,'utf8')):null;
const cookies=new Map(fixture?.cookies??[]);
async function request(path,method='GET',body,form=false) {
 const headers={};
 if(method!=='GET'){const csrf=await request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
 headers.Cookie=[...cookies].map(([name,value])=>name+'='+value).join('; ');
 if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
 const response=await fetch(base+path,{method,headers,redirect:'manual',body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
 for(const item of response.headers.getSetCookie()){const[name,...value]=item.split(';')[0].split('=');if(value.join('='))cookies.set(name,value.join('='));else cookies.delete(name);}
 const text=await response.text();return{status:response.status,body:text?JSON.parse(text):null};
}
if(phase==='before'){
 const email=randomUUID()+'@example.test',password='senha ficticia exclusiva do teste de restauracao';
 const registered=await request('/api/auth/register','POST',{requestKey:randomUUID(),businessName:'Empresa fictícia de restauração',category:'Teste',address:'Online fictício',zoneId:'America/Sao_Paulo',ownerName:'Ana Teste',email,password,demoAcknowledged:true});
 assert.equal(registered.status,201);
 const inbox=await request('/api/auth/demo-mailbox');assert.equal(inbox.status,200);assert.ok(inbox.body.length);
 fs.writeFileSync(file,JSON.stringify({email,password,businessId:registered.body.businessId,mailId:inbox.body[0].id,cookies:[...cookies]}),{mode:0o600,flag:'wx'});
 console.log('OK: dados fictícios preparados para conferir a restauração, sem exibir prova privada ou senha.');
}else{
 const inbox=await request('/api/auth/demo-mailbox');assert.equal(inbox.status,200);assert.ok(inbox.body.some(item=>item.id===fixture.mailId));
 const login=await request('/api/auth/login','POST',{username:fixture.email,password:fixture.password},true);assert.equal(login.status,204);
 const me=await request('/api/auth/me');assert.equal(me.body.business.id,fixture.businessId);
 const history=await request('/api/admin/emails');assert.ok(history.body.messages.some(item=>item.id===fixture.mailId));
 assert.equal((await request('/api/auth/logout','POST')).status,204);
 console.log('OK: empresa, conta, senha e mesma mensagem privada recuperadas após backup criptografado e restauração em outro diretório.');
}

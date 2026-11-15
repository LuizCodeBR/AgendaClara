import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import fs from 'node:fs';
const phase=['before','after'].includes(process.argv[2])?process.argv[2]:'smoke',base=process.argv[phase==='smoke'?2:3]??'http://127.0.0.1:8080',file=process.argv[4];
class Client{
 cookies=[];
 async request(path,method='GET',body,form=false){
  const headers={};if(this.accountId&&(path.startsWith('/api/professional/')||path.startsWith('/api/admin/professional-access')))headers['X-Agenda-Account']=this.accountId;if(this.businessId&&path.startsWith('/api/admin/reports'))headers['X-Agenda-Business']=this.businessId;if(method!=='GET'){const csrf=await this.request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
  headers.Cookie=this.cookies.filter(c=>path===c.path||path.startsWith(c.path.endsWith('/')?c.path:c.path+'/')).map(c=>c.name+'='+c.value).join('; ');
  if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
  const r=await fetch(base+path,{method,headers,redirect:'manual',signal:AbortSignal.timeout(45000),body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
  for(const item of r.headers.getSetCookie()){const pair=item.split(';')[0],split=pair.indexOf('='),name=pair.slice(0,split),value=pair.slice(split+1),path=/;\s*Path=([^;]+)/i.exec(item)?.[1]??'/';this.cookies=this.cookies.filter(c=>!(c.name===name&&c.path===path));if(value)this.cookies.push({name,value,path});}
  const text=await r.text();return{status:r.status,body:text?(r.headers.get('content-type')?.startsWith('text/csv')?text:JSON.parse(text)):null,headers:r.headers};
 }
 async ok(path,method='GET',body,status=200,form=false){const r=await this.request(path,method,body,form);assert.equal(r.status,status,method+' '+path);return r.body;}
}
const owner=new Client(),staff=new Client();let proof,keep=false;
await owner.ok('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
owner.accountId=(await owner.ok('/api/auth/me')).id;
async function login(){await staff.ok('/api/auth/login','POST',{username:proof.email,password:proof.password},204,true);const me=await staff.ok('/api/auth/me');assert.equal(me.role,'PROFESSIONAL');assert.equal(me.professionalId,proof.person.id);if(proof.staffId)assert.equal(me.id,proof.staffId);staff.accountId=me.id;return me;}
async function clean(){
 if(!proof)return;
 for(const person of [proof.person,proof.waiting].filter(Boolean)){
  const state=(await owner.ok('/api/admin/professional-access')).find(p=>p.professionalId===person.id);
  if(state)await owner.ok('/api/admin/professional-access/'+person.id+'/revoke','POST',{version:state.version},204);
 }
 if(proof.appointment)await owner.ok('/api/admin/appointments/'+proof.appointment.id+'/cancel','POST');
 for(const person of [proof.person,proof.waiting].filter(Boolean)){
  const current=(await owner.ok('/api/admin/professionals')).find(p=>p.id===person.id);
  await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...current,active:false,serviceIds:[]});
 }
}
try{
 if(phase==='after'){proof=JSON.parse(fs.readFileSync(file,'utf8'));await login();const preview=await staff.ok('/api/auth/professional-invitations/preview','POST',{token:proof.token});assert.equal(preview.email,proof.waitingEmail);}
 else{
  const catalog=await owner.ok('/api/admin/catalog'),service=catalog.services[0],date=catalog.suggestedDate;
  const person=await owner.ok('/api/admin/professionals','POST',{name:'Profissional fictício '+randomUUID(),specialty:'Ensaio isolado de acesso'},201);
  proof={person,date,email:'staff-'+randomUUID()+'@example.test',password:'Senha longa fictícia profissional 123!'};
  const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
  await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
  const slots=(await owner.ok('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;
  const receipt=await owner.ok('/api/public/barbearia-horizonte/bookings','POST',{requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente próprio fictício',customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version},201);
  proof.appointment=(await owner.ok('/api/admin/agenda?date='+date)).appointments.find(a=>a.reference===receipt.reference);
  const invitation=await owner.ok('/api/admin/professional-access/'+person.id+'/invite','POST',{email:proof.email,version:0});
  await staff.ok('/api/auth/professional-invitations/preview','POST',{token:invitation.token});
  await staff.ok('/api/auth/professional-invitations/accept','POST',{token:invitation.token,password:proof.password},204);
  await staff.ok('/api/auth/professional-invitations/accept','POST',{token:invitation.token,password:proof.password},400);
  proof.staffId=(await login()).id;
  proof.waiting=await owner.ok('/api/admin/professionals','POST',{name:'Convite fictício '+randomUUID(),specialty:'Prova de restauração'},201);
  proof.waitingEmail='pending-'+randomUUID()+'@example.test';
  proof.token=(await owner.ok('/api/admin/professional-access/'+proof.waiting.id+'/invite','POST',{email:proof.waitingEmail,version:0})).token;
 }
 const agenda=await staff.ok('/api/professional/agenda?'+new URLSearchParams({date:proof.date,businessId:randomUUID(),professionalId:proof.waiting.id}));
 assert.equal(agenda.appointments.length,1);assert.equal(agenda.appointments[0].id,proof.appointment.id);
 const text=JSON.stringify(agenda);for(const field of ['customerPhone','priceCents','reference','token'])assert.ok(!text.includes(field));
 await staff.ok('/api/admin/reports/options','GET',undefined,403);
 await staff.ok('/api/admin/professional-access','GET',undefined,403);
 const current=staff.accountId;staff.accountId=randomUUID();await staff.ok('/api/professional/agenda?date='+proof.date,'GET',undefined,412);staff.accountId=current;
 const history=await staff.ok('/api/professional/appointments/'+proof.appointment.id+'/history');assert.equal(history.events.length,1);assert.ok(!JSON.stringify(history).includes('reason'));
 await staff.ok('/api/professional/appointments/'+proof.appointment.id+'/outcome','POST',{status:'COMPLETED',version:0,requestKey:randomUUID(),reason:''},409);
 if(phase==='before'){fs.writeFileSync(file,JSON.stringify(proof),{flag:'wx',mode:0o600});keep=true;console.log('OK: conta profissional, reserva própria e convite pendente preparados para preservação.');}
 else{await clean();proof=null;await staff.ok('/api/auth/me','GET',undefined,401);console.log('OK: acesso próprio, isolamento, dados mínimos, convite, prazo e revogação'+(phase==='after'?' preservados após atualização/restauração.':'.'));}
}finally{if(!keep&&proof)await clean();await owner.ok('/api/auth/logout','POST',undefined,204);}

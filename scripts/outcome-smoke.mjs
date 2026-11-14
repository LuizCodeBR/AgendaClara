import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import fs from 'node:fs';
const phase=['before','after'].includes(process.argv[2])?process.argv[2]:'smoke',base=process.argv[phase==='smoke'?2:3]??'http://127.0.0.1:8080',file=process.argv[4];
class Client{
 cookies=[];
 async request(path,method='GET',body,form=false){
  const headers={};if(method!=='GET'){const csrf=await this.request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
  headers.Cookie=this.cookies.filter(c=>path===c.path||path.startsWith(c.path.endsWith('/')?c.path:c.path+'/')).map(c=>c.name+'='+c.value).join('; ');
  if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
  const r=await fetch(base+path,{method,headers,redirect:'manual',signal:AbortSignal.timeout(45000),body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
  for(const item of r.headers.getSetCookie()){const pair=item.split(';')[0],split=pair.indexOf('='),name=pair.slice(0,split),value=pair.slice(split+1),path=/;\s*Path=([^;]+)/i.exec(item)?.[1]??'/';this.cookies=this.cookies.filter(c=>!(c.name===name&&c.path===path));if(value)this.cookies.push({name,value,path});}
  const text=await r.text();return{status:r.status,body:text?JSON.parse(text):null};
 }
 async ok(path,method='GET',body,status=200,form=false){const r=await this.request(path,method,body,form);assert.equal(r.status,status,method+' '+path);return r.body;}
}
const owner=new Client(),client=new Client();
await owner.ok('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
let person,appointment,date,receipt;
async function cleanup(){if(appointment)await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST');if(person){const current=(await owner.ok('/api/admin/professionals')).find(p=>p.id===person.id);if(current)await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...current,active:false,serviceIds:[]});}await owner.ok('/api/auth/logout','POST',undefined,204);}
if(phase==='after'){
 const proof=JSON.parse(fs.readFileSync(file,'utf8'));({person,appointment,date,receipt}=proof);client.cookies=proof.cookies;
 const entries=await owner.ok('/api/admin/appointments/'+appointment.id+'/history');assert.deepEqual(entries.events,proof.events);
 const view=await client.ok('/api/customer/reservations/'+appointment.id);assert.equal(view.reference,receipt.reference);assert.equal(view.version,appointment.version);
 await cleanup();console.log('OK: histórico, versões, sessão e reserva preservados após atualização ou restauração.');
}else{
 try{
 const catalog=await owner.ok('/api/admin/catalog'),service=catalog.services[0];date=catalog.suggestedDate;
 person=await owner.ok('/api/admin/professionals','POST',{name:'Histórico fictício '+randomUUID(),specialty:'Teste isolado'},201);
 const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
 await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
 const slots=(await client.ok('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;assert.ok(slots.length>1);
 receipt=await client.ok('/api/public/barbearia-horizonte/bookings','POST',{requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente fictício de histórico',customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version},201);
 appointment=(await owner.ok('/api/admin/agenda?date='+date)).appointments.find(a=>a.reference===receipt.reference);assert.ok(appointment);
 const path='/api/admin/appointments/'+appointment.id;
 const initial=await owner.ok(path+'/history');assert.equal(initial.events.length,1);assert.equal(initial.events[0].action,'CREATED');assert.equal(initial.events[0].actorType,'CUSTOMER');
 await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken});
 await client.ok(path+'/history','GET',undefined,401);
 await client.ok(path+'/outcome','POST',{status:'COMPLETED',version:0,requestKey:randomUUID()},401);
 await owner.ok(path+'/outcome','POST',{status:'COMPLETED',version:0,requestKey:randomUUID()},409);
 await owner.ok(path+'/outcome','POST',{status:'BOOKED',version:0,requestKey:randomUUID()},400);
 await owner.ok(path+'/outcome','POST',{status:'NO_SHOW',version:0.5,requestKey:randomUUID()},400);
 await owner.ok(path+'/history?beforeVersion=-1','GET',undefined,400);
 appointment=await owner.ok(path+'/reschedule','PATCH',{version:0,startsAt:slots[1].startsAt});
 const entries=await owner.ok(path+'/history');assert.equal(entries.events.length,2);assert.equal(entries.events[0].action,'RESCHEDULED');assert.equal(entries.events[0].actorName,'Marina');assert.equal(entries.events[0].fromStart,slots[0].startsAt);assert.equal(entries.events[0].toStart,slots[1].startsAt);
 if(phase==='before'){assert.ok(file);fs.writeFileSync(file,JSON.stringify({person,appointment,date,receipt,events:entries.events,cookies:client.cookies}),{flag:'wx',mode:0o600});await owner.ok('/api/auth/logout','POST',undefined,204);console.log('OK: prova privada de histórico e sessão preparada para verificar restauração.');}
 else{await owner.ok(path+'/cancel','POST',{version:1});const last=await owner.ok(path+'/history');assert.deepEqual(last.events.map(e=>e.action),['CANCELLED','RESCHEDULED','CREATED']);assert.equal(last.events[0].actorType,'OWNER');console.log('OK: histórico por empresa, autor, remarcação, cancelamento, acesso privado e recusa de resultado antecipado e entradas inválidas.');}
 }finally{if(phase!=='before'||!receipt)await cleanup();}
}

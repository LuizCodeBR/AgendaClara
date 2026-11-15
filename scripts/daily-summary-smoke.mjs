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
async function summary(){return owner.ok('/api/admin/professionals/'+person.id+'/daily-summary?date='+date);}
async function cleanup(){
 if(appointment)await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST');
 if(person){const current=(await owner.ok('/api/admin/professionals')).find(p=>p.id===person.id);if(current)await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...current,active:false,serviceIds:[]});}
 await owner.ok('/api/auth/logout','POST',undefined,204);
}
if(phase==='after'){
 const proof=JSON.parse(fs.readFileSync(file,'utf8'));({person,appointment,date,receipt}=proof);
 const result=await summary();assert.equal(result.professional.phone,'5511000000000');assert.equal(result.professional.version,proof.version);
 assert.deepEqual(result.appointments,proof.appointments);
 await cleanup();console.log('OK: contato privado, versão e resumo preservados após restauração.');
}else{
 try{
 const catalog=await owner.ok('/api/admin/catalog'),service=catalog.services[0];date=catalog.suggestedDate;
 person=await owner.ok('/api/admin/professionals','POST',{name:'Resumo fictício '+randomUUID(),specialty:'Teste isolado de resumo'},201);
 const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
 await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
 const path='/api/admin/professionals/'+person.id,initial=await summary();
 assert.equal(initial.professional.phone,'');assert.deepEqual(initial.appointments,[]);
 await client.ok(path+'/daily-summary?date='+date,'GET',undefined,401);
 const people=(await client.ok('/api/public/estudio-aurora/catalog')).professionals;
 await owner.ok('/api/admin/professionals/'+people[0].id+'/daily-summary?date='+date,'GET',undefined,404);
 await owner.ok(path+'/daily-summary?date=invalid','GET',undefined,400);
 await owner.ok(path+'/whatsapp-contact','PUT',{phone:'https://example.com',version:initial.professional.version},400);
 const saved=await owner.ok(path+'/whatsapp-contact','PUT',{phone:'+55 (11) 00000-0000',version:initial.professional.version});
 assert.equal(saved.phone,'5511000000000');assert.equal(saved.version,initial.professional.version+1);
 await owner.ok(path+'/whatsapp-contact','PUT',{phone:'',version:initial.professional.version},409);
 const publicCatalog=await client.ok('/api/public/barbearia-horizonte/catalog');assert.ok(!JSON.stringify(publicCatalog).includes('5511000000000'));
 const slots=(await client.ok('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;
 receipt=await client.ok('/api/public/barbearia-horizonte/bookings','POST',{requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente fictício do resumo',customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version},201);
 appointment=(await owner.ok('/api/admin/agenda?date='+date)).appointments.find(a=>a.reference===receipt.reference);
 let result=await summary();assert.equal(result.appointments.length,1);assert.equal(result.appointments[0].customerName,'Cliente fictício do resumo');
 assert.equal(result.appointments[0].serviceName,service.name);assert.equal(result.appointments[0].startsAt,slots[0].startsAt);
 assert.ok(!JSON.stringify(result).includes('customerPhone'));assert.ok(!JSON.stringify(result).includes(receipt.customerAccessToken));
 if(phase==='before'){
  fs.writeFileSync(file,JSON.stringify({person,appointment,date,receipt,version:result.professional.version,appointments:result.appointments}),{flag:'wx',mode:0o600});
  await owner.ok('/api/auth/logout','POST',undefined,204);console.log('OK: prova privada de contato e resumo preparada para restauração.');
 }else{
  await owner.ok('/api/admin/appointments/'+appointment.id+'/reschedule','PATCH',{version:0,startsAt:slots[1].startsAt});
  result=await summary();assert.equal(result.appointments[0].startsAt,slots[1].startsAt);
  await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST',{version:1});assert.deepEqual((await summary()).appointments,[]);
  console.log('OK: resumo real, isolamento por empresa, contato privado versionado, remarcação e exclusão de cancelados; nenhum WhatsApp enviado.');
 }
 }finally{if(phase!=='before'||!receipt)await cleanup();}
}

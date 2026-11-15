import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import fs from 'node:fs';
const phase=['before','after'].includes(process.argv[2])?process.argv[2]:'smoke',base=process.argv[phase==='smoke'?2:3]??'http://127.0.0.1:8080',file=process.argv[4];
class Client{
 cookies=[];
 async request(path,method='GET',body,form=false){
  const headers={};if(this.businessId&&path.startsWith('/api/admin/reports'))headers['X-Agenda-Business']=this.businessId;if(method!=='GET'){const csrf=await this.request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
  headers.Cookie=this.cookies.filter(c=>path===c.path||path.startsWith(c.path.endsWith('/')?c.path:c.path+'/')).map(c=>c.name+'='+c.value).join('; ');
  if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
  const r=await fetch(base+path,{method,headers,redirect:'manual',signal:AbortSignal.timeout(45000),body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
  for(const item of r.headers.getSetCookie()){const pair=item.split(';')[0],split=pair.indexOf('='),name=pair.slice(0,split),value=pair.slice(split+1),path=/;\s*Path=([^;]+)/i.exec(item)?.[1]??'/';this.cookies=this.cookies.filter(c=>!(c.name===name&&c.path===path));if(value)this.cookies.push({name,value,path});}
  const text=await r.text();return{status:r.status,body:text?(r.headers.get('content-type')?.startsWith('text/csv')?text:JSON.parse(text)):null,headers:r.headers};
 }
 async ok(path,method='GET',body,status=200,form=false){const r=await this.request(path,method,body,form);assert.equal(r.status,status,method+' '+path);return r.body;}
}
const owner=new Client(),anonymous=new Client();await owner.ok('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
owner.businessId=(await owner.ok('/api/auth/me')).business.id;
let person,appointment,date,service;
const query=()=>new URLSearchParams({from:date,to:date,professionalId:person.id,serviceId:service.id}).toString();
const report=()=>owner.ok('/api/admin/reports?'+query());
async function cleanup(){if(appointment)await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST');if(person){const current=(await owner.ok('/api/admin/professionals')).find(p=>p.id===person.id);await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...current,active:false,serviceIds:[]});}await owner.ok('/api/auth/logout','POST',undefined,204);}
if(phase==='after'){
 const proof=JSON.parse(fs.readFileSync(file,'utf8'));({person,appointment,date,service}=proof);const result=await report();assert.deepEqual(result.totals,proof.totals);assert.deepEqual(result.rows,proof.rows);assert.deepEqual(result.days,proof.days);await cleanup();console.log('OK: indicadores, filtros, valores e dias preservados após atualização/restauração.');
}else{
 try{
 const catalog=await owner.ok('/api/admin/catalog');service=catalog.services[0];date=catalog.suggestedDate;
 person=await owner.ok('/api/admin/professionals','POST',{name:'Relatório fictício '+randomUUID(),specialty:'Teste isolado de relatório'},201);const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
 await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
 const slots=(await anonymous.ok('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;
 const receipt=await anonymous.ok('/api/public/barbearia-horizonte/bookings','POST',{requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente privado do relatório',customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version},201);
 appointment=(await owner.ok('/api/admin/agenda?date='+date)).appointments.find(a=>a.reference===receipt.reference);
 const result=await report();assert.equal(result.totals.total,1);assert.equal(result.totals.completedValueCents,0);assert.equal(result.days.length,1);assert.equal(result.rows[0].professionalId,person.id);
 for(const path of ['/api/admin/reports?','/api/admin/reports/export?'])await anonymous.ok(path+query(),'GET',undefined,401);
 const other=(await anonymous.ok('/api/public/estudio-aurora/catalog')).professionals[0];await owner.ok('/api/admin/reports?'+new URLSearchParams({from:date,to:date,professionalId:other.id}),'GET',undefined,404);
 await owner.ok('/api/admin/reports?from=2020-01-01&to=2021-01-01','GET',undefined,400);
 if(phase==='smoke'){
  const currentBusiness=owner.businessId;owner.businessId=randomUUID();
  try{for(const path of ['/api/admin/reports/options','/api/admin/reports?'+query(),'/api/admin/reports/export?'+query()])await owner.ok(path,'GET',undefined,412);}
  finally{owner.businessId=currentBusiness;}
 }
 const csv=await owner.request('/api/admin/reports/export?'+query());assert.equal(csv.status,200);assert.ok(csv.headers.get('cache-control').includes('no-store'));assert.ok(csv.headers.get('content-disposition').startsWith('attachment;'));assert.ok(csv.body.includes('PROFISSIONAL_SERVICO'));assert.ok(!csv.body.includes('Cliente privado'));assert.ok(!csv.body.includes('11999991234'));
 if(phase==='before'){fs.writeFileSync(file,JSON.stringify({person,appointment,date,service,totals:result.totals,rows:result.rows,days:result.days}),{flag:'wx',mode:0o600});await owner.ok('/api/auth/logout','POST',undefined,204);console.log('OK: prova privada de indicadores preparada para restauração.');}
 else{await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST',{version:0});assert.equal((await report()).totals.cancelled,1);console.log('OK: relatório real, filtros combinados, isolamento, CSV privado e atualização dos indicadores.');}
 }finally{if(phase!=='before'||!appointment)await cleanup();}
}
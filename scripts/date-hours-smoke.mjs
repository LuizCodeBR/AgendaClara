import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.argv[2]??'http://127.0.0.1:8080';
const cookies=new Map();
async function request(path,method='GET',body,form=false) {
  const headers={};
  if(method!=='GET') { const csrf=await request('/api/auth/csrf'); assert.equal(csrf.status,200); headers[csrf.body.headerName]=csrf.body.token; }
  headers.Cookie=[...cookies].map(([key,value])=>key+'='+value).join('; ');
  if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
  const response=await fetch(base+path,{method,headers,redirect:'manual',body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
  for(const cookie of response.headers.getSetCookie()) { const [key,...value]=cookie.split(';')[0].split('='); if(value.join('='))cookies.set(key,value.join('=')); else cookies.delete(key); }
  const text=await response.text();
  return {status:response.status,body:text?JSON.parse(text):null};
}
async function expect(path,method,body,status,form=false) {
  const result=await request(path,method,body,form);
  assert.equal(result.status,status,method+' '+path+': '+JSON.stringify(result.body));
  return result.body;
}
assert.equal((await expect('/api/public/config','GET',undefined,200)).demo,true,'Use somente o ambiente de demonstração.');
await expect('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
const catalog=await expect('/api/admin/catalog','GET',undefined,200);
const created=await expect('/api/admin/professionals','POST',{name:'Verificação de folgas '+Date.now(),specialty:'Dados fictícios'},201);
const profile='/api/admin/professionals/'+created.id;
const date=catalog.suggestedDate, weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
const weekly=await expect(profile,'PUT',{...created,hours:[{weekday,opensAt:'09:00',closesAt:'12:00'}]},200);
const endpoint=profile+'/exceptions';
const query=new URLSearchParams({professionalId:created.id,serviceId:catalog.services[0].id,date});
const availability='/api/public/barbearia-horizonte/availability?'+query;
let calendar=await expect(endpoint,'GET',undefined,200);
assert.equal(calendar.version,weekly.version);
calendar=await expect(endpoint+'/'+date,'PUT',{version:calendar.version,reason:'Folga fictícia privada',hours:[]},200);
assert.equal((await expect(availability,'GET',undefined,200)).slots.length,0);
calendar=await expect(endpoint+'/'+date,'PUT',{version:calendar.version,reason:'Horário especial privado',hours:[{opensAt:'19:00',closesAt:'20:00'}]},200);
const free=await expect(availability,'GET',undefined,200);
assert.ok(free.slots.length>0);
assert.ok(!JSON.stringify(free).includes('Horário especial privado'));
const receipt=await expect('/api/public/barbearia-horizonte/bookings','POST',{
  requestKey:randomUUID(),professionalId:created.id,serviceId:catalog.services[0].id,startsAt:free.slots[0].startsAt,
  customerName:'Verificação de exceções',customerPhone:'11999990000',
},201);
await expect(endpoint+'/'+date,'PUT',{version:calendar.version,reason:'Não deve fechar',hours:[]},409);
await expect(endpoint+'/'+date+'?version='+calendar.version,'DELETE',undefined,409);
const agenda=await expect('/api/admin/agenda?date='+date,'GET',undefined,200);
const appointment=agenda.appointments.find(item=>item.reference===receipt.reference);
assert.ok(appointment);
await expect('/api/admin/appointments/'+appointment.id+'/cancel','POST',undefined,200);
calendar=await expect(endpoint+'/'+date+'?version='+calendar.version,'DELETE',undefined,200);
assert.equal(calendar.exceptions.length,0);
assert.ok((await expect(availability,'GET',undefined,200)).slots.length>0);
await expect(profile,'PUT',{...weekly,version:calendar.version,active:false},200);
await expect('/api/auth/logout','POST',undefined,204);
console.log('OK: folga, horário especial, reserva, proteção contra fechamento/remoção, cancelamento, retorno à semana e inativação do profissional fictício.');

import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.argv[2]??'http://127.0.0.1:8080';
function client() {
  const cookies=new Map();
  async function request(path,method='GET',body,form=false) {
    const headers={};
    if(method!=='GET') { const csrf=await request('/api/auth/csrf'); assert.equal(csrf.status,200); headers[csrf.body.headerName]=csrf.body.token; }
    headers.Cookie=[...cookies].map(([key,value])=>key+'='+value).join('; ');
    if(body) headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
    const response=await fetch(base+path,{method,headers,redirect:'manual',body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
    for(const cookie of response.headers.getSetCookie()) {
      const [key,...value]=cookie.split(';')[0].split('=');
      if(value.join('=')) {
        if(key==='JSESSIONID') {
          assert.match(cookie,/HttpOnly/i); assert.match(cookie,/SameSite=Lax/i);
          if(base.startsWith('https://'))assert.match(cookie,/; Secure/i);
        }
        cookies.set(key,value.join('='));
      } else cookies.delete(key);
    }
    const text=await response.text(); return {status:response.status,body:text?JSON.parse(text):null};
  }
  async function expect(path,method,body,status,form=false) {
    const result=await request(path,method,body,form);
    assert.equal(result.status,status,method+' '+path+': '+JSON.stringify(result.body)); return result.body;
  }
  return {expect,cookies};
}
const first=client(), second=client();
const config=await first.expect('/api/public/config','GET',undefined,200);
assert.equal(config.demo,true,'Este roteiro cria somente dados fictícios em uma demonstração.');
assert.equal(config.registrationEnabled,true); assert.equal(config.mailMode,"demo");
const password='frase exclusiva de teste 2030';
function data(suffix) { return {requestKey:randomUUID(),businessName:'Empresa fictícia '+suffix,category:'Teste',address:'Atendimento online fictício',
  zoneId:'America/Sao_Paulo',ownerName:'Responsável Teste',email:randomUUID()+'@example.test',password,demoAcknowledged:true}; }
const a=data('A'), b=data('B');
const receipt=await first.expect('/api/auth/register','POST',a,201);
const retry=await first.expect('/api/auth/register','POST',a,200); assert.deepEqual(retry,receipt);
const other=await second.expect('/api/auth/register','POST',b,201);
await first.expect('/api/public/'+receipt.slug+'/catalog','GET',undefined,404);
const before=first.cookies.get('JSESSIONID');
await first.expect('/api/auth/login','POST',{username:a.email,password},204,true);
assert.notEqual(first.cookies.get('JSESSIONID'),before,'Login deve trocar o identificador da sessão.');
assert.equal((await first.expect('/api/auth/me','GET',undefined,200)).business.id,receipt.businessId);
let setup=await first.expect('/api/admin/business','GET',undefined,200);
assert.equal(setup.published,false); assert.equal(setup.ready,false);
await first.expect('/api/admin/business/publication','PUT',{published:true,version:setup.version},400);
const person=await first.expect('/api/admin/professionals','POST',{name:'Profissional fictício próprio',specialty:'Teste'},201);
const catalog=await first.expect('/api/admin/catalog','GET',undefined,200);
assert.equal(catalog.services.length,0);
const date=catalog.suggestedDate, weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
const service=await first.expect('/api/admin/services','POST',{name:'Serviço fictício próprio',description:'Apenas teste',durationMinutes:30,priceCents:1000},201);
await first.expect('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]},200);
const message=(await first.expect('/api/auth/demo-mailbox','GET',undefined,200)).find(item=>item.purpose==='VERIFY');
assert.ok(message);
await first.expect('/api/auth/email/confirm','POST',{token:new URL(message.actionUrl).hash.split('token=')[1]},200);
setup=await first.expect('/api/admin/business/publication','PUT',{published:true,version:setup.version},200);
assert.equal(setup.published,true); assert.equal(setup.ready,true);
const query=new URLSearchParams({professionalId:person.id,serviceId:service.id,date});
const slots=await first.expect('/api/public/'+receipt.slug+'/availability?'+query,'GET',undefined,200);
assert.ok(slots.slots.length);
const booking=await first.expect('/api/public/'+receipt.slug+'/bookings','POST',{requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots.slots[0].startsAt,customerName:'Cliente fictício próprio',customerPhone:'11999990000'},201);
const agenda=await first.expect('/api/admin/agenda?date='+date,'GET',undefined,200);
const appointment=agenda.appointments.find(item=>item.reference===booking.reference); assert.ok(appointment);
await second.expect('/api/auth/login','POST',{username:b.email,password},204,true);
assert.equal((await second.expect('/api/admin/business?businessId='+receipt.businessId,'GET',undefined,200)).business.id,other.businessId);
assert.equal((await second.expect('/api/admin/agenda?date='+date,'GET',undefined,200)).appointments.length,0);
await second.expect('/api/admin/appointments/'+appointment.id+'/cancel','POST',undefined,404);
await second.expect('/api/admin/professionals/'+person.id+'/exceptions','GET',undefined,404);
await first.expect('/api/admin/business/publication','PUT',{published:false,version:setup.version-1},409);
setup=await first.expect('/api/admin/business/publication','PUT',{published:false,version:setup.version},200);
await first.expect('/api/public/'+receipt.slug+'/catalog','GET',undefined,404);
assert.equal((await first.expect('/api/admin/agenda?date='+date,'GET',undefined,200)).appointments[0].status,'BOOKED');
await first.expect('/api/admin/appointments/'+appointment.id+'/cancel','POST',undefined,200);
await first.expect('/api/auth/logout','POST',undefined,204); await first.expect('/api/admin/business','GET',undefined,401);
await second.expect('/api/auth/logout','POST',undefined,204);
console.log('OK: cadastro próprio, repetição segura, sessão renovada, configuração, publicação, reserva, isolamento entre duas empresas, ocultação e saída.');

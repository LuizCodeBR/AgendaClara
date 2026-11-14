import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base=process.argv[2]??'http://127.0.0.1:8080',cookies=new Map();
async function request(path,method='GET',body,form=false) {
  const headers={};if(method!=='GET'){const csrf=await request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
  headers.Cookie=[...cookies].map(([k,v])=>k+'='+v).join('; ');
  if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
  const response=await fetch(base+path,{method,headers,redirect:'manual',signal:AbortSignal.timeout(30000),body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
  for(const cookie of response.headers.getSetCookie()){const [k,...parts]=cookie.split(';')[0].split('=');const v=parts.join('=');if(v)cookies.set(k,v);else cookies.delete(k);}
  const text=await response.text();return {status:response.status,body:text?JSON.parse(text):null};
}
async function expect(path,method,body,status,form=false){const r=await request(path,method,body,form);assert.equal(r.status,status,method+' '+path);return r.body;}
const config=await expect('/api/public/config','GET',undefined,200);assert.equal(config.demo,true);
await expect('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
let person,bookingId,profile;
try{
  const catalog=await expect('/api/admin/catalog','GET',undefined,200),selected=catalog.services[0],other=catalog.services[1];
  assert.ok(selected&&other);
  person=await expect('/api/admin/professionals','POST',{name:'Serviços fictícios '+randomUUID(),specialty:'Teste isolado de vinculação'},201);
  profile='/api/admin/professionals/'+person.id;assert.deepEqual(person.serviceIds,[]);
  const date=catalog.suggestedDate,weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
  const configured=await expect(profile,'PUT',{...person,hours:[{weekday,opensAt:'09:00',closesAt:'12:00'}],serviceIds:[selected.id]},200);
  const after=await expect('/api/public/barbearia-horizonte/catalog','GET',undefined,200);
  assert.deepEqual(after.professionals.find(p=>p.id===person.id).serviceIds,[selected.id]);
  const query=id=>'/api/public/barbearia-horizonte/availability?'+new URLSearchParams({professionalId:person.id,serviceId:id,date});
  await expect(query(other.id),'GET',undefined,409);
  const available=await expect(query(selected.id),'GET',undefined,200);assert.ok(available.slots.length);
  const payload={requestKey:randomUUID(),professionalId:person.id,serviceId:other.id,startsAt:available.slots[0].startsAt,customerName:'Cliente fictício de vinculação',customerPhone:'11999991234'};
  await expect('/api/public/barbearia-horizonte/bookings','POST',payload,409);
  payload.serviceId=selected.id;payload.requestKey=randomUUID();
  const receipt=await expect('/api/public/barbearia-horizonte/bookings','POST',payload,201);
  const agenda=await expect('/api/admin/agenda?date='+date,'GET',undefined,200);
  bookingId=agenda.appointments.find(a=>a.reference===receipt.reference)?.id;assert.ok(bookingId);
  await expect(profile,'PUT',{...configured,serviceIds:[]},409);
  const stable=(await expect('/api/admin/professionals','GET',undefined,200)).find(p=>p.id===person.id);
  assert.deepEqual(stable.serviceIds,[selected.id]);assert.equal(stable.version,configured.version);
  await expect('/api/admin/appointments/'+bookingId+'/cancel','POST',undefined,200);
  const unlinked=await expect(profile,'PUT',{...stable,serviceIds:[]},200);
  await expect(profile,'PUT',{...stable,serviceIds:[selected.id]},409);
  assert.deepEqual(unlinked.serviceIds,[]);
  console.log('OK: vínculos explícitos, catálogo filtrável, reserva direta inválida recusada, atendimento protegido, cancelamento, remoção e edição antiga recusada.');
}finally{
  if(bookingId)await expect('/api/admin/appointments/'+bookingId+'/cancel','POST',undefined,200);
  if(person){const current=(await expect('/api/admin/professionals','GET',undefined,200)).find(p=>p.id===person.id);if(current)await expect(profile,'PUT',{...current,active:false,serviceIds:[]},200);}
  await expect('/api/auth/logout','POST',undefined,204);
}

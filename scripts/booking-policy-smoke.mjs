import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import fs from 'node:fs';
const phase=['before','after'].includes(process.argv[2])?process.argv[2]:'smoke';
const base=process.argv[phase==='smoke'?2:3]??'http://127.0.0.1:8080';
const file=process.argv[4];
class Client {
  cookies=[];
  async request(path,method='GET',body,form=false) {
    const headers={};
    if(method!=='GET'){const csrf=await this.request('/api/auth/csrf');headers[csrf.body.headerName]=csrf.body.token;}
    headers.Cookie=this.cookies.filter(c=>path===c.path||path.startsWith(c.path.endsWith('/')?c.path:c.path+'/')).map(c=>c.name+'='+c.value).join('; ');
    if(body)headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
    const response=await fetch(base+path,{method,headers,redirect:'manual',signal:AbortSignal.timeout(45000),body:body?(form?new URLSearchParams(body).toString():JSON.stringify(body)):undefined});
    const set=response.headers.getSetCookie();
    for(const item of set){const pair=item.split(';')[0],split=pair.indexOf('='),name=pair.slice(0,split),value=pair.slice(split+1),pathValue=/;\s*Path=([^;]+)/i.exec(item)?.[1]??'/';this.cookies=this.cookies.filter(c=>!(c.name===name&&c.path===pathValue));if(value)this.cookies.push({name,value,path:pathValue});}
    const text=await response.text();return{status:response.status,body:text?JSON.parse(text):null,headers:response.headers,set};
  }
  async ok(path,method='GET',body,status=200,form=false){const r=await this.request(path,method,body,form);assert.equal(r.status,status,method+' '+path);return r;}
}

const owner=new Client(),client=new Client(),policyPath='/api/admin/business/booking-policy';
let proof,appointment,receipt,publicBooking,payload;
async function hide(){
  const state=(await owner.ok('/api/admin/business')).body;
  if(state.published)await owner.ok('/api/admin/business/publication','PUT',{published:false,version:state.version});
}
async function cleanup(){
  if(appointment)await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST');
  if(proof)await hide();
  await owner.ok('/api/auth/logout','POST',undefined,204);
}
if(phase==='after'){
  assert.ok(file);proof=JSON.parse(fs.readFileSync(file,'utf8'));
  await owner.ok('/api/auth/login','POST',{username:proof.email,password:proof.password},204,true);
  assert.equal((await owner.ok('/api/auth/me')).body.business.id,proof.businessId);
  assert.deepEqual((await owner.ok(policyPath)).body,proof.currentPolicy);
  appointment=proof.appointment;receipt=proof.receipt;client.cookies=proof.cookies;
  const path='/api/customer/reservations/'+appointment.id;
  const view=(await client.ok(path)).body;assert.deepEqual(view.bookingPolicy,receipt.bookingPolicy);assert.equal(view.canCancel,false);
  const opened=(await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken})).body;
  assert.deepEqual(opened.bookingPolicy,receipt.bookingPolicy);
  await cleanup();
  console.log('OK: regras atuais, condições originais da reserva, sessão e mesmo link preservados após atualização ou restauração.');
}else{
  const config=(await owner.ok('/api/public/config')).body;assert.equal(config.demo,true);assert.equal(config.mailMode,'demo');
  const email=randomUUID()+'@example.test',password='frase ficticia exclusiva de teste de regras 2030';
  const registration=(await owner.ok('/api/auth/register','POST',{requestKey:randomUUID(),businessName:'Empresa fictícia de regras '+randomUUID(),category:'Teste',address:'Online fictício',zoneId:'America/Sao_Paulo',ownerName:'Pessoa de teste',email,password,demoAcknowledged:true},201)).body;
  proof={email,password,businessId:registration.businessId,slug:registration.slug};
  try{
    const mail=(await owner.ok('/api/auth/demo-mailbox')).body.find(m=>m.purpose==='VERIFY');assert.ok(mail);
    const token=new URLSearchParams(new URL(mail.actionUrl).hash.split('?')[1]).get('token');
    await owner.ok('/api/auth/email/confirm','POST',{token});
    await owner.ok('/api/auth/login','POST',{username:email,password},204,true);
    const catalog=(await owner.ok('/api/admin/catalog')).body;
    const service=(await owner.ok('/api/admin/services','POST',{name:'Consulta fictícia',description:'Apenas teste de regras',durationMinutes:30,priceCents:2500},201)).body;
    const person=(await owner.ok('/api/admin/professionals','POST',{name:'Profissional fictício',specialty:'Teste'},201)).body;
    const date=catalog.suggestedDate,weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
    await owner.ok('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
    let setup=(await owner.ok('/api/admin/business')).body;
    await owner.ok('/api/admin/business/publication','PUT',{published:true,version:setup.version});
    const rules=(await owner.ok(policyPath,'PUT',{bookingNoticeMinutes:15,cancellationNoticeMinutes:10080,rescheduleNoticeMinutes:0,version:0})).body;
    assert.equal(rules.version,1);
    publicBooking='/api/public/'+proof.slug+'/bookings';
    const published=(await client.ok('/api/public/'+proof.slug+'/catalog')).body;assert.deepEqual(published.bookingPolicy,rules);
    const slots=(await client.ok('/api/public/'+proof.slug+'/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).body.slots;assert.ok(slots.length>1);
    payload={requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente fictício das regras',customerPhone:'11999991234',policyVersion:0};
    await client.ok(publicBooking,'POST',payload,409);payload.policyVersion=1;
    receipt=(await client.ok(publicBooking,'POST',payload,201)).body;assert.deepEqual(receipt.bookingPolicy,rules);
    appointment=(await owner.ok('/api/admin/agenda?date='+date)).body.appointments.find(a=>a.reference===receipt.reference);assert.ok(appointment);
    const path='/api/customer/reservations/'+appointment.id;
    let view=(await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken})).body;
    assert.equal(view.canCancel,false);assert.equal(view.canReschedule,true);
    await client.ok(path+'/cancel','POST',{version:view.version},409);
    const currentPolicy=(await owner.ok(policyPath,'PUT',{bookingNoticeMinutes:0,cancellationNoticeMinutes:0,rescheduleNoticeMinutes:0,version:1})).body;
    assert.equal(currentPolicy.version,2);
    assert.deepEqual((await client.ok(publicBooking,'POST',payload,200)).body,receipt);
    view=(await client.ok(path)).body;assert.equal(view.canCancel,false);assert.deepEqual(view.bookingPolicy,rules);
    await owner.ok(policyPath,'PUT',{...rules,version:1},409);
    if(phase==='before'){
      assert.ok(file);await hide();
      fs.writeFileSync(file,JSON.stringify({...proof,appointment,receipt,currentPolicy,cookies:client.cookies}),{flag:'wx',mode:0o600});
      console.log('OK: empresa fictícia com regras atuais e reserva com condições originais preparada para verificar a persistência.');
    }else{
      const available=(await client.ok(path+'/availability?date='+date)).body.slots;
      const next=available.find(s=>s.startsAt!==view.startsAt);assert.ok(next);
      const moved=(await client.ok(path+'/reschedule','PATCH',{startsAt:next.startsAt,version:view.version})).body;
      assert.deepEqual(moved.bookingPolicy,rules);assert.equal(moved.priceCents,receipt.priceCents);
      assert.equal(Date.parse(moved.cancelUntil),Date.parse(next.startsAt)-rules.cancellationNoticeMinutes*60000);
      const canceled=(await owner.ok('/api/admin/appointments/'+appointment.id+'/cancel','POST',{version:moved.version})).body;assert.equal(canceled.status,'CANCELLED');
      console.log('OK: regras por empresa, revisão antiga recusada, condições preservadas, prazos distintos, remarcação e exceção pelo responsável.');
    }
  }finally{if(phase!=='before'||!receipt)await cleanup();else await owner.ok('/api/auth/logout','POST',undefined,204);}
}

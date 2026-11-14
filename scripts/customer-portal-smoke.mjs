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
const owner=new Client(),client=new Client();
await owner.ok('/api/auth/login','POST',{username:'marina@demo.agendaclara.dev',password:'Demo@12345'},204,true);
let person,bookingId,receipt,profile,date;
async function cleanup(){
  if(bookingId)await owner.ok('/api/admin/appointments/'+bookingId+'/cancel','POST');
  if(person){const current=(await owner.ok('/api/admin/professionals')).body.find(p=>p.id===person.id);if(current)await owner.ok(profile,'PUT',{...current,active:false,serviceIds:[]});}
  await owner.ok('/api/auth/logout','POST',undefined,204);
}
if(phase==='after'){
  assert.ok(file);const proof=JSON.parse(fs.readFileSync(file,'utf8'));({person,bookingId,receipt,profile,date}=proof);
  const path='/api/customer/reservations/'+bookingId;
  client.cookies=proof.cookies;
  const read=(await client.ok(path)).body;
  assert.equal(read.reference,receipt.reference);assert.equal(read.version,proof.version);
  const reopened=(await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken})).body;
  assert.equal(reopened.reference,receipt.reference);
  await cleanup();
  console.log('OK: reserva, sessão privada e mesmo link preservados após atualização ou restauração, sem expor credenciais.');
}else{
  try{
    const catalog=(await owner.ok('/api/admin/catalog')).body,service=catalog.services[0];date=catalog.suggestedDate;
    person=(await owner.ok('/api/admin/professionals','POST',{name:'Acesso fictício '+randomUUID(),specialty:'Teste isolado da área do cliente'},201)).body;
    profile='/api/admin/professionals/'+person.id;
    const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
    await owner.ok(profile,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'12:00'}]});
    const slots=(await owner.ok('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({professionalId:person.id,serviceId:service.id,date}))).body.slots;
    assert.ok(slots.length>1);
    const payload={requestKey:randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente fictício de acesso privado',customerPhone:'11999991234'};
    receipt=(await owner.ok('/api/public/barbearia-horizonte/bookings','POST',payload,201)).body;
    assert.match(receipt.customerAccessToken,/^[A-Za-z0-9_-]{43}$/);
    bookingId=(await owner.ok('/api/admin/agenda?date='+date)).body.appointments.find(a=>a.reference===receipt.reference).id;
    const path='/api/customer/reservations/'+bookingId;
    await client.ok(path,'GET',undefined,401);
    const opened=await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken});
    const cookie=opened.set.find(c=>c.startsWith('AGENDA_CUSTOMER_ACCESS='));
    assert.ok(cookie);assert.match(cookie,/HttpOnly/i);assert.match(cookie,/SameSite=Strict/i);assert.ok(cookie.includes('Path='+path));
    if(base.startsWith('https:'))assert.match(cookie,/;\s*Secure/i);
    assert.match(opened.headers.get('cache-control'),/no-store/);
    let view=opened.body;assert.equal(view.customerPhone,undefined);assert.equal(view.customerAccessToken,undefined);
    assert.equal((await client.ok(path)).body.reference,receipt.reference);
    if(phase==='before'){
      assert.ok(file);fs.writeFileSync(file,JSON.stringify({person,bookingId,receipt,profile,date,cookies:client.cookies,version:view.version}),{flag:'wx',mode:0o600});
      console.log('OK: reserva fictícia e prova privada preparadas para verificar persistência e restauração.');
    }else{
      await client.ok('/api/customer/reservations/'+randomUUID(),'GET',undefined,401);
      await client.ok('/api/admin/catalog','GET',undefined,401);
      const available=(await client.ok(path+'/availability?date='+date)).body.slots;
      const next=available.find(s=>s.startsAt!==view.startsAt);assert.ok(next);
      const changed=(await client.ok(path+'/reschedule','PATCH',{version:view.version,startsAt:next.startsAt})).body;
      assert.equal(changed.priceCents,view.priceCents);assert.equal(new Date(changed.endsAt)-new Date(changed.startsAt),new Date(view.endsAt)-new Date(view.startsAt));
      await client.ok(path+'/reschedule','PATCH',{version:view.version,startsAt:next.startsAt});
      await client.ok(path+'/cancel','POST',{version:view.version},409);
      const accessPath='/api/admin/appointments/'+bookingId+'/customer-access';
      const state=(await owner.ok(accessPath)).body;
      const issue={version:state.version,requestKey:randomUUID()};
      const fresh=(await owner.ok(accessPath,'POST',issue)).body;
      assert.equal((await owner.ok(accessPath,'POST',issue)).body.token,fresh.token);
      assert.notEqual(fresh.token,receipt.customerAccessToken);
      await client.ok(path,'GET',undefined,401);
      await client.ok('/api/customer/session','POST',{token:receipt.customerAccessToken},401);
      assert.equal((await owner.ok('/api/public/barbearia-horizonte/bookings','POST',payload,200)).body.customerAccessToken,null);
      view=(await client.ok('/api/customer/session','POST',{token:fresh.token})).body;
      const canceled=(await client.ok(path+'/cancel','POST',{version:view.version})).body;
      assert.equal(canceled.status,'CANCELLED');
      await client.ok(path+'/cancel','POST',{version:view.version});
      await owner.ok(accessPath,'DELETE',{version:canceled.version});
      await client.ok(path,'GET',undefined,401);
      await client.ok('/api/customer/session','POST',{token:fresh.token},401);
      console.log('OK: acesso privado, cookie por reserva, sigilo, isolamento, remarcação, versão antiga, repetição segura, cancelamento, rotação e revogação.');
    }
  }finally{if(phase!=='before'||!receipt)await cleanup();else await owner.ok('/api/auth/logout','POST',undefined,204);}
}

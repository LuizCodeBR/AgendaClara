import {test,expect} from '@playwright/test';
test('responsável convida, profissional ativa e consulta somente sua agenda; revogação encerra acesso',async({page,browser,baseURL},info)=>{
 test.setTimeout(90000);page.setDefaultTimeout(10000);
 async function req(path:string,method='GET',body?:unknown){const headers:Record<string,string>={};if(method!=='GET'){const c=await(await page.request.get('/api/auth/csrf')).json();headers[c.headerName]=c.token;}if(path.startsWith('/api/admin/professional-access'))headers['X-Agenda-Account']=owner.id;const r=await page.request.fetch(path,{method,headers,...(body?{data:body}:{})});expect(r.ok(),path).toBe(true);return r.status()===204?null:r.json();}
 let owner:{id:string};
 await page.goto('/#/painel');await page.getByRole('button',{name:'Barbearia Horizonte Marina · responsável'}).click();await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();await expect(page.getByRole('button',{name:'Sair do painel'})).toBeVisible();
 owner=await(await page.request.get('/api/auth/me')).json();
 const catalog=await req('/api/admin/catalog'),date=catalog.suggestedDate,service=catalog.services[0],name='Profissional fictício '+info.project.name+' '+Date.now();
 let person=await req('/api/admin/professionals','POST',{name,specialty:'Ensaio de acesso'}),appointment;
 const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
 person=await req('/api/admin/professionals/'+person.id,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
 let reactivated:Awaited<ReturnType<typeof browser.newContext>>|undefined;
 const context=await browser.newContext({...info.project.use,baseURL} as Parameters<typeof browser.newContext>[0]);const professional=await context.newPage();
 try{
  const slots=(await req('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;
  const receipt=await req('/api/public/barbearia-horizonte/bookings','POST',{requestKey:crypto.randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente próprio fictício',customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version});
  appointment=(await req('/api/admin/agenda?date='+date)).appointments.find((a:{reference:string})=>a.reference===receipt.reference);
  await page.getByRole('button',{name:'Acessos da equipe',exact:true}).click();await page.getByRole('button',{name:name+' Sem acesso ativo',exact:true}).click();
  const email='professional-'+crypto.randomUUID()+'@example.test',password='Senha profissional fictícia 123!';
  await page.getByLabel('E-mail do profissional',{exact:true}).fill(email);await page.getByRole('button',{name:'Gerar convite',exact:true}).click();
  const link=await page.getByLabel('Link privado do convite').inputValue();
  await professional.goto(link);await expect(professional.getByRole('heading',{name:name+' · Barbearia Horizonte'})).toBeVisible();expect(professional.url()).not.toContain('token=');
  await professional.getByLabel('Nova senha',{exact:true}).fill(password);await professional.getByLabel('Repita a nova senha').fill(password);await professional.getByRole('button',{name:'Ativar meu acesso'}).click();await professional.getByRole('button',{name:'Entrar na minha agenda'}).click();
  await professional.getByLabel('Senha',{exact:true}).fill(password);await professional.getByRole('button',{name:'Entrar no painel',exact:true}).click();
  await expect(professional.getByRole('heading',{name:'Minha agenda',exact:true})).toBeVisible();await professional.getByLabel('Data da minha agenda').fill(date);await expect(professional.getByRole('heading',{name:'Cliente próprio fictício'})).toBeVisible();
  await expect(professional.getByRole('button',{name:'Indicadores e relatórios',exact:true})).toHaveCount(0);await expect(professional.getByRole('button',{name:'Registrar resultado',exact:true})).toHaveCount(0);
  const me=await(await professional.request.get('/api/auth/me')).json();
  const response=await professional.request.get('/api/professional/agenda?date='+date,{headers:{'X-Agenda-Account':me.id}});
  const text=await response.text();expect(response.ok()).toBe(true);for(const field of ['customerPhone','priceCents','reference','customerAccessToken'])expect(text).not.toContain(field);
  expect((await professional.request.get('/api/admin/reports/options')).status()).toBe(403);
  await professional.getByRole('button',{name:'Histórico',exact:true}).click();await expect(professional.getByRole('dialog',{name:'Histórico do atendimento'})).toBeVisible();await expect(professional.getByText(/Histórico resumido/)).toBeVisible();await professional.getByRole('button',{name:'Fechar',exact:true}).click();
  expect(await professional.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await professional.locator('.staff-agenda').screenshot({path:info.outputPath('minha-agenda.png')});
  async function staffPost(path:string,body:unknown){const c=await(await professional.request.get('/api/auth/csrf')).json();return professional.request.post(path,{headers:{[c.headerName]:c.token},data:body});}
  expect((await staffPost('/api/auth/password/request',{email})).status()).toBe(202);
  const mailbox=await(await professional.request.get('/api/auth/demo-mailbox')).json();
  const reset=mailbox.find((m:{purpose:string})=>m.purpose==='RESET');expect(reset).toBeTruthy();
  const resetToken=new URLSearchParams(reset.actionUrl.split('?')[1]).get('token');
  const oldProof=(await context.cookies()).find(c=>c.name==='AGENDA_DEMO_MAILBOX')?.value;expect(oldProof).toBeTruthy();
  await page.getByRole('button',{name:'Atualizar acessos'}).click();await expect(page.getByRole('button',{name:name+' Acesso ativo',exact:true})).toBeVisible();await page.getByRole('button',{name:'Revogar acesso e convites'}).click();await page.getByRole('button',{name:'Confirmar revogação'}).click();await page.getByText(/Acesso e convites revogados/).waitFor();
  await professional.getByRole('button',{name:'Atualizar agenda'}).click();await expect(professional.getByRole('button',{name:'Entrar no painel',exact:true})).toBeVisible();await expect(professional.getByRole('heading',{name:'Cliente próprio fictício'})).toHaveCount(0);
  expect(await(await professional.request.get('/api/auth/demo-mailbox')).json()).toEqual([]);
  expect((await staffPost('/api/auth/password/reset',{token:resetToken,password:password+'2'})).status()).toBe(400);
  await page.getByRole('button',{name:'Gerar convite',exact:true}).click();
  const secondLink=await page.getByLabel('Link privado do convite').inputValue();
  reactivated=await browser.newContext({...info.project.use,baseURL} as Parameters<typeof browser.newContext>[0]);
  const next=await reactivated.newPage();await next.goto(secondLink);
  await next.getByLabel('Nova senha',{exact:true}).fill(password+'2');await next.getByLabel('Repita a nova senha').fill(password+'2');
  await next.getByRole('button',{name:'Ativar meu acesso'}).click();await next.getByRole('button',{name:'Entrar na minha agenda'}).click();
  await next.getByLabel('Senha',{exact:true}).fill(password+'2');await next.getByRole('button',{name:'Entrar no painel',exact:true}).click();
  await expect(next.getByRole('heading',{name:'Minha agenda',exact:true})).toBeVisible();
  const fresh=await(await next.request.get('/api/auth/me')).json();expect(fresh.id).toBe(me.id);expect(fresh.role).toBe('PROFESSIONAL');
  expect((await reactivated.cookies()).find(c=>c.name==='AGENDA_DEMO_MAILBOX')?.value).not.toBe(oldProof);
  expect(await(await professional.request.get('/api/auth/demo-mailbox')).json()).toEqual([]);
  await professional.getByLabel('E-mail',{exact:true}).fill(email);await professional.getByLabel('Senha',{exact:true}).fill(password);
  await professional.getByRole('button',{name:'Entrar no painel',exact:true}).click();await expect(professional.getByText('E-mail ou senha inválidos.')).toBeVisible();
 }finally{
  await reactivated?.close();await context.close();if(appointment)await req('/api/admin/appointments/'+appointment.id+'/cancel','POST');
  const state=(await req('/api/admin/professional-access')).find((p:{professionalId:string})=>p.professionalId===person.id);if(state)await req('/api/admin/professional-access/'+person.id+'/revoke','POST',{version:state.version});
  const current=(await req('/api/admin/professionals')).find((p:{id:string})=>p.id===person.id);await req('/api/admin/professionals/'+person.id,'PUT',{...current,active:false,serviceIds:[]});
 }
});

import {test,expect} from '@playwright/test';
test('responsável prepara resumo real sem enviar mensagem externa',async({page},info)=>{
 async function req(path:string,method='GET',body?:unknown){
  const headers:Record<string,string>={};if(method!=='GET'){const csrf=await(await page.request.get('/api/auth/csrf')).json();headers[csrf.headerName]=csrf.token;}
  const response=await page.request.fetch(path,{method,headers,...(body?{data:body}:{})});expect(response.ok(),path).toBe(true);return response.status()===204?null:response.json();
 }
 await page.goto('/#/painel');await page.getByRole('button',{name:'Barbearia Horizonte Marina · responsável'}).click();
 await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await expect(page.getByRole('button',{name:'Sair do painel'})).toBeVisible();
 const catalog=await req('/api/admin/catalog'),service=catalog.services[0],date=catalog.suggestedDate;
 let person=await req('/api/admin/professionals','POST',{name:'Resumo E2E '+info.project.name+' '+Date.now(),specialty:'Demonstração fictícia'});
 const path='/api/admin/professionals/'+person.id;
 const weekday=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
 person=await req(path,'PUT',{...person,serviceIds:[service.id],hours:[{weekday,opensAt:'09:00',closesAt:'18:00'}]});
 const slots=(await req('/api/public/barbearia-horizonte/availability?'+new URLSearchParams({date,professionalId:person.id,serviceId:service.id}))).slots;
 let appointment;
 try{
 const receipt=await req('/api/public/barbearia-horizonte/bookings','POST',{requestKey:crypto.randomUUID(),professionalId:person.id,serviceId:service.id,startsAt:slots[0].startsAt,customerName:'Cliente fictício E2E '+info.project.name,customerPhone:'11999991234',policyVersion:catalog.bookingPolicy.version});
 appointment=(await req('/api/admin/agenda?date='+date)).appointments.find((a:{reference:string})=>a.reference===receipt.reference);
 await page.getByRole('button',{name:'Resumo diário',exact:true}).click();
 await page.getByRole('combobox',{name:'Profissional do resumo',exact:true}).selectOption(person.id);
 await page.getByLabel('Data do resumo',{exact:true}).fill(date);
 await expect(page.getByLabel('Prévia da mensagem',{exact:true})).toHaveValue(new RegExp('Cliente fictício E2E '+info.project.name));
 await page.getByLabel('Telefone com DDI',{exact:true}).fill('+55 11 00000-0000');
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeDisabled();
 await page.getByRole('button',{name:'Salvar contato',exact:true}).click();
 await expect(page.getByLabel('Telefone com DDI',{exact:true})).toHaveValue('5511000000000');
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeEnabled();
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeChecked();
 const link=page.getByRole('link',{name:'Abrir WhatsApp para conferir',exact:true});
 const url=new URL((await link.getAttribute('href'))!);
 expect(url.hostname).toBe('wa.me');expect(url.pathname).toBe('/5511000000000');
 expect(url.searchParams.get('text')).toBe(await page.getByLabel('Prévia da mensagem',{exact:true}).inputValue());
 expect(url.searchParams.get('text')).not.toContain('11999991234');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.locator('.daily-summary-panel').screenshot({path:info.outputPath('resumo-diario.png')});
 // Do not follow the external link or send any message.
 // A different window changes the destination after this preview was loaded.
 const currentSummary=await req(path+'/daily-summary?date='+date);
 await req(path+'/whatsapp-contact','PUT',{phone:'5511000000001',version:currentSummary.professional.version});
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await expect(page.getByText('A agenda ou o cadastro mudou. Confira a prévia e o destinatário novamente.',{exact:true})).toBeVisible();
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).not.toBeChecked();
 await expect(page.getByRole('link',{name:'Abrir WhatsApp para conferir'})).toHaveCount(0);
 await expect(page.getByLabel('Telefone com DDI',{exact:true})).toHaveValue('5511000000001');
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeChecked();
 expect(new URL((await link.getAttribute('href'))!).pathname).toBe('/5511000000001');
 await req('/api/admin/appointments/'+appointment.id+'/cancel','POST',{version:0});
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();
 await expect(page.getByLabel('Prévia da mensagem',{exact:true})).toHaveValue(/Nenhum atendimento confirmado/);
 await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).not.toBeChecked();
 }finally{
 if(appointment)await req('/api/admin/appointments/'+appointment.id+'/cancel','POST');
 const current=(await req('/api/admin/professionals')).find((p:{id:string})=>p.id===person.id);
 await req(path,'PUT',{...current,active:false,serviceIds:[]});
 }
});

test('contato gravado com resposta perdida exige reconciliação antes de compartilhar',async({page},info)=>{
 async function req(path:string,method='GET',body?:unknown){
  const headers:Record<string,string>={};if(method!=='GET'){const csrf=await(await page.request.get('/api/auth/csrf')).json();headers[csrf.headerName]=csrf.token;}
  const response=await page.request.fetch(path,{method,headers,...(body?{data:body}:{})});expect(response.ok(),path).toBe(true);return response.status()===204?null:response.json();
 }
 await page.goto('/#/painel');await page.getByRole('button',{name:'Barbearia Horizonte Marina · responsável'}).click();await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();await expect(page.getByRole('button',{name:'Sair do painel'})).toBeVisible();
 const person=await req('/api/admin/professionals','POST',{name:'Reconciliação fictícia '+info.project.name+' '+Date.now(),specialty:'Teste isolado'}),path='/api/admin/professionals/'+person.id;
 try{
 await page.getByRole('button',{name:'Resumo diário',exact:true}).click();await page.getByRole('combobox',{name:'Profissional do resumo',exact:true}).selectOption(person.id);await expect(page.getByLabel('Telefone com DDI',{exact:true})).toHaveValue('');
 await page.route('**'+path+'/whatsapp-contact',async route=>{const response=await route.fetch();expect(response.ok()).toBe(true);await route.abort('failed');},{times:1});
 await page.getByLabel('Telefone com DDI',{exact:true}).fill('5511000000000');await page.getByRole('button',{name:'Salvar contato',exact:true}).click();
 await expect(page.getByText('Não foi possível confirmar o contato salvo. Consulte o cadastro atual antes de compartilhar ou tentar outra edição.',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'Descartar edição do contato',exact:true})).toBeDisabled();await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeDisabled();
 await page.getByRole('button',{name:'Atualizar e descartar edição do contato',exact:true}).click();await expect(page.getByLabel('Telefone com DDI',{exact:true})).toHaveValue('5511000000000');
 await page.getByRole('checkbox',{name:/Conferi a prévia/}).click();await expect(page.getByRole('checkbox',{name:/Conferi a prévia/})).toBeChecked();
 const link=page.getByRole('link',{name:'Abrir WhatsApp para conferir'});expect(new URL((await link.getAttribute('href'))!).pathname).toBe('/5511000000000');
 // No external navigation: check the prepared URL only.
 }finally{const current=(await req('/api/admin/professionals')).find((p:{id:string})=>p.id===person.id);await req(path,'PUT',{...current,active:false,serviceIds:[]});}
});

import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const require=createRequire(new URL('../frontend/package.json',import.meta.url));
const {chromium,devices,expect}=require('@playwright/test');
const root=fileURLToPath(new URL('../',import.meta.url));
const base=new URL(process.argv[2]??'http://127.0.0.1:8092');
assert.equal(process.env.AGENDA_PORTFOLIO_CAPTURE,'DADOS_FICTICIOS',
 'Confirme a base local descartável com AGENDA_PORTFOLIO_CAPTURE=DADOS_FICTICIOS.');
assert.ok(base.protocol==='http:'&&['localhost','127.0.0.1'].includes(base.hostname)
 &&base.pathname==='/'&&!base.search&&!base.hash&&!base.username&&!base.password,'Use uma origem HTTP local.');
const output=path.join(root,'docs','imagens');
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true});
const owner=await browser.newContext({baseURL:base.origin,viewport:{width:1440,height:1000},locale:'pt-BR',timezoneId:'America/Sao_Paulo',deviceScaleFactor:1});
const mobile=await browser.newContext({...devices['Pixel 7'],deviceScaleFactor:1,baseURL:base.origin,locale:'pt-BR',timezoneId:'America/Sao_Paulo'});
const images=[];
let accountId,professionalId,invited=false;
async function request(context,url,method='GET',body){
 const headers={};
 if(url.startsWith('/api/admin/professional-access'))headers['X-Agenda-Account']=accountId;
 if(method!=='GET'){
  const csrf=await context.request.get('/api/auth/csrf');assert.equal(csrf.status(),200);
  const token=await csrf.json();headers[token.headerName]=token.token;
 }
 const response=await context.request.fetch(url,{method,headers,...(body?{data:body}:{})});
 assert.ok(response.ok(),method+' '+url+' respondeu '+response.status());
 return response.status()===204?null:response.json();
}
async function capture(page,name,{fullPage=true,locator}={}){
 await page.evaluate(async()=>{await document.fonts.ready;if(document.activeElement instanceof HTMLElement)document.activeElement.blur();});
 await expect(page.locator('[role="alert"]')).toHaveCount(0);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Conteúdo excede a largura da tela.');
 const bytes=await(locator??page).screenshot({path:path.join(output,name),...(locator?{}:{fullPage}),animations:'disabled',caret:'hide'});
 images.push({file:name,bytes:bytes.length,width:bytes.readUInt32BE(16),height:bytes.readUInt32BE(20),sha256:createHash('sha256').update(bytes).digest('hex')});
}
try{
 const config=await request(owner,'/api/public/config');
 assert.equal(config.version,'0.15.1');assert.equal(config.mailMode,'demo');assert.equal(config.storageMode,'local');
 const publicCatalog=await request(owner,'/api/public/barbearia-horizonte/catalog');
 assert.equal(publicCatalog.professionals.length,2);
 const date=publicCatalog.suggestedDate;
 const page=await owner.newPage();page.setDefaultTimeout(15000);
 await page.goto('/#/inicio');
 await expect(page.getByRole('heading',{level:1})).toContainText('Sua agenda.');
 await capture(page,'01-inicio-desktop.png',{fullPage:false});
 await page.goto('/#/agendar/barbearia-horizonte');
 await page.getByRole('button',{name:/^\d{2}:\d{2}$/}).first().click();
 await expect(page.getByRole('button',{name:'Confirmar agendamento',exact:true})).toBeVisible();
 await capture(page,'02-reserva-desktop.png');

 await page.goto('/#/painel');
 await page.getByRole('button',{name:'Barbearia Horizonte Marina · responsável'}).click();
 await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await expect(page.getByRole('heading',{name:'João Exemplo',exact:true})).toBeVisible();
 const me=await request(owner,'/api/auth/me');accountId=me.id;
 const catalog=await request(owner,'/api/admin/catalog');professionalId=catalog.professionals.find(p=>p.name==='Lucas Almeida').id;
 const states=await request(owner,'/api/admin/professional-access');
 assert.ok(states.every(s=>!s.enabled&&!s.email&&s.version===0),'Use um banco demo novo, sem acessos existentes.');
 await capture(page,'03-agenda-responsavel.png');

 await page.getByRole('button',{name:'Indicadores e relatórios',exact:true}).click();
 await page.getByLabel('Data inicial',{exact:true}).fill(date);
 await page.getByLabel('Data final',{exact:true}).fill(date);
 await page.getByRole('button',{name:'Consultar relatório',exact:true}).click();
 await expect(page.getByRole('article',{name:'Atendimentos do período',exact:true}).locator('strong')).toHaveText('3');
 await expect(page.getByRole('button',{name:'Exportar CSV',exact:true})).toBeVisible();
 await capture(page,'04-indicadores-desktop.png');

 const email='lucas.portfolio@example.test',password='Senha fictícia portfólio 123!';
 const invitation=await request(owner,'/api/admin/professional-access/'+professionalId+'/invite','POST',{email,version:0});invited=true;
 await request(mobile,'/api/auth/professional-invitations/accept','POST',{token:invitation.token,password});
 const staffPage=await mobile.newPage();staffPage.setDefaultTimeout(15000);
 await staffPage.goto('/#/painel');
 await staffPage.getByLabel('E-mail',{exact:true}).fill(email);
 await staffPage.getByLabel('Senha',{exact:true}).fill(password);
 await staffPage.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await expect(staffPage.getByRole('heading',{name:'Minha agenda',exact:true})).toBeVisible();
 await staffPage.getByLabel('Data da minha agenda').fill(date);
 await expect(staffPage.getByRole('heading',{name:'João Exemplo',exact:true})).toBeVisible();
 await expect(staffPage.getByRole('heading',{name:'Pedro Exemplo',exact:true})).toBeVisible();
 await expect(staffPage.getByRole('heading',{name:'Maria Exemplo',exact:true})).toHaveCount(0);
 await capture(staffPage,'05-minha-agenda-mobile.png');
 await request(mobile,'/api/auth/logout','POST');
 await staffPage.goto('/#/agendar/barbearia-horizonte');
 await staffPage.getByRole('button',{name:/^\d{2}:\d{2}$/}).first().click();
 await expect(staffPage.getByRole('button',{name:'Confirmar agendamento',exact:true})).toBeVisible();
 await capture(staffPage,'06-reserva-mobile.png');
 await fs.writeFile(path.join(output,'capturas.json'),JSON.stringify({
  version:config.version,capturedAt:new Date().toISOString(),source:'Aplicação local real, perfil demo, banco descartável separado.',
  data:'Exclusivamente exemplos fictícios; APIs e conteúdo visual sem mocks.',bookingDate:date,
  desktop:{width:1440,height:1000},mobile:{device:'Pixel 7 emulado',width:devices['Pixel 7'].viewport.width,height:devices['Pixel 7'].viewport.height,deviceScaleFactor:1},
  images
 },null,2)+'\n');
 console.log('OK: '+images.length+' capturas reais, sem rolagem horizontal, com fontes carregadas e sem avisos de erro.');
}catch(error){
 console.error(error instanceof Error?error.message.replace(/token=[A-Za-z0-9_-]+/g,'token=[protegido]'):'Falha na captura.');
 process.exitCode=1;
}finally{
 try{
  if(invited){
   const state=(await request(owner,'/api/admin/professional-access')).find(p=>p.professionalId===professionalId);
   await request(owner,'/api/admin/professional-access/'+professionalId+'/revoke','POST',{version:state.version});
  }
 }catch{console.error('Confira a revogação da conta fictícia no banco descartável.');process.exitCode=1;}
 await mobile.close();await owner.close();await browser.close();
}

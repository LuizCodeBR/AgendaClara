import {test,expect} from '@playwright/test';

async function login(page:import('@playwright/test').Page,name:string){
 await page.goto('/#/painel');
 await page.getByRole('button',{name:name==='marina'?'Barbearia Horizonte Marina · responsável':'Estúdio Aurora Rafael · responsável'}).click();
 await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await expect(page.getByRole('button',{name:'Sair do painel'})).toBeVisible();
}
async function reports(page:import('@playwright/test').Page){
 await page.getByRole('button',{name:'Indicadores e relatórios',exact:true}).click();
 await expect(page.getByRole('article',{name:'Atendimentos do período',exact:true})).toBeVisible();
}

test('trocar a conta em outra aba impede CSV sob a empresa anterior',async({page})=>{
 await login(page,'marina');await reports(page);
 const company=(await(await page.request.get('/api/auth/me')).json()).business.id;
 const other=await page.context().newPage();
 try{
  await other.goto('/#/painel');await other.getByRole('button',{name:'Sair do painel'}).click();
  await expect(other.getByRole('button',{name:'Entrar no painel',exact:true})).toBeVisible();
  await other.getByRole('button',{name:'Estúdio Aurora Rafael · responsável'}).click();
  await other.getByRole('button',{name:'Entrar no painel',exact:true}).click();
  await expect(other.getByRole('button',{name:'Sair do painel'})).toBeVisible();
  const downloads:string[]=[];page.on('download',d=>downloads.push(d.suggestedFilename()));
  const response=page.waitForResponse(r=>r.url().includes('/api/admin/reports/export?'));
  await page.getByRole('button',{name:'Exportar CSV',exact:true}).click();
  const result=await response;expect(result.status()).toBe(412);expect(result.request().headers()['x-agenda-business']).toBe(company);
  expect(result.headers()['content-disposition']).toBeUndefined();
  await expect(page.getByRole('button',{name:'Entrar no painel',exact:true})).toBeVisible();
  await expect(page.getByRole('article',{name:'Atendimentos do período',exact:true})).toHaveCount(0);expect(downloads).toEqual([]);
 }finally{await other.close();}
});

test('resposta incorreta e acesso negado nunca se tornam arquivo CSV',async({page})=>{
 await login(page,'marina');await reports(page);
 const downloads:string[]=[];page.on('download',d=>downloads.push(d.suggestedFilename()));
 let denied=false;
 await page.route('**/api/admin/reports/export?*',route=>route.fulfill(denied
  ?{status:403,contentType:'application/problem+json',body:JSON.stringify({detail:'Acesso revogado para este teste.'})}
  :{status:200,contentType:'text/html',body:'<html>Resposta de entrada inesperada</html>'}));
 await page.getByRole('button',{name:'Exportar CSV',exact:true}).click();
 await expect(page.getByText('O servidor não retornou um arquivo CSV. Atualize a consulta e tente novamente.')).toBeVisible();expect(downloads).toEqual([]);
 denied=true;await page.getByRole('button',{name:'Exportar CSV',exact:true}).click();
 await expect(page.getByRole('button',{name:'Entrar no painel',exact:true})).toBeVisible();
 await expect(page.getByRole('article',{name:'Atendimentos do período',exact:true})).toHaveCount(0);expect(downloads).toEqual([]);
});

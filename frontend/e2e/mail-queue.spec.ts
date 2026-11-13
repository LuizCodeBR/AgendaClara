import { test, expect } from '@playwright/test';

test('responsável acompanha seus e-mails e outra empresa não vê o histórico', async ({ page }, testInfo) => {
 test.setTimeout(60000); page.setDefaultTimeout(10000);
 const email='queue-'+testInfo.project.name+'-'+Date.now()+'@example.test',password='frase exclusiva para acompanhar e-mails 2030';
 await page.goto('/#/cadastro');
 for(const [label,value] of Object.entries({'Nome da empresa':'Empresa de fila '+testInfo.project.name,'Área de atuação':'Teste','Local de atendimento':'Online fictício','Seu nome':'Ana Teste','E-mail de acesso':email,'Crie uma senha':password,'Confirme a senha':password}))
  await page.getByLabel(label,{exact:true}).fill(value);
 const acknowledgement=page.getByRole('checkbox'); if(await acknowledgement.isVisible())await acknowledgement.check();
 await page.getByRole('button',{name:'Criar empresa e conta',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Sua conta foi criada.',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Entrar no meu painel',exact:true}).click();
 await page.getByLabel('Senha',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await page.getByRole('button',{name:'E-mails e envios',exact:true}).click();
 await expect(page.getByText('Disponível na caixa de teste',{exact:true})).toBeVisible();
 await expect(page.getByText('Confirmação de e-mail',{exact:true})).toBeVisible();
 await expect(page.getByText(email,{exact:true})).toHaveCount(0);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 await page.screenshot({path:'../.cache/outbox-'+testInfo.project.name+'.png',fullPage:true});
 await page.getByLabel('Situação dos envios',{exact:true}).selectOption('FAILED');
 await expect(page.getByText('Nenhum envio nesta situação.',{exact:true})).toBeVisible();
 await page.getByLabel('Situação dos envios',{exact:true}).selectOption('');
 await expect(page.getByText('Disponível na caixa de teste',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Sair do painel',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Bem-vindo de volta.',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Barbearia Horizonte Marina · responsável'}).click();
 await page.getByRole('button',{name:'Entrar no painel',exact:true}).click();
 await page.getByRole('button',{name:'E-mails e envios',exact:true}).click();
 await expect(page.getByText('Nenhum envio nesta situação.',{exact:true})).toBeVisible();
 await expect(page.getByText('Disponível na caixa de teste',{exact:true})).toHaveCount(0);
});

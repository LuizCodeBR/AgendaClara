import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
async function openAdmin(page: Page) {
  const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Meu negócio', exact: true }).click();
}
test('cliente reserva; responsável cancela; outra empresa não vê o atendimento', async ({ page }) => {
  const customer = 'Cliente E2E ' + Date.now();
  await page.goto('/');
  await page.getByRole('button', { name: 'Começar meu agendamento', exact: true }).click();
  await page.getByRole('button', { name: /^\d{2}:\d{2}$/ }).first().click();
  await page.getByLabel('Seu nome', { exact: true }).fill(customer);
  await page.getByLabel('Telefone de contato', { exact: true }).fill('11999991234');
  await page.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Seu horário.*está reservado/ })).toBeVisible();
  await openAdmin(page);
  await page.getByRole('button', { name: 'Barbearia Horizonte Marina · responsável' }).click();
  await page.getByRole('button', { name: 'Entrar no painel', exact: true }).click();
  await expect(page.getByRole('heading', { name: customer, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Cancelar ' + customer, exact: true }).click();
  await page.getByRole('button', { name: 'Sim, cancelar', exact: true }).click();
  await expect(page.locator('article').filter({ hasText: customer }).getByText('Cancelado', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sair do painel', exact: true }).click();
  await page.getByRole('button', { name: 'Estúdio Aurora Rafael · responsável' }).click();
  await page.getByRole('button', { name: 'Entrar no painel', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Estúdio Aurora', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: customer, exact: true })).toHaveCount(0);
});
test('layout cabe na tela e PWA não armazena respostas da API', async ({ page, context }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Começar meu agendamento', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const manifestResponse = await page.request.get('/manifest.webmanifest');
  expect(manifestResponse.ok()).toBe(true);
  const manifest = await manifestResponse.json();
  expect(manifest.display).toBe('standalone');
  expect(manifest.icons.map((icon: { sizes: string }) => icon.sizes)).toEqual(expect.arrayContaining(['192x192', '512x512']));
  await page.evaluate(async () => { await navigator.serviceWorker.ready; });
  await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
  await context.setOffline(true);
  try {
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Um instante para reconectar.' })).toBeVisible();
  } finally { await context.setOffline(false); }
  const cached = await page.evaluate(async () => {
    const keys = await caches.keys();
    const requests = await Promise.all(keys.map(async key => (await (await caches.open(key)).keys()).map(request => request.url)));
    return requests.flat();
  });
  expect(cached.some(url => new URL(url).pathname.startsWith('/api/'))).toBe(false);
});

test('responsável cadastra profissional, abre expediente e preserva reserva ao editar horários', async ({ page }, testInfo) => {
  const name = 'Equipe E2E ' + testInfo.project.name + ' ' + Date.now();
  const customer = 'Cliente da equipe ' + testInfo.project.name + ' ' + Date.now();
  await page.goto('/');
  await openAdmin(page);
  await page.getByRole('button', { name: 'Barbearia Horizonte Marina · responsável' }).click();
  await page.getByRole('button', { name: 'Entrar no painel', exact: true }).click();
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  await page.getByRole('button', { name: 'Cadastrar profissional', exact: true }).click();
  await page.getByLabel('Nome do novo profissional', { exact: true }).fill(name);
  await page.getByLabel('Especialidade do novo profissional', { exact: true }).fill('Cortes e barba');
  await page.getByRole('button', { name: 'Cadastrar e configurar horários', exact: true }).click();
  await expect(page.getByLabel('Nome do profissional', { exact: true })).toHaveValue(name);
  const catalog = await (await page.request.get('/api/admin/catalog')).json();
  await page.getByRole('checkbox', { name: 'Atende o serviço ' + catalog.services[0].name, exact: true }).check();
  const dayIndex = (new Date(catalog.suggestedDate + 'T12:00:00Z').getUTCDay() + 6) % 7;
  const day = ['Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado', 'Domingo'][dayIndex];
  await page.getByRole('checkbox', { name: 'Atende ' + day, exact: true }).check();
  await page.getByRole('button', { name: 'Remover intervalo 2 de ' + day, exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Nova reserva', exact: true }).click();
  await page.getByRole('button', { name: new RegExp(name + '.*Profissional') }).click();
  await page.getByRole('button', { name: '09:00', exact: true }).click();
  await page.getByLabel('Seu nome', { exact: true }).fill(customer);
  await page.getByLabel('Telefone de contato', { exact: true }).fill('11999991234');
  await page.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Seu horário.*está reservado/ })).toBeVisible();
  await openAdmin(page);
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  await page.getByRole('button', { name: new RegExp(name + '.*Cortes e barba') }).click();
  await page.getByLabel(day + ' fim 1', { exact: true }).fill('09:15');
  await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('ficaria fora do expediente');
  await expect(page.getByLabel(day + ' fim 1', { exact: true })).toHaveValue('09:15');
  await page.getByRole('button', { name: 'Descartar alterações', exact: true }).click();
  await expect(page.getByLabel(day + ' fim 1', { exact: true })).toHaveValue('12:00');
  await page.getByRole('button', { name: 'Minha agenda', exact: true }).click();
  await page.getByRole('button', { name: 'Cancelar ' + customer, exact: true }).click();
  await page.getByRole('button', { name: 'Sim, cancelar', exact: true }).click();
  await expect(page.locator('article').filter({ hasText: customer }).getByText('Cancelado', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  await page.getByRole('button', { name: new RegExp(name + '.*Cortes e barba') }).click();
  await page.getByRole('checkbox', { name: 'Profissional ativo para reservas', exact: true }).uncheck();
  await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
  await expect(page.getByRole('button', { name: new RegExp(name + '.*Inativo') })).toBeVisible();
});

test('links inválidos permitem voltar ao início sem selecionar outra empresa silenciosamente', async ({ page }) => {
  await page.goto('/#/agendar/%E0%A4%A');
  await expect(page.getByRole('heading', { name: 'Página não encontrada', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao início', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Começar meu agendamento', exact: true })).toBeVisible();
  await page.goto('/#/agendar/empresa-inexistente');
  await expect(page.getByRole('heading', { name: 'Estabelecimento não encontrado', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Confirmar agendamento', exact: true })).toHaveCount(0);
});

test('rascunho permanece na navegação e Escape não fecha um cadastro enquanto ele está sendo gravado', async ({ page }, testInfo) => {
  await page.goto('/');
  await openAdmin(page);
  await page.getByRole('button', { name: 'Barbearia Horizonte Marina · responsável' }).click();
  await page.getByRole('button', { name: 'Entrar no painel', exact: true }).click();
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  const specialty = page.getByLabel('Especialidade', { exact: true });
  await expect(specialty).toBeVisible();
  const original = await specialty.inputValue();
  await specialty.fill(original + ' rascunho');
  const openMenu = page.getByRole('button', { name: 'Abrir menu', exact: true });
  if (await openMenu.isVisible()) await openMenu.click();
  await expect(page.getByRole('button', { name: 'Agendar um horário', exact: true })).toBeDisabled();
  await page.evaluate(() => { window.location.hash = '/inicio'; });
  await expect(page).toHaveURL(/#\/painel$/);
  await expect(specialty).toHaveValue(original + ' rascunho');
  await page.getByRole('button', { name: 'Descartar alterações', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Agendar um horário', exact: true })).toBeEnabled();
  const closeMenu = page.getByRole('button', { name: 'Fechar menu', exact: true });
  if (await closeMenu.isVisible()) await closeMenu.click();

  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/admin/professionals', async route => {
    if (route.request().method() === 'POST') await gate;
    await route.continue();
  });
  try {
    const name = 'Janela E2E ' + testInfo.project.name + ' ' + Date.now();
    await page.getByRole('button', { name: 'Cadastrar profissional', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Nome do novo profissional').fill(name);
    await dialog.getByLabel('Especialidade do novo profissional').fill('Teste de gravação');
    await dialog.getByRole('button', { name: 'Cadastrar e configurar horários', exact: true }).click();
    await expect(dialog.getByRole('button', { name: 'Cadastrando…', exact: true })).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Fechar', exact: true })).toBeDisabled();
    release();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByLabel('Nome do profissional', { exact: true })).toHaveValue(name);
    await page.getByRole('checkbox', { name: 'Profissional ativo para reservas', exact: true }).uncheck();
    await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
    await expect(page.getByRole('button', { name: new RegExp(name + '.*Inativo') })).toBeVisible();
  } finally { release(); await page.unrouteAll({ behavior: 'wait' }); }
});

test('folga fecha a data; horário especial permite reservar; alterações preservam o atendimento', async ({ page }, testInfo) => {
  test.setTimeout(60000);
  page.setDefaultTimeout(10000);
  const name = 'Exceções E2E ' + testInfo.project.name + ' ' + Date.now();
  const customer = 'Cliente das exceções ' + testInfo.project.name + ' ' + Date.now();
  await page.goto('/');
  await openAdmin(page);
  await page.getByRole('button', { name: 'Barbearia Horizonte Marina · responsável' }).click();
  await page.getByRole('button', { name: 'Entrar no painel', exact: true }).click();
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  await page.getByRole('button', { name: 'Cadastrar profissional', exact: true }).click();
  await page.getByLabel('Nome do novo profissional', { exact: true }).fill(name);
  await page.getByLabel('Especialidade do novo profissional', { exact: true }).fill('Atendimento especial');
  await page.getByRole('button', { name: 'Cadastrar e configurar horários', exact: true }).click();
  await expect(page.getByLabel('Nome do profissional', { exact: true })).toHaveValue(name);
  const catalog = await (await page.request.get('/api/admin/catalog')).json();
  await page.getByRole('checkbox', { name: 'Atende o serviço ' + catalog.services[0].name, exact: true }).check();
  const target = catalog.suggestedDate;
  const dayIndex = (new Date(target + 'T12:00:00Z').getUTCDay() + 6) % 7;
  const day = ['Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado', 'Domingo'][dayIndex];
  await page.getByRole('checkbox', { name: 'Atende ' + day, exact: true }).check();
  await page.getByRole('button', { name: 'Remover intervalo 2 de ' + day, exact: true }).click();
  await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true })).toBeDisabled();

  async function openDate() {
    await page.getByRole('button', { name: 'Folgas e horários especiais', exact: true }).click();
    await page.getByLabel('Profissional da exceção', { exact: true }).selectOption({ label: name });
    await page.getByLabel('Data da exceção', { exact: true }).fill(target);
  }
  async function openPublic() {
    await page.getByRole('button', { name: 'Nova reserva', exact: true }).click();
    await page.getByRole('button', { name: new RegExp(name + '.*Profissional') }).click();
  }
  await openDate();
  await page.getByLabel('Motivo da exceção', { exact: true }).fill('Folga E2E');
  await page.getByRole('button', { name: 'Salvar exceção', exact: true }).click();
  await expect(page.getByText('Exceção cadastrada', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await openPublic();
  await expect(page.getByText('Sem horários disponíveis neste dia.', { exact: true })).toBeVisible();

  await openAdmin(page);
  await openDate();
  await page.getByRole('checkbox', { name: 'Não atender nesta data', exact: true }).uncheck();
  await page.getByLabel('Exceção início 1', { exact: true }).fill('19:00');
  await page.getByLabel('Exceção fim 1', { exact: true }).fill('20:00');
  await page.getByLabel('Motivo da exceção', { exact: true }).fill('Atendimento excepcional E2E');
  await page.getByRole('button', { name: 'Salvar exceção', exact: true }).click();
  await expect(page.getByText('Exceção cadastrada', { exact: true })).toBeVisible();
  await openPublic();
  await page.getByRole('button', { name: '19:00', exact: true }).click();
  await page.getByLabel('Seu nome', { exact: true }).fill(customer);
  await page.getByLabel('Telefone de contato', { exact: true }).fill('11999991234');
  await page.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Seu horário.*está reservado/ })).toBeVisible();

  await openAdmin(page);
  await openDate();
  await page.getByRole('checkbox', { name: 'Não atender nesta data', exact: true }).check();
  await page.getByRole('button', { name: 'Salvar exceção', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('ficaria fora do expediente');
  await expect(page.getByRole('checkbox', { name: 'Não atender nesta data', exact: true })).toBeChecked();
  await page.getByRole('button', { name: 'Descartar alterações da exceção', exact: true }).click();
  await page.getByRole('button', { name: 'Remover exceção e usar expediente semanal', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar remoção da exceção', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('ficaria fora do expediente');

  await page.getByRole('button', { name: 'Minha agenda', exact: true }).click();
  await page.getByRole('button', { name: 'Cancelar ' + customer, exact: true }).click();
  await page.getByRole('button', { name: 'Sim, cancelar', exact: true }).click();
  await expect(page.locator('article').filter({ hasText: customer }).getByText('Cancelado', { exact: true })).toBeVisible();
  await openDate();
  await page.getByRole('button', { name: 'Remover exceção e usar expediente semanal', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar remoção da exceção', exact: true }).click();
  await expect(page.getByText('Esta data segue o expediente semanal', { exact: true })).toBeVisible();
  await openPublic();
  await expect(page.getByRole('button', { name: '09:00', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '19:00', exact: true })).toHaveCount(0);
  await openAdmin(page);
  await page.getByRole('button', { name: 'Equipe e horários', exact: true }).click();
  await page.getByRole('button', { name: new RegExp(name + '.*Atendimento especial') }).click();
  await page.getByRole('checkbox', { name: 'Profissional ativo para reservas', exact: true }).uncheck();
  await page.getByRole('button', { name: 'Salvar cadastro e horários', exact: true }).click();
  await expect(page.getByRole('button', { name: new RegExp(name + '.*Inativo') })).toBeVisible();
});

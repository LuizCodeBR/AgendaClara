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

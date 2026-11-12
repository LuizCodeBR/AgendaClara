import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import { api } from './api';
import { catalog } from './test/fixtures';
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
vi.mock('./HomePage', () => ({ default: () => <h1>Início</h1> }));
vi.mock('./BookingPage', () => ({ default: ({ slug }: { slug: string }) => <h1>Reserva de {slug}</h1> }));
const mocked = vi.mocked(api);
beforeEach(() => {
  window.history.replaceState(null, '', '/');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  mocked.mockImplementation(async path => {
    if (path.endsWith('/businesses')) return [catalog.business];
    if (path.endsWith('/config')) return { demo: true, publicDemo: false, distribution: {} };
    return null;
  });
});
it('recupera um link com codificação inválida sem derrubar a aplicação', async () => {
  window.history.replaceState(null, '', '/#/agendar/%E0%A4%A');
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button', { name: 'Voltar ao início' }));
  expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();
});
it('não substitui silenciosamente a empresa de um link inexistente pela primeira da lista', async () => {
  window.history.replaceState(null, '', '/#/agendar/empresa-inexistente');
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Estabelecimento não encontrado' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Reserva de teste' })).not.toBeInTheDocument();
});
it('mantém links válidos funcionando na abertura e na troca de endereço', async () => {
  window.history.replaceState(null, '', '/#/agendar/teste');
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Reserva de teste' })).toBeInTheDocument();
  act(() => {
    window.history.replaceState(null, '', '/#/rota-inexistente');
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
  expect(await screen.findByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
});

it('preserva o rascunho do expediente ao tentar sair pelo menu principal ou pelo histórico', async () => {
  const { owner, agenda } = await import('./test/fixtures');
  mocked.mockImplementation(async path => {
    if (path.endsWith('/businesses')) return [catalog.business];
    if (path.endsWith('/config')) return { demo: true, distribution: {} };
    if (path.endsWith('/me')) return owner;
    if (path.endsWith('/catalog')) return catalog;
    if (path.includes('/agenda?')) return agenda;
    if (path.endsWith('/professionals')) return [{
      id: 'professional', name: 'Ana Exemplo', specialty: 'Corte', active: true, version: 0, hours: [],
    }];
    throw new Error('Unexpected: ' + path);
  });
  window.history.replaceState(null, '', '/#/painel');
  render(<App />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'Equipe e horários' }));
  const specialty = await screen.findByLabelText('Especialidade', { exact: true });
  await user.type(specialty, ' e barba');
  expect(screen.getByRole('button', { name: 'Agendar um horário' })).toBeDisabled();
  await user.click(screen.getByRole('link', { name: 'Agenda Clara início' }));
  expect(specialty).toHaveValue('Corte e barba');
  act(() => {
    window.history.replaceState(null, '', '/#/inicio');
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
  expect(window.location.hash).toBe('#/painel');
  expect(specialty).toHaveValue('Corte e barba');
  await user.click(screen.getByRole('button', { name: 'Descartar alterações' }));
  await user.click(screen.getByRole('link', { name: 'Agenda Clara início' }));
  expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();
});

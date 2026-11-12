import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import AdminPage from './AdminPage';
import { api, ApiError } from './api';
import { agenda, catalog, owner } from './test/fixtures';
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
beforeEach(() => { mocked.mockReset(); });
function mount() { render(<AdminPage user={owner} onUser={vi.fn()} demo online onBook={vi.fn()} />); }

it('não apresenta os atendimentos e bloqueios anteriores como se fossem da nova data quando a consulta falha', async () => {
  mocked.mockImplementation(async path => {
    if (path.endsWith('/catalog')) return catalog;
    if (path.endsWith('2030-06-04')) return agenda;
    throw new ApiError('Não foi possível carregar a data escolhida.', 503);
  });
  mount();
  await screen.findByRole('heading', { name: 'Cliente do dia anterior' });
  fireEvent.change(screen.getByLabelText('Data da agenda'), { target: { value: '2030-06-05' } });
  await screen.findByText('Não foi possível carregar a data escolhida.');
  expect(screen.queryByRole('heading', { name: 'Cliente do dia anterior' })).not.toBeInTheDocument();
  expect(screen.queryByText('Bloqueio do dia anterior')).not.toBeInTheDocument();
  expect(screen.queryByText('Um espaço livre por aqui.')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeEnabled();
});

it('permite tentar novamente a mesma data e só então exibe seus resultados', async () => {
  let failed = true;
  mocked.mockImplementation(async path => {
    if (path.endsWith('/catalog')) return catalog;
    if (path.endsWith('2030-06-04')) return agenda;
    if (failed) throw new ApiError('Falha temporária.', 503);
    return { ...agenda, date: '2030-06-05', appointments: [], blocks: [] };
  });
  mount();
  await screen.findByRole('heading', { name: 'Cliente do dia anterior' });
  fireEvent.change(screen.getByLabelText('Data da agenda'), { target: { value: '2030-06-05' } });
  await screen.findByText('Falha temporária.');
  failed = false;
  await userEvent.setup().click(screen.getByRole('button', { name: 'Tentar novamente' }));
  await screen.findByText('Um espaço livre por aqui.');
  await waitFor(() => expect(screen.queryByText('Falha temporária.')).not.toBeInTheDocument());
});

it('mantém o erro do catálogo visível e permite recuperar a abertura inicial do painel', async () => {
  let failed = true;
  mocked.mockImplementation(async path => {
    if (path.endsWith('/catalog')) {
      if (failed) throw new ApiError('Catálogo indisponível.', 503);
      return catalog;
    }
    return agenda;
  });
  mount();
  await screen.findByText('Catálogo indisponível.');
  expect(screen.queryByText('Um espaço livre por aqui.')).not.toBeInTheDocument();
  failed = false;
  await userEvent.setup().click(screen.getByRole('button', { name: 'Recarregar catálogo' }));
  await screen.findByRole('heading', { name: 'Cliente do dia anterior' });
  expect(screen.queryByText('Catálogo indisponível.')).not.toBeInTheDocument();
});

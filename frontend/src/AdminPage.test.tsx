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

it('mostra resultados e ações apenas para atendimentos encerrados',async()=>{
 vi.setSystemTime(new Date('2030-06-04T13:00:00Z'));
 mocked.mockImplementation(async path=>path.endsWith('/catalog')?catalog:{...agenda,appointments:[...agenda.appointments,{...agenda.appointments[0],id:'done',customerName:'Cliente concluído',status:'COMPLETED'}]});
 try{mount();await screen.findByRole('heading',{name:'Cliente concluído'});expect(screen.getByText('Concluídos: 1')).toBeInTheDocument();expect(screen.getByText('Aguardando resultado: 1')).toBeInTheDocument();expect(screen.getByRole('button',{name:'Concluir Cliente do dia anterior'})).toBeEnabled();expect(screen.getByRole('button',{name:'Corrigir resultado de Cliente concluído'})).toBeEnabled();expect(screen.queryByRole('button',{name:'Cancelar Cliente concluído'})).not.toBeInTheDocument();}
 finally{vi.useRealTimers();}
});
it('filtra faltas sem apresentar reservas de outra situação',async()=>{
 mocked.mockImplementation(async path=>path.endsWith('/catalog')?catalog:{...agenda,appointments:[...agenda.appointments,{...agenda.appointments[0],id:'absent',customerName:'Cliente ausente',status:'NO_SHOW'}]});
 mount();await screen.findByRole('heading',{name:'Cliente ausente'});await userEvent.setup().click(screen.getByRole('button',{name:'Faltas'}));expect(screen.queryByRole('heading',{name:'Cliente do dia anterior'})).not.toBeInTheDocument();expect(screen.getByRole('heading',{name:'Cliente ausente'})).toBeInTheDocument();
});

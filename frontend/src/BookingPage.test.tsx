import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import BookingPage from './BookingPage';
import { api, ApiError } from './api';
import type { Catalog, Receipt } from './types';
vi.mock('./api', async importOriginal => {
  const original = await importOriginal<typeof import('./api')>();
  return { ...original, api: vi.fn() };
});
const catalog: Catalog = {
  business: { id: 'business', slug: 'teste', name: 'Negócio Teste', category: 'Barbearia', zoneId: 'America/Sao_Paulo', address: 'Rua Teste' },
  services: [{ id: 'service', name: 'Corte', description: 'Atendimento', durationMinutes: 30, priceCents: 5000 }],
  professionals: [{ id: 'professional', name: 'Ana Exemplo', specialty: 'Corte' }],
  suggestedDate: '2030-06-04', horizonDays: 60,
};
const slot = { startsAt: '2030-06-04T12:00:00Z', endsAt: '2030-06-04T12:30:00Z' };
const receipt: Receipt = { ...slot, serviceName: 'Corte', professionalName: 'Ana Exemplo', reference: 'ABC123456789', priceCents: 5000 };
const mocked = vi.mocked(api);
beforeEach(() => { mocked.mockReset(); });
function mount(online = true) { return render(<BookingPage slug="teste" businesses={[catalog.business]} onSelectBusiness={vi.fn()} online={online} />); }
async function fill() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: /^09:00$/ }));
  await user.type(screen.getByLabelText('Seu nome'), 'Cliente Exemplo');
  await user.type(screen.getByLabelText('Telefone de contato'), '11999991234');
  return user;
}
it('mantém a mesma chave ao repetir uma confirmação após falha de conexão', async () => {
  let attempts = 0;
  mocked.mockImplementation(async (path, options) => {
    if (path.endsWith('/catalog')) return catalog;
    if (path.includes('/availability?')) return { slots: [slot], date: '2030-06-04', zoneId: 'America/Sao_Paulo' };
    if (options?.method === 'POST') {
      if (++attempts === 1) throw new ApiError('Conexão interrompida.', 0);
      return receipt;
    }
    throw new Error('Unexpected request: ' + path);
  });
  mount(); const user = await fill();
  await user.click(screen.getByRole('button', { name: 'Confirmar agendamento' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Conexão interrompida.');
  await user.click(screen.getByRole('button', { name: 'Confirmar agendamento' }));
  expect(await screen.findByRole('heading', { name: /Seu horário.*está reservado/ })).toBeInTheDocument();
  const calls = mocked.mock.calls.filter(([, options]) => options?.method === 'POST');
  expect(JSON.parse(calls[0][1]?.body as string).requestKey).toBe(JSON.parse(calls[1][1]?.body as string).requestKey);
});
it('atualiza a disponibilidade e exige nova escolha após um conflito', async () => {
  mocked.mockImplementation(async path => {
    if (path.endsWith('/catalog')) return catalog;
    if (path.includes('/availability?')) return { slots: [slot] };
    throw new ApiError('Esse horário acabou de ser ocupado.', 409);
  });
  mount(); const user = await fill();
  await user.click(screen.getByRole('button', { name: 'Confirmar agendamento' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('acabou de ser ocupado');
  await waitFor(() => expect(screen.getByRole('button', { name: 'Confirmar agendamento' })).toBeDisabled());
  expect(screen.getByText('Selecione um horário disponível')).toBeInTheDocument();
});
it('impede confirmação quando o navegador está sem conexão', async () => {
  mocked.mockImplementation(async path => path.endsWith('/catalog') ? catalog : { slots: [slot] });
  mount(false); await fill();
  expect(screen.getByRole('button', { name: 'Confirmar agendamento' })).toBeDisabled();
  expect(mocked.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false);
});

it('mostra no comprovante a data local de um atendimento noturno, mesmo quando UTC já está no dia seguinte', async () => {
  const night = { startsAt: '2030-06-05T01:00:00Z', endsAt: '2030-06-05T01:30:00Z' };
  mocked.mockImplementation(async (path, options) => {
    if (path.endsWith('/catalog')) return catalog;
    if (path.includes('/availability?')) return { slots: [night] };
    if (options?.method === 'POST') return { ...receipt, ...night };
    throw new Error('Unexpected: ' + path);
  });
  mount();
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: '22:00' }));
  await user.type(screen.getByLabelText('Seu nome'), 'Cliente da noite');
  await user.type(screen.getByLabelText('Telefone de contato'), '11999991234');
  await user.click(screen.getByRole('button', { name: 'Confirmar agendamento' }));
  await screen.findByRole('heading', { name: /Seu horário.*está reservado/ });
  expect(document.getElementById('booking-receipt')).toHaveTextContent('4 de junho');
  expect(document.getElementById('booking-receipt')).not.toHaveTextContent('5 de junho');
});

import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import MailQueuePanel from './MailQueuePanel';
import type { MailMonitor } from './MailQueuePanel';
import { api, ApiError } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const state: MailMonitor = { mode: 'demo', counts: { PENDING: 0, PROCESSING: 0, ACCEPTED: 1, FAILED: 0 }, oldestPendingAt: null, lastCheckAt: '2030-06-03T10:00:00Z', health: 'SIMULATED', messages: [{ id: 'mail', purpose: 'VERIFY', mode: 'demo', status: 'ACCEPTED', attempts: 0, manualRetries: 0, createdAt: '2030-06-03T10:00:00Z', expiresAt: '2030-06-04T10:00:00Z', nextAttemptAt: null, acceptedAt: '2030-06-03T10:00:00Z', lastError: null, canRetry: false }] };
beforeEach(() => { mocked.mockReset(); });
function mount(online = true) { const dirty = vi.fn(), unauthorized = vi.fn(); render(<MailQueuePanel online={online} onDirty={dirty} onUnauthorized={unauthorized} />); return { dirty, unauthorized }; }
it('identifica o modo simulado e mostra os estados sem confundir aceitação com entrega', async () => {
  mocked.mockResolvedValue(state); mount();
  expect(await screen.findByText('Disponível na caixa de teste')).toBeVisible();
  expect(screen.getByText('Confirmação de e-mail')).toBeVisible();
  expect(screen.getByText(/Nenhum e-mail real é enviado/)).toBeVisible();
  expect(screen.getByText(/a chegada à caixa de entrada depende do provedor/)).toBeVisible();
  expect(screen.queryByRole('button', { name: 'Tentar este envio novamente' })).not.toBeInTheDocument();
});
it('reagenda uma falha elegível e atualiza o acompanhamento', async () => {
  const failed: MailMonitor = { ...state, mode: 'resend', health: 'ATTENTION', counts: { FAILED: 1 }, messages: [{ ...state.messages[0], mode: 'resend', status: 'FAILED', attempts: 3, lastError: 'NETWORK', canRetry: true }] };
  mocked.mockImplementation(async (_path, options) => options?.method === 'POST' ? undefined : failed);
  const { dirty } = mount();
  await userEvent.setup().click(await screen.findByRole('button', { name: 'Tentar este envio novamente' }));
  expect(await screen.findByText('Nova tentativa agendada.')).toBeVisible();
  expect(mocked).toHaveBeenCalledWith('/api/admin/emails/mail/retry', { method: 'POST' });
  expect(dirty).toHaveBeenCalledWith(true);
});
it('a falha de consulta do novo filtro não mostra o histórico anterior como resultado atualizado', async () => {
  mocked.mockImplementation(async path => path.includes('?') ? Promise.reject(new ApiError('Falha na consulta.', 503)) : state);
  mount(); await screen.findByText('Disponível na caixa de teste');
  fireEvent.change(screen.getByLabelText('Situação dos envios'), { target: { value: 'FAILED' } });
  expect(await screen.findByText('Falha na consulta.')).toBeVisible();
  expect(screen.queryByText('Disponível na caixa de teste')).not.toBeInTheDocument();
});
it('solicita novo login quando a sessão expira', async () => {
  mocked.mockRejectedValue(new ApiError('Entre novamente.', 401)); const { unauthorized } = mount();
  await screen.findByText('Entre novamente.'); expect(unauthorized).toHaveBeenCalledOnce();
});
it('não consulta nem tenta enviar enquanto está sem conexão', async () => {
  mount(false);
  expect(await screen.findByText(/Reconecte para consultar/)).toBeVisible(); expect(mocked).not.toHaveBeenCalled();
});

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import AccountAccessPage from './AccountAccessPage';
import type { AccessMode } from './AccountAccessPage';
import DemoMailbox from './DemoMailbox';
import { api, ApiError } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api), token = 'a'.repeat(43);
beforeEach(() => { mocked.mockReset(); });
function mount(mode: AccessMode, mailMode = 'disabled', value = token) {
  const login = vi.fn(), reset = vi.fn(), blocked = vi.fn();
  render(<AccountAccessPage mode={mode} token={value} initialEmail="ana@example.test" mailMode={mailMode} online authenticated={false} onLogin={login} onRequestVerification={vi.fn()} onReset={reset} onNavigationBlocked={blocked} />);
  return { login, reset, blocked };
}
it('abrir o link não consome a confirmação; a pessoa precisa confirmar explicitamente', async () => {
  mocked.mockResolvedValue({ message: 'E-mail confirmado.' }); mount('verify');
  expect(mocked).not.toHaveBeenCalled();
  await userEvent.setup().click(screen.getByRole('button', { name: 'Confirmar este e-mail' }));
  expect(mocked).toHaveBeenCalledWith('/api/auth/email/confirm', expect.objectContaining({ method: 'POST', body: JSON.stringify({ token }) }));
  expect(await screen.findByText('E-mail confirmado.')).toBeVisible();
});
it('não envia um link incompleto e orienta solicitar outro', () => {
  mount('reset', 'disabled', 'invalid');
  expect(screen.getByText(/Abra o link completo/)).toBeVisible(); expect(mocked).not.toHaveBeenCalled();
});
it('mantém a mensagem genérica de solicitação sem afirmar que a conta existe', async () => {
  mocked.mockImplementation(async path => path.endsWith('demo-mailbox') ? [] : { message: 'Se houver uma conta elegível, o link será enviado.' });
  mount('forgot', 'demo');
  await userEvent.setup().click(screen.getByRole('button', { name: 'Solicitar recuperação de senha' }));
  expect(await screen.findByText('Se houver uma conta elegível, o link será enviado.')).toBeVisible();
  expect(JSON.parse(mocked.mock.calls.find(([path]) => path.endsWith('/password/request'))![1]!.body as string)).toEqual({ email: 'ana@example.test' });
});
it('recusa confirmação diferente e senhas maiores que o limite em bytes', async () => {
  mount('reset'); const user = userEvent.setup();
  const fill = (password: string, confirmation: string) => {
    fireEvent.change(screen.getByLabelText('Nova senha', { exact: true }), { target: { value: password } });
    fireEvent.change(screen.getByLabelText('Confirme a nova senha'), { target: { value: confirmation } });
  };
  fill('á'.repeat(37), 'á'.repeat(37)); await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }));
  expect(await screen.findByText(/Use pelo menos 15 caracteres/)).toBeVisible();
  fill('uma frase comprida 2030', 'outra frase comprida 2030'); await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }));
  expect(await screen.findByText('As senhas precisam ser iguais.')).toBeVisible(); expect(mocked).not.toHaveBeenCalled();
});
it('bloqueia edição e navegação durante a troca, limpa a senha e exige novo login', async () => {
  let finish!: (value: { message: string }) => void;
  mocked.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const { reset, blocked, login } = mount('reset');
  for (const label of ['Nova senha', 'Confirme a nova senha']) fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value: 'nova frase comprida 2030' } });
  await userEvent.setup().click(screen.getByRole('button', { name: 'Salvar nova senha' }));
  expect(screen.getByLabelText('Nova senha', { exact: true })).toBeDisabled(); expect(blocked).toHaveBeenCalledWith(true);
  finish({ message: 'Senha alterada.' }); await screen.findByText('Senha alterada.');
  expect(screen.queryByLabelText('Nova senha', { exact: true })).not.toBeInTheDocument();
  expect(reset).toHaveBeenCalledOnce(); expect(blocked).toHaveBeenLastCalledWith(false);
  await userEvent.setup().click(screen.getByRole('button', { name: 'Voltar ao painel' }));
  expect(login).toHaveBeenCalled();
});
it('link vencido mostra o erro sem apresentar sucesso', async () => {
  mocked.mockRejectedValue(new ApiError('Link inválido ou expirado.', 400)); mount('verify');
  await userEvent.setup().click(screen.getByRole('button', { name: 'Confirmar este e-mail' }));
  expect(await screen.findByText('Link inválido ou expirado.')).toBeVisible();
});
it('desativa a solicitação quando o envio não está configurado', () => {
  mount('forgot');
  expect(screen.getByRole('button', { name: 'Solicitar recuperação de senha' })).toBeDisabled();
  expect(screen.getByText(/O envio de e-mails ainda não foi configurado/)).toBeVisible();
});
it('a caixa de teste mostra somente a resposta privada da API e distingue uma notificação sem link', async () => {
  mocked.mockResolvedValue([{ id: 'notice', purpose: 'CHANGED', to: 'ana@example.test', subject: 'Senha alterada', text: 'Suas sessões foram encerradas.', actionUrl: '', expiresAt: '' }]);
  render(<DemoMailbox />);
  expect(await screen.findByText('Senha alterada')).toBeVisible();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  expect(screen.getByText(/Nenhum e-mail real é enviado/)).toBeVisible();
  await waitFor(() => expect(mocked).toHaveBeenCalledWith('/api/auth/demo-mailbox', expect.anything()));
});

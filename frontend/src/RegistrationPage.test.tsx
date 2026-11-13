import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import RegistrationPage from './RegistrationPage';
import { api, ApiError } from './api';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
beforeEach(() => { mocked.mockReset(); });
function mount(publicDemo = false, enabled = true) {
  const onLogin = vi.fn(), blocked = vi.fn();
  render(<RegistrationPage enabled={enabled} publicDemo={publicDemo} online user={null} onLogin={onLogin} onNavigationBlocked={blocked} />);
  return { onLogin, blocked };
}
function fill(password = 'minha frase de teste 2030') {
  for (const [label, value] of Object.entries({ 'Nome da empresa': 'Negócio teste', 'Área de atuação': 'Consultoria', 'Local de atendimento': 'Online', 'Seu nome': 'Ana Teste', 'E-mail de acesso': 'ana@example.test', 'Crie uma senha': password, 'Confirme a senha': password }))
    fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
}
it('mantém a chave ao repetir uma falha de rede e troca a chave ao editar o cadastro', async () => {
  const user = userEvent.setup(); const { blocked } = mount(); fill();
  mocked.mockRejectedValue(new ApiError('Sem conexão.', 0));
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  await screen.findByText('Sem conexão.');
  const first = JSON.parse(mocked.mock.calls[0][1]!.body as string);
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  await waitFor(() => expect(mocked).toHaveBeenCalledTimes(2));
  expect(JSON.parse(mocked.mock.calls[1][1]!.body as string).requestKey).toBe(first.requestKey);
  fireEvent.change(screen.getByLabelText('Seu nome'), { target: { value: 'Ana Editada' } });
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  await waitFor(() => expect(mocked).toHaveBeenCalledTimes(3));
  expect(JSON.parse(mocked.mock.calls[2][1]!.body as string).requestKey).not.toBe(first.requestKey);
  expect(first).not.toHaveProperty('confirmation'); expect(blocked).toHaveBeenCalledWith(true);
});
it('exige confirmação da demonstração, limpa a senha e encaminha o e-mail para o login', async () => {
  const user = userEvent.setup(); const { onLogin, blocked } = mount(true); fill();
  const button = screen.getByRole('button', { name: 'Criar empresa e conta' });
  expect(button).toBeDisabled(); expect(screen.getByText(/As contas e os agendamentos podem ser apagados/)).toBeVisible();
  await user.click(screen.getByRole('checkbox')); mocked.mockResolvedValue({ businessName: 'Negócio teste', email: 'ana@example.test' });
  await user.click(button); await screen.findByRole('heading', { name: 'Sua conta foi criada.' });
  expect(screen.queryByLabelText('Crie uma senha')).not.toBeInTheDocument();
  expect(blocked).toHaveBeenLastCalledWith(false);
  await user.click(screen.getByRole('button', { name: 'Entrar no meu painel' }));
  expect(onLogin).toHaveBeenCalledWith('ana@example.test');
});
it('valida tamanho em bytes e confirmação sem enviar senhas inválidas', async () => {
  const user = userEvent.setup(); mount(); fill('á'.repeat(37));
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  expect(await screen.findByText(/Use pelo menos 15 caracteres/)).toBeVisible(); expect(mocked).not.toHaveBeenCalled();
  fill(); fireEvent.change(screen.getByLabelText('Confirme a senha'), { target: { value: 'outra senha comprida' } });
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  expect(await screen.findByText('As senhas precisam ser iguais.')).toBeVisible(); expect(mocked).not.toHaveBeenCalled();
});
it('impede nova edição durante o envio e permite descartar o rascunho antes do login', async () => {
  const user = userEvent.setup(); const { onLogin } = mount(); fill();
  let finish!: (value: unknown) => void;
  mocked.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  await user.click(screen.getByRole('button', { name: 'Criar empresa e conta' }));
  expect(screen.getByLabelText('Nome da empresa')).toBeDisabled();
  finish({ businessName: 'Negócio teste', email: 'ana@example.test' });
  await screen.findByRole('heading', { name: 'Sua conta foi criada.' });
  await user.click(screen.getByRole('button', { name: 'Entrar no meu painel' }));
  expect(onLogin).toHaveBeenCalledWith('ana@example.test');
});
it('não exibe o formulário quando o cadastro está desativado', () => {
  mount(false, false); expect(screen.getByRole('heading', { name: 'Cadastro indisponível' })).toBeVisible();
  expect(screen.queryByLabelText('Crie uma senha')).not.toBeInTheDocument();
});
it('limpar cadastro libera a navegação e remove os dados digitados', async () => {
  const user = userEvent.setup(); const { blocked } = mount(); fill();
  expect(blocked).toHaveBeenCalledWith(true);
  await user.click(screen.getByRole('button', { name: 'Limpar cadastro' }));
  expect(screen.getByLabelText('Crie uma senha')).toHaveValue('');
  expect(screen.getByLabelText('Nome da empresa')).toHaveValue('');
  expect(blocked).toHaveBeenLastCalledWith(false);
});

it('mantém o aceite fictício e descreve uma demonstração persistente', () => {
  render(<RegistrationPage enabled publicDemo temporaryStorage={false} online user={null} onLogin={vi.fn()} onNavigationBlocked={vi.fn()} />);
  expect(screen.getByText(/demonstração com armazenamento persistente/)).toBeVisible();
  expect(screen.queryByText(/podem ser apagados quando o servidor reiniciar/)).not.toBeInTheDocument();
  expect(screen.getByRole('checkbox')).toBeRequired();
});

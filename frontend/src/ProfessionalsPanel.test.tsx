import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import ProfessionalsPanel, { ProfessionalEditor } from './ProfessionalsPanel';
import { api, ApiError } from './api';
import { catalog } from './test/fixtures';
import type { ProfessionalConfiguration } from './types';
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const person: ProfessionalConfiguration = { id: 'p1', name: 'Ana Exemplo', specialty: 'Cortes', serviceIds: ['service'], active: true, version: 3, hours: [{ weekday: 1, opensAt: '09:00:00', closesAt: '12:00:00' }] };
beforeEach(() => {
  mocked.mockReset();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});
function editor(online = true) {
  const onSaved = vi.fn(), onDirty = vi.fn(), onReload = vi.fn();
  render(<ProfessionalEditor services={catalog.services} person={person} online={online} onDirty={onDirty} onSaved={onSaved} onReload={onReload} />);
  return { onSaved, onDirty, onReload };
}
it('envia perfil, estado, versão e semana completa juntos; permite fechar e abrir dias', async () => {
  mocked.mockResolvedValue({ ...person, version: 4 });
  const { onSaved, onDirty } = editor(); const user = userEvent.setup();
  await user.clear(screen.getByLabelText('Nome do profissional'));
  await user.type(screen.getByLabelText('Nome do profissional'), 'Ana Silva');
  await user.click(screen.getByRole('checkbox', { name: 'Atende Segunda-feira' }));
  await user.click(screen.getByRole('checkbox', { name: 'Atende Terça-feira' }));
  await user.click(screen.getByRole('button', { name: 'Remover intervalo 2 de Terça-feira' }));
  await user.click(screen.getByRole('button', { name: 'Salvar cadastro e horários' }));
  const [path, options] = mocked.mock.calls[0];
  expect(path).toBe('/api/admin/professionals/p1');
  expect(options?.method).toBe('PUT');
  expect(JSON.parse(options?.body as string)).toEqual({ name: 'Ana Silva', specialty: 'Cortes', active: true, version: 3, serviceIds: ['service'], hours: [{ weekday: 2, opensAt: '09:00', closesAt: '12:00' }] });
  expect(onDirty).toHaveBeenCalledWith(true); expect(onSaved).toHaveBeenCalled();
});
it('copia segunda para os dias úteis e limita a quatro intervalos por dia', async () => {
  editor(); const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'Copiar segunda para terça a sexta' }));
  expect(screen.getByLabelText('Sexta-feira fim 1')).toHaveValue('12:00');
  expect(screen.getByRole('checkbox', { name: 'Atende Sábado' })).not.toBeChecked();
  for (let i = 0; i < 3; i++) await user.click(screen.getByRole('button', { name: 'Adicionar intervalo em Segunda-feira' }));
  expect(screen.getByLabelText('Segunda-feira início 4')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Adicionar intervalo em Segunda-feira' })).not.toBeInTheDocument();
});
it('preserva o rascunho quando a API recusa uma mudança e só recarrega por escolha explícita', async () => {
  mocked.mockRejectedValue(new ApiError('Existe atendimento confirmado neste horário.', 409));
  const { onReload, onSaved } = editor(); const user = userEvent.setup();
  await user.clear(screen.getByLabelText('Segunda-feira fim 1')); await user.type(screen.getByLabelText('Segunda-feira fim 1'), '09:15');
  await user.click(screen.getByRole('button', { name: 'Salvar cadastro e horários' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('atendimento confirmado');
  expect(screen.getByLabelText('Segunda-feira fim 1')).toHaveValue('09:15');
  expect(onSaved).not.toHaveBeenCalled(); expect(onReload).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: 'Descartar rascunho e recarregar dados' })); expect(onReload).toHaveBeenCalledOnce();
});
it('mantém o rascunho e impede salvar sem conexão', async () => {
  editor(false); const user = userEvent.setup(); await user.type(screen.getByLabelText('Especialidade'), ' e barba');
  expect(screen.getByRole('button', { name: 'Salvar cadastro e horários' })).toBeDisabled();
  expect(mocked).not.toHaveBeenCalled();
});
it('bloqueia a troca de profissional enquanto há alterações e permite descartá-las', async () => {
  mocked.mockResolvedValue([person, { ...person, id: 'p2', name: 'Bruno Exemplo' }]);
  render(<ProfessionalsPanel services={catalog.services} online zoneId="America/Sao_Paulo" onUpdated={vi.fn()} onDirty={vi.fn()} />);
  const user = userEvent.setup(); await screen.findByLabelText('Nome do profissional');
  await user.type(screen.getByLabelText('Especialidade'), ' extra');
  expect(screen.getByRole('button', { name: /Bruno Exemplo/ })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Cadastrar profissional' })).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Descartar alterações' }));
  expect(screen.getByLabelText('Especialidade')).toHaveValue('Cortes');
  await user.click(screen.getByRole('button', { name: /Bruno Exemplo/ }));
  expect(screen.getByLabelText('Nome do profissional')).toHaveValue('Bruno Exemplo');
});
it('cadastra e seleciona um profissional sem expediente até o responsável configurá-lo', async () => {
  const created = { ...person, id: 'p3', name: 'Carla Exemplo', version: 0, hours: [], serviceIds: [] };
  mocked.mockImplementation(async (_path, options) => options?.method === 'POST' ? created : [person]);
  const updated = vi.fn();
  render(<ProfessionalsPanel services={catalog.services} online zoneId="America/Sao_Paulo" onUpdated={updated} onDirty={vi.fn()} />);
  const user = userEvent.setup(); await screen.findByLabelText('Nome do profissional');
  await user.click(screen.getByRole('button', { name: 'Cadastrar profissional' }));
  const dialog = within(screen.getByRole('dialog'));
  await user.type(dialog.getByLabelText('Nome do novo profissional'), 'Carla Exemplo');
  await user.type(dialog.getByLabelText('Especialidade do novo profissional'), 'Cortes');
  await user.click(dialog.getByRole('button', { name: 'Cadastrar e configurar horários' }));
  await waitFor(() => expect(screen.getByLabelText('Nome do profissional')).toHaveValue('Carla Exemplo'));
  expect(screen.getAllByRole('checkbox').filter(item => (item as HTMLInputElement).checked)).toHaveLength(1);
  expect(updated).toHaveBeenCalledOnce();
});

it('salva os serviços junto com o expediente e avisa ao retirar todos', async () => {
  mocked.mockResolvedValue({ ...person, version: 4, serviceIds: [] });
  editor(); const user = userEvent.setup();
  await user.click(screen.getByRole('checkbox', { name: 'Atende o serviço Corte' }));
  expect(screen.getByText(/Sem serviços selecionados/)).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Salvar cadastro e horários' }));
  expect(JSON.parse(mocked.mock.calls[0][1]?.body as string)).toMatchObject({ serviceIds: [], version: 3, active: true });
});
it('mantém a seleção em rascunho se uma reserva impedir retirar o serviço e permite descartar', async () => {
  mocked.mockRejectedValue(new ApiError('Há uma reserva para este serviço.', 409));
  editor(); const user = userEvent.setup();
  await user.click(screen.getByRole('checkbox', { name: 'Atende o serviço Corte' }));
  await user.click(screen.getByRole('button', { name: 'Salvar cadastro e horários' }));
  await screen.findByText('Há uma reserva para este serviço.');
  expect(screen.getByRole('checkbox', { name: 'Atende o serviço Corte' })).not.toBeChecked();
  await user.click(screen.getByRole('button', { name: 'Descartar alterações' }));
  expect(screen.getByRole('checkbox', { name: 'Atende o serviço Corte' })).toBeChecked();
});

it('aguarda o catálogo sem informar incorretamente que a empresa não tem serviços', async () => {
  mocked.mockResolvedValue([person]);
  render(<ProfessionalsPanel services={null} online zoneId="America/Sao_Paulo" onUpdated={vi.fn()} onDirty={vi.fn()} />);
  expect(await screen.findByText('Carregando serviços da equipe…')).toBeInTheDocument();
  expect(screen.queryByText(/Cadastre um serviço na seção/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Salvar cadastro e horários' })).not.toBeInTheDocument();
});

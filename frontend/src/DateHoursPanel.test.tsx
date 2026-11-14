import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import DateHoursPanel, { DateHoursEditor } from './DateHoursPanel';
import { api, ApiError } from './api';
import type { DateCalendar, ProfessionalConfiguration } from './types';
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const calendar: DateCalendar = {
  version: 7, firstDate: '2030-06-03', lastDate: '2031-06-03', exceptions: [],
  weeklyHours: [{ weekday: 1, opensAt: '09:00:00', closesAt: '12:00:00' }],
};
const person: ProfessionalConfiguration = { id: 'p1', name: 'Ana Exemplo', specialty: 'Cortes', serviceIds: ['service'], active: true, version: 7, hours: calendar.weeklyHours };
beforeEach(() => {
  mocked.mockReset();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});
function mount(online = true) {
  const onDirty = vi.fn(), onUpdated = vi.fn();
  render(<DateHoursEditor professionalId="p1" online={online} onDirty={onDirty} onUpdated={onUpdated} />);
  return { onDirty, onUpdated };
}

it('salva uma folga com a data escolhida e versão atual sem alterar a semana', async () => {
  mocked.mockImplementation(async (_path, options) => options?.method === 'PUT'
    ? { ...calendar, version: 8, exceptions: [{ date: '2030-06-04', reason: 'Feriado', hours: [] }] } : calendar);
  const { onDirty, onUpdated } = mount();
  fireEvent.change(await screen.findByLabelText('Data da exceção'), { target: { value: '2030-06-04' } });
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Motivo da exceção'), 'Feriado');
  expect(screen.getByLabelText('Data da exceção')).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Salvar exceção' }));
  const write = mocked.mock.calls.find(([, options]) => options?.method === 'PUT')!;
  expect(write[0]).toBe('/api/admin/professionals/p1/exceptions/2030-06-04');
  expect(JSON.parse(write[1]?.body as string)).toEqual({ version: 7, reason: 'Feriado', hours: [] });
  await screen.findByText('Exceção cadastrada');
  expect(onUpdated).toHaveBeenCalledOnce();
  expect(onDirty).toHaveBeenCalledWith(true);
  await waitFor(() => expect(onDirty).toHaveBeenLastCalledWith(false));
});

it('permite abrir um domingo e enviar apenas os intervalos especiais', async () => {
  mocked.mockImplementation(async (_path, options) => options?.method === 'PUT'
    ? { ...calendar, version: 8, exceptions: [{ date: '2030-06-09', reason: 'Atendimento especial', hours: [{ opensAt: '19:00', closesAt: '20:00' }] }] } : calendar);
  mount();
  fireEvent.change(await screen.findByLabelText('Data da exceção'), { target: { value: '2030-06-09' } });
  const user = userEvent.setup();
  await user.click(screen.getByRole('checkbox', { name: 'Não atender nesta data' }));
  fireEvent.change(screen.getByLabelText('Exceção início 1'), { target: { value: '19:00' } });
  fireEvent.change(screen.getByLabelText('Exceção fim 1'), { target: { value: '20:00' } });
  await user.type(screen.getByLabelText('Motivo da exceção'), 'Atendimento especial');
  await user.click(screen.getByRole('button', { name: 'Salvar exceção' }));
  await screen.findByText('Exceção cadastrada');
  const write = mocked.mock.calls.find(([, options]) => options?.method === 'PUT')!;
  expect(JSON.parse(write[1]?.body as string).hours).toEqual([{ opensAt: '19:00', closesAt: '20:00' }]);
});

it('preserva o rascunho recusado e só recarrega após descarte explícito', async () => {
  let read = 0;
  mocked.mockImplementation(async (_path, options) => {
    if (options?.method === 'PUT') throw new ApiError('Existe atendimento confirmado.', 409);
    return { ...calendar, version: ++read === 1 ? 7 : 9 };
  });
  const { onUpdated } = mount(); const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Motivo da exceção'), 'Folga planejada');
  await user.click(screen.getByRole('button', { name: 'Salvar exceção' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('atendimento confirmado');
  expect(screen.getByLabelText('Motivo da exceção')).toHaveValue('Folga planejada');
  expect(read).toBe(1); expect(onUpdated).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: 'Descartar rascunho e recarregar exceções' }));
  await waitFor(() => expect(screen.getByLabelText('Motivo da exceção')).toHaveValue(''));
  expect(read).toBe(2);
});

it('mantém o formulário editável e impede gravação sem conexão', async () => {
  mocked.mockResolvedValue(calendar); mount(false);
  await userEvent.setup().type(await screen.findByLabelText('Motivo da exceção'), 'Folga');
  expect(screen.getByRole('button', { name: 'Salvar exceção' })).toBeDisabled();
  expect(screen.getByLabelText('Motivo da exceção')).toHaveValue('Folga');
  expect(mocked.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(false);
});

it('pede confirmação antes de remover e usa a versão atual na exclusão', async () => {
  const configured = { ...calendar, exceptions: [{ date: calendar.firstDate, reason: 'Folga', hours: [] }] };
  mocked.mockImplementation(async (_path, options) => options?.method === 'DELETE' ? { ...calendar, version: 8 } : configured);
  const { onUpdated } = mount(); const user = userEvent.setup();
  const remove = await screen.findByRole('button', { name: 'Remover exceção e usar expediente semanal' });
  await user.click(remove);
  await user.click(screen.getByRole('button', { name: 'Manter exceção' }));
  expect(mocked.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false);
  await user.click(remove);
  await user.click(screen.getByRole('button', { name: 'Confirmar remoção da exceção' }));
  await screen.findByText('Esta data segue o expediente semanal');
  expect(mocked).toHaveBeenCalledWith('/api/admin/professionals/p1/exceptions/2030-06-03?version=7', { method: 'DELETE' });
  expect(onUpdated).toHaveBeenCalledOnce();
});

it('bloqueia a troca de profissional enquanto há rascunho e permite descartá-lo', async () => {
  mocked.mockImplementation(async path => path === '/api/admin/professionals' ? [person, { ...person, id: 'p2', name: 'Bruno Exemplo' }] : calendar);
  render(<DateHoursPanel online zoneId="America/Sao_Paulo" onDirty={vi.fn()} onUpdated={vi.fn()} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Motivo da exceção'), 'Folga');
  expect(screen.getByLabelText('Profissional da exceção')).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Descartar alterações da exceção' }));
  await user.selectOptions(screen.getByLabelText('Profissional da exceção'), 'p2');
  await waitFor(() => expect(mocked.mock.calls.some(([path]) => path === '/api/admin/professionals/p2/exceptions')).toBe(true));
});

it('recupera falha de carregamento sem informar falsamente que não existem exceções', async () => {
  mocked.mockRejectedValueOnce(new ApiError('Servidor indisponível.', 503)).mockResolvedValue(calendar);
  mount(); const user = userEvent.setup();
  expect(await screen.findByRole('alert')).toHaveTextContent('Servidor indisponível.');
  expect(screen.queryByText(/Nenhuma exceção cadastrada/)).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
  expect(await screen.findByLabelText('Data da exceção')).toHaveValue(calendar.firstDate);
});

it('limita a quatro intervalos e preserva ao menos um quando o dia está aberto', async () => {
  mocked.mockResolvedValue(calendar); mount(); const user = userEvent.setup();
  await user.click(await screen.findByRole('checkbox', { name: 'Não atender nesta data' }));
  expect(screen.getByRole('button', { name: 'Remover intervalo da exceção 1' })).toBeDisabled();
  for (let i = 0; i < 3; i++) await user.click(screen.getByRole('button', { name: 'Adicionar intervalo da exceção' }));
  expect(screen.getByRole('button', { name: 'Adicionar intervalo da exceção' })).toBeDisabled();
  expect(screen.getByLabelText('Exceção início 4')).toBeInTheDocument();
});

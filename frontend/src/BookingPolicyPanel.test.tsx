import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach,expect,it,vi} from 'vitest';
import BookingPolicyPanel from './BookingPolicyPanel';
import {api,ApiError} from './api';
import {noPolicy} from './BookingPolicySummary';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const mocked=vi.mocked(api),endpoint='/api/admin/business/booking-policy';
beforeEach(()=>{mocked.mockReset();});
function mount(online=true){const dirty=vi.fn(),updated=vi.fn(),unauthorized=vi.fn();render(<BookingPolicyPanel online={online} onDirty={dirty} onUpdated={updated} onUnauthorized={unauthorized}/>);return{dirty,updated,unauthorized};}
it('carrega regras da empresa com campos independentes e explica as condições preservadas',async()=>{
  mocked.mockResolvedValue(noPolicy);mount();
  expect(await screen.findByLabelText('Antecedência mínima para reservar')).toHaveValue('0');
  expect(screen.getByLabelText('Antecedência para cancelar pelo cliente')).toHaveValue('0');
  expect(screen.getByRole('button',{name:'Salvar regras de agendamento'})).toBeDisabled();
  expect(screen.getByText(/não restringem reservas já confirmadas/)).toBeVisible();
  expect(mocked.mock.calls[0][0]).toBe(endpoint);
});
it('salva os três valores e a versão atual sem enviar empresa escolhida pelo navegador',async()=>{
  const saved={bookingNoticeMinutes:120,cancellationNoticeMinutes:1440,rescheduleNoticeMinutes:720,version:4};
  mocked.mockImplementation(async(_path,options)=>options?.method==='PUT'?saved:{...noPolicy,version:3});
  const{dirty,updated}=mount();const user=userEvent.setup();
  await user.selectOptions(await screen.findByLabelText('Antecedência mínima para reservar'),'120');
  await user.selectOptions(screen.getByLabelText('Antecedência para cancelar pelo cliente'),'1440');
  await user.selectOptions(screen.getByLabelText('Antecedência para remarcar pelo cliente'),'720');
  expect(dirty).toHaveBeenCalledWith(true);
  await user.click(screen.getByRole('button',{name:'Salvar regras de agendamento'}));
  await screen.findByText('Regras salvas. Valem para novas reservas; as já confirmadas mantêm suas condições.');
  expect(JSON.parse(mocked.mock.calls.find(([,o])=>o?.method==='PUT')![1]!.body as string)).toEqual({...saved,version:3});
  expect(updated).toHaveBeenCalledOnce();expect(screen.getByRole('button',{name:'Salvar regras de agendamento'})).toBeDisabled();
});
it('preserva o rascunho após conflito e só descarta ao recarregar explicitamente',async()=>{
  let loaded=0;mocked.mockImplementation(async(_path,options)=>{if(options?.method==='PUT')throw new ApiError('As regras mudaram em outra janela.',409);return ++loaded===1?noPolicy:{...noPolicy,bookingNoticeMinutes:30,version:1};});
  mount();const user=userEvent.setup();await user.selectOptions(await screen.findByLabelText('Antecedência mínima para reservar'),'120');
  await user.click(screen.getByRole('button',{name:'Salvar regras de agendamento'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('As regras mudaram');
  expect(screen.getByLabelText('Antecedência mínima para reservar')).toHaveValue('120');
  await user.click(screen.getByRole('button',{name:'Recarregar regras e descartar rascunho'}));
  await waitFor(()=>expect(screen.getByLabelText('Antecedência mínima para reservar')).toHaveValue('30'));
  expect(mocked.mock.calls.filter(([,o])=>o?.method==='PUT')).toHaveLength(1);
});
it('permite descartar edição sem gravar no servidor',async()=>{
  mocked.mockResolvedValue(noPolicy);const{dirty}=mount();const user=userEvent.setup();
  await user.selectOptions(await screen.findByLabelText('Antecedência mínima para reservar'),'60');
  await user.click(screen.getByRole('button',{name:'Descartar alterações'}));
  expect(screen.getByLabelText('Antecedência mínima para reservar')).toHaveValue('0');
  expect(dirty).toHaveBeenLastCalledWith(false);expect(mocked.mock.calls.filter(([,o])=>o?.method==='PUT')).toHaveLength(0);
});
it('preserva valor personalizado vindo da API e impede gravação sem conexão',async()=>{
  mocked.mockResolvedValue({...noPolicy,bookingNoticeMinutes:37});mount(false);
  const select=await screen.findByLabelText('Antecedência mínima para reservar');
  expect(select).toHaveValue('37');expect(select).toBeDisabled();expect(screen.getByRole('button',{name:'Salvar regras de agendamento'})).toBeDisabled();
});
it('informa sessão expirada sem guardar uma configuração falsa',async()=>{
  mocked.mockRejectedValue(new ApiError('Entre novamente.',401));const{unauthorized}=mount();
  expect(await screen.findByRole('alert')).toHaveTextContent('Entre novamente.');expect(unauthorized).toHaveBeenCalledOnce();
  expect(screen.queryByRole('button',{name:'Salvar regras de agendamento'})).not.toBeInTheDocument();
});
it('protege a saída enquanto há rascunho',async()=>{
  mocked.mockResolvedValue(noPolicy);mount();const user=userEvent.setup();
  await user.selectOptions(await screen.findByLabelText('Antecedência mínima para reservar'),'60');
  const event=new Event('beforeunload',{cancelable:true});window.dispatchEvent(event);expect(event.defaultPrevented).toBe(true);
});

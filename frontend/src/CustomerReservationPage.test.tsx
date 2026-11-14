import {render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import CustomerReservationPage from './CustomerReservationPage';
import type {CustomerReservation} from './CustomerReservationPage';
import {api,ApiError} from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const mocked=vi.mocked(api), token='a'.repeat(43), id='11111111-1111-1111-1111-111111111111';
export const sample:CustomerReservation={reservationKey:id,business:{name:'Empresa teste',slug:'teste',address:'Online',zoneId:'America/Sao_Paulo'},reference:'PUBLICO123',serviceName:'Corte',professionalName:'Ana',customerName:'Cliente fictício',startsAt:'2030-06-04T12:00:00Z',endsAt:'2030-06-04T12:45:00Z',priceCents:5500,status:'BOOKED',version:3,canChange:true,accessValidUntil:'2030-06-11T12:45:00Z',sessionExpiresAt:'2030-06-03T11:00:00Z',suggestedDate:'2030-06-04',horizonDays:60};
const path='/api/customer/reservations/'+id;
beforeEach(()=>{vi.setSystemTime(new Date('2030-06-03T10:00:00Z'));mocked.mockReset();HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};});
afterEach(()=>vi.useRealTimers());
function mount(link=token,key=''){const opened=vi.fn(),blocked=vi.fn();render(<CustomerReservationPage token={link} reservationKey={key} onOpened={opened} online onNavigationBlocked={blocked}/>);return {opened,blocked};}
it('troca o link por sessão via POST e mostra somente o atendimento retornado',async()=>{
  mocked.mockResolvedValue(sample);const {opened}=mount();
  await screen.findByRole('heading',{name:'Empresa teste'});
  expect(mocked.mock.calls[0][0]).toBe('/api/customer/session');
  expect(mocked.mock.calls[0][1]?.method).toBe('POST');
  expect(JSON.parse(mocked.mock.calls[0][1]?.body as string)).toEqual({token});
  expect(opened).toHaveBeenCalledWith(id);
  expect(screen.getByText('Cliente fictício')).toBeInTheDocument();
  expect(screen.getByLabelText('Link privado da reserva')).toHaveValue(window.location.origin+'/#/minha-reserva?token='+token);
});
it('retoma pelo identificador sem usar o token na URL da API',async()=>{
  mocked.mockResolvedValue(sample);mount('',id);
  await screen.findByRole('heading',{name:'Empresa teste'});
  expect(mocked.mock.calls[0][0]).toBe(path);expect(mocked.mock.calls[0][1]?.method).toBeUndefined();
  expect(screen.queryByLabelText('Link privado da reserva')).not.toBeInTheDocument();
});
it('não aceita código público como acesso privado e não consulta uma reserva por nome ou telefone',async()=>{
  mount('','');const user=userEvent.setup();
  await user.type(await screen.findByLabelText('Link ou código privado'),'PUBLICO123');
  await user.click(screen.getByRole('button',{name:'Acessar minha reserva'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('código público');
  expect(mocked).not.toHaveBeenCalled();
});
it('exige confirmação e envia a versão da reserva ao cancelar',async()=>{
  mocked.mockImplementation(async (url,options)=>url.endsWith('/cancel')&&options?.method==='POST'?{...sample,status:'CANCELLED',version:4,canChange:false}:sample);
  mount();await screen.findByRole('heading',{name:'Empresa teste'});const user=userEvent.setup();
  await user.click(screen.getByRole('button',{name:'Cancelar minha reserva'}));
  expect(mocked.mock.calls.filter(([url])=>url.endsWith('/cancel'))).toHaveLength(0);
  await user.click(screen.getByRole('button',{name:'Sim, cancelar minha reserva'}));
  await screen.findByText('Reserva cancelada. O horário foi liberado.');
  const call=mocked.mock.calls.find(([url])=>url===path+'/cancel');
  expect(JSON.parse(call![1]?.body as string)).toEqual({version:3});
  expect(screen.queryByRole('button',{name:'Remarcar minha reserva'})).not.toBeInTheDocument();
});
it('consulta horários somente para a reserva atual e preserva preço e duração na tela',async()=>{
  const slot={startsAt:'2030-06-04T13:00:00Z',endsAt:'2030-06-04T13:45:00Z'};
  mocked.mockImplementation(async url=>url.includes('/availability?')?{slots:[slot]}:url.endsWith('/reschedule')?{...sample,...slot,version:4}:sample);
  mount();await screen.findByRole('heading',{name:'Empresa teste'});const user=userEvent.setup();
  await user.click(screen.getByRole('button',{name:'Remarcar minha reserva'}));
  await user.click(await screen.findByRole('button',{name:'10:00'}));
  await user.click(screen.getByRole('button',{name:'Confirmar novo horário'}));
  await screen.findByText('Reserva remarcada. Confira o novo horário abaixo.');
  const call=mocked.mock.calls.find(([url])=>url.endsWith('/reschedule'));
  expect(call![0]).toBe(path+'/reschedule');
  expect(JSON.parse(call![1]?.body as string)).toEqual({version:3,startsAt:slot.startsAt});
  expect(screen.getByText('R$ 55,00')).toBeInTheDocument();
});
it('recusa confirmar novamente um horário depois de conflito e permite atualizar os dados',async()=>{
  mocked.mockImplementation(async url=>url.includes('/availability?')?{slots:[{startsAt:'2030-06-04T13:00:00Z',endsAt:'2030-06-04T13:45:00Z'}]}:url.endsWith('/reschedule')?Promise.reject(new ApiError('A reserva foi alterada em outra janela.',409)):sample);
  mount();await screen.findByRole('heading',{name:'Empresa teste'});const user=userEvent.setup();
  await user.click(screen.getByRole('button',{name:'Remarcar minha reserva'}));await user.click(await screen.findByRole('button',{name:'10:00'}));
  await user.click(screen.getByRole('button',{name:'Confirmar novo horário'}));
  await screen.findByText('A reserva foi alterada em outra janela.');
  expect(screen.getByRole('button',{name:'Confirmar novo horário'})).toBeDisabled();
  await user.click(screen.getByRole('button',{name:'Atualizar dados da reserva'}));
  await waitFor(()=>expect(mocked.mock.calls.filter(([url])=>url==='/api/customer/session')).toHaveLength(2));
  expect(mocked.mock.calls.filter(([url])=>url.endsWith('/reschedule'))).toHaveLength(1);
});
it('encerra somente a sessão da reserva exibida e remove o link da tela',async()=>{
  mocked.mockImplementation(async (_url,options)=>options?.method==='DELETE'?undefined:sample);
  const {opened}=mount();await screen.findByRole('heading',{name:'Empresa teste'});const user=userEvent.setup();
  await user.click(screen.getByRole('button',{name:'Encerrar acesso neste navegador'}));
  await screen.findByText(/Acesso encerrado neste navegador/);
  expect(mocked.mock.calls.some(([url,options])=>url===path+'/session'&&options?.method==='DELETE')).toBe(true);
  expect(opened).toHaveBeenCalledWith('');
  expect(screen.queryByLabelText('Link privado da reserva')).not.toBeInTheDocument();
});

it('apresenta cada prazo e oculta somente a ação encerrada',async()=>{
  mocked.mockResolvedValue({...sample,bookingPolicy:{bookingNoticeMinutes:120,cancellationNoticeMinutes:1440,rescheduleNoticeMinutes:720,version:1},canCancel:false,canReschedule:true});
  mount();await screen.findByRole('heading',{name:'Empresa teste'});
  expect(screen.queryByRole('button',{name:'Cancelar minha reserva'})).not.toBeInTheDocument();
  expect(screen.getByRole('button',{name:'Remarcar minha reserva'})).toBeVisible();
  expect(screen.getByText('24 horas antes')).toBeVisible();expect(screen.getByText('12 horas antes')).toBeVisible();
});
it('continua permitindo consulta quando os dois prazos terminaram',async()=>{
  mocked.mockResolvedValue({...sample,canCancel:false,canReschedule:false});
  mount();await screen.findByRole('heading',{name:'Empresa teste'});
  expect(screen.getByText('Os prazos online terminaram. Fale com o estabelecimento para solicitar uma alteração.')).toBeVisible();
  expect(screen.queryByRole('button',{name:'Remarcar minha reserva'})).not.toBeInTheDocument();
  expect(screen.getByText('Cliente fictício')).toBeVisible();
});

it.each([['COMPLETED','Atendimento concluído'],['NO_SHOW','Não compareceu']] as const)('apresenta o resultado %s sem permitir alterações',async(status,label)=>{
 mocked.mockResolvedValue({...sample,status,canChange:false,canCancel:false,canReschedule:false});mount();await screen.findByText(label);expect(screen.queryByRole('button',{name:'Cancelar minha reserva'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'Remarcar minha reserva'})).not.toBeInTheDocument();
});

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ShieldCheck } from 'lucide-react';
import { api, ApiError, errorMessage } from './api';
import { Message, Modal } from './components';
import { DatePicker, TimeSlots } from './availability';
import { dateInZone, dayLabel, money, plusDays, time, todayInZone } from './format';
import { PrivateReservationLink, reservationToken } from './customerAccess';
import type { Availability, Slot, BookingPolicy } from './types';
import BookingPolicySummary from './BookingPolicySummary';
import './customer.css';

export interface CustomerReservation {
  reservationKey: string;
  business: { name: string; slug: string; address: string; zoneId: string };
  reference: string; serviceName: string; professionalName: string; customerName: string;
  startsAt: string; endsAt: string; priceCents: number; status: 'BOOKED' | 'CANCELLED'; version: number; canChange: boolean;
  accessValidUntil: string; sessionExpiresAt: string; suggestedDate: string; horizonDays: number;
  bookingPolicy?:BookingPolicy; canCancel?:boolean; canReschedule?:boolean; cancelUntil?:string; rescheduleUntil?:string;
}
export default function CustomerReservationPage({ token, reservationKey, onOpened, online, onNavigationBlocked }: { token: string; reservationKey: string; onOpened: (key:string)=>void; online: boolean; onNavigationBlocked: (blocked: boolean)=>void }) {
  const [reservation,setReservation]=useState<CustomerReservation|null>(null);
  const [entry,setEntry]=useState(''); const [error,setError]=useState(''); const [success,setSuccess]=useState('');
  const [loading,setLoading]=useState(true); const [pending,setPending]=useState(false);
  const [privateLink,setPrivateLink]=useState<{key:string;token:string}|null>(null);
  const [retry,setRetry]=useState(0); const [move,setMove]=useState(false); const [cancel,setCancel]=useState(false);
  const [date,setDate]=useState(''); const [slot,setSlot]=useState<Slot|null>(null);
  const [availability,setAvailability]=useState<{ slots: Slot[]; loading: boolean; error: string }>({ slots: [],loading:false,error:'' });
  useEffect(()=>{
    const controller=new AbortController();setLoading(true);setReservation(null);setError('');
    const valid=reservationToken(token);
    if(token && !valid){setError('Confira seu link privado. O código público da reserva não permite acessar o atendimento.');setLoading(false);return;}
    if(!valid && !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(reservationKey)){setLoading(false);return;}
    api<CustomerReservation>(valid?'/api/customer/session':'/api/customer/reservations/'+encodeURIComponent(reservationKey),valid
      ? {method:'POST',body:JSON.stringify({token:valid}),signal:controller.signal}:{signal:controller.signal})
      .then(result=>{if(!controller.signal.aborted){setReservation(result);setDate(result.suggestedDate);if(valid){setPrivateLink({key:result.reservationKey,token:valid});onOpened(result.reservationKey);}}})
      .catch(error=>{if(!controller.signal.aborted){if(!(error instanceof ApiError && error.status===401 && !token && !reservationKey))setError(errorMessage(error));}})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return()=>controller.abort();
  },[token,reservationKey,retry]);
  useEffect(()=>{
    onNavigationBlocked(pending);const protect=(event:BeforeUnloadEvent)=>{if(pending)event.preventDefault();};
    window.addEventListener('beforeunload',protect);
    return()=>{onNavigationBlocked(false);window.removeEventListener('beforeunload',protect);};
  },[pending,onNavigationBlocked]);
  useEffect(()=>{
    setSlot(null);if(!move||!date||!reservation){setAvailability({slots:[],loading:false,error:''});return;}
    const controller=new AbortController();setAvailability({slots:[],loading:true,error:''});
    api<Availability>('/api/customer/reservations/'+reservation.reservationKey+'/availability?'+new URLSearchParams({date}),{signal:controller.signal})
      .then(result=>{if(!controller.signal.aborted)setAvailability({slots:result.slots,loading:false,error:''});})
      .catch(error=>{if(!controller.signal.aborted)setAvailability({slots:[],loading:false,error:errorMessage(error)});});
    return()=>controller.abort();
  },[move,date,retry,reservation?.reservationKey]);
  async function enter(event:FormEvent) {
    event.preventDefault();if(pending||!online)return;
    const key=reservationToken(entry);
    if(!key){setError('Cole o link privado completo ou seu código privado de acesso. O código público da reserva não é suficiente.');return;}
    setPending(true);setError('');setSuccess('');
    try {const result=await api<CustomerReservation>('/api/customer/session',{method:'POST',body:JSON.stringify({token:key})});setReservation(result);setDate(result.suggestedDate);setPrivateLink({key:result.reservationKey,token:key});setEntry('');onOpened(result.reservationKey);}
    catch(error){setError(errorMessage(error));}finally{setPending(false);}
  }
  async function change(action:'cancel'|'move') {
    if(!reservation||pending||!online||(action==='move'&&!slot))return;
    setPending(true);setError('');setSuccess('');
    try {
      const result=await api<CustomerReservation>('/api/customer/reservations/'+reservation.reservationKey+'/'+(action==='cancel'?'cancel':'reschedule'),{
        method:action==='cancel'?'POST':'PATCH',body:JSON.stringify({version:reservation.version,...(action==='move'?{startsAt:slot!.startsAt}:{})})});
      setReservation(result);setDate(result.suggestedDate);setMove(false);setCancel(false);setSlot(null);
      setSuccess(action==='cancel'?'Reserva cancelada. O horário foi liberado.':'Reserva remarcada. Confira o novo horário abaixo.');
    }catch(error){setError(errorMessage(error));setSlot(null);if(error instanceof ApiError && error.status===401){setReservation(null);setMove(false);setCancel(false);}}
    finally{setPending(false);}
  }
  async function close() {
    if(pending)return;setPending(true);setError('');
    try{await api('/api/customer/reservations/'+reservation!.reservationKey+'/session',{method:'DELETE'});setReservation(null);setPrivateLink(null);onOpened('');setMove(false);setCancel(false);setSuccess('Acesso encerrado neste navegador. Guarde seu link privado para abrir novamente.');}
    catch(error){setError(errorMessage(error));}finally{setPending(false);}
  }
  const zone=reservation?.business.zoneId??'America/Sao_Paulo';
  return <section className="customer-portal"><span className="eyebrow"><ShieldCheck size={15}/> ACESSO PRIVADO DO CLIENTE</span><h1>Minha reserva</h1>
    <p>Consulte seu atendimento e organize um novo horário quando precisar.</p>
    {success&&<Message kind="success">{success}</Message>}
    {error&&!cancel&&<><Message>{error}</Message>{reservation&&<button className="button secondary" disabled={pending||!online} onClick={()=>{setCancel(false);setMove(false);setRetry(value=>value+1);}}>Atualizar dados da reserva</button>}</>}
    {loading?<p role="status">Abrindo sua reserva…</p>:!reservation?<div className="agenda-panel"><h2>Abra seu link privado</h2><p>Use o link recebido no comprovante ou fornecido pelo estabelecimento. Nome, telefone e código público não substituem esse acesso.</p>
      <form className="stack-form" onSubmit={event=>void enter(event)}><label>Link ou código privado<input required autoComplete="off" value={entry} maxLength={2048} onChange={event=>setEntry(event.target.value)} /></label><button className="button primary" disabled={pending||!online}>{pending?'Abrindo…':'Acessar minha reserva'}</button></form>
    </div>:<>
      <div className="agenda-panel customer-details"><span className={'status-badge '+(reservation.status==='BOOKED'?'booked':'')}>{reservation.status==='BOOKED'?'Confirmada':'Cancelada'}</span>
        <h2>{reservation.business.name}</h2><p>{reservation.business.address}</p>
        <dl><dt>Cliente</dt><dd>{reservation.customerName}</dd><dt>Serviço</dt><dd>{reservation.serviceName}</dd><dt>Profissional</dt><dd>{reservation.professionalName}</dd><dt>Data</dt><dd>{dayLabel(dateInZone(reservation.startsAt,zone))}</dd><dt>Horário</dt><dd>{time(reservation.startsAt,zone)} – {time(reservation.endsAt,zone)}</dd><dt>Valor</dt><dd>{money(reservation.priceCents)}</dd><dt>Código público</dt><dd>{reservation.reference}</dd></dl>
        <p className="field-note">Horários no fuso {zone}. Preço, duração, serviço e profissional são preservados na remarcação.</p>
        <div className="customer-actions">{(reservation.canReschedule??reservation.canChange)&&<button className="button primary" disabled={pending||!online} onClick={()=>{setMove(value=>!value);setError('');setSlot(null);}}>Remarcar minha reserva</button>}{(reservation.canCancel??reservation.canChange)&&<button className="button danger" disabled={pending||!online} onClick={()=>{setCancel(true);setError('');}}>Cancelar minha reserva</button>}<button className="button secondary" disabled={pending} onClick={()=>void close()}>Encerrar acesso neste navegador</button></div>
        <BookingPolicySummary policy={reservation.bookingPolicy} startsAt={reservation.startsAt} zone={zone} confirmed/>
        {reservation.canChange&&!(reservation.canCancel??true)&&!(reservation.canReschedule??true)&&<p className="field-note">Os prazos online terminaram. Fale com o estabelecimento para solicitar uma alteração.</p>}
        {!reservation.canChange&&<p className="field-note">Este atendimento está cancelado ou já começou. Para outras solicitações, fale com o estabelecimento.</p>}
        <p className="field-note">O link privado vale até {dayLabel(dateInZone(reservation.accessValidUntil,zone))}. O estabelecimento pode revogar o acesso antes.</p>
      </div>
      {move&&(reservation.canReschedule??reservation.canChange)&&<form className="agenda-panel customer-move" onSubmit={event=>{event.preventDefault();void change('move');}}><h2>Escolha o novo horário</h2><fieldset disabled={pending}><DatePicker date={date} onChange={setDate} today={todayInZone(zone)} max={plusDays(todayInZone(zone),reservation.horizonDays)}/><TimeSlots {...availability} selected={slot?.startsAt??''} onSelect={setSlot} zone={zone}/><BookingPolicySummary policy={reservation.bookingPolicy} startsAt={slot?.startsAt} zone={zone} confirmed/><div className="customer-actions"><button type="button" className="button secondary" onClick={()=>setMove(false)}>Manter horário atual</button><button className="button primary" disabled={!slot||pending||!online}>{pending?'Salvando…':'Confirmar novo horário'}</button></div></fieldset></form>}
      {privateLink?.key===reservation.reservationKey&&<div className="agenda-panel"><PrivateReservationLink token={privateLink.token}/></div>}
    </>}
    {cancel&&reservation&&<Modal title="Cancelar sua reserva?" busy={pending} onClose={()=>setCancel(false)}><p>Seu atendimento de {reservation.serviceName} será cancelado e o horário ficará disponível para outras pessoas.</p><div className="modal-actions"><button className="button secondary" disabled={pending} onClick={()=>setCancel(false)}>Manter minha reserva</button><button className="button danger" disabled={pending||!online} onClick={()=>void change('cancel')}>{pending?'Cancelando…':'Sim, cancelar minha reserva'}</button></div>{error&&<Message>{error}</Message>}</Modal>}
  </section>;
}

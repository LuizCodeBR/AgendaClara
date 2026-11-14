import type {BookingPolicy} from './types';
import {dateInZone,dayLabel,time} from './format';
import './policy.css';
export const noPolicy:BookingPolicy={bookingNoticeMinutes:0,cancellationNoticeMinutes:0,rescheduleNoticeMinutes:0,version:0};
export function noticeLabel(minutes:number):string {
  if(!minutes)return 'Sem antecedência adicional';
  const hours=Math.floor(minutes/60),rest=minutes%60;
  return [hours?hours+(hours===1?' hora':' horas'):'',rest?rest+(rest===1?' minuto':' minutos'):''].filter(Boolean).join(' e ');
}
export const deadlineLabel=(value:string,zone:string)=>dayLabel(dateInZone(value,zone))+' às '+time(value,zone);
export default function BookingPolicySummary({policy=noPolicy,startsAt,zone='America/Sao_Paulo',confirmed=false}:{policy?:BookingPolicy;startsAt?:string;zone?:string;confirmed?:boolean}){
  const deadline=(minutes:number)=>startsAt?deadlineLabel(new Date(new Date(startsAt).getTime()-minutes*60000).toISOString(),zone):'';
  return <div className="booking-policy-summary"><h3>{confirmed?'Regras desta reserva':'Regras do agendamento'}</h3><ul>
    <li>Antecedência para reservar: <strong>{noticeLabel(policy.bookingNoticeMinutes)}</strong>.</li>
    <li>Cancelamento pelo cliente: <strong>{policy.cancellationNoticeMinutes?noticeLabel(policy.cancellationNoticeMinutes)+' antes':'até antes de começar'}</strong>{startsAt&&<>. Prazo: {deadline(policy.cancellationNoticeMinutes)}</>}.</li>
    <li>Remarcação pelo cliente: <strong>{policy.rescheduleNoticeMinutes?noticeLabel(policy.rescheduleNoticeMinutes)+' antes':'até antes de começar'}</strong>{startsAt&&<>. Prazo: {deadline(policy.rescheduleNoticeMinutes)}</>}.</li>
  </ul><p className="field-note">{confirmed?'As regras registradas na confirmação são preservadas.':'As regras vigentes serão registradas quando a reserva for confirmada.'} A remarcação usa a mesma antecedência mínima e recalcula os prazos pelo novo horário. Horários e prazos são conferidos ao confirmar.</p></div>;
}

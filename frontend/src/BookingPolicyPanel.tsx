import {useEffect,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message} from './components';
import type {BookingPolicy} from './types';
import {noPolicy,noticeLabel} from './BookingPolicySummary';
import './policy.css';
const presets=[0,15,30,60,120,240,720,1440,2880,4320,10080];
const fields=[['bookingNoticeMinutes','Antecedência mínima para reservar'],['cancellationNoticeMinutes','Antecedência para cancelar pelo cliente'],['rescheduleNoticeMinutes','Antecedência para remarcar pelo cliente']] as const;
export default function BookingPolicyPanel({online,onDirty,onUpdated,onUnauthorized}:{online:boolean;onDirty:(dirty:boolean)=>void;onUpdated:()=>void;onUnauthorized:()=>void}){
  const [saved,setSaved]=useState<BookingPolicy|null>(null),[draft,setDraft]=useState(noPolicy);
  const [loading,setLoading]=useState(true),[pending,setPending]=useState(false),[error,setError]=useState(''),[success,setSuccess]=useState(''),[retry,setRetry]=useState(0);
  const dirty=!!saved&&fields.some(([key])=>draft[key]!==saved[key]);
  useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');
    api<BookingPolicy>('/api/admin/business/booking-policy',{signal:controller.signal}).then(result=>{if(!controller.signal.aborted){setSaved(result);setDraft(result);}})
      .catch(error=>{if(!controller.signal.aborted){setError(errorMessage(error));if(error instanceof ApiError&&error.status===401)onUnauthorized();}})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();
  },[retry]);
  useEffect(()=>{onDirty(dirty||pending);const protect=(event:BeforeUnloadEvent)=>{if(dirty||pending)event.preventDefault();};window.addEventListener('beforeunload',protect);return()=>{onDirty(false);window.removeEventListener('beforeunload',protect);};},[dirty,pending,onDirty]);
  async function save(){if(!saved||!dirty||pending||!online)return;setPending(true);setError('');setSuccess('');
    try{const result=await api<BookingPolicy>('/api/admin/business/booking-policy',{method:'PUT',body:JSON.stringify({...draft,version:saved.version})});setSaved(result);setDraft(result);setSuccess('Regras salvas. Valem para novas reservas; as já confirmadas mantêm suas condições.');onUpdated();}
    catch(error){setError(errorMessage(error));if(error instanceof ApiError&&error.status===401)onUnauthorized();}finally{setPending(false);}
  }
  return <section className="agenda-panel policy-panel"><span className="eyebrow">ORGANIZE OS PRAZOS</span><h2>Regras de agendamento</h2><p>Cada empresa tem suas próprias regras. Alterações valem para novos agendamentos e não restringem reservas já confirmadas.</p>
    <p className="field-note">Os prazos de cancelamento e remarcação são contados para trás a partir do início do atendimento. Zero permite a ação até antes de começar. O responsável pode ajustar atendimentos futuros no painel mesmo depois desses prazos.</p>
    {loading?<p role="status">Consultando as regras…</p>:saved&&<form onSubmit={event=>{event.preventDefault();void save();}}><fieldset disabled={pending||!online} className="policy-fields">{fields.map(([key,label])=><div key={key} className="policy-field"><label htmlFor={'policy-'+key}>{label}</label><select id={'policy-'+key} value={draft[key]} onChange={event=>{setDraft({...draft,[key]:Number(event.target.value)});setSuccess('');}}>{[...new Set([...presets,draft[key]])].sort((a,b)=>a-b).map(value=><option key={value} value={value}>{noticeLabel(value)}</option>)}</select></div>)}</fieldset>
    <p className="field-note">Se o prazo de cancelar ou remarcar for maior que a antecedência de uma nova reserva, o cliente poderá precisar falar com a empresa desde a confirmação. Os prazos são mostrados antes de confirmar.</p>
    <div className="policy-actions"><button className="button primary" disabled={!dirty||pending||!online}>{pending?'Salvando regras…':'Salvar regras de agendamento'}</button><button className="button secondary" type="button" disabled={!dirty||pending} onClick={()=>{setDraft(saved);setError('');}}>Descartar alterações</button></div></form>}
    {success&&<Message kind="success">{success}</Message>}{error&&<><Message>{error}</Message><button className="button secondary" disabled={pending||!online} onClick={()=>setRetry(value=>value+1)}>Recarregar regras e descartar rascunho</button></>}
  </section>;
}

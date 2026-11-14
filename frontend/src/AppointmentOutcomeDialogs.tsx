import {useEffect,useRef,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message,Modal} from './components';
import type {Appointment,AppointmentStatus} from './types';
import {statusLabel} from './appointmentStatus';
import './outcomes.css';

export interface HistoryEntry {id:string;version:number;action:string;fromStatus:AppointmentStatus|null;toStatus:AppointmentStatus;fromStart:string|null;fromEnd:string|null;toStart:string;toEnd:string;actorType:string;actorName:string;reason:string;occurredAt:string}
export interface HistoryPage {events:HistoryEntry[];nextBeforeVersion:number|null}
export function OutcomeDialog({appointment,initial,online,onClose,onSaved,onPending}:{appointment:Appointment;initial:'COMPLETED'|'NO_SHOW';online:boolean;onClose:()=>void;onSaved:()=>void;onPending:(pending:boolean)=>void}){
  const [status,setStatus]=useState(initial),[reason,setReason]=useState(''),[pending,setPending]=useState(false),[error,setError]=useState(''),[conflict,setConflict]=useState(false),[discard,setDiscard]=useState(false);
  const request=useRef({signature:'',key:''});
  const correction=appointment.status!=='BOOKED';
  useEffect(()=>{const dirty=pending||!!reason.trim();onPending(dirty);const protect=(e:BeforeUnloadEvent)=>{if(dirty)e.preventDefault();};window.addEventListener('beforeunload',protect);return()=>{onPending(false);window.removeEventListener('beforeunload',protect);};},[pending,reason,onPending]);
  async function save(){
    if(pending||!online||conflict||(correction&&reason.trim().length<5))return;
    const data={status,version:appointment.version,reason:reason.trim()},signature=JSON.stringify(data);
    if(request.current.signature!==signature)request.current={signature,key:crypto.randomUUID()};
    setPending(true);setError('');
    try{await api('/api/admin/appointments/'+appointment.id+'/outcome',{method:'POST',body:JSON.stringify({...data,requestKey:request.current.key})});onSaved();}
    catch(e){setError(errorMessage(e));if(e instanceof ApiError&&e.status===409)setConflict(true);}
    finally{setPending(false);}
  }
  return <Modal title={correction?'Corrigir resultado':'Registrar resultado'} busy={pending} onClose={()=>reason.trim()?setDiscard(true):onClose()}>
    <p>{appointment.customerName} · {appointment.serviceName}</p><p className="field-note">Disponível após o término previsto. A alteração e seu histórico são salvos juntos.</p>
    {discard?<div><p>Descartar a justificativa preenchida?</p><div className="modal-actions"><button className="button secondary" onClick={()=>setDiscard(false)}>Continuar preenchendo</button><button className="button danger" onClick={onClose}>Descartar rascunho</button></div></div>:<form className="stack-form outcome-form" onSubmit={e=>{e.preventDefault();void save();}}>
      <fieldset disabled={pending||!online||conflict}><label>Resultado do atendimento<select value={status} onChange={e=>setStatus(e.target.value as typeof status)}>{(['COMPLETED','NO_SHOW'] as const).filter(value=>!correction||value!==appointment.status).map(value=><option key={value} value={value}>{statusLabel[value]}</option>)}</select></label>
      <label>Justificativa{correction?' da correção':' (opcional)'}<textarea value={reason} onChange={e=>setReason(e.target.value)} required={correction} minLength={correction?5:undefined} maxLength={500} rows={3}/></label></fieldset>
      {correction&&<p className="field-note">Explique a correção em 5 a 500 caracteres. O registro anterior será preservado.</p>}
      {error&&<Message>{error}</Message>}
      {conflict?<button type="button" className="button secondary" onClick={onSaved}>Atualizar e descartar rascunho</button>:<button className="button primary" disabled={pending||!online||(correction&&reason.trim().length<5)}>{pending?'Salvando resultado…':'Confirmar resultado'}</button>}
    </form>}
  </Modal>;
}
export function HistoryDialog({appointment,zone,online,onClose}:{appointment:Appointment;zone:string;online:boolean;onClose:()=>void}){
  const [page,setPage]=useState<HistoryPage|null>(null),[pending,setPending]=useState(true),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{const controller=new AbortController();setPending(true);setError('');
    api<HistoryPage>('/api/admin/appointments/'+appointment.id+'/history',{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setPage(result);})
      .catch(e=>{if(!controller.signal.aborted)setError(errorMessage(e));}).finally(()=>{if(!controller.signal.aborted)setPending(false);});return()=>controller.abort();
  },[appointment.id,retry]);
  async function older(){if(!page||page.nextBeforeVersion===null||pending||!online)return;setPending(true);setError('');
    try{const next=await api<HistoryPage>('/api/admin/appointments/'+appointment.id+'/history?beforeVersion='+page.nextBeforeVersion);setPage({events:[...page.events,...next.events],nextBeforeVersion:next.nextBeforeVersion});}
    catch(e){setError(errorMessage(e));}finally{setPending(false);}}
  const date=(value:string)=>new Intl.DateTimeFormat('pt-BR',{timeZone:zone,dateStyle:'short',timeStyle:'short'}).format(new Date(value));
  const actions:Record<string,string>={CREATED:'Reserva criada',CANCELLED:'Reserva cancelada',RESCHEDULED:'Horário remarcado',OUTCOME:'Resultado registrado',CORRECTION:'Resultado corrigido'};
  return <Modal title="Histórico do atendimento" onClose={onClose}><p>{appointment.customerName} · {appointment.serviceName}</p>
    <p className="field-note">Registros feitos a partir da versão 0.12.0. Horários no fuso {zone}. Reservas anteriores podem ter histórico parcial.</p>
    {error&&<><Message>{error}</Message>{!page&&<button className="button secondary" disabled={pending||!online} onClick={()=>setRetry(v=>v+1)}>Tentar novamente</button>}</>}
    {page&&!page.events.length&&<p>Nenhuma alteração registrada desde a implantação do histórico.</p>}
    <ol className="appointment-history">{page?.events.map(entry=><li key={entry.id}><strong>{actions[entry.action]??entry.action}</strong><span>{date(entry.occurredAt)} · {entry.actorName}</span><span>{entry.fromStatus?statusLabel[entry.fromStatus]+' → ':''}{statusLabel[entry.toStatus]}</span>
      {entry.action==='RESCHEDULED'&&entry.fromStart&&<span>{date(entry.fromStart)} → {date(entry.toStart)}</span>}{entry.reason&&<p>{entry.reason}</p>}</li>)}</ol>
    {pending&&<p role="status">Consultando histórico…</p>}{page?.nextBeforeVersion!==null&&page&&<button className="button secondary" disabled={pending||!online} onClick={()=>void older()}>Carregar registros anteriores</button>}
  </Modal>;
}

import {useEffect,useRef,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message,Modal} from './components';
import type {CurrentUser} from './types';
import {time,dayLabel,plusDays,todayInZone} from './format';
import {statusLabel} from './appointmentStatus';
import type {AppointmentStatus} from './types';
import './professional-access.css';
interface Item {id:string;customerName:string;serviceName:string;startsAt:string;endsAt:string;status:AppointmentStatus;version:number}
interface Agenda {date:string;zoneId:string;professionalName:string;appointments:Item[]}
interface Event {version:number;action:string;status:AppointmentStatus;actor:string;occurredAt:string}
interface History {events:Event[];nextBeforeVersion:number|null}
export default function ProfessionalAgendaPage({user,online,onUser,onNavigationBlocked}:{user:CurrentUser;online:boolean;onUser:(u:CurrentUser|null)=>void;onNavigationBlocked:(v:boolean)=>void}){
 const [date,setDate]=useState(()=>todayInZone(user.business.zoneId)),[agenda,setAgenda]=useState<Agenda|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(false),[revision,setRevision]=useState(0),[target,setTarget]=useState<Item|null>(null),[status,setStatus]=useState<'COMPLETED'|'NO_SHOW'>('COMPLETED'),[reason,setReason]=useState(''),[pending,setPending]=useState(false),[conflict,setConflict]=useState(false),[historyTarget,setHistoryTarget]=useState<Item|null>(null),[history,setHistory]=useState<History|null>(null),[historyLoading,setHistoryLoading]=useState(false),[now,setNow]=useState(Date.now());
 const requests=useRef(new Set<AbortController>()),invalid=useRef(false),busy=useRef(false),attempt=useRef({signature:'',key:''}),historyBusy=useRef(false);
 const historyRequest=useRef<AbortController|null>(null);
 const headers={'X-Agenda-Account':user.id??''},zone=user.business.zoneId;
 function controller(){const c=new AbortController();requests.current.add(c);return c;}
 function failed(e:unknown){setError(errorMessage(e));if(e instanceof ApiError&&[401,403,412].includes(e.status)){invalid.current=true;for(const c of requests.current)c.abort();setAgenda(null);setHistory(null);setTarget(null);setHistoryTarget(null);onUser(null);}}
 useEffect(()=>()=>{for(const c of requests.current)c.abort();},[]);
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return()=>clearInterval(timer);},[]);
 useEffect(()=>{onNavigationBlocked(pending||!!reason.trim());return()=>onNavigationBlocked(false);},[pending,reason,onNavigationBlocked]);
 useEffect(()=>{setTarget(null);setReason('');setConflict(false);},[date,user.id]);
 useEffect(()=>{for(const request of requests.current)request.abort();requests.current.clear();historyRequest.current=null;historyBusy.current=false;setHistoryTarget(null);setHistory(null);setHistoryLoading(false);
  if(!online){setTarget(null);setReason('');setPending(false);}const c=controller();setAgenda(null);setError('');setLoading(online);
  if(online&&!invalid.current)api<Agenda>('/api/professional/agenda?date='+date,{headers,signal:c.signal}).then(a=>{if(!c.signal.aborted)setAgenda(a);}).catch(e=>{if(!c.signal.aborted)failed(e);}).finally(()=>{requests.current.delete(c);if(!c.signal.aborted)setLoading(false);});
  return()=>{c.abort();requests.current.delete(c);};
 },[date,revision,online,user.id]);
 useEffect(()=>{const focus=()=>{if(!busy.current&&!target)setRevision(v=>v+1);};window.addEventListener('focus',focus);return()=>window.removeEventListener('focus',focus);},[target]);
 async function save(){
  if(!target||busy.current||!online||invalid.current||conflict)return;
  const body={status,reason:reason.trim(),version:target.version},signature=JSON.stringify({id:target.id,...body});
  if(attempt.current.signature!==signature)attempt.current={signature,key:crypto.randomUUID()};
  const c=controller();busy.current=true;setPending(true);setError('');
  try{await api('/api/professional/appointments/'+target.id+'/outcome',{method:'POST',headers,signal:c.signal,body:JSON.stringify({...body,requestKey:attempt.current.key})});if(!c.signal.aborted){setTarget(null);setReason('');setRevision(v=>v+1);}}
  catch(e){if(!c.signal.aborted){failed(e);if(e instanceof ApiError&&e.status===409)setConflict(true);}}
  finally{requests.current.delete(c);busy.current=false;if(!c.signal.aborted)setPending(false);}
 }
 async function loadHistory(item:Item,older=false){
  if(historyBusy.current||!online||invalid.current)return;
  historyRequest.current?.abort();
  const c=controller();historyRequest.current=c;historyBusy.current=true;setHistoryLoading(true);setError('');setHistoryTarget(item);if(!older)setHistory(null);
  try{const result=await api<History>('/api/professional/appointments/'+item.id+'/history'+(older?'?beforeVersion='+history?.nextBeforeVersion:''),{headers,signal:c.signal});
   if(!c.signal.aborted)setHistory(current=>older&&current?{events:[...current.events,...result.events],nextBeforeVersion:result.nextBeforeVersion}:result);
  }catch(e){if(!c.signal.aborted)failed(e);}
  finally{requests.current.delete(c);if(historyRequest.current===c){historyBusy.current=false;if(!c.signal.aborted)setHistoryLoading(false);}}
 }
 return <section className="staff-agenda"><div className="team-heading"><div><span className="eyebrow">{user.business.name} · PROFISSIONAL</span><h1>Minha agenda</h1><p>Olá, {agenda?.professionalName??user.name}. Consulte seus atendimentos no fuso {zone}.</p></div><button className="button secondary" aria-label="Sair do painel" disabled={pending||!!reason.trim()} onClick={()=>void api('/api/auth/logout',{method:'POST'}).then(()=>onUser(null)).catch(failed)}>Sair</button></div>
  <p className="field-note">Somente seus atendimentos são exibidos. Atualize para conferir alterações feitas em outro aparelho.</p>
  <div className="agenda-date-controls"><button className="button secondary" disabled={pending||!!reason.trim()} aria-label="Dia anterior" onClick={()=>setDate(plusDays(date,-1))}>Anterior</button><label>Data da minha agenda<input type="date" value={date} disabled={pending||!!reason.trim()} onChange={e=>{if(e.target.value)setDate(e.target.value);}}/></label><button className="button secondary" disabled={pending||!!reason.trim()} aria-label="Próximo dia" onClick={()=>setDate(plusDays(date,1))}>Próximo</button><button className="button secondary" disabled={!online||pending||!!reason.trim()} onClick={()=>setRevision(v=>v+1)}>Atualizar agenda</button></div>
  {error&&<Message>{error}</Message>}{!online&&<Message>Reconecte para consultar ou alterar atendimentos.</Message>}{loading&&<p role="status">Consultando sua agenda…</p>}
  {agenda&&<><h2>{dayLabel(agenda.date)}</h2>{!agenda.appointments.length&&<p>Nenhum atendimento nesta data.</p>}<div className="staff-appointments">{agenda.appointments.map(item=><article className="staff-appointment" key={item.id}><strong>{time(item.startsAt,zone)}–{time(item.endsAt,zone)}</strong><h3>{item.customerName}</h3><p>{item.serviceName}</p><span className="status-badge">{statusLabel[item.status]}</span><div className="staff-actions"><button className="button secondary" disabled={!online||pending} onClick={()=>void loadHistory(item)}>Histórico</button>{item.status!=='CANCELLED'&&new Date(item.endsAt).getTime()<=now&&<button className="button primary" disabled={!online||pending} onClick={()=>{setTarget(item);setStatus(item.status==='COMPLETED'?'NO_SHOW':'COMPLETED');setReason('');setConflict(false);setError('');}}>{item.status==='BOOKED'?'Registrar resultado':'Corrigir resultado'}</button>}</div></article>)}</div></>}
  {target&&<Modal title={target.status==='BOOKED'?'Registrar resultado':'Corrigir resultado'} busy={pending} onClose={()=>{if(!reason.trim()||window.confirm('Descartar a justificativa preenchida?')){setTarget(null);setReason('');}}}><p>{target.customerName} · {target.serviceName}</p><form className="stack-form" onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={pending||!online||conflict}><label>Resultado do atendimento<select value={status} onChange={e=>setStatus(e.target.value as typeof status)}>{(['COMPLETED','NO_SHOW'] as const).filter(s=>s!==target.status).map(s=><option key={s} value={s}>{statusLabel[s]}</option>)}</select></label><label>Justificativa{target.status==='BOOKED'?' (opcional)':' da correção'}<textarea rows={3} value={reason} maxLength={500} minLength={target.status==='BOOKED'?undefined:5} required={target.status!=='BOOKED'} onChange={e=>setReason(e.target.value)}/></label></fieldset><p className="field-note">Resultado disponível após o término previsto. Correções exigem justificativa de pelo menos 5 caracteres.</p>{error&&<Message>{error}</Message>}{conflict?<button type="button" className="button secondary" onClick={()=>{setTarget(null);setReason('');setRevision(v=>v+1);}}>Atualizar e descartar rascunho</button>:<button className="button primary" disabled={pending||!online}>{pending?'Salvando…':'Confirmar resultado'}</button>}</form></Modal>}
  {historyTarget&&<Modal title="Histórico do atendimento" onClose={()=>{historyRequest.current?.abort();historyRequest.current=null;historyBusy.current=false;setHistoryLoading(false);setHistoryTarget(null);setHistory(null);}}><p>{historyTarget.customerName} · {historyTarget.serviceName}</p><p>Histórico resumido dos seus atendimentos, sem justificativas privadas ou dados de contas.</p>{historyLoading&&<p role="status">Consultando histórico…</p>}{error&&<Message>{error}</Message>}<ol className="appointment-history">{history?.events.map(e=><li key={e.version}><strong>{statusLabel[e.status]}</strong><span>{new Intl.DateTimeFormat('pt-BR',{timeZone:zone,dateStyle:'short',timeStyle:'short'}).format(new Date(e.occurredAt))} · {e.actor}</span></li>)}</ol>{history?.nextBeforeVersion!=null&&<button className="button secondary" disabled={historyLoading||!online} onClick={()=>void loadHistory(historyTarget,true)}>Carregar registros anteriores</button>}</Modal>}
 </section>;
}

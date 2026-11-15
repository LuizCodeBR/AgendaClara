import {useEffect,useRef,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message} from './components';
import {todayInZone} from './format';
import type {ProfessionalConfiguration} from './types';
import {normalizedPhone,summaryParts,whatsappUrl,sameSummary,summaryLifetime,summaryRemaining} from './dailySummary';
import type {DailySummary,SummaryProfessional} from './dailySummary';
import './daily-summary.css';

export default function DailySummaryPanel({online,zoneId,onDirty,onUnauthorized}:{online:boolean;zoneId:string;onDirty:(dirty:boolean)=>void;onUnauthorized:()=>void}){
 const [people,setPeople]=useState<ProfessionalConfiguration[]>([]),[selected,setSelected]=useState(''),[date,setDate]=useState(()=>todayInZone(zoneId));
 const [data,setData]=useState<DailySummary|null>(null),[phone,setPhone]=useState(''),[part,setPart]=useState(0),[reviewed,setReviewed]=useState(false);
 const [loading,setLoading]=useState(false),[loadingPeople,setLoadingPeople]=useState(true),[saving,setSaving]=useState(false),[checking,setChecking]=useState(false);
 const [error,setError]=useState(''),[notice,setNotice]=useState(''),[revision,setRevision]=useState(0),[peopleRevision,setPeopleRevision]=useState(0);
 const [expired,setExpired]=useState(false),[conflict,setConflict]=useState(false);
 const preview=useRef<HTMLTextAreaElement>(null),busy=useRef(false),onDenied=useRef(onUnauthorized);onDenied.current=onUnauthorized;
 const lifetime=useRef<ReturnType<typeof summaryLifetime>|null>(null),timer=useRef<number|undefined>(undefined),epoch=useRef(0);
 const request=useRef<AbortController|null>(null),saveRequest=useRef<AbortController|null>(null);
 const view=useRef({data,part,phone,selected,date,revision});view.current={data,part,phone,selected,date,revision};
 const dirty=data!==null&&phone!==data.professional.phone;
 const pending=saving||checking;
 function invalidate(){epoch.current++;lifetime.current=null;window.clearTimeout(timer.current);setExpired(true);setReviewed(false);}
 function accept(result:DailySummary,started:ReturnType<typeof summaryLifetime>,requestEpoch:number){
  setData(result);setPhone(result.professional.phone);window.clearTimeout(timer.current);
  const remaining=summaryRemaining(started);
  if(requestEpoch!==epoch.current||document.visibilityState==='hidden'||remaining<=0){lifetime.current=null;setExpired(true);setReviewed(false);return false;}
  lifetime.current=started;setExpired(false);timer.current=window.setTimeout(invalidate,remaining);return true;
 }
 useEffect(()=>{onDirty(dirty||pending);const protect=(e:BeforeUnloadEvent)=>{if(dirty||pending)e.preventDefault();};window.addEventListener('beforeunload',protect);return()=>{onDirty(false);window.removeEventListener('beforeunload',protect);};},[dirty,pending,onDirty]);
 useEffect(()=>{
  const hidden=()=>{if(document.visibilityState==='hidden')invalidate();};
  const restored=(event:PageTransitionEvent)=>{if(event.persisted)invalidate();};
  document.addEventListener('visibilitychange',hidden);window.addEventListener('pagehide',invalidate);window.addEventListener('pageshow',restored);window.addEventListener('offline',invalidate);
  return()=>{epoch.current++;request.current?.abort();saveRequest.current?.abort();window.clearTimeout(timer.current);document.removeEventListener('visibilitychange',hidden);window.removeEventListener('pagehide',invalidate);window.removeEventListener('pageshow',restored);window.removeEventListener('offline',invalidate);};
 },[]);
 useEffect(()=>{if(!online)invalidate();},[online]);
 useEffect(()=>{
  const controller=new AbortController();setLoadingPeople(true);setError('');
  api<ProfessionalConfiguration[]>('/api/admin/professionals',{signal:controller.signal}).then(result=>{if(!controller.signal.aborted){setPeople(result);setSelected(current=>result.some(p=>p.id===current)?current:result[0]?.id??'');}})
   .catch(e=>{if(!controller.signal.aborted){setError(errorMessage(e));if(e instanceof ApiError&&e.status===401)onDenied.current();}})
   .finally(()=>{if(!controller.signal.aborted)setLoadingPeople(false);});return()=>controller.abort();
 },[peopleRevision]);
 useEffect(()=>{
  request.current?.abort();window.clearTimeout(timer.current);lifetime.current=null;setData(null);setReviewed(false);setChecking(false);
  if(!selected||!date)return;
  const controller=new AbortController();request.current=controller;const started=summaryLifetime(),requestEpoch=epoch.current;
  setLoading(true);setError('');setNotice('');setConflict(false);setPart(0);setExpired(false);
  api<DailySummary>('/api/admin/professionals/'+selected+'/daily-summary?date='+date,{signal:controller.signal}).then(result=>{
   if(!controller.signal.aborted)accept(result,started,requestEpoch);
  }).catch(e=>{if(!controller.signal.aborted){setError(errorMessage(e));if(e instanceof ApiError&&e.status===401)onDenied.current();}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>{controller.abort();request.current?.abort();window.clearTimeout(timer.current);};
 },[selected,date,revision]);
 const parts=data?summaryParts(data):[],text=parts[part]??'',phoneValue=normalizedPhone(phone);
 const url=data?whatsappUrl(data.professional.phone,text):null;
 const ready=!!data&&!loading&&!pending&&!dirty&&!conflict&&!expired&&summaryRemaining(lifetime.current)>0&&online;
 function stillFresh(){if(online&&document.visibilityState!=='hidden'&&summaryRemaining(lifetime.current)>0)return true;invalidate();return false;}
 async function confirm(){
  if(reviewed){setReviewed(false);return;}
  if(!data||!ready||busy.current||!stillFresh())return;
  busy.current=true;setChecking(true);setReviewed(false);setError('');setNotice('');
  const controller=new AbortController();request.current=controller;const started=summaryLifetime(),requestEpoch=epoch.current;
  try{
   const current=await api<DailySummary>('/api/admin/professionals/'+selected+'/daily-summary?date='+date,{signal:controller.signal});
   if(controller.signal.aborted)return;
   const unchanged=sameSummary(data,current),fresh=accept(current,started,requestEpoch);
   if(!unchanged){setPart(0);setNotice('A agenda ou o cadastro mudou. Confira a prévia e o destinatário novamente.');}
   setReviewed(unchanged&&fresh);
  }catch(e){if(!controller.signal.aborted){invalidate();setError(errorMessage(e));if(e instanceof ApiError&&e.status===401){setData(null);setPhone('');onDenied.current();}}}
  finally{if(!controller.signal.aborted){busy.current=false;setChecking(false);}}
 }
 async function saveContact(){
  if(!data||busy.current||!online||phoneValue===null||conflict)return;
  busy.current=true;setSaving(true);setError('');setNotice('');setReviewed(false);
  const controller=new AbortController();saveRequest.current=controller;
  try{
   await api<SummaryProfessional>('/api/admin/professionals/'+selected+'/whatsapp-contact',{signal:controller.signal,method:'PUT',body:JSON.stringify({phone,version:data.professional.version})});
   if(controller.signal.aborted)return;
   // Reload the complete snapshot instead of mixing a new profile with old appointments.
   setRevision(v=>v+1);
  }catch(e){if(!controller.signal.aborted){invalidate();setConflict(true);setError(errorMessage(e));if(e instanceof ApiError&&e.status===401){setData(null);setPhone('');onDenied.current();}}}
  finally{if(!controller.signal.aborted){busy.current=false;setSaving(false);}}
 }
 async function copy(){
  if(!ready||!reviewed||!stillFresh())return;
  const currentView=view.current,currentEpoch=epoch.current;
  const current=()=>view.current===currentView&&epoch.current===currentEpoch;
  try{if(!navigator.clipboard?.writeText)throw Error();await navigator.clipboard.writeText(text);if(current())setNotice('Texto copiado. Cole na conversa e confira o destinatário antes de enviar.');}
  catch{if(current()){preview.current?.focus();preview.current?.select();setNotice('Cópia automática indisponível. O texto foi selecionado; use Copiar no seu dispositivo.');}}
 }
 return <section className="agenda-panel daily-summary-panel">
  <span className="eyebrow">ORGANIZE O EXPEDIENTE</span><h2>Resumo diário por profissional</h2>
  <p className="field-note">Compartilhamento manual: a Agenda Clara prepara a mensagem. Você confere a conversa e confirma o envio no WhatsApp.</p>
  <div className="form-row daily-summary-filters"><label>Profissional do resumo<select aria-label="Profissional do resumo" value={selected} disabled={loading||loadingPeople||pending||dirty} onChange={e=>setSelected(e.target.value)}><option value="" disabled>Escolha um profissional</option>{people.map(p=><option key={p.id} value={p.id}>{p.name}{p.active?'':' (inativo)'}</option>)}</select></label>
  <label>Data do resumo<input type="date" value={date} disabled={pending||dirty} onChange={e=>{if(e.target.value)setDate(e.target.value);}}/></label></div>
  {error&&<Message>{error}</Message>}{notice&&<Message kind="info">{notice}</Message>}
  {!people.length&&!loadingPeople&&<><p>Nenhum profissional disponível. Cadastre a equipe para gerar um resumo.</p><button className="button secondary" onClick={()=>setPeopleRevision(v=>v+1)} disabled={!online}>Recarregar equipe</button></>}
  {(loading||loadingPeople)&&<p role="status">Consultando o resumo…</p>}
  {!loading&&selected&&<button className="button secondary" disabled={!online||pending||dirty} onClick={()=>setRevision(v=>v+1)}>Atualizar resumo</button>}
  {data&&!loading&&<>
   <p className="field-note">Somente reservas ainda confirmadas, pelo horário de início nesta data, no fuso {data.zoneId}. Cancelamentos, conclusões e faltas ficam fora. Atualize após alterações na agenda.</p>
   <fieldset disabled={pending||!online||conflict} className="daily-contact"><legend>WhatsApp do profissional</legend>
    <label>Telefone com DDI<input type="tel" autoComplete="off" maxLength={32} placeholder="+55 11 99999-1234" value={phone} onChange={e=>{setPhone(e.target.value);setReviewed(false);setNotice('');}}/></label>
    <p className="field-note">Inclua o código do país, sem zero inicial. Deixe vazio para remover. O contato fica privado ao responsável da empresa.</p>
    {phoneValue===null&&<Message>Informe de 8 a 15 dígitos com DDI, usando apenas números e separadores de telefone.</Message>}
    <div className="daily-summary-actions"><button className="button secondary" disabled={!dirty||phoneValue===null} onClick={()=>void saveContact()}>{saving?'Salvando contato…':'Salvar contato'}</button>
    {dirty&&<button className="button quiet" onClick={()=>{setPhone(data.professional.phone);setError('');}}>Descartar edição do contato</button>}</div>
   </fieldset>
   {conflict&&<><Message>Não foi possível confirmar o contato salvo. Consulte o cadastro atual antes de compartilhar ou tentar outra edição.</Message><button className="button secondary" disabled={!online||pending} onClick={()=>{setData(null);setRevision(v=>v+1);}}>Atualizar e descartar edição do contato</button></>}
   {expired&&<Message>Atualize o resumo antes de compartilhar: passaram cinco minutos, a consulta demorou ou você saiu do aplicativo/perdeu a conexão.</Message>}
   {parts.length>1&&<label>Parte do resumo<select aria-label="Parte do resumo" disabled={pending} value={part} onChange={e=>{setPart(Number(e.target.value));setReviewed(false);setNotice('');}}>{parts.map((_,i)=><option key={i} value={i}>Parte {i+1} de {parts.length}</option>)}</select></label>}
   <label>Prévia da mensagem<textarea aria-label="Prévia da mensagem" ref={preview} readOnly rows={12} value={text}/></label>
   {parts.length>1&&<p className="field-note">Resumo dividido para manter os links curtos. Confira e compartilhe cada parte separadamente.</p>}
   <label className="checkbox-label"><input type="checkbox" checked={reviewed} disabled={!ready} onChange={()=>void confirm()}/> Conferi a prévia e o destinatário{data.professional.phone?': +'+data.professional.phone:'. Escolherei a conversa ao colar o texto.'}</label>
   {checking&&<p role="status">Conferindo a agenda e o contato atuais…</p>}
   <p className="field-note">Ao confirmar, consultamos novamente a agenda e o contato. Se houver mudança, confira a nova prévia.</p>
   <div className="daily-summary-actions"><button className="button secondary" disabled={!ready||!reviewed} onClick={()=>void copy()}>{parts.length>1?'Copiar esta parte':'Copiar resumo'}</button>
   {ready&&reviewed&&url?<a className="button primary" href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" onClick={event=>{if(!stillFresh())event.preventDefault();}} onAuxClick={event=>{if(!stillFresh())event.preventDefault();}} onContextMenu={event=>{if(!stillFresh())event.preventDefault();}}>Abrir WhatsApp para conferir</a>:<button className="button primary" disabled>Abrir WhatsApp para conferir</button>}</div>
   {!data.professional.phone&&<p className="field-note">Salve o telefone para abrir uma conversa direcionada, ou copie o resumo e escolha a conversa manualmente.</p>}
   <p className="field-note">Abrir o WhatsApp não comprova envio ou entrega. A mensagem é uma cópia deste momento e não acompanha mudanças posteriores. O número e o texto são transferidos ao WhatsApp ao abrir o link.</p>
  </>}
 </section>;
}
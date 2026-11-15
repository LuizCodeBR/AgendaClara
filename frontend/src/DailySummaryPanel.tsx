import {useEffect,useRef,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message} from './components';
import {todayInZone} from './format';
import type {ProfessionalConfiguration} from './types';
import {normalizedPhone,summaryParts,whatsappUrl} from './dailySummary';
import type {DailySummary,SummaryProfessional} from './dailySummary';
import './daily-summary.css';

export default function DailySummaryPanel({online,zoneId,onDirty,onUnauthorized}:{online:boolean;zoneId:string;onDirty:(dirty:boolean)=>void;onUnauthorized:()=>void}){
 const [people,setPeople]=useState<ProfessionalConfiguration[]>([]),[selected,setSelected]=useState(''),[date,setDate]=useState(()=>todayInZone(zoneId));
 const [data,setData]=useState<DailySummary|null>(null),[phone,setPhone]=useState(''),[part,setPart]=useState(0),[reviewed,setReviewed]=useState(false);
 const [loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[revision,setRevision]=useState(0),[peopleRevision,setPeopleRevision]=useState(0);
 const [expired,setExpired]=useState(false),[conflict,setConflict]=useState(false);
 const preview=useRef<HTMLTextAreaElement>(null),busy=useRef(false),expiresAt=useRef(0),onDenied=useRef(onUnauthorized);onDenied.current=onUnauthorized;
 const dirty=data!==null&&phone!==data.professional.phone;
 useEffect(()=>{onDirty(dirty||saving);const protect=(e:BeforeUnloadEvent)=>{if(dirty||saving)e.preventDefault();};window.addEventListener('beforeunload',protect);return()=>{onDirty(false);window.removeEventListener('beforeunload',protect);};},[dirty,saving,onDirty]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');
  api<ProfessionalConfiguration[]>('/api/admin/professionals',{signal:controller.signal}).then(result=>{if(!controller.signal.aborted){setPeople(result);setSelected(current=>result.some(p=>p.id===current)?current:result[0]?.id??'');}})
   .catch(e=>{if(!controller.signal.aborted){setError(errorMessage(e));if(e instanceof ApiError&&e.status===401)onDenied.current();}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();
 },[peopleRevision]);
 useEffect(()=>{
  if(!selected||!date)return;
  const controller=new AbortController();setLoading(true);setData(null);setError('');setNotice('');setReviewed(false);setConflict(false);setPart(0);setExpired(false);expiresAt.current=0;
  let timeout:number|undefined;
  api<DailySummary>('/api/admin/professionals/'+selected+'/daily-summary?date='+date,{signal:controller.signal}).then(result=>{
   if(!controller.signal.aborted){setData(result);setPhone(result.professional.phone);expiresAt.current=Date.now()+5*60*1000;timeout=window.setTimeout(()=>{setExpired(true);setReviewed(false);},5*60*1000);}
  }).catch(e=>{if(!controller.signal.aborted){setError(errorMessage(e));if(e instanceof ApiError&&e.status===401)onDenied.current();}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>{controller.abort();window.clearTimeout(timeout);};
 },[selected,date,revision]);
 const parts=data?summaryParts(data):[],text=parts[part]??'',phoneValue=normalizedPhone(phone);
 const url=data?whatsappUrl(data.professional.phone,text):null;
 const ready=!!data&&!loading&&!saving&&!dirty&&!conflict&&!expired&&Date.now()<expiresAt.current&&online;
 function stillFresh(){if(Date.now()<expiresAt.current)return true;setExpired(true);setReviewed(false);return false;}
 async function saveContact(){
  if(!data||busy.current||!online||phoneValue===null||conflict)return;
  busy.current=true;setSaving(true);setError('');setNotice('');setReviewed(false);
  try{
   const saved=await api<SummaryProfessional>('/api/admin/professionals/'+selected+'/whatsapp-contact',{method:'PUT',body:JSON.stringify({phone,version:data.professional.version})});
   setData({...data,professional:saved});setPhone(saved.phone);setNotice('Contato salvo. Confira o número antes de compartilhar.');
  }catch(e){setError(errorMessage(e));if(e instanceof ApiError&&e.status===409)setConflict(true);if(e instanceof ApiError&&e.status===401)onDenied.current();}
  finally{busy.current=false;setSaving(false);}
 }
 async function copy(){
  if(!ready||!reviewed||!stillFresh())return;
  try{if(!navigator.clipboard?.writeText)throw Error();await navigator.clipboard.writeText(text);setNotice('Texto copiado. Cole na conversa e confira o destinatário antes de enviar.');}
  catch{preview.current?.focus();preview.current?.select();setNotice('Cópia automática indisponível. O texto foi selecionado; use Copiar no seu dispositivo.');}
 }
 return <section className="agenda-panel daily-summary-panel">
  <span className="eyebrow">ORGANIZE O EXPEDIENTE</span><h2>Resumo diário por profissional</h2>
  <p className="field-note">Compartilhamento manual: a Agenda Clara prepara a mensagem. Você confere a conversa e confirma o envio no WhatsApp.</p>
  <div className="form-row daily-summary-filters"><label>Profissional do resumo<select aria-label="Profissional do resumo" value={selected} disabled={loading||saving||dirty} onChange={e=>setSelected(e.target.value)}><option value="" disabled>Escolha um profissional</option>{people.map(p=><option key={p.id} value={p.id}>{p.name}{p.active?'':' (inativo)'}</option>)}</select></label>
  <label>Data do resumo<input type="date" value={date} disabled={saving||dirty} onChange={e=>{if(e.target.value)setDate(e.target.value);}}/></label></div>
  {error&&<Message>{error}</Message>}{notice&&<Message kind="info">{notice}</Message>}
  {!people.length&&!loading&&<><p>Nenhum profissional disponível. Cadastre a equipe para gerar um resumo.</p><button className="button secondary" onClick={()=>setPeopleRevision(v=>v+1)} disabled={!online}>Recarregar equipe</button></>}
  {loading&&<p role="status">Consultando o resumo…</p>}
  {!loading&&selected&&<button className="button secondary" disabled={!online||saving||dirty} onClick={()=>setRevision(v=>v+1)}>Atualizar resumo</button>}
  {data&&!loading&&<>
   <p className="field-note">Somente reservas ainda confirmadas, pelo horário de início nesta data, no fuso {data.zoneId}. Cancelamentos, conclusões e faltas ficam fora. Atualize após alterações na agenda.</p>
   <fieldset disabled={saving||!online||conflict} className="daily-contact"><legend>WhatsApp do profissional</legend>
    <label>Telefone com DDI<input type="tel" autoComplete="off" maxLength={32} placeholder="+55 11 99999-1234" value={phone} onChange={e=>{setPhone(e.target.value);setReviewed(false);setNotice('');}}/></label>
    <p className="field-note">Inclua o código do país, sem zero inicial. Deixe vazio para remover. O contato fica privado ao responsável da empresa.</p>
    {phoneValue===null&&<Message>Informe de 8 a 15 dígitos com DDI, usando apenas números e separadores de telefone.</Message>}
    <div className="daily-summary-actions"><button className="button secondary" disabled={!dirty||phoneValue===null} onClick={()=>void saveContact()}>{saving?'Salvando contato…':'Salvar contato'}</button>
    {dirty&&<button className="button quiet" onClick={()=>{setPhone(data.professional.phone);setConflict(false);setError('');}}>Descartar edição do contato</button>}</div>
   </fieldset>
   {conflict&&<button className="button secondary" disabled={!online||saving} onClick={()=>{setData(null);setRevision(v=>v+1);}}>Atualizar e descartar edição do contato</button>}
   {expired&&<Message>Este resumo tem mais de cinco minutos. Atualize antes de copiar ou compartilhar.</Message>}
   {parts.length>1&&<label>Parte do resumo<select aria-label="Parte do resumo" value={part} onChange={e=>{setPart(Number(e.target.value));setReviewed(false);setNotice('');}}>{parts.map((_,i)=><option key={i} value={i}>Parte {i+1} de {parts.length}</option>)}</select></label>}
   <label>Prévia da mensagem<textarea aria-label="Prévia da mensagem" ref={preview} readOnly rows={12} value={text}/></label>
   {parts.length>1&&<p className="field-note">Resumo dividido para manter os links curtos. Confira e compartilhe cada parte separadamente.</p>}
   <label className="checkbox-label"><input type="checkbox" checked={reviewed} disabled={!ready} onChange={e=>setReviewed(e.target.checked)}/> Conferi a prévia e o destinatário{data.professional.phone?': +'+data.professional.phone:'. Escolherei a conversa ao colar o texto.'}</label>
   <div className="daily-summary-actions"><button className="button secondary" disabled={!ready||!reviewed} onClick={()=>void copy()}>{parts.length>1?'Copiar esta parte':'Copiar resumo'}</button>
   {ready&&reviewed&&url?<a className="button primary" href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" onClick={event=>{if(!stillFresh())event.preventDefault();}}>Abrir WhatsApp para conferir</a>:<button className="button primary" disabled>Abrir WhatsApp para conferir</button>}</div>
   {!data.professional.phone&&<p className="field-note">Salve o telefone para abrir uma conversa direcionada, ou copie o resumo e escolha a conversa manualmente.</p>}
   <p className="field-note">Abrir o WhatsApp não comprova envio ou entrega. A mensagem é uma cópia deste momento e não acompanha mudanças posteriores.</p>
  </>}
 </section>;
}

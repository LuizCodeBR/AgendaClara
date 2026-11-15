import {useEffect,useRef,useState} from 'react';
import {api,ApiError,errorMessage} from './api';
import {Message,Modal} from './components';
import './professional-access.css';
interface Access {professionalId:string;name:string;active:boolean;version:number;email:string;enabled:boolean;inviteExpiresAt:string|null}
export default function TeamAccessPanel({accountId,online,onUnauthorized,onDirty}:{accountId:string;online:boolean;onUnauthorized:()=>void;onDirty:(v:boolean)=>void}){
 const [people,setPeople]=useState<Access[]>([]),[selected,setSelected]=useState(''),[email,setEmail]=useState(''),[link,setLink]=useState(''),[error,setError]=useState(''),[notice,setNotice]=useState(''),[loading,setLoading]=useState(true),[pending,setPending]=useState(false),[revision,setRevision]=useState(0),[confirm,setConfirm]=useState(false);
 const active=useRef<AbortController|null>(null),busy=useRef(false);
 const headers={'X-Agenda-Account':accountId};
 const failed=(e:unknown)=>{setError(errorMessage(e));if(e instanceof ApiError&&[401,403,412].includes(e.status)){active.current?.abort();setPeople([]);setLink('');onUnauthorized();}};
 useEffect(()=>{onDirty(pending);return()=>onDirty(false);},[pending,onDirty]);
 useEffect(()=>()=>active.current?.abort(),[]);
 useEffect(()=>{active.current?.abort();setPending(false);const c=new AbortController();active.current=c;setLink('');setPeople([]);setLoading(true);setError('');
  if(online)api<Access[]>('/api/admin/professional-access',{headers:{'X-Agenda-Account':accountId},signal:c.signal}).then(rows=>{if(!c.signal.aborted){setPeople(rows);setSelected(id=>rows.some(p=>p.professionalId===id)?id:rows[0]?.professionalId??'');}}).catch(e=>{if(!c.signal.aborted)failed(e);}).finally(()=>{if(!c.signal.aborted)setLoading(false);});
  else{setLoading(false);setError('Reconecte para consultar os acessos da equipe.');}
  return()=>c.abort();
 },[accountId,online,revision]);
 const person=people.find(p=>p.professionalId===selected);
 useEffect(()=>{setEmail(person?.email??'');setConfirm(false);},[person?.professionalId,person?.version]);
 async function change(action:'invite'|'revoke'){
  if(!person||busy.current||!online)return;
  busy.current=true;setPending(true);setError('');setNotice('');setLink('');
  const c=new AbortController();active.current=c;
  try{
   const result=await api<{token:string;expiresAt:string}|undefined>('/api/admin/professional-access/'+person.professionalId+'/'+action,{method:'POST',headers,signal:c.signal,body:JSON.stringify(action==='invite'?{email,version:person.version}:{version:person.version})});
   const rows=await api<Access[]>('/api/admin/professional-access',{headers,signal:c.signal});
   if(c.signal.aborted)return;
   setPeople(rows);setConfirm(false);
   // State refresh commits before the link is shown; a failed refresh requires reconciliation.
   if(result){
    const current=rows.find(p=>p.professionalId===person.professionalId);
    if(!current||!current.active||current.enabled||current.version!==person.version+1||current.email!==email.trim().toLowerCase()||current.inviteExpiresAt!==result.expiresAt)
     throw new ApiError('O convite mudou em outra janela. Confira os acessos e gere outro se necessário.',409);
    setLink(window.location.origin+window.location.pathname+'#/aceitar-convite?token='+encodeURIComponent(result.token));
   }
   setNotice(action==='invite'?'Convite gerado. Válido por 24 horas; compartilhe somente com o profissional.':'Acesso e convites revogados. Cadastro e reservas foram preservados.');
  }catch(e){if(!c.signal.aborted){failed(e);setPeople([]);setError(errorMessage(e)+' Atualize os acessos antes de tentar novamente.');}}
  finally{busy.current=false;if(!c.signal.aborted)setPending(false);}
 }
 return <section className="team-section" aria-label="Acessos da equipe"><h2>Acessos da equipe</h2><p>Cada profissional consulta somente sua agenda. Gere o convite e compartilhe o link em uma conversa privada.</p>
  {error&&<Message>{error}</Message>}{notice&&<Message kind="success">{notice}</Message>}
  <button className="button secondary" disabled={!online||pending} onClick={()=>setRevision(v=>v+1)}>Atualizar acessos</button>
  {loading?<p role="status">Consultando acessos…</p>:<div className="staff-access-grid"><div className="team-list">{people.map(p=><button className={'team-person '+(p.professionalId===selected?'selected':'')} key={p.professionalId} disabled={pending} onClick={()=>{setSelected(p.professionalId);setLink('');setNotice('');}}><span><strong>{p.name}</strong><small>{!p.active?'Profissional inativo':p.enabled?'Acesso ativo':p.inviteExpiresAt&&new Date(p.inviteExpiresAt).getTime()>Date.now()?'Convite pendente':'Sem acesso ativo'}</small></span></button>)}{!people.length&&!error&&<p>Cadastre um profissional em Equipe e horários.</p>}</div>
  {person&&<div className="staff-access-card"><h3>{person.name}</h3><p>{person.enabled?'A conta pode acessar Minha agenda.':'A conta ainda não tem acesso ativo.'}</p><form className="stack-form" onSubmit={e=>{e.preventDefault();void change('invite');}}>
   <label>E-mail do profissional<input type="email" required maxLength={180} value={email} disabled={pending||person.enabled||!!person.email&&!person.inviteExpiresAt} onChange={e=>setEmail(e.target.value)}/></label>
   <p className="field-note">O convite não comprova a titularidade do e-mail. Uma conta já ativada mantém o mesmo endereço na reativação. Um novo convite invalida o anterior.</p>
   <button className="button primary" disabled={!online||pending||!person.active||person.enabled}>{pending?'Aguarde…':person.inviteExpiresAt?'Gerar novo convite':'Gerar convite'}</button></form>
   {(person.enabled||person.inviteExpiresAt)&&<button className="button danger" disabled={!online||pending} onClick={()=>setConfirm(true)}>Revogar acesso e convites</button>}
   {link&&<div className="staff-private-link"><label>Link privado do convite<textarea readOnly value={link} rows={4} onFocus={e=>e.target.select()}/></label><button className="button secondary" onClick={()=>void navigator.clipboard?.writeText(link).then(()=>setNotice('Link copiado. Confira o destinatário antes de compartilhar.')).catch(()=>setNotice('Selecione o link e copie pelo dispositivo.'))}>Copiar convite</button><p>O link aparece somente nesta tela. Se perdê-lo, gere outro.</p></div>}
  </div>}</div>}
  {confirm&&person&&<Modal title="Revogar acesso profissional" busy={pending} onClose={()=>setConfirm(false)}><p>Revogar o acesso de {person.name} e invalidar seus convites? As reservas e o histórico serão preservados. As sessões perderão acesso na próxima requisição.</p><div className="modal-actions"><button className="button secondary" disabled={pending} onClick={()=>setConfirm(false)}>Voltar</button><button className="button danger" disabled={pending||!online} onClick={()=>void change('revoke')}>Confirmar revogação</button></div></Modal>}
 </section>;
}

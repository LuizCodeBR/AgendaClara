import {useEffect,useRef,useState} from 'react';
import {api,errorMessage} from './api';
import {Message} from './components';
import type {CurrentUser} from './types';
interface Preview {businessName:string;professionalName:string;email:string;expiresAt:string}
export default function ProfessionalInvitePage({token,user,online,onLogout,onLogin}:{token:string;user:CurrentUser|null;online:boolean;onLogout:()=>void;onLogin:(email:string)=>void}){
 const [preview,setPreview]=useState<Preview|null>(null),[password,setPassword]=useState(''),[confirmation,setConfirmation]=useState(''),[error,setError]=useState(''),[pending,setPending]=useState(false),[done,setDone]=useState(false),[retry,setRetry]=useState(0);
 const busy=useRef(false),active=useRef<AbortController|null>(null);
 useEffect(()=>()=>active.current?.abort(),[]);
 useEffect(()=>{active.current?.abort();setPending(false);const c=new AbortController();active.current=c;setPreview(null);setError('');
  if(!token){setError('Abra o link completo enviado pelo responsável.');return;}
  if(online)api<Preview>('/api/auth/professional-invitations/preview',{method:'POST',body:JSON.stringify({token}),signal:c.signal}).then(p=>{if(!c.signal.aborted)setPreview(p);}).catch(e=>{if(!c.signal.aborted)setError(errorMessage(e));});
  return()=>c.abort();
 },[token,online,retry]);
 async function accept(){
  if(busy.current||!online||user||!preview||done)return;
  if(password!==confirmation){setError('As senhas devem ser iguais.');return;}
  busy.current=true;setPending(true);setError('');const c=new AbortController();active.current=c;
  try{await api('/api/auth/professional-invitations/accept',{method:'POST',signal:c.signal,body:JSON.stringify({token,password})});if(!c.signal.aborted){setDone(true);setPassword('');setConfirmation('');}}
  catch(e){if(!c.signal.aborted)setError(errorMessage(e)+' Se a ativação já tiver sido confirmada, tente entrar com a senha escolhida.');}
  finally{busy.current=false;if(!c.signal.aborted)setPending(false);}
 }
 return <section className="account-page staff-invitation"><span className="eyebrow">ACESSO PROFISSIONAL</span><h1>Ative sua conta na Agenda Clara.</h1>
  {error&&<Message>{error}</Message>}{!preview&&!error&&<p role="status">Conferindo convite…</p>}
  {preview&&<><h2>{preview.professionalName} · {preview.businessName}</h2><p>Conta: {preview.email}</p><p>Acesso à sua agenda e aos resultados dos seus atendimentos. O convite não confirma a titularidade do e-mail.</p>
   {done?<><Message kind="success">Acesso ativado. Entre com seu e-mail e a senha que acabou de definir.</Message><button className="button primary" onClick={()=>onLogin(preview.email)}>Entrar na minha agenda</button></>:user?<><p>Saia da conta atual para ativar este convite.</p><button className="button secondary" disabled={pending||!online} onClick={()=>void api('/api/auth/logout',{method:'POST'}).then(onLogout).catch(e=>setError(errorMessage(e)))}>Sair da conta atual</button></>:<form className="stack-form" onSubmit={e=>{e.preventDefault();void accept();}}><fieldset disabled={pending||!online}>
    <label>Nova senha<input type="password" autoComplete="new-password" required minLength={15} maxLength={72} value={password} onChange={e=>setPassword(e.target.value)}/></label>
    <label>Repita a nova senha<input type="password" autoComplete="new-password" required minLength={15} maxLength={72} value={confirmation} onChange={e=>setConfirmation(e.target.value)}/></label><p className="field-note">Use ao menos 15 caracteres. Limite de 72 bytes em UTF-8; caracteres acentuados podem ocupar mais de um byte.</p>
    <button className="button primary" disabled={pending||!online}>{pending?'Ativando…':'Ativar meu acesso'}</button></fieldset></form>}</>}
  {error&&!done&&<button className="button secondary" disabled={!online||pending} onClick={()=>setRetry(v=>v+1)}>Conferir convite novamente</button>}
 </section>;
}

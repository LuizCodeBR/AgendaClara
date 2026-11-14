import { useEffect, useRef, useState } from 'react';
import { api, errorMessage } from './api';
import { Message, Modal } from './components';
import { PrivateReservationLink } from './customerAccess';
import type { Appointment } from './types';
import './customer.css';
interface AccessState { available:boolean;active:boolean;version:number;validUntil:string }
export default function CustomerAccessDialog({appointment,online,onClose,onUpdated,onPending}:{appointment:Appointment;online:boolean;onClose:()=>void;onUpdated:()=>void;onPending:(pending:boolean)=>void}){
  const [state,setState]=useState<AccessState|null>(null),[token,setToken]=useState(''),[error,setError]=useState(''),[pending,setPending]=useState(false),[revision,setRevision]=useState(0);
  const requestKey=useRef(crypto.randomUUID());const endpoint='/api/admin/appointments/'+appointment.id+'/customer-access';
  useEffect(()=>{const controller=new AbortController();setError('');api<AccessState>(endpoint,{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setState(result);}).catch(error=>{if(!controller.signal.aborted)setError(errorMessage(error));});return()=>controller.abort();},[endpoint,revision]);
  async function run(action:'issue'|'revoke'){
    if(!state||pending||!online)return;setPending(true);onPending(true);setError('');
    try{
      if(action==='issue'){const result=await api<{token:string;version:number;validUntil:string}>(endpoint,{method:'POST',body:JSON.stringify({version:state.version,requestKey:requestKey.current})});setToken(result.token);setState({...state,active:true,version:result.version,validUntil:result.validUntil});requestKey.current=crypto.randomUUID();}
      else{setState(await api<AccessState>(endpoint,{method:'DELETE',body:JSON.stringify({version:state.version})}));setToken('');}
      onUpdated();
    }catch(error){setError(errorMessage(error));}finally{setPending(false);onPending(false);}
  }
  return <Modal title="Acesso privado do cliente" busy={pending} onClose={onClose}><p>{appointment.customerName} · {appointment.serviceName}</p><p className="field-note">Gerar um novo link invalida o anterior e os acessos abertos com ele. Entregue o link somente ao cliente desta reserva.</p>
    {!state?<p role="status">Consultando o acesso…</p>:<><p>{state.active?'Existe um link privado ativo para esta reserva.':'Não há link privado ativo para esta reserva.'}</p><div className="customer-actions"><button className="button primary" disabled={pending||!online||!state.available} onClick={()=>void run('issue')}>{pending?'Atualizando…':'Gerar novo link privado'}</button><button className="button danger" disabled={pending||!online||!state.active} onClick={()=>void run('revoke')}>Revogar acesso do cliente</button></div>{!state.available&&<p className="field-note">A geração está indisponível por validade encerrada ou configuração da instalação.</p>}</>}
    {token&&<PrivateReservationLink token={token}/>}
    {error&&<><Message>{error}</Message><button className="button secondary" disabled={pending} onClick={()=>{setToken('');setRevision(value=>value+1);}}>Atualizar dados do acesso</button></>}
    <div className="modal-actions"><button className="button secondary" disabled={pending} onClick={onClose}>Fechar</button></div>
  </Modal>;
}

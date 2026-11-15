import {useEffect,useRef,useState} from 'react';
import type {FormEvent} from 'react';
import {api,apiBlob,ApiError,errorMessage} from './api';
import {Message} from './components';
import {money,plusDays,todayInZone} from './format';
import {downloadReport,fetchReport,reportHeaders,reportQuery,reportRangeError} from './reports';
import type {PeriodReport,ReportFilters,ReportMetrics,ReportOptions} from './reports';
import './reports.css';
const dateLabel=(s:string)=>s.split('-').reverse().join('/');
function MetricsTable({items,label}:{items:{id:string;label:string;metrics:ReportMetrics}[];label:string}){
 const [page,setPage]=useState(0),pages=Math.max(1,Math.ceil(items.length/25));
 return <><div className="report-table-scroll" role="region" aria-label={label} tabIndex={0}><table><caption>{label}</caption><thead><tr><th scope="col">Identificação</th><th scope="col">Total</th><th scope="col">Concluídos</th><th scope="col">Faltas</th><th scope="col">Cancelados</th><th scope="col">Pendentes</th><th scope="col">Sem término</th><th scope="col">Valor concluído</th></tr></thead><tbody>{items.slice(page*25,(page+1)*25).map(item=><tr key={item.id}><th scope="row">{item.label}</th>{[item.metrics.total,item.metrics.completed,item.metrics.noShow,item.metrics.cancelled,item.metrics.pending,item.metrics.upcoming].map((v,i)=><td key={i}>{v}</td>)}<td>{money(item.metrics.completedValueCents)}</td></tr>)}</tbody></table></div>{pages>1&&<div className="report-pages"><button className="button secondary" disabled={page===0} onClick={()=>setPage(v=>v-1)}>Anterior</button><span>Página {page+1} de {pages}</span><button className="button secondary" disabled={page+1===pages} onClick={()=>setPage(v=>v+1)}>Próxima</button></div>}</>;
}
export default function ReportsPanel({businessId,online,zoneId,onUnauthorized}:{businessId:string;online:boolean;zoneId:string;onUnauthorized:()=>void}){
 const initial=()=>{const to=todayInZone(zoneId);return{from:plusDays(to,-29),to,professionalId:'',serviceId:''};};
 const [draft,setDraft]=useState<ReportFilters>(initial),[applied,setApplied]=useState<ReportFilters>(initial),[revision,setRevision]=useState(0);
 const [options,setOptions]=useState<ReportOptions|null>(null),[optionsError,setOptionsError]=useState(''),[optionsRevision,setOptionsRevision]=useState(0);
 const [report,setReport]=useState<PeriodReport|null>(null),[loading,setLoading]=useState(true),[exporting,setExporting]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const exportRequest=useRef<AbortController|null>(null),busy=useRef(false),denied=useRef(onUnauthorized);denied.current=onUnauthorized;
 const optionRequest=useRef<AbortController|null>(null),reportRequest=useRef<AbortController|null>(null),sessionInvalid=useRef(false);
 const failSession=(e:unknown)=>{
  if(!(e instanceof ApiError)||![401,403,412].includes(e.status))return false;
  if(!sessionInvalid.current){
   sessionInvalid.current=true;optionRequest.current?.abort();reportRequest.current?.abort();exportRequest.current?.abort();
   busy.current=false;setReport(null);setOptions(null);setLoading(false);setExporting(false);setNotice('');setError(errorMessage(e));denied.current();
  }
  return true;
 };
 const failSessionRef=useRef(failSession);failSessionRef.current=failSession;
 const signature=reportQuery(draft),dirty=signature!==reportQuery(applied),rangeError=reportRangeError(draft);
 useEffect(()=>{
  if(sessionInvalid.current)return;
  const controller=new AbortController();optionRequest.current=controller;setOptionsError('');
  api<ReportOptions>('/api/admin/reports/options',{signal:controller.signal,headers:reportHeaders(businessId)}).then(v=>{if(!controller.signal.aborted)setOptions(v);}).catch(e=>{if(!controller.signal.aborted&&!failSessionRef.current(e))setOptionsError(errorMessage(e));});
  return()=>controller.abort();
 },[optionsRevision,businessId]);
 useEffect(()=>{
  if(sessionInvalid.current)return;
  const controller=new AbortController();reportRequest.current=controller;setReport(null);setError('');setNotice('');setLoading(true);
  fetchReport(applied,controller.signal,businessId).then(v=>{if(!controller.signal.aborted)setReport(v);}).catch(e=>{if(!controller.signal.aborted&&!failSessionRef.current(e))setError(errorMessage(e));}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[applied,revision,businessId]);
 useEffect(()=>{exportRequest.current?.abort();busy.current=false;setExporting(false);setNotice('');return()=>exportRequest.current?.abort();},[signature,revision,online,businessId]);
 function change(key:keyof ReportFilters,value:string){setDraft(v=>({...v,[key]:value}));}
 function consult(event?:FormEvent){event?.preventDefault();if(sessionInvalid.current||!online||rangeError)return;setApplied({...draft});setRevision(v=>v+1);}
 function preset(days:number){if(sessionInvalid.current||!online)return;const to=todayInZone(zoneId),next={...draft,from:plusDays(to,1-days),to};setDraft(next);setApplied(next);setRevision(v=>v+1);}
 async function exportCsv(){
  if(sessionInvalid.current||!online||busy.current||loading||dirty||!report||rangeError)return;
  const controller=new AbortController();exportRequest.current=controller;busy.current=true;setExporting(true);setError('');setNotice('');
  try{
   const blob=await apiBlob('/api/admin/reports/export?'+reportQuery(applied),{signal:controller.signal,headers:reportHeaders(businessId)});
   if(controller.signal.aborted)return;
   if(blob.type.split(';')[0].trim().toLowerCase()!=='text/csv')throw new ApiError('O servidor não retornou um arquivo CSV. Atualize a consulta e tente novamente.',0);
   downloadReport(blob,applied);setNotice('CSV preparado. O arquivo contém uma nova consulta dos filtros aplicados.');
  }
  catch(e){if(!controller.signal.aborted&&!failSessionRef.current(e))setError(errorMessage(e));}
  finally{if(!controller.signal.aborted){busy.current=false;setExporting(false);}}
 }
 const current=!loading&&!dirty?report:null;
 const maximum=current?Math.max(1,...current.days.map(d=>d.metrics.total)):1;
 return <section className="agenda-panel reports-panel">
  <span className="eyebrow">ACOMPANHE O NEGÓCIO</span><h2>Indicadores e relatórios</h2>
  <p>Consulte até 90 dias por vez. As datas consideram o início atual do atendimento no fuso da empresa.</p>
  <div className="report-presets" aria-label="Períodos rápidos">{[[1,'Hoje'],[7,'Últimos 7 dias'],[30,'Últimos 30 dias']].map(([days,label])=><button key={days} className="button secondary" disabled={sessionInvalid.current||!online} onClick={()=>preset(Number(days))}>{label}</button>)}</div>
  <form onSubmit={consult} className="report-filters">
   <label>Data inicial<input type="date" min="1900-01-01" max="2100-12-31" required value={draft.from} onChange={e=>change('from',e.target.value)}/></label>
   <label>Data final<input type="date" min="1900-01-01" max="2100-12-31" required value={draft.to} onChange={e=>change('to',e.target.value)}/></label>
   <label>Profissional do relatório<select aria-label="Profissional do relatório" value={draft.professionalId} disabled={!options} onChange={e=>change('professionalId',e.target.value)}><option value="">Todos os profissionais</option>{options?.professionals.map(p=><option key={p.id} value={p.id}>{p.name}{p.active?'':' (inativo)'}</option>)}</select></label>
   <label>Serviço do relatório<select aria-label="Serviço do relatório" value={draft.serviceId} disabled={!options} onChange={e=>change('serviceId',e.target.value)}><option value="">Todos os serviços</option>{options?.services.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
   <button className="button primary" disabled={sessionInvalid.current||!online||!!rangeError}>Consultar relatório</button>
  </form>
  {rangeError&&<Message>{rangeError}</Message>}{dirty&&!rangeError&&<Message kind="info">Filtros alterados. Consulte para apresentar os resultados correspondentes.</Message>}
  {optionsError&&<><Message>{optionsError}</Message><button className="button secondary" disabled={sessionInvalid.current||!online} onClick={()=>setOptionsRevision(v=>v+1)}>Recarregar opções</button></>}
  {error&&<Message>{error}</Message>}{notice&&<Message kind="info">{notice}</Message>}
  {!online&&<Message>Sem conexão. Os dados visíveis são da última consulta; reconecte para atualizar ou exportar.</Message>}
  {loading&&<p role="status">Calculando os indicadores…</p>}
  {current&&<>
   <div className="report-caption"><h3>{dateLabel(current.from)} a {dateLabel(current.to)}</h3><p>{current.businessName} · {current.zoneId}</p><p>Profissional: {current.professionalLabel} · Serviço: {current.serviceLabel}</p><small>Consultado em {new Intl.DateTimeFormat('pt-BR',{timeZone:current.zoneId,dateStyle:'short',timeStyle:'short'}).format(new Date(current.generatedAt))}. Atualize para acompanhar alterações.</small></div>
   <div className="report-metrics">{[
    ['Atendimentos do período',String(current.totals.total),'inclui todas as situações'],['Concluídos',String(current.totals.completed),'resultado registrado'],['Faltas',String(current.totals.noShow),'não comparecimento registrado'],['Cancelamentos',String(current.totals.cancelled),'pelo dia reservado'],['Resultados pendentes',String(current.totals.pending),'término passou; resultado não registrado'],['Valor dos serviços concluídos',money(current.totals.completedValueCents),'preço da reserva; não comprova pagamento']
   ].map(([label,value,note])=><article className="report-metric" key={label} aria-label={label}><span>{label}</span><strong>{value}</strong><small>{note}</small></article>)}</div>
   <p className="field-note">Confirmados ainda não encerrados: {current.totals.upcoming}. Resultado pendente não é contado como falta. O valor usa os preços preservados nas reservas concluídas.</p>
   <div className="report-export"><button className="button secondary" disabled={!online||exporting} onClick={()=>void exportCsv()}>{exporting?'Preparando CSV…':'Exportar CSV'}</button><p className="field-note">O CSV consulta os filtros novamente e pode refletir alterações mais recentes. Inclui totais e combinações de profissional/serviço, sem nomes ou telefones de clientes.</p></div>
   {!current.totals.total?<div className="report-empty">Nenhum atendimento encontrado neste período e nestes filtros.</div>:<>
    <h3>Atendimentos por dia</h3><p className="field-note">Quantidade total pelo início do atendimento, incluindo cancelados.</p>
    <ol className="report-chart" aria-label="Quantidade de atendimentos por dia">{current.days.map(day=><li key={day.date}><span>{dateLabel(day.date)}</span><div className="report-bar-track" aria-hidden="true"><div style={{width:(day.metrics.total/maximum*100)+'%'}}/></div><strong>{day.metrics.total}</strong></li>)}</ol>
    <MetricsTable key={current.generatedAt+'professionals'} label="Resumo por profissional" items={current.professionals.map(p=>({id:p.id,label:p.name+(p.active?'':' (inativo)'),metrics:p.metrics}))}/>
    <details><summary>Detalhar por profissional e serviço</summary><p className="field-note">Serviços aparecem com o nome preservado na reserva; um nome antigo pode aparecer em uma linha separada.</p><MetricsTable key={current.generatedAt+'services'} label="Resumo por profissional e serviço" items={current.rows.map((r,i)=>({id:String(i),label:r.professionalName+' · '+r.serviceName,metrics:r.metrics}))}/></details>
   </>}
  </>}
 </section>;
}
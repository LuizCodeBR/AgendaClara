import {api} from './api';
export interface ReportChoice{id:string;name:string;active:boolean}
export interface ReportOptions{professionals:ReportChoice[];services:ReportChoice[]}
export interface ReportFilters{from:string;to:string;professionalId:string;serviceId:string}
export interface ReportMetrics{total:number;completed:number;noShow:number;cancelled:number;pending:number;upcoming:number;completedValueCents:number}
export interface PeriodReport{from:string;to:string;zoneId:string;businessName:string;professionalId:string|null;serviceId:string|null;professionalLabel:string;serviceLabel:string;generatedAt:string;totals:ReportMetrics;professionals:{id:string;name:string;active:boolean;metrics:ReportMetrics}[];rows:{professionalId:string;professionalName:string;active:boolean;serviceId:string;serviceName:string;metrics:ReportMetrics}[];days:{date:string;metrics:ReportMetrics}[]}
export function reportQuery(filters:ReportFilters){const params=new URLSearchParams({from:filters.from,to:filters.to});if(filters.professionalId)params.set('professionalId',filters.professionalId);if(filters.serviceId)params.set('serviceId',filters.serviceId);return params.toString();}
export function reportRangeError(filters:ReportFilters):string{
 const valid=(s:string)=>/^\d{4}-\d{2}-\d{2}$/.test(s)&&Number.isFinite(Date.parse(s+'T12:00:00Z'))&&new Date(s+'T12:00:00Z').toISOString().slice(0,10)===s;
 if(!valid(filters.from)||!valid(filters.to))return 'Informe as duas datas válidas.';
 const days=(Date.parse(filters.to+'T12:00:00Z')-Date.parse(filters.from+'T12:00:00Z'))/86400000+1;
 return filters.from<'1900-01-01'||filters.to>'2100-12-31'||days<1||days>90?'Escolha um período de 1 a 90 dias, entre 1900 e 2100.':'';
}
export const reportHeaders=(businessId:string)=>({'X-Agenda-Business':businessId});
export const fetchReport=(filters:ReportFilters,signal:AbortSignal,businessId:string)=>api<PeriodReport>('/api/admin/reports?'+reportQuery(filters),{signal,headers:reportHeaders(businessId)});
export function downloadReport(blob:Blob,filters:ReportFilters){
 const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='AgendaClara-relatorio-'+filters.from+'-a-'+filters.to+'.csv';link.style.display='none';document.body.appendChild(link);
 try{link.click();}finally{link.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1500);}
}
import {time} from './format';
export interface SummaryProfessional {id:string;name:string;phone:string;version:number}
export interface DailySummary {date:string;zoneId:string;businessName:string;professional:SummaryProfessional;generatedAt:string;appointments:{startsAt:string;customerName:string;serviceName:string}[]}
export const MAX_WHATSAPP_URL=6000;
export function normalizedPhone(input:string):string|null{
 const value=input.trim();if(!value)return '';
 if(value.length>32||!/^\+?[0-9() .-]+$/.test(value))return null;
 const digits=value.replace(/[^0-9]/g,'');return /^[1-9][0-9]{7,14}$/.test(digits)?digits:null;
}
const line=(input:string)=>input.replace(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]+/gu,' ').replace(/\s+/g,' ').trim();
export function whatsappUrl(phone:string,text:string):string|null{
 if(!/^[1-9][0-9]{7,14}$/.test(phone))return null;
 try{const url='https://wa.me/'+phone+'?text='+encodeURIComponent(text);
 return url.length<=MAX_WHATSAPP_URL?url:null;}catch{return null;}
}
export function summaryParts(summary:DailySummary):string[]{
 const date=summary.date.split('-').reverse().join('/');
 const stamp=new Intl.DateTimeFormat('pt-BR',{timeZone:summary.zoneId,dateStyle:'short',timeStyle:'short'}).format(new Date(summary.generatedAt));
 const head='Agenda Clara — agenda de '+date+'\n'+line(summary.businessName)+'\nProfissional: '+line(summary.professional.name)+'\nFuso: '+summary.zoneId;
 const foot='\n\n'+summary.appointments.length+' atendimento(s) confirmado(s) no dia.\nAtualizado em '+stamp+'. Confira alterações na Agenda Clara.';
 const rows=summary.appointments.map(a=>time(a.startsAt,summary.zoneId)+' — '+line(a.customerName)+' — '+line(a.serviceName));
 if(!rows.length)rows.push('Nenhum atendimento confirmado para esta data.');
 const groups:string[][]=[];let group:string[]=[];
 for(const row of rows){
  if(group.length&&!whatsappUrl('123456789012345',head+'\nParte 200/200\n\n'+[...group,row].join('\n')+foot)){groups.push(group);group=[];}
  group.push(row);
 }
 groups.push(group);
 return groups.map((rows,index)=>head+(groups.length>1?'\nParte '+(index+1)+'/'+groups.length:'')+'\n\n'+rows.join('\n')+foot);
}

import {expect,it,vi} from 'vitest';
import {normalizedPhone,summaryParts,whatsappUrl,MAX_WHATSAPP_URL,sameSummary,summaryLifetime,summaryRemaining} from './dailySummary';
import type {DailySummary} from './dailySummary';
export const dailyFixture:DailySummary={date:'2030-06-04',zoneId:'America/Sao_Paulo',businessName:'Negócio Teste',professional:{id:'professional',name:'Ana Exemplo',phone:'5511999991234',version:0},generatedAt:'2030-06-04T11:45:00Z',appointments:[{startsAt:'2030-06-04T12:00:00Z',customerName:'João Exemplo',serviceName:'Corte'}]};
it('formata data, horário local, serviço e momento da atualização',()=>{
 const text=summaryParts(dailyFixture)[0];expect(text).toContain('04/06/2030');expect(text).toContain('09:00 — João Exemplo — Corte');expect(text).toContain('08:45');expect(text).toContain('America/Sao_Paulo');
});
it('aceita telefone internacional e recusa destino com URL ou zero inicial',()=>{
 expect(normalizedPhone('+55 (11) 99999-1234')).toBe('5511999991234');expect(normalizedPhone('')).toBe('');
 for(const input of ['https://evil.test','0123456789','1234567','1234567890123456','55+11999991234'])expect(normalizedPhone(input)).toBeNull();
});
it('gera somente link oficial com conteúdo codificado',()=>{
 const text='João & Maria #teste\nCorte + barba';const url=whatsappUrl(dailyFixture.professional.phone,text)!;
 expect(url.startsWith('https://wa.me/5511999991234?text=')).toBe(true);expect(new URL(url).searchParams.get('text')).toBe(text);
 expect(whatsappUrl('javascript:alert(1)',text)).toBeNull();
 expect(whatsappUrl(dailyFixture.professional.phone,String.fromCharCode(0xD800))).toBeNull();
});
it('divide resumos longos sem perder ou repetir atendimentos',()=>{
 const appointments=Array.from({length:200},(_,i)=>({startsAt:dailyFixture.appointments[0].startsAt,customerName:'Cliente '+i+' '+('á'.repeat(60)),serviceName:'Serviço '+i+' '+('ç'.repeat(60))}));
 const parts=summaryParts({...dailyFixture,appointments});
 expect(parts.length).toBeGreaterThan(1);for(const part of parts)expect(whatsappUrl(dailyFixture.professional.phone,part)!.length).toBeLessThanOrEqual(MAX_WHATSAPP_URL);
 for(let i=0;i<200;i++)expect(parts.join('\n').split('Cliente '+i+' ').length-1).toBe(1);
 expect(parts[0]).toContain('Parte 1/');expect(parts.at(-1)).toContain('Parte '+parts.length+'/'+parts.length);
});
it('informa dia vazio e remove controles que falsificariam linhas',()=>{
 expect(summaryParts({...dailyFixture,appointments:[]})[0]).toContain('Nenhum atendimento confirmado');
 const text=summaryParts({...dailyFixture,appointments:[{...dailyFixture.appointments[0],customerName:'João\n09:00\u202e'}]})[0];
 expect(text).toContain('João 09:00');expect(text).not.toContain('\u202e');
});

it('validade usa ambos os relógios e não cresce com ajuste para trás',()=>{
 const wall=vi.spyOn(Date,'now').mockReturnValue(1000000),mono=vi.spyOn(performance,'now').mockReturnValue(1000);
 try{const start=summaryLifetime();expect(summaryRemaining(start)).toBe(300000);wall.mockReturnValue(999999);expect(summaryRemaining(start)).toBe(0);wall.mockReturnValue(1000000);mono.mockReturnValue(301001);expect(summaryRemaining(start)).toBe(0);mono.mockReturnValue(1001);wall.mockReturnValue(1300001);expect(summaryRemaining(start)).toBe(0);}finally{wall.mockRestore();mono.mockRestore();}
});
it('compara todo o conteúdo e destinatário sem confundir nova hora de consulta com alteração',()=>{
 expect(sameSummary(dailyFixture,{...dailyFixture,generatedAt:'2030-06-04T12:00:00Z'})).toBe(true);
 for(const changed of [{...dailyFixture,date:'2030-06-05'},{...dailyFixture,appointments:[]},{...dailyFixture,professional:{...dailyFixture.professional,phone:''}},{...dailyFixture,professional:{...dailyFixture.professional,version:1}},{...dailyFixture,zoneId:'UTC'},{...dailyFixture,businessName:'Outro negócio'},{...dailyFixture,appointments:[{...dailyFixture.appointments[0],startsAt:'2030-06-04T13:00:00Z'}]}])expect(sameSummary(dailyFixture,changed)).toBe(false);
});

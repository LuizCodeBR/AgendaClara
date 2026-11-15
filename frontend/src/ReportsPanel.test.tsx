import {act,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import ReportsPanel from './ReportsPanel';
import {api,apiBlob,ApiError} from './api';
import {downloadReport} from './reports';
import type {PeriodReport,ReportOptions} from './reports';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn(),apiBlob:vi.fn()}));
vi.mock('./reports',async original=>({...await original<typeof import('./reports')>(),downloadReport:vi.fn()}));
const mocked=vi.mocked(api),blob=vi.mocked(apiBlob),download=vi.mocked(downloadReport);
const metrics={total:5,completed:1,noShow:1,cancelled:1,pending:1,upcoming:1,completedValueCents:1234};
const fixture:PeriodReport={from:'2030-05-06',to:'2030-06-04',zoneId:'America/Sao_Paulo',businessName:'Negócio Teste',professionalId:null,serviceId:null,professionalLabel:'Todos',serviceLabel:'Todos',generatedAt:'2030-06-04T14:00:00Z',totals:metrics,professionals:[{id:'p',name:'Ana Exemplo',active:false,metrics}],rows:[{professionalId:'p',professionalName:'Ana Exemplo',active:false,serviceId:'s',serviceName:'Corte antigo',metrics}],days:[{date:'2030-06-04',metrics}]};
const options:ReportOptions={professionals:[{id:'p',name:'Ana Exemplo',active:false}],services:[{id:'s',name:'Corte atual',active:true}]};
beforeEach(()=>{vi.setSystemTime(new Date('2030-06-04T14:00:00Z'));mocked.mockReset();blob.mockReset();download.mockReset();mocked.mockImplementation(async path=>path.endsWith('/options')?options:fixture);});
afterEach(()=>vi.useRealTimers());
function mount(online=true){const denied=vi.fn();return{...render(<ReportsPanel businessId="business-current" online={online} zoneId="America/Sao_Paulo" onUnauthorized={denied}/>),denied};}
const loaded=()=>screen.findByRole('article',{name:'Atendimentos do período'});
it('mostra indicadores, horário da consulta e preços sem prometer pagamento',async()=>{
 mount();const card=await loaded();expect(within(card).getByText('5')).toBeInTheDocument();expect(within(screen.getByRole('article',{name:'Resultados pendentes'})).getByText('1')).toBeInTheDocument();expect(screen.getByText('preço da reserva; não comprova pagamento')).toBeInTheDocument();expect(screen.getByRole('region',{name:'Resumo por profissional'})).toHaveTextContent('Ana Exemplo (inativo)');
});
it('não exibe nem exporta dados de filtros anteriores enquanto o rascunho mudou',async()=>{
 mount();await loaded();fireEvent.change(screen.getByLabelText('Data inicial'),{target:{value:'2030-06-01'}});expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();expect(screen.queryByRole('button',{name:'Exportar CSV'})).not.toBeInTheDocument();
 await userEvent.setup().click(screen.getByRole('button',{name:'Consultar relatório'}));await loaded();expect(mocked.mock.calls.at(-1)![0]).toContain('from=2030-06-01');
});
it('combina filtros de serviço e profissional e inclui inativos',async()=>{
 mount();await loaded();const user=userEvent.setup();await user.selectOptions(screen.getByLabelText('Profissional do relatório'),'p');await user.selectOptions(screen.getByLabelText('Serviço do relatório'),'s');await user.click(screen.getByRole('button',{name:'Consultar relatório'}));await loaded();expect(mocked.mock.calls.at(-1)![0]).toContain('professionalId=p&serviceId=s');
});
it('atalho consulta os sete dias incluindo hoje no fuso da empresa',async()=>{
 mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Últimos 7 dias'}));await loaded();expect(mocked.mock.calls.at(-1)![0]).toContain('from=2030-05-29&to=2030-06-04');
});
it('recusa período maior que noventa dias sem enviar a consulta',async()=>{
 mount();await loaded();fireEvent.change(screen.getByLabelText('Data inicial'),{target:{value:'2030-01-01'}});expect(screen.getByRole('button',{name:'Consultar relatório'})).toBeDisabled();expect(mocked).toHaveBeenCalledTimes(2);
});
it('resposta atrasada de outra consulta é ignorada',async()=>{
 let finish!:(v:unknown)=>void;mocked.mockImplementation(path=>path.endsWith('/options')?Promise.resolve(options):new Promise(resolve=>{finish=resolve;}));mount();await waitFor(()=>expect(mocked).toHaveBeenCalledTimes(2));mocked.mockResolvedValueOnce({...fixture,totals:{...metrics,total:12}});
 await userEvent.setup().click(screen.getByRole('button',{name:'Hoje'}));await loaded();await act(async()=>{finish(fixture);});expect(within(screen.getByRole('article',{name:'Atendimentos do período'})).getByText('12')).toBeInTheDocument();
});
it('falha de consulta não mantém resultados antigos como se fossem atuais',async()=>{
 mount();await loaded();mocked.mockRejectedValueOnce(new ApiError('Falha temporária.',503));await userEvent.setup().click(screen.getByRole('button',{name:'Consultar relatório'}));await screen.findByText('Falha temporária.');expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();
});
it('exporta uma nova consulta com exatamente os filtros aplicados',async()=>{
 const content=new Blob(['csv'],{type:'text/csv'});blob.mockResolvedValue(content);mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));await screen.findByText(/CSV preparado/);expect(blob.mock.calls[0][0]).toBe('/api/admin/reports/export?from=2030-05-06&to=2030-06-04');expect(download).toHaveBeenCalledWith(content,expect.objectContaining({from:'2030-05-06',to:'2030-06-04'}));
});
it('mudança de filtros cancela exportação e não baixa resposta tardia',async()=>{
 let finish!:(v:Blob)=>void;blob.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));const signal=blob.mock.calls[0][1]!.signal;fireEvent.change(screen.getByLabelText('Data inicial'),{target:{value:'2030-06-01'}});expect(signal?.aborted).toBe(true);await act(async()=>{finish(new Blob(['old']));});expect(download).not.toHaveBeenCalled();
});
it('sair do painel cancela consultas e download pendentes',async()=>{
 let finish!:(v:Blob)=>void;blob.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const {unmount}=mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));const signal=blob.mock.calls[0][1]!.signal;unmount();expect(signal?.aborted).toBe(true);await act(async()=>{finish(new Blob(['old']));});expect(download).not.toHaveBeenCalled();
});
it('sessão expirada na exportação limpa dados e volta ao login',async()=>{
 blob.mockRejectedValue(new ApiError('Entre novamente.',401));const {denied}=mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));await screen.findByText('Entre novamente.');expect(denied).toHaveBeenCalledOnce();expect(download).not.toHaveBeenCalled();expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();
});
it('período vazio informa ausência de atendimentos e permite CSV de totais zerados',async()=>{
 mocked.mockImplementation(async path=>path.endsWith('/options')?options:{...fixture,totals:{total:0,completed:0,noShow:0,cancelled:0,pending:0,upcoming:0,completedValueCents:0},professionals:[],rows:[],days:[]});mount();await screen.findByText('Nenhum atendimento encontrado neste período e nestes filtros.');expect(screen.getByRole('button',{name:'Exportar CSV'})).toBeEnabled();
});
it('sem conexão bloqueia consulta e exportação',async()=>{mount(false);await loaded();expect(screen.getByRole('button',{name:'Consultar relatório'})).toBeDisabled();expect(screen.getByRole('button',{name:'Exportar CSV'})).toBeDisabled();});
it('tabelas têm paginação e não omitem registros fora da primeira página',async()=>{
 mocked.mockImplementation(async path=>path.endsWith('/options')?options:{...fixture,professionals:Array.from({length:26},(_,i)=>({id:String(i),name:'Pessoa '+i,active:true,metrics}))});mount();await loaded();expect(screen.queryByText('Pessoa 25')).not.toBeInTheDocument();await userEvent.setup().click(screen.getByRole('button',{name:'Próxima'}));expect(screen.getByText('Pessoa 25')).toBeInTheDocument();expect(screen.queryByText('Pessoa 0')).not.toBeInTheDocument();
});
it('vincula opções, consulta e CSV à empresa que está na tela',async()=>{
 blob.mockResolvedValue(new Blob(['csv'],{type:'text/csv'}));mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));await screen.findByText(/CSV preparado/);
 for(const [,request] of mocked.mock.calls)expect(new Headers(request?.headers).get('X-Agenda-Business')).toBe('business-current');
 expect(new Headers(blob.mock.calls[0][1]?.headers).get('X-Agenda-Business')).toBe('business-current');
});
it('troca de empresa durante exportação remove os dados e exige entrar novamente',async()=>{
 blob.mockRejectedValue(new ApiError('A conta ativa mudou. Entre novamente.',412));const {denied}=mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));await screen.findByText('A conta ativa mudou. Entre novamente.');expect(denied).toHaveBeenCalledOnce();expect(download).not.toHaveBeenCalled();expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();
});
it('resposta HTML com sucesso HTTP não é baixada como relatório CSV',async()=>{
 blob.mockResolvedValue(new Blob(['<html>Entrada</html>'],{type:'text/html'}));mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));await waitFor(()=>expect(blob).toHaveBeenCalledOnce());expect(download).not.toHaveBeenCalled();expect(await screen.findByText(/não retornou um arquivo CSV/)).toBeInTheDocument();
});

it('uma falha de autorização cancela a outra consulta e ignora seu resultado tardio',async()=>{
 let finish!:(v:unknown)=>void;
 mocked.mockImplementation(path=>path.endsWith('/options')?Promise.reject(new ApiError('Conta alterada.',412)):new Promise(resolve=>{finish=resolve;}));
 const {denied}=mount();await waitFor(()=>expect(denied).toHaveBeenCalledOnce());expect(mocked.mock.calls[1][1]?.signal?.aborted).toBe(true);
 await act(async()=>{finish(fixture);});expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();expect(screen.getByRole('button',{name:'Consultar relatório'})).toBeDisabled();
});
it('acesso negado nas opções interrompe também um CSV que já estava sendo preparado',async()=>{
 let rejectOptions!:(e:Error)=>void,finishCsv!:(v:Blob)=>void;
 mocked.mockImplementation(path=>path.endsWith('/options')?new Promise((_,reject)=>{rejectOptions=reject;}):Promise.resolve(fixture));
 blob.mockImplementation(()=>new Promise(resolve=>{finishCsv=resolve;}));const {denied}=mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));
 const signal=blob.mock.calls[0][1]?.signal;
 await act(async()=>{rejectOptions(new ApiError('Acesso negado.',403));});expect(signal?.aborted).toBe(true);expect(denied).toHaveBeenCalledOnce();
 await act(async()=>{finishCsv(new Blob(['old'],{type:'text/csv'}));});expect(download).not.toHaveBeenCalled();expect(screen.queryByRole('article',{name:'Atendimentos do período'})).not.toBeInTheDocument();
});
it('tempo esgotado informa a falha sem baixar arquivo nem repetir a consulta sozinho',async()=>{
 blob.mockRejectedValue(new ApiError('Reduza o período e tente novamente.',503));mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));
 await screen.findByText('Reduza o período e tente novamente.');expect(blob).toHaveBeenCalledOnce();expect(download).not.toHaveBeenCalled();expect(screen.getByRole('button',{name:'Exportar CSV'})).toBeEnabled();expect(screen.getByRole('article',{name:'Atendimentos do período'})).toBeInTheDocument();
});
it('cliques repetidos não iniciam exportações simultâneas',async()=>{
 let finish!:(v:Blob)=>void;blob.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));mount();await loaded();const button=screen.getByRole('button',{name:'Exportar CSV'});
 fireEvent.click(button);fireEvent.click(button);expect(blob).toHaveBeenCalledOnce();await act(async()=>{finish(new Blob(['csv'],{type:'text/csv'}));});expect(download).toHaveBeenCalledOnce();
});
it('perder a conexão cancela o CSV pendente mesmo que a resposta chegue depois',async()=>{
 let finish!:(v:Blob)=>void;blob.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const {rerender,denied}=mount();await loaded();await userEvent.setup().click(screen.getByRole('button',{name:'Exportar CSV'}));
 const signal=blob.mock.calls[0][1]?.signal;rerender(<ReportsPanel businessId="business-current" online={false} zoneId="America/Sao_Paulo" onUnauthorized={denied}/>);
 expect(signal?.aborted).toBe(true);await act(async()=>{finish(new Blob(['old'],{type:'text/csv'}));});expect(download).not.toHaveBeenCalled();
});

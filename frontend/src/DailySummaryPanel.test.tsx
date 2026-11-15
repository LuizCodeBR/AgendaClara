import {act,fireEvent,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import DailySummaryPanel from './DailySummaryPanel';
import {api,ApiError} from './api';
import type {DailySummary} from './dailySummary';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const mocked=vi.mocked(api);
const fixture:DailySummary={date:'2030-06-04',zoneId:'America/Sao_Paulo',businessName:'Negócio Teste',professional:{id:'professional',name:'Ana Exemplo',phone:'5511999991234',version:0},generatedAt:'2030-06-04T11:45:00Z',appointments:[{startsAt:'2030-06-04T12:00:00Z',customerName:'João Exemplo',serviceName:'Corte'}]};
const people=[{id:'professional',name:'Ana Exemplo',active:true},{id:'second',name:'Bruno Exemplo',active:true}];
beforeEach(()=>{mocked.mockReset();mocked.mockImplementation(async path=>path==='/api/admin/professionals'?people:fixture);});
afterEach(()=>{vi.useRealTimers();});
function mount(online=true){const denied=vi.fn(),dirty=vi.fn();const view=render(<DailySummaryPanel zoneId="America/Sao_Paulo" online={online} onDirty={dirty} onUnauthorized={denied}/>);return{...view,denied,dirty};}
const check=()=>screen.getByRole('checkbox',{name:/Conferi a prévia/});
it('exige conferência e abre link oficial com o mesmo texto da prévia',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');
 expect(screen.getByRole('button',{name:'Abrir WhatsApp para conferir'})).toBeDisabled();
 await userEvent.setup().click(check());
 const link=screen.getByRole('link',{name:'Abrir WhatsApp para conferir'});
 expect(new URL(link.getAttribute('href')!).searchParams.get('text')).toBe((screen.getByLabelText('Prévia da mensagem') as HTMLTextAreaElement).value);
 expect(link).toHaveAttribute('rel','noopener noreferrer');expect(link).toHaveAttribute('referrerpolicy','no-referrer');
});
it('copia somente após revisão e informa que o usuário precisa enviar',async()=>{
 const user=userEvent.setup();const copy=vi.spyOn(navigator.clipboard,'writeText').mockResolvedValue();
 mount();await screen.findByLabelText('Prévia da mensagem');await user.click(check());await user.click(screen.getByRole('button',{name:'Copiar resumo'}));
 expect(copy).toHaveBeenCalledWith(expect.stringContaining('João Exemplo'));
 await screen.findByText('Texto copiado. Cole na conversa e confira o destinatário antes de enviar.');
});
it('oferece seleção manual quando a área de transferência é recusada',async()=>{
 const user=userEvent.setup();vi.spyOn(navigator.clipboard,'writeText').mockRejectedValue(new Error('Denied'));
 mount();const preview=await screen.findByLabelText('Prévia da mensagem');await user.click(check());await user.click(screen.getByRole('button',{name:'Copiar resumo'}));
 await screen.findByText(/Cópia automática indisponível/);expect(preview).toHaveFocus();
});
it('salva contato com versão e impede compartilhar uma edição pendente',async()=>{
 mocked.mockImplementation(async(path,options)=>path==='/api/admin/professionals'?people:options?.method==='PUT'?{...fixture.professional,phone:'5511988881234',version:1}:fixture);
 const {dirty}=mount();await screen.findByLabelText('Telefone com DDI');const user=userEvent.setup();await user.clear(screen.getByLabelText('Telefone com DDI'));await user.type(screen.getByLabelText('Telefone com DDI'),'+55 11 98888-1234');
 expect(check()).toBeDisabled();expect(screen.getByLabelText('Profissional do resumo')).toBeDisabled();expect(dirty).toHaveBeenCalledWith(true);
 await user.click(screen.getByRole('button',{name:'Salvar contato'}));await waitFor(()=>expect(check()).toBeEnabled());
 expect(mocked.mock.calls.find(c=>c[1]?.method==='PUT')?.[1]?.body).toBe(JSON.stringify({phone:'+55 11 98888-1234',version:0}));
 expect(check()).toBeEnabled();expect(check()).not.toBeChecked();
});
it('conflito preserva rascunho e exige atualização explícita',async()=>{
 mocked.mockImplementation(async(path,options)=>path==='/api/admin/professionals'?people:options?.method==='PUT'?Promise.reject(new ApiError('O cadastro mudou.',409)):fixture);
 mount();await screen.findByLabelText('Telefone com DDI');fireEvent.change(screen.getByLabelText('Telefone com DDI'),{target:{value:'5511988881234'}});
 await userEvent.setup().click(screen.getByRole('button',{name:'Salvar contato'}));await screen.findByText('O cadastro mudou.');
 expect(screen.getByLabelText('Telefone com DDI')).toHaveValue('5511988881234');expect(check()).toBeDisabled();
 await userEvent.setup().click(screen.getByRole('button',{name:'Atualizar e descartar edição do contato'}));await waitFor(()=>expect(screen.getByLabelText('Telefone com DDI')).toHaveValue(fixture.professional.phone));
});
it('resposta antiga não substitui o profissional selecionado',async()=>{
 let resolveOld!:(value:unknown)=>void;
 mocked.mockResolvedValueOnce(people).mockImplementationOnce(()=>new Promise(resolve=>{resolveOld=resolve;})).mockResolvedValueOnce({...fixture,professional:{...fixture.professional,id:'second',name:'Bruno Exemplo'}});
 mount();await waitFor(()=>expect(mocked).toHaveBeenCalledTimes(2));
 // Change the date while the first request is pending.
 fireEvent.change(screen.getByLabelText('Data do resumo'),{target:{value:'2030-06-05'}});
 await screen.findByLabelText('Prévia da mensagem');
 await act(async()=>{resolveOld(fixture);});
 expect((screen.getByLabelText('Prévia da mensagem') as HTMLTextAreaElement).value).toContain('Bruno Exemplo');
});
it('expira após cinco minutos e exige nova consulta',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');
 vi.useFakeTimers(); // Install timeout only for the fresh request.
 fireEvent.click(screen.getByRole('button',{name:'Atualizar resumo'}));
 await act(async()=>{await Promise.resolve();await Promise.resolve();});
 await act(async()=>{vi.advanceTimersByTime(300001);});
 expect(screen.getByText(/passaram cinco minutos/)).toBeInTheDocument();expect(check()).toBeDisabled();
});
it('sem contato permite copiar, mas não abre uma conversa direcionada',async()=>{
 mocked.mockImplementation(async path=>path==='/api/admin/professionals'?people:{...fixture,professional:{...fixture.professional,phone:''}});
 mount();await screen.findByLabelText('Prévia da mensagem');await userEvent.setup().click(check());
 expect(screen.getByRole('button',{name:'Copiar resumo'})).toBeEnabled();expect(screen.getByRole('button',{name:'Abrir WhatsApp para conferir'})).toBeDisabled();
});
it('sem conexão impede compartilhamento e gravação',async()=>{
 mount(false);await screen.findByLabelText('Prévia da mensagem');expect(check()).toBeDisabled();
 expect(screen.getByRole('button',{name:'Atualizar resumo'})).toBeDisabled();expect(screen.getByLabelText('Telefone com DDI')).toBeDisabled();
});
it('sessão expirada devolve o controle ao login',async()=>{
 mocked.mockImplementation(async path=>path==='/api/admin/professionals'?people:Promise.reject(new ApiError('Entre novamente.',401)));
 const {denied}=mount();await screen.findByText('Entre novamente.');expect(denied).toHaveBeenCalledOnce();
});

it('consulta novamente ao confirmar e exige nova conferência se o destinatário mudou',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');
 mocked.mockResolvedValueOnce({...fixture,professional:{...fixture.professional,phone:'5511000000000',version:1}});
 await userEvent.setup().click(check());
 await waitFor(()=>expect(screen.getByLabelText('Telefone com DDI')).toHaveValue('5511000000000'));
 expect(check()).not.toBeChecked();expect(screen.queryByRole('link',{name:'Abrir WhatsApp para conferir'})).not.toBeInTheDocument();
});
it('falha de gravação exige reconciliar com o servidor antes de reutilizar o contato',async()=>{
 mocked.mockImplementation(async(path,options)=>path==='/api/admin/professionals'?people:options?.method==='PUT'?Promise.reject(new ApiError('Resposta perdida.',0)):fixture);
 mount();await screen.findByLabelText('Telefone com DDI');fireEvent.change(screen.getByLabelText('Telefone com DDI'),{target:{value:'5511000000000'}});
 await userEvent.setup().click(screen.getByRole('button',{name:'Salvar contato'}));await screen.findByText('Resposta perdida.');
 expect(screen.getByRole('button',{name:'Descartar edição do contato'})).toBeDisabled();
 expect(screen.getByRole('button',{name:'Atualizar e descartar edição do contato'})).toBeEnabled();
});
it('consulta atrasada não recebe mais cinco minutos quando chega',async()=>{
 let finish!:(value:unknown)=>void;mocked.mockResolvedValueOnce(people).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
 const now=vi.spyOn(Date,'now').mockReturnValue(1000000);
 try{mount();await waitFor(()=>expect(mocked).toHaveBeenCalledTimes(2));now.mockReturnValue(1300001);await act(async()=>{finish(fixture);});expect(check()).toBeDisabled();}finally{now.mockRestore();}
});
it('sair para outro aplicativo invalida a conferência mesmo sem esperar o temporizador',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');await userEvent.setup().click(check());
 const hidden=vi.spyOn(document,'visibilityState','get').mockReturnValue('hidden');
 try{fireEvent(document,new Event('visibilitychange'));expect(check()).toBeDisabled();expect(check()).not.toBeChecked();}finally{hidden.mockRestore();}
});
it('não usa sucesso tardio da área de transferência para selecionar outra prévia',async()=>{
 const user=userEvent.setup();let fail!:(reason:unknown)=>void;vi.spyOn(navigator.clipboard,'writeText').mockImplementation(()=>new Promise((_,reject)=>{fail=reject;}));
 mount();await screen.findByLabelText('Prévia da mensagem');await user.click(check());await user.click(screen.getByRole('button',{name:'Copiar resumo'}));
 await user.click(screen.getByRole('button',{name:'Atualizar resumo'}));await screen.findByLabelText('Prévia da mensagem');
 await act(async()=>{fail(new Error('Denied'));});expect(screen.queryByText(/Cópia automática indisponível/)).not.toBeInTheDocument();
});

it('cancelamento em outra janela atualiza a prévia sem autorizar o texto anterior',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');mocked.mockResolvedValueOnce({...fixture,appointments:[]});
 await userEvent.setup().click(check());await screen.findByText(/A agenda ou o cadastro mudou/);
 expect((screen.getByLabelText('Prévia da mensagem') as HTMLTextAreaElement).value).toContain('Nenhum atendimento confirmado');expect(check()).not.toBeChecked();
});
it('nova conferência detecta sessão encerrada e retira os dados privados',async()=>{
 const {denied}=mount();await screen.findByLabelText('Prévia da mensagem');mocked.mockRejectedValueOnce(new ApiError('Entre novamente.',401));
 await userEvent.setup().click(check());await screen.findByText('Entre novamente.');expect(denied).toHaveBeenCalledOnce();expect(screen.queryByLabelText('Prévia da mensagem')).not.toBeInTheDocument();
});
it('falha na nova consulta não libera compartilhamento',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');mocked.mockRejectedValueOnce(new ApiError('Sem conexão.',0));
 await userEvent.setup().click(check());await screen.findByText('Sem conexão.');expect(check()).not.toBeChecked();expect(check()).toBeDisabled();
});
it('duplo clique não duplica a conferência nem altera filtros durante a consulta',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');let finish!:(value:unknown)=>void;mocked.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
 fireEvent.click(check());fireEvent.click(check());expect(mocked).toHaveBeenCalledTimes(3);expect(screen.getByLabelText('Data do resumo')).toBeDisabled();expect(screen.getByLabelText('Telefone com DDI')).toBeDisabled();
 await act(async()=>{finish(fixture);});expect(check()).toBeChecked();
});
it('resposta pendente depois de sair do aplicativo não revalida a prévia',async()=>{
 mount();await screen.findByLabelText('Prévia da mensagem');let finish!:(value:unknown)=>void;mocked.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
 fireEvent.click(check());const hidden=vi.spyOn(document,'visibilityState','get').mockReturnValue('hidden');
 try{fireEvent(document,new Event('visibilitychange'));hidden.mockReturnValue('visible');await act(async()=>{finish(fixture);});expect(check()).toBeDisabled();expect(check()).not.toBeChecked();}finally{hidden.mockRestore();}
});
it('gravação bem sucedida recarrega contato e agenda juntos',async()=>{
 mount();await screen.findByLabelText('Telefone com DDI');
 mocked.mockResolvedValueOnce({...fixture.professional,phone:'5511000000000',version:1}).mockResolvedValueOnce({...fixture,professional:{...fixture.professional,phone:'5511000000000',version:1},appointments:[]});
 fireEvent.change(screen.getByLabelText('Telefone com DDI'),{target:{value:'5511000000000'}});await userEvent.setup().click(screen.getByRole('button',{name:'Salvar contato'}));
 await waitFor(()=>expect((screen.getByLabelText('Prévia da mensagem') as HTMLTextAreaElement).value).toContain('Nenhum atendimento confirmado'));expect(screen.getByLabelText('Telefone com DDI')).toHaveValue('5511000000000');expect(check()).not.toBeChecked();
});
it('resposta de conferência após desmontar é descartada',async()=>{
 const {unmount,denied}=mount();await screen.findByLabelText('Prévia da mensagem');let fail!:(reason:unknown)=>void;mocked.mockImplementationOnce(()=>new Promise((_,reject)=>{fail=reject;}));fireEvent.click(check());
 const signal=mocked.mock.calls.at(-1)![1]!.signal;unmount();expect(signal?.aborted).toBe(true);await act(async()=>{fail(new ApiError('Sessão anterior.',401));});expect(denied).not.toHaveBeenCalled();
});

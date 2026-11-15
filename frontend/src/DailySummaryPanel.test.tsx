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
 await user.click(screen.getByRole('button',{name:'Salvar contato'}));await screen.findByText(/Contato salvo/);
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
 expect(screen.getByText(/mais de cinco minutos/)).toBeInTheDocument();expect(check()).toBeDisabled();
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

import {act,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {api,ApiError} from './api';
import TeamAccessPanel from './TeamAccessPanel';
import ProfessionalInvitePage from './ProfessionalInvitePage';
import ProfessionalAgendaPage from './ProfessionalAgendaPage';
import AdminPage from './AdminPage';
import {owner} from './test/fixtures';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const mocked=vi.mocked(api);
const staff={...owner,id:'staff-account',role:'PROFESSIONAL' as const,professionalId:'p'};
const person={professionalId:'p',name:'Ana Exemplo',active:true,version:0,email:'',enabled:false,inviteExpiresAt:null as string|null};
const item={id:'a',customerName:'Cliente próprio',serviceName:'Corte',startsAt:'2030-06-04T12:00:00Z',endsAt:'2030-06-04T12:30:00Z',status:'BOOKED',version:0};
const agenda={date:'2030-06-04',zoneId:owner.business.zoneId,professionalName:'Ana Exemplo',appointments:[item]};
const preview={businessName:'Negócio Teste',professionalName:'Ana Exemplo',email:'ana@example.test',expiresAt:'2030-06-05T14:00:00Z'};
beforeEach(()=>{HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};vi.setSystemTime(new Date('2030-06-04T14:00:00Z'));mocked.mockReset();mocked.mockResolvedValue(agenda);});
afterEach(()=>vi.useRealTimers());
function team(online=true){const denied=vi.fn();return{...render(<TeamAccessPanel accountId="owner-account" online={online} onUnauthorized={denied} onDirty={()=>{}}/>),denied};}
function mine(online=true){const changed=vi.fn();return{...render(<ProfessionalAgendaPage user={staff} online={online} onUser={changed} onNavigationBlocked={()=>{}}/>),changed};}
it('gera convite com versão e contexto da conta, exibindo link somente após reconciliação',async()=>{
 let rows=[person];mocked.mockImplementation(async(path)=>{if(path.endsWith('/invite')){rows=[{...person,version:1,email:'ana@example.test',inviteExpiresAt:'2030-06-05T14:00:00Z'}] as typeof rows;return{token:'a'.repeat(43),expiresAt:'2030-06-05T14:00:00Z'};}return rows;});
 team();await screen.findByText('Sem acesso ativo');fireEvent.change(screen.getByLabelText('E-mail do profissional'),{target:{value:'ana@example.test'}});fireEvent.click(screen.getByRole('button',{name:'Gerar convite'}));
 await screen.findByLabelText('Link privado do convite');expect((screen.getByLabelText('Link privado do convite') as HTMLTextAreaElement).value).toContain('#/aceitar-convite?token=');
 expect(mocked.mock.calls.find(c=>c[0].endsWith('/invite'))?.[1]).toMatchObject({headers:{'X-Agenda-Account':'owner-account'},body:JSON.stringify({email:'ana@example.test',version:0})});
});
it('confirma revogação sem excluir profissional ou reservas',async()=>{
 mocked.mockImplementation(async(path)=>path.endsWith('/revoke')?undefined:[{...person,version:2,email:'ana@example.test',enabled:true}]);
 team();await screen.findByText('Acesso ativo');fireEvent.click(screen.getByRole('button',{name:'Revogar acesso e convites'}));expect(mocked.mock.calls.some(c=>c[0].endsWith('/revoke'))).toBe(false);fireEvent.click(screen.getByRole('button',{name:'Confirmar revogação'}));await screen.findByText(/Acesso e convites revogados/);expect(mocked.mock.calls.find(c=>c[0].endsWith('/revoke'))?.[1]?.body).toBe('{"version":2}');
});
it('falha de autorização limpa acessos e pede novo login',async()=>{
 mocked.mockRejectedValue(new ApiError('Conta mudou',412));const {denied}=team();await waitFor(()=>expect(denied).toHaveBeenCalledOnce());expect(screen.queryByText('Ana Exemplo')).not.toBeInTheDocument();
});
it('convite não é exibido se a atualização após gerar falhar',async()=>{
 let reads=0;mocked.mockImplementation(async path=>{if(path.endsWith('/invite'))return{token:'b'.repeat(43)};if(++reads>1)throw new ApiError('Sem conexão',0);return[person];});
 team();await screen.findByText('Sem acesso ativo');fireEvent.change(screen.getByLabelText('E-mail do profissional'),{target:{value:'ana@example.test'}});fireEvent.click(screen.getByRole('button',{name:'Gerar convite'}));await screen.findByText(/Atualize os acessos antes/);expect(screen.queryByLabelText('Link privado do convite')).not.toBeInTheDocument();
});
it('ativação exige senhas iguais e envia token no corpo, sem login automático',async()=>{
 mocked.mockImplementation(async path=>path.endsWith('/preview')?preview:undefined);const login=vi.fn();
 render(<ProfessionalInvitePage token={'a'.repeat(43)} user={null} online onLogout={()=>{}} onLogin={login}/>);
 await screen.findByText('Conta: ana@example.test');fireEvent.change(screen.getByLabelText('Nova senha',{exact:true}),{target:{value:'Senha longa de teste 123!'}});fireEvent.change(screen.getByLabelText('Repita a nova senha'),{target:{value:'outra senha de teste 123!'}});fireEvent.click(screen.getByRole('button',{name:'Ativar meu acesso'}));await screen.findByText('As senhas devem ser iguais.');expect(mocked.mock.calls.some(c=>c[0].endsWith('/accept'))).toBe(false);
 fireEvent.change(screen.getByLabelText('Repita a nova senha'),{target:{value:'Senha longa de teste 123!'}});fireEvent.click(screen.getByRole('button',{name:'Ativar meu acesso'}));await screen.findByText(/Acesso ativado/);expect(login).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Entrar na minha agenda'}));expect(login).toHaveBeenCalledWith('ana@example.test');
});
it('conta já autenticada precisa sair antes de ativar convite',async()=>{
 mocked.mockResolvedValue(preview);render(<ProfessionalInvitePage token={'a'.repeat(43)} user={owner} online onLogout={()=>{}} onLogin={()=>{}}/>);await screen.findByText(/Saia da conta atual/);expect(screen.queryByLabelText('Nova senha',{exact:true})).not.toBeInTheDocument();
});
it('profissional usa painel próprio e não consulta rotas administrativas',async()=>{
 render(<AdminPage user={staff} onUser={()=>{}} demo={false} online onBook={()=>{}}/>);
 await screen.findByText('Cliente próprio');expect(screen.getByRole('heading',{name:'Minha agenda'})).toBeInTheDocument();expect(screen.queryByRole('button',{name:'Indicadores e relatórios'})).not.toBeInTheDocument();expect(mocked.mock.calls.every(c=>!c[0].startsWith('/api/admin/'))).toBe(true);
 expect(mocked.mock.calls[0][1]).toMatchObject({headers:{'X-Agenda-Account':'staff-account'}});
});
it('registra resultado próprio com versão e repetição identificada',async()=>{
 mocked.mockImplementation(async path=>path.endsWith('/outcome')?{}:agenda);mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Registrar resultado'}));fireEvent.click(screen.getByRole('button',{name:'Confirmar resultado'}));await waitFor(()=>expect(mocked.mock.calls.some(c=>c[0]==='/api/professional/appointments/a/outcome')).toBe(true));const sent=JSON.parse(mocked.mock.calls.find(c=>c[0].endsWith('/outcome'))![1]!.body as string);expect(sent).toMatchObject({status:'COMPLETED',version:0,reason:''});expect(sent.requestKey).toMatch(/^[a-f0-9-]{36}$/);
});
it('acesso revogado remove agenda e solicita nova entrada',async()=>{
 mocked.mockRejectedValue(new ApiError('Acesso revogado',403));const {changed}=mine();await waitFor(()=>expect(changed).toHaveBeenCalledWith(null));expect(screen.queryByText('Cliente próprio')).not.toBeInTheDocument();
});
it('resposta antiga não reaparece depois de trocar a data',async()=>{
 let resolve!:(a:unknown)=>void;mocked.mockImplementation(path=>path.endsWith('2030-06-04')?new Promise(r=>resolve=r):Promise.resolve({...agenda,date:'2030-06-05',appointments:[]}));
 mine();fireEvent.change(screen.getByLabelText('Data da minha agenda'),{target:{value:'2030-06-05'}});await screen.findByText('Nenhum atendimento nesta data.');await act(async()=>resolve(agenda));expect(screen.queryByText('Cliente próprio')).not.toBeInTheDocument();
});
it('sem conexão remove dados e impede consulta',async()=>{
 const props={user:staff,onUser:vi.fn(),onNavigationBlocked:vi.fn()};const {rerender}=render(<ProfessionalAgendaPage {...props} online/>);await screen.findByText('Cliente próprio');rerender(<ProfessionalAgendaPage {...props} online={false}/>);await screen.findByText(/Reconecte para consultar ou alterar/);expect(screen.queryByText('Cliente próprio')).not.toBeInTheDocument();expect(screen.getByRole('button',{name:'Atualizar agenda'})).toBeDisabled();
});
it('histórico restrito não exige dados administrativos',async()=>{
 mocked.mockImplementation(async path=>path.includes('/history')?{events:[{version:1,action:'OUTCOME',status:'COMPLETED',actor:'Profissional',occurredAt:'2030-06-04T13:00:00Z'}],nextBeforeVersion:null}:agenda);mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Histórico'}));await screen.findByText(/Profissional/);expect(mocked.mock.calls.some(c=>c[0]==='/api/professional/appointments/a/history')).toBe(true);
});

it('não compartilha um convite substituído durante a atualização dos acessos',async()=>{
 let reads=0;mocked.mockImplementation(async path=>{
  if(path.endsWith('/invite'))return{token:'c'.repeat(43),expiresAt:'2030-06-05T14:00:00Z'};
  return ++reads===1?[person]:[{...person,version:2,email:'outra@example.test',inviteExpiresAt:'2030-06-05T14:00:01Z'}];
 });
 team();await screen.findByText('Sem acesso ativo');fireEvent.change(screen.getByLabelText('E-mail do profissional'),{target:{value:'ana@example.test'}});
 fireEvent.click(screen.getByRole('button',{name:'Gerar convite'}));await waitFor(()=>expect(mocked.mock.calls.length).toBe(3));
 expect(screen.queryByLabelText('Link privado do convite')).not.toBeInTheDocument();
 await screen.findByText(/O convite mudou/);
});
it('trocar a data encerra o histórico e descarta a resposta que ficou em andamento',async()=>{
 let resolve!:(a:unknown)=>void;mocked.mockImplementation(path=>path.includes('/history')?new Promise(r=>resolve=r):Promise.resolve(agenda));
 mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Histórico'}));
 fireEvent.change(screen.getByLabelText('Data da minha agenda'),{target:{value:'2030-06-05'}});
 await act(async()=>resolve({events:[{version:1,action:'OUTCOME',status:'COMPLETED',actor:'Registro antigo',occurredAt:'2030-06-04T13:00:00Z'}],nextBeforeVersion:null}));
 expect(screen.queryByRole('dialog')).not.toBeInTheDocument();expect(screen.queryByText(/Registro antigo/)).not.toBeInTheDocument();
});

it('voltar à janela descarta um histórico ainda em trânsito antes de atualizar a agenda',async()=>{
 let resolve!:(a:unknown)=>void;mocked.mockImplementation(path=>path.includes('/history')?new Promise(r=>resolve=r):Promise.resolve(agenda));
 mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Histórico'}));fireEvent(window,new Event('focus'));
 await act(async()=>resolve({events:[{version:1,action:'OUTCOME',status:'COMPLETED',actor:'Resposta antiga',occurredAt:'2030-06-04T13:00:00Z'}],nextBeforeVersion:null}));
 expect(screen.queryByRole('dialog')).not.toBeInTheDocument();expect(screen.queryByText(/Resposta antiga/)).not.toBeInTheDocument();
});
it('preserva a justificativa aberta ao voltar à janela',async()=>{
 mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Registrar resultado'}));
 fireEvent.change(screen.getByLabelText('Justificativa (opcional)'),{target:{value:'Cliente atendido normalmente'}});
 const before=mocked.mock.calls.length;fireEvent(window,new Event('focus'));
 expect(mocked.mock.calls.length).toBe(before);expect(screen.getByLabelText('Justificativa (opcional)')).toHaveValue('Cliente atendido normalmente');
});
it('fechar um histórico lento permite abrir outro sem aguardar a resposta cancelada',async()=>{
 let resolve!:(a:unknown)=>void;let histories=0;
 mocked.mockImplementation(path=>path.includes('/history')?(++histories===1?new Promise(r=>resolve=r):Promise.resolve({events:[],nextBeforeVersion:null})):Promise.resolve(agenda));
 mine();await screen.findByText('Cliente próprio');fireEvent.click(screen.getByRole('button',{name:'Histórico'}));fireEvent.click(screen.getByRole('button',{name:'Fechar'}));
 fireEvent.click(screen.getByRole('button',{name:'Histórico'}));await waitFor(()=>expect(histories).toBe(2));
 await act(async()=>resolve({events:[{version:1,action:'OUTCOME',status:'COMPLETED',actor:'Histórico descartado',occurredAt:'2030-06-04T13:00:00Z'}],nextBeforeVersion:null}));
 expect(screen.queryByText(/Histórico descartado/)).not.toBeInTheDocument();
});

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, Building2, Check, ShieldCheck } from 'lucide-react';
import { api, errorMessage } from './api';
import { Message } from './components';
import type { CurrentUser } from './types';
import './registration.css';
import DemoMailbox from './DemoMailbox';

const empty = { businessName: '', category: '', address: '', zoneId: 'America/Sao_Paulo', ownerName: '', email: '', password: '', confirmation: '', demoAcknowledged: false };
export default function RegistrationPage({ enabled, publicDemo, temporaryStorage = publicDemo, online, user, onLogin, onNavigationBlocked, mailMode = 'disabled', onVerify, onRegistered }: { onRegistered?: (email: string) => void; mailMode?: string; onVerify?: (email: string) => void;
  enabled: boolean; publicDemo: boolean; temporaryStorage?: boolean; online: boolean; user: CurrentUser | null;
  onLogin: (email: string) => void; onNavigationBlocked: (blocked: boolean) => void;
}) {
  const [form, setForm] = useState(empty);
  const [requestKey, setRequestKey] = useState(() => crypto.randomUUID());
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<{ businessName: string; email: string } | null>(null);
  const dirty = Object.entries(form).some(([key, value]) => value !== empty[key as keyof typeof empty]);
  useEffect(() => {
    const blocked = !receipt && (dirty || pending);
    onNavigationBlocked(blocked);
    const prevent = (event: BeforeUnloadEvent) => { if (blocked) event.preventDefault(); };
    window.addEventListener('beforeunload', prevent);
    return () => { onNavigationBlocked(false); window.removeEventListener('beforeunload', prevent); };
  }, [dirty, pending, receipt, onNavigationBlocked]);
  function change<K extends keyof typeof empty>(key: K, value: typeof empty[K]) {
    setForm(current => ({ ...current, [key]: value })); setRequestKey(crypto.randomUUID()); setError('');
  }
  function discard() { setForm(empty); setError(''); setRequestKey(crypto.randomUUID()); }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if ([...form.password].length < 15 || new TextEncoder().encode(form.password).length > 72) {
      setError('Use pelo menos 15 caracteres e até 72 bytes na senha. Acentos e emojis ocupam mais de um byte.'); return;
    }
    if (form.password !== form.confirmation) { setError('As senhas precisam ser iguais.'); return; }
    setPending(true);
    try {
      const { confirmation: _confirmation, ...fields } = form;
      const result = await api<{ businessName: string; email: string }>('/api/auth/register', { method: 'POST', body: JSON.stringify({ ...fields, requestKey }) });
      setForm(empty); setReceipt(result); onRegistered?.(result.email);
    } catch (error) { setError(errorMessage(error)); } finally { setPending(false); }
  }
  if (user) return <section className="page-state"><h1>Você já está conectado.</h1><p>Continue configurando {user.business.name} no seu painel.</p><button className="button primary" onClick={() => onLogin(user.email)}>Voltar ao meu painel</button></section>;
  if (!enabled) return <section className="page-state"><h1>Cadastro indisponível</h1><p>Novas contas estão desativadas neste ambiente.</p><button className="button primary" onClick={() => onLogin('')}>Entrar com minha conta</button></section>;
  if (receipt) return <section className="registration-success agenda-panel"><span className="login-icon"><Check /></span><h1>Sua conta foi criada.</h1><p><strong>{receipt.businessName}</strong> começa com uma página de reservas em rascunho. Entre com <strong>{receipt.email}</strong> e a senha escolhida para cadastrar serviços, equipe e horários.</p><p>Confirme seu e-mail antes de publicar a página. Enquanto isso, você pode entrar e preparar o atendimento.</p>{publicDemo && <Message>{temporaryStorage ? 'Esta é uma conta de teste temporária. Ela pode ser apagada quando a demonstração reiniciar.' : 'Esta é uma conta de demonstração. Use apenas dados fictícios; a instalação preserva os dados entre reinícios.'}</Message>}<button className="button primary" onClick={() => onLogin(receipt.email)}>Entrar no meu painel <ArrowRight size={17} /></button>{onVerify && <button className="button quiet" onClick={() => onVerify(receipt.email)}>Solicitar confirmação de e-mail</button>}{mailMode === "demo" && <DemoMailbox />}{mailMode === "disabled" && <Message>O envio de e-mails precisa ser configurado nesta instalação para liberar a confirmação.</Message>}</section>;
  return <div className="registration-layout"><div className="registration-intro"><span className="eyebrow">SEU PRÓPRIO ESPAÇO</span><h1>Um começo claro para o seu negócio.</h1><p>Crie a empresa e a conta do responsável juntas. Depois, prepare sua equipe, os serviços e os horários antes de compartilhar o link de reservas.</p><p className="login-feature"><ShieldCheck size={22} /> O painel de cada empresa tem acesso separado.</p>{publicDemo && <Message>{temporaryStorage ? 'Você está na demonstração pública gratuita. Use apenas dados fictícios e uma senha exclusiva para testes. As contas e os agendamentos podem ser apagados quando o servidor reiniciar. Para preservar seu cadastro, use sua própria instalação local.' : 'Você está em uma demonstração com armazenamento persistente. Use apenas dados fictícios e uma senha exclusiva para testes. Os cadastros são preservados entre reinícios; cópias de segurança são administradas pelo responsável pela instalação.'}</Message>}</div>
    <section className="agenda-panel registration-card"><span className="login-icon"><Building2 size={23} /></span><h2>Cadastrar meu negócio</h2><p>Uma empresa e um responsável por conta. O e-mail será usado para entrar, confirmar o cadastro e recuperar a senha. A confirmação é obrigatória antes de publicar.</p>
      <form className="stack-form" onSubmit={event => void submit(event)}><fieldset disabled={pending}>
        <legend>Dados do negócio</legend>
        <label htmlFor="reg-business">Nome da empresa</label><input id="reg-business" required minLength={2} maxLength={120} autoComplete="organization" value={form.businessName} onChange={e => change('businessName', e.target.value)} />
        <label htmlFor="reg-category">Área de atuação</label><input id="reg-category" required maxLength={80} placeholder="Ex.: barbearia, estética, aulas" value={form.category} onChange={e => change('category', e.target.value)} />
        <label htmlFor="reg-address">Local de atendimento</label><input id="reg-address" required maxLength={200} placeholder="Endereço comercial, cidade ou atendimento online" value={form.address} onChange={e => change('address', e.target.value)} /><small>Este local aparecerá para clientes quando a página for publicada.</small>
        <label htmlFor="reg-zone">Fuso horário do atendimento</label><select id="reg-zone" value={form.zoneId} onChange={e => change('zoneId', e.target.value)}><option value="America/Sao_Paulo">Brasília / São Paulo</option><option value="America/Manaus">Manaus</option><option value="America/Rio_Branco">Rio Branco</option><option value="America/Noronha">Fernando de Noronha</option></select>
      </fieldset><fieldset disabled={pending}><legend>Conta do responsável</legend>
        <label htmlFor="reg-owner">Seu nome</label><input id="reg-owner" required minLength={2} maxLength={100} autoComplete="name" value={form.ownerName} onChange={e => change('ownerName', e.target.value)} />
        <label htmlFor="reg-email">E-mail de acesso</label><input id="reg-email" type="email" required maxLength={180} autoComplete="username" value={form.email} onChange={e => change('email', e.target.value)} />
        <label htmlFor="reg-password">Crie uma senha</label><input id="reg-password" type="password" required maxLength={144} autoComplete="new-password" aria-describedby="password-help" value={form.password} onChange={e => change('password', e.target.value)} /><small id="password-help">Pelo menos 15 caracteres e até 72 bytes. Prefira uma frase longa; acentos e emojis ocupam mais espaço. Guarde a senha em um gerenciador.</small>
        <label htmlFor="reg-confirm">Confirme a senha</label><input id="reg-confirm" type="password" required maxLength={144} autoComplete="new-password" value={form.confirmation} onChange={e => change('confirmation', e.target.value)} />
        {publicDemo && <label className="registration-check"><input type="checkbox" required checked={form.demoAcknowledged} onChange={e => change('demoAcknowledged', e.target.checked)} />{temporaryStorage ? 'Usarei apenas dados fictícios e entendo que esta conta de teste pode ser apagada.' : 'Usarei apenas dados fictícios e uma senha exclusiva para esta demonstração.'}</label>}
      </fieldset>{error && <Message>{error}</Message>}<button className="button primary full" disabled={pending || !online || (publicDemo && !form.demoAcknowledged)} aria-busy={pending}>{pending ? 'Criando sua conta…' : 'Criar empresa e conta'}<ArrowRight size={17} /></button>
        <div className="registration-actions"><button type="button" className="button quiet" disabled={pending} onClick={() => { discard(); onLogin(form.email.trim().toLowerCase()); }}>{dirty ? 'Descartar cadastro e entrar' : 'Já tenho uma conta'}</button>{dirty && <button type="button" className="button quiet" disabled={pending} onClick={discard}>Limpar cadastro</button>}</div>
      </form>
    </section>
  </div>;
}

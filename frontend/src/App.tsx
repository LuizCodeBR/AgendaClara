import { useEffect, useState } from 'react';
import { ArrowUpRight, CalendarDays, Check, Download, Leaf, Menu, ShieldCheck, WifiOff, X } from 'lucide-react';
import { api, errorMessage } from './api';
import type { Business, CurrentUser } from './types';
import { Message } from './components';
import BookingPage from './BookingPage';
import AdminPage from './AdminPage';
import HomePage from './HomePage';

interface InstallPrompt extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: string }> }
const currentMode = (): 'home' | 'admin' | 'booking' => window.location.hash.startsWith('#/painel') ? 'admin' : window.location.hash.startsWith('#/agendar/') ? 'booking' : 'home';
const initialSlug = () => window.location.hash.startsWith('#/agendar/') ? decodeURIComponent(window.location.hash.slice(10)) : '';

export default function App() {
  const [mode, setMode] = useState(currentMode);
  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [slug, setSlug] = useState(initialSlug);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [demo, setDemo] = useState(false);
  const [publicDemo, setPublicDemo] = useState(false);
  const [distribution, setDistribution] = useState({ sourceUrl: '', downloadUrl: '' });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [online, setOnline] = useState(navigator.onLine || ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname));
  const [install, setInstall] = useState<InstallPrompt | null>(null);
  const [installHelp, setInstallHelp] = useState(false);
  const [menu, setMenu] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    Promise.all([
      api<Business[]>('/api/public/businesses', { signal: controller.signal }),
      api<{ demo: boolean; publicDemo: boolean; distribution: { sourceUrl: string; downloadUrl: string } }>('/api/public/config', { signal: controller.signal }),
      api<CurrentUser>('/api/auth/me', { signal: controller.signal }).catch(error => {
        if (error?.status === 401) return null;
        throw error;
      }),
    ]).then(([items, config, me]) => {
      if (controller.signal.aborted) return;
      setBusinesses(items); setDemo(config.demo); setUser(me); setPublicDemo(config.publicDemo); setDistribution(config.distribution ?? { sourceUrl: '', downloadUrl: '' });
      setSlug(current => items.some(item => item.slug === current) ? current : items[0]?.slug ?? '');
    }).catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [retry]);

  useEffect(() => {
    const status = () => setOnline(navigator.onLine || ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname));
    const route = () => { setMode(currentMode()); const target = initialSlug(); if (target) setSlug(target); setMenu(false); };
    const prompt = (event: Event) => { event.preventDefault(); setInstall(event as InstallPrompt); };
    const installed = () => { setInstall(null); setInstallHelp(false); };
    window.addEventListener('online', status); window.addEventListener('offline', status);
    window.addEventListener('hashchange', route); window.addEventListener('beforeinstallprompt', prompt);
    window.addEventListener('appinstalled', installed);
    return () => {
      window.removeEventListener('online', status); window.removeEventListener('offline', status);
      window.removeEventListener('hashchange', route); window.removeEventListener('beforeinstallprompt', prompt);
      window.removeEventListener('appinstalled', installed);
    };
  }, []);

  useEffect(() => { window.scrollTo(0, 0); }, [mode, slug, user?.business.id]);

  const navigate = (next: 'home' | 'booking' | 'admin') => {
    window.location.hash = next === 'home' ? '/inicio' : next === 'admin' ? '/painel' : '/agendar/' + slug;
    setMode(next); setMenu(false);
  };
  const selectBusiness = (value: string) => { setSlug(value); window.location.hash = '/agendar/' + value; };
  const installApp = async () => {
    if (!install) { setInstallHelp(current => !current); return; }
    await install.prompt(); await install.userChoice; setInstall(null);
  };
  const current = businesses.find(item => item.slug === slug);

  return <div className="app-shell">
    <header className="topbar">
      <a className="brand" href="#/inicio" onClick={() => navigate('home')} aria-label="Agenda Clara início">
        <span className="brand-mark"><Leaf size={23} strokeWidth={1.8} /></span><span>agenda<span className="brand-light">clara</span><span className="brand-dot">.</span></span>
      </a>
      <nav className={'top-nav ' + (menu ? 'is-open' : '')} aria-label="Navegação principal">
        <button className={mode === 'booking' ? 'nav-link active' : 'nav-link'} onClick={() => navigate('booking')}>Agendar um horário</button>
        <button className={mode === 'admin' ? 'nav-link active' : 'nav-link'} onClick={() => navigate('admin')}>Meu negócio <ArrowUpRight size={14} /></button>
      </nav>
      <div className="header-actions">
        <button className="button install-button" aria-label="Instalar app" onClick={() => void installApp()}><Download size={15} /><span>Instalar app</span></button>
        <button className="icon-button mobile-menu" aria-label={menu ? 'Fechar menu' : 'Abrir menu'} aria-expanded={menu} onClick={() => setMenu(!menu)}>{menu ? <X /> : <Menu />}</button>
      </div>
    </header>
    {demo && <div className="demo-strip"><span className="status-dot" /> {publicDemo ? 'Demonstração pública · use apenas dados fictícios' : <>Ambiente de demonstração <span className="demo-strip-detail">· estabelecimentos e dados fictícios</span></>}</div>}
    {!online && <div className="offline-bar" role="status"><WifiOff size={17} /> Você está sem conexão. Reconecte para consultar ou confirmar horários.</div>}
    {installHelp && <div className="install-help" role="status"><Download size={20} /><div><strong>Seu agendamento, com acesso direto.</strong><p>No Chrome ou Edge, abra o menu do navegador e procure “Instalar Agenda Clara” ou “Instalar este site como aplicativo”. No iPhone, use Compartilhar → Adicionar à Tela de Início. O servidor precisa estar em execução.</p></div><button className="icon-button" aria-label="Fechar instruções de instalação" onClick={() => setInstallHelp(false)}><X size={18} /></button></div>}
    <main id="main-content">
      {loading ? <div className="page-state" role="status"><CalendarDays size={30} /><h1>Preparando sua agenda…</h1><p>Só um instante.</p></div> : error ? <div className="page-state"><Message>{error}</Message><button className="button primary" onClick={() => setRetry(value => value + 1)}>Tentar novamente</button></div> : mode === 'home' ? <HomePage onBook={() => navigate('booking')} onInstall={() => { void installApp(); window.scrollTo(0, 0); }} sourceUrl={distribution.sourceUrl} downloadUrl={distribution.downloadUrl} /> : mode === 'booking' ? (
        slug ? <BookingPage key={slug} slug={slug} businesses={businesses} onSelectBusiness={selectBusiness} online={online} /> : <div className="page-state"><h1>Nenhum estabelecimento disponível</h1><p>Configure os dados iniciais para começar a receber reservas.</p></div>
      ) : <AdminPage user={user} onUser={setUser} demo={demo} online={online} onBook={business => { setSlug(business.slug); window.location.hash = '/agendar/' + business.slug; setMode('booking'); }} />}
    </main>
    <footer className="footer"><span>agenda clara<span className="brand-dot">.</span> <span className="footer-muted">Tempo para o que importa.</span></span><span><ShieldCheck size={14} /> {current?.zoneId === 'America/Sao_Paulo' ? 'Horários de Brasília' : 'Horários do estabelecimento'} <span className="footer-divider">/</span> <Check size={14} /> Confirmação na hora</span></footer>
  </div>;
}

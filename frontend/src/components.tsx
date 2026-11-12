import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { X } from 'lucide-react';
export function Message({ children, kind = 'error' }: { children: ReactNode; kind?: 'error' | 'success' | 'info' }) {
  return <div className={'message ' + kind} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}
export function Modal({ title, onClose, children, busy = false }: { title: string; onClose: () => void; children: ReactNode; busy?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  const close = () => { if (!busy) onClose(); };
  return <dialog ref={ref} className="modal" aria-labelledby={titleId} aria-busy={busy}
    onCancel={event => { event.preventDefault(); close(); }}
    onClick={event => { if (event.target === event.currentTarget) close(); }}>
    <div className="modal-heading"><h2 id={titleId}>{title}</h2><button className="icon-button" aria-label="Fechar" disabled={busy} onClick={close}><X size={20} /></button></div>
    {children}
  </dialog>;
}

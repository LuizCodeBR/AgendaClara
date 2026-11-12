import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { Modal } from './components';

beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});

it('impede o fechamento nativo por Escape quando a tela ainda não autoriza sair', () => {
  const close = vi.fn();
  render(<Modal title="Gravando" busy onClose={close}><p>Aguarde</p></Modal>);
  const cancel = new Event('cancel', { bubbles: true, cancelable: true });
  fireEvent(screen.getByRole('dialog'), cancel);
  expect(cancel.defaultPrevented).toBe(true);
  expect(close).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Fechar' })).toBeDisabled();
});

it('não fecha ao clicar fora durante a gravação e volta a permitir fechar após a resposta', async () => {
  const close = vi.fn();
  const { rerender } = render(<Modal title="Gravando" busy onClose={close}><p>Aguarde</p></Modal>);
  fireEvent.click(screen.getByRole('dialog'));
  expect(close).not.toHaveBeenCalled();
  rerender(<Modal title="Gravando" onClose={close}><p>Concluído</p></Modal>);
  await userEvent.setup().click(screen.getByRole('button', { name: 'Fechar' }));
  expect(close).toHaveBeenCalledOnce();
});

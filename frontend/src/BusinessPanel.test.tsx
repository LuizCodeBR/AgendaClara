import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import BusinessPanel from './BusinessPanel';
import { api, ApiError } from './api';
import { catalog } from './test/fixtures';
import type { BusinessSetup } from './types';
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: vi.fn() }));
const mocked = vi.mocked(api);
beforeEach(() => { mocked.mockReset(); });
const empty: BusinessSetup = { business: catalog.business, published: false, version: 0, services: 0, professionals: 0, hasHours: false, ready: false, hasBookableProfessional: false };
it('orienta a configuração e impede publicar um cadastro incompleto', async () => {
  const configure = vi.fn(); render(<BusinessPanel state={empty} online onUpdated={vi.fn()} onConfigure={configure} />);
  expect(screen.getByRole('button', { name: 'Publicar página de reservas' })).toBeDisabled();
  expect(screen.queryByLabelText('Link para seus clientes')).not.toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole('button', { name: 'Configurar equipe' }));
  expect(configure).toHaveBeenCalledWith('team'); expect(mocked).not.toHaveBeenCalled();
});
it('envia apenas a publicação e sua versão e apresenta o link após publicar', async () => {
  const ready = { ...empty, ready: true, hasBookableProfessional: true, hasHours: true, services: 1, professionals: 1 };
  const published = { ...ready, published: true, version: 1 }; const updated = vi.fn();
  mocked.mockResolvedValue(published);
  const { rerender } = render(<BusinessPanel state={ready} online onUpdated={updated} onConfigure={vi.fn()} />);
  await userEvent.setup().click(screen.getByRole('button', { name: 'Publicar página de reservas' }));
  expect(updated).toHaveBeenCalledWith(published);
  expect(JSON.parse(mocked.mock.calls[0][1]!.body as string)).toEqual({ published: true, version: 0 });
  rerender(<BusinessPanel state={published} online onUpdated={updated} onConfigure={vi.fn()} />);
  expect(screen.getByLabelText('Link para seus clientes')).toHaveValue(window.location.origin + '/#/agendar/' + catalog.business.slug);
});
it('mantém o estado atual quando outra janela já alterou a publicação', async () => {
  mocked.mockRejectedValue(new ApiError('Atualize o painel.', 409)); const updated = vi.fn();
  render(<BusinessPanel state={{ ...empty, ready: true, hasBookableProfessional: true, published: true, version: 3 }} online onUpdated={updated} onConfigure={vi.fn()} />);
  await userEvent.setup().click(screen.getByRole('button', { name: 'Ocultar página de reservas' }));
  expect(await screen.findByText('Atualize o painel.')).toBeVisible();
  expect(updated).not.toHaveBeenCalled(); expect(screen.getByLabelText('Link para seus clientes')).toBeVisible();
});

# Agenda Clara 0.13.1

Revisão de segurança e concorrência do resumo diário com WhatsApp manual.

- Nova consulta autenticada ao confirmar a prévia; alteração de contato ou agenda exige nova conferência.
- Falha de gravação do telefone exige consultar o cadastro atual; sucesso recarrega o resumo completo.
- Validade contada desde o início da consulta, com proteção contra atraso, mudança do relógio e retorno do segundo plano.
- Respostas tardias da cópia/conferência não alteram outra visualização.
- Testes de regressão, autorização, privacidade, conflitos e fluxos reais desktop/mobile.

Sem nova dependência ou instalação. Mesmo aplicativo para web, PWA e local; nenhum WhatsApp enviado automaticamente. Sem serviço pago. Esquema V11 e backups ACBK0001 preservados.

No Windows, extraia o ZIP de demonstração e execute iniciar-demo.cmd com Java 21–27. Faça backup e encerre a versão anterior antes de atualizar, preservando data e mail-outbox.key. Consulte docs/REVISAO-RESUMO-0.13.1.md e docs/RESUMO-DIARIO.md no código-fonte.
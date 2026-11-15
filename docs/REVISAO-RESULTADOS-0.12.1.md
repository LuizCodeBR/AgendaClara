# Revisão de segurança e concorrência dos resultados — 0.12.1

14/11/2026. Nova leitura do código da 0.12.0 e testes de reprodução realizados nesta conversa, separadamente da implementação original. Não é auditoria externa ou certificação. Escopo: conclusão, faltas, correções, histórico, permissões, transações e interação com remarcação e acesso privado.

## Achados corrigidos

1. **Coerção de resultados numéricos (validação).** A API aceitava 0/1 e os textos "0"/"1" como COMPLETED/NO_SHOW pela posição do enum. Quatro testes HTTP reproduziram alteração 200 quando a entrada deveria ser recusada. Habilitada FAIL_ON_NUMBERS_FOR_ENUMS em spring.jackson.datatype.enum, apropriado ao Jackson 3 usado pelo projeto. O cliente oficial já envia os nomes. Não se trata de acesso sem autenticação; a falha era aceitar um contrato diferente do documentado.

2. **Caractere nulo na justificativa (portabilidade e disponibilidade).** U+0000 era aceito pela validação, embora PostgreSQL não o aceite em texto. O teste reproduziu aceitação em H2; a correção recusa a entrada com 400 antes da gravação em ambos os bancos, evitando comportamento divergente e erro de banco. Espaços, acentos, tabulação e quebras de linha válidos continuam permitidos.

3. **Cancelamento de estado terminal após retrocesso de relógio (consistência).** O cancelamento dependia do horário futuro e só excluía CANCELLED. Com relógio controlado recuando para antes do início depois de concluir/marcar falta, era possível transformar o resultado em cancelamento. Agora o fluxo exige BOOKED explicitamente. COMPLETED/NO_SHOW só mudam pelo fluxo de correção com justificativa. Foram reproduzidos os dois estados; esse cenário depende de relógio/dados inconsistentes, não de o visitante alterar o relógio do navegador. A mensagem de remarcação também passou a descrever corretamente os estados permitidos.

4. **Exigência explícita de transação (proteção preventiva de arquitetura).** O bloqueio do profissional e a inclusão do histórico dependiam da disciplina do chamador. WriteTransaction.require recusa chamada sem transação ou em transação somente de leitura. Não foi encontrado endpoint atual que chamasse esses métodos fora da transação; a proteção evita uma futura refatoração liberar o bloqueio cedo ou confirmar um evento isoladamente. Testes verificam as duas condições.

5. **Respostas atrasadas na paginação do histórico (consistência da interface).** A primeira página tinha cancelamento, mas páginas anteriores não. Testes reproduziram consulta continuando após desmontar e resposta de um atendimento sobrescrevendo o histórico de outro quando o componente era reutilizado. Todas as consultas agora têm cancelamento, resultados cancelados são ignorados e a troca de atendimento recria a janela. É reforço contra concorrência de respostas no cliente, não evidência de vazamento entre empresas: a autorização do servidor já limita cada consulta.

## Proteções verificadas

- Empresa derivada da sessão, responsável autenticado, CSRF e recusa do acesso do cliente ao histórico interno.
- Versão e horário reconsultados depois do bloqueio do profissional.
- Resultado e evento confirmados ou desfeitos juntos, inclusive em conflito de chave entre profissionais diferentes.
- Mesma chave e mesmos dados não duplica evento; reutilização incompatível retorna 409.
- Rotação/revogação de acesso privado e classificação compartilham a mesma versão.
- Leitura não expõe resultado ou evento ainda não confirmado.
- Paginação por versão, sem editar/apagar eventos pela API; texto do autor/justificativa renderizado sem interpretar HTML.

A ordem de bloqueios foi preservada. Não foi acrescentado bloqueio global da empresa. Não se encontraram caminhos novos de acesso a outra empresa nos cenários examinados. O histórico continua sendo um registro transacional do aplicativo, não um registro criptográfico imutável.

## Compatibilidade

Nenhuma migração nova: V10, dados, chaves e ACBK0001 permanecem. Ferramentas aceitam esquemas 6 a 10. Web/PWA compartilham o servidor online; a cópia local usa seu banco independente. Arquivos no D:, nenhuma nova dependência/extensão, nenhum recurso pago ou tarefa recorrente. Backups e monitoramento continuam sob demanda.

Referências técnicas: [enum no Jackson 3](https://github.com/FasterXML/jackson-databind/blob/3.1/src/main/java/tools/jackson/databind/cfg/EnumFeature.java), [propriedades Spring Boot](https://docs.spring.io/spring-boot/appendix/application-properties/index.html), [transações Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html), [texto no PostgreSQL](https://www.postgresql.org/docs/17/datatype-character.html).

As evidências de execução e publicação estão em [VALIDACAO-0.12.1.md](VALIDACAO-0.12.1.md).

package br.com.luizcode.agenda.shared;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A row lock or audit insert must belong to the caller's write transaction. */
public final class WriteTransaction {
    private WriteTransaction(){}
    public static void require(){
        if(!TransactionSynchronizationManager.isActualTransactionActive()
                ||TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Esta operação exige uma transação de escrita ativa.");
    }
}

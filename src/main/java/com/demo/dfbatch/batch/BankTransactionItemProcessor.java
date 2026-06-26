package com.demo.dfbatch.batch;

import com.demo.dfbatch.domain.BankTransaction;
import com.demo.dfbatch.domain.TransactionType;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;

public class BankTransactionItemProcessor implements ItemProcessor<BankTransaction, BankTransaction> {

    @Override
    public BankTransaction process(BankTransaction tx) {
        if (tx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (tx.getTransactionType() == TransactionType.TRANSFER && tx.getCounterpartyAccountId() == null) {
            return null;
        }
        if (tx.getDescription() != null) {
            tx.setDescription(tx.getDescription().trim());
        }
        tx.setReferenceNumber(tx.getReferenceNumber().toUpperCase());
        return tx;
    }
}

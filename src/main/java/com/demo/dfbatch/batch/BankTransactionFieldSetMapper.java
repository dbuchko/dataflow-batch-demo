package com.demo.dfbatch.batch;

import com.demo.dfbatch.domain.BankTransaction;
import com.demo.dfbatch.domain.TransactionType;
import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BankTransactionFieldSetMapper implements FieldSetMapper<BankTransaction> {

    @Override
    public BankTransaction mapFieldSet(FieldSet fieldSet) throws BindException {
        BankTransaction tx = new BankTransaction();
        tx.setAccountId(fieldSet.readString("accountId"));
        tx.setTransactionType(TransactionType.valueOf(fieldSet.readString("transactionType")));
        tx.setAmount(new BigDecimal(fieldSet.readString("amount")));
        tx.setDescription(fieldSet.readString("description"));
        tx.setTransactionDate(LocalDate.parse(fieldSet.readString("transactionDate")));
        tx.setReferenceNumber(fieldSet.readString("referenceNumber"));
        String counterparty = fieldSet.readString("counterpartyAccountId");
        tx.setCounterpartyAccountId(counterparty.isBlank() ? null : counterparty);
        tx.setBalanceAfter(new BigDecimal(fieldSet.readString("balanceAfter")));
        return tx;
    }
}

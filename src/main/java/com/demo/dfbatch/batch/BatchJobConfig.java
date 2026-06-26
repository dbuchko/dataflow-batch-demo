package com.demo.dfbatch.batch;

import com.demo.dfbatch.domain.BankTransaction;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;


import javax.sql.DataSource;

@Configuration
public class BatchJobConfig {

    @Bean
    @StepScope
    public FlatFileItemReader<BankTransaction> bankTransactionReader(
            @Value("#{jobParameters['inputFile']}") String inputFilePath,
            @Value("${app.batch.default-input-file}") String defaultInputFile) {

        String path = (inputFilePath != null && !inputFilePath.isBlank()) ? inputFilePath : defaultInputFile;
        Resource resource = new DefaultResourceLoader().getResource(path);

        return new FlatFileItemReaderBuilder<BankTransaction>()
                .name("bankTransactionReader")
                .resource(resource)
                .delimited()
                .names("accountId", "transactionType", "amount", "description",
                        "transactionDate", "referenceNumber", "counterpartyAccountId", "balanceAfter")
                .linesToSkip(1)
                .fieldSetMapper(new BankTransactionFieldSetMapper())
                .build();
    }

    @Bean
    public BankTransactionItemProcessor bankTransactionProcessor() {
        return new BankTransactionItemProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<BankTransaction> bankTransactionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<BankTransaction>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO bank_transactions
                            (account_id, transaction_type, amount, description,
                             transaction_date, reference_number, counterparty_account_id, balance_after)
                        VALUES
                            (:accountId, :transactionType, :amount, :description,
                             :transactionDate, :referenceNumber, :counterpartyAccountId, :balanceAfter)
                        """)
                .itemSqlParameterSourceProvider(tx -> {
                    var params = new MapSqlParameterSource();
                    params.addValue("accountId", tx.getAccountId());
                    params.addValue("transactionType", tx.getTransactionType().name());
                    params.addValue("amount", tx.getAmount());
                    params.addValue("description", tx.getDescription());
                    params.addValue("transactionDate", tx.getTransactionDate());
                    params.addValue("referenceNumber", tx.getReferenceNumber());
                    params.addValue("counterpartyAccountId", tx.getCounterpartyAccountId());
                    params.addValue("balanceAfter", tx.getBalanceAfter());
                    return params;
                })
                .build();
    }

    @Bean
    public Step importBankTransactionsStep(JobRepository jobRepository,
                                           PlatformTransactionManager transactionManager,
                                           FlatFileItemReader<BankTransaction> bankTransactionReader,
                                           BankTransactionItemProcessor bankTransactionProcessor,
                                           JdbcBatchItemWriter<BankTransaction> bankTransactionWriter) {
        return new StepBuilder("importBankTransactionsStep", jobRepository)
                .<BankTransaction, BankTransaction>chunk(10)
                .reader(bankTransactionReader)
                .processor(bankTransactionProcessor)
                .writer(bankTransactionWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job importBankTransactionsJob(JobRepository jobRepository,
                                         Step importBankTransactionsStep,
                                         JobArchiveListener archiveListener) {
        return new JobBuilder("importBankTransactionsJob", jobRepository)
                .start(importBankTransactionsStep)
                .listener(archiveListener)
                .build();
    }
}

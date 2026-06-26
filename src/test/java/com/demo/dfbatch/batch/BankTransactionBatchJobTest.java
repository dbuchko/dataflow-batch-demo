package com.demo.dfbatch.batch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class BankTransactionBatchJobTest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.execute("DELETE FROM bank_transactions");
    }

    private JobParameters testJobParams() {
        return jobOperatorTestUtils.getUniqueJobParametersBuilder()
                .addString("inputFile", "classpath:data/test-transactions.csv")
                .toJobParameters();
    }

    @Test
    void jobCompletesSuccessfully() throws Exception {
        JobExecution execution = jobOperatorTestUtils.startJob(testJobParams());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void correctNumberOfRecordsWritten() throws Exception {
        jobOperatorTestUtils.startJob(testJobParams());
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bank_transactions", Integer.class);
        assertThat(count).isEqualTo(5);
    }

    @Test
    void specificTransactionDataValidated() throws Exception {
        jobOperatorTestUtils.startJob(testJobParams());

        String counterparty = jdbcTemplate.queryForObject(
                "SELECT counterparty_account_id FROM bank_transactions WHERE transaction_type = 'TRANSFER'",
                String.class);
        assertThat(counterparty).isEqualTo("ACC002");

        BigDecimal depositAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM bank_transactions WHERE transaction_type = 'DEPOSIT' AND reference_number = 'TEST-REF-0001'",
                BigDecimal.class);
        assertThat(depositAmount).isEqualByComparingTo(new BigDecimal("1000.00"));

        String checkCounterparty = jdbcTemplate.queryForObject(
                "SELECT counterparty_account_id FROM bank_transactions WHERE transaction_type = 'CHECK'",
                String.class);
        assertThat(checkCounterparty).isNull();
    }

    @Test
    void stepWriteCountMatchesExpected() throws Exception {
        JobExecution execution = jobOperatorTestUtils.startJob(testJobParams());
        var stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(5);
        assertThat(stepExecution.getWriteCount()).isEqualTo(5);
        assertThat(stepExecution.getFilterCount()).isEqualTo(0);
    }
}

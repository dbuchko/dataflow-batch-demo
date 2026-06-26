CREATE TABLE IF NOT EXISTS bank_transactions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id              VARCHAR(50)   NOT NULL,
    transaction_type        VARCHAR(20)   NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    description             VARCHAR(255),
    transaction_date        DATE          NOT NULL,
    reference_number        VARCHAR(50)   NOT NULL,
    counterparty_account_id VARCHAR(50),
    balance_after           DECIMAL(15,2) NOT NULL
);

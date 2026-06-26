# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

SDKMAN manages the JVM — run `sdk env` once after cloning, or enable `sdkman_auto_env=true` for automatic switching.

```bash
# Build
./mvnw clean package

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=BankTransactionBatchJobTest

# Run locally (H2, auto-runs the batch job)
./mvnw spring-boot:run

# Run with local MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql \
  -Dspring-boot.run.jvmArguments="-DMYSQL_HOST=localhost -DMYSQL_DATABASE=batchdb -DMYSQL_USER=root -DMYSQL_PASSWORD=secret"

# Build fat JAR for CF deployment
./mvnw clean package -DskipTests
```

## Spring Batch 6 Package Changes

Spring Batch 6 (bundled in Spring Boot 4.x) reorganized packages significantly — the old paths will not compile:

| Old (Spring Batch 5) | New (Spring Batch 6) |
|---|---|
| `org.springframework.batch.core.Job` | `org.springframework.batch.core.job.Job` |
| `org.springframework.batch.core.Step` | `org.springframework.batch.core.step.Step` |
| `org.springframework.batch.core.JobExecution` | `org.springframework.batch.core.job.JobExecution` |
| `org.springframework.batch.core.JobParameters` | `org.springframework.batch.core.job.parameters.JobParameters` |
| `org.springframework.batch.core.JobParametersBuilder` | `org.springframework.batch.core.job.parameters.JobParametersBuilder` |
| `org.springframework.batch.item.*` | `org.springframework.batch.infrastructure.item.*` |
| `org.springframework.batch.item.file.*` | `org.springframework.batch.infrastructure.item.file.*` |
| `org.springframework.batch.item.database.*` | `org.springframework.batch.infrastructure.item.database.*` |

The chunk step builder also changed — use `chunk(int)` + `.transactionManager(tm)` instead of the deprecated `chunk(int, tm)`.

In tests, `JobLauncherTestUtils` + `launchJob()` is replaced by `JobOperatorTestUtils` + `startJob()`. `@SpringBatchTest` auto-wires `JobOperatorTestUtils`.

Java version: this project requires Java 21 and uses SDKMAN for JVM management. The correct version is pinned in [`.sdkmanrc`](.sdkmanrc). Run `sdk env` in the project root to activate it, or enable auto-switching in `~/.sdkman/etc/config` (`sdkman_auto_env=true`). Do not prepend `JAVA_HOME=...` to commands — let SDKMAN manage it.

## Architecture

This is a **Spring Batch + Spring Cloud Task** application that ingests CSV banking transactions into a database. It is designed to run as a short-lived task — locally, as a CF Task, and managed by Tanzu Data Flow (TDF) 2.1.

### Spring Batch pipeline

```
FlatFileItemReader (CSV) → BankTransactionItemProcessor → JdbcBatchItemWriter (DB)
```

- `BatchJobConfig` wires the single job `importBankTransactionsJob` → step `importBankTransactionsStep` (chunk size 10).
- The reader is `@StepScope` because it resolves `#{jobParameters['inputFile']}` at step execution time. It falls back to `app.batch.default-input-file` from properties.
- The writer uses a lambda `itemSqlParameterSourceProvider` (not `.beanMapped()`) to explicitly call `.name()` on the `TransactionType` enum.
- **No `@EnableBatchProcessing`** — Spring Boot 4.x auto-configures all Batch infrastructure. Adding it would conflict.

### Spring Cloud Task integration

`@EnableTask` on `DfbatchApplication` replaces Boot's `JobLauncherApplicationRunner` with Spring Cloud Task's `TaskJobLauncherApplicationRunner`. This records `TaskExecution` and links it to the `JobExecution` in `TASK_BATCH_JOB_EXECUTION`, making the run visible in TDF's dashboard.

### Datasource profiles

| Profile | Database | When used |
|---------|----------|-----------|
| *(default)* | H2 in-memory (`batchdb`) | Local dev, tests |
| `mysql` | MySQL via env vars (`MYSQL_HOST`, etc.) | Local MySQL testing |
| `cloud` | Auto-configured from CF service binding / TDF injection | Tanzu Platform (CF) |
| `test` | H2 in-memory (`testdb`) | Integration tests |

Spring Batch and Spring Cloud Task metadata tables (`BATCH_*`, `TASK_*`) are created automatically. The application table `bank_transactions` is created via `schema.sql` (initialized by `spring.sql.init`).

### Testing

Tests use `@SpringBatchTest @SpringBootTest @ActiveProfiles("test")`. The `test` profile sets `spring.batch.job.enabled=false` to prevent auto-launch; tests drive the job explicitly via `JobLauncherTestUtils`. Each test passes a unique `run.id` (timestamp) to create a new `JobInstance`.

The smoke test `DfbatchApplicationTests` must have `@ActiveProfiles("test")` to prevent the job from launching on context load.

### CF / TDF 2.1 deployment

- `manifest.yml` stages the app with `instances: 0`, `no-route: true`, `health-check-type: process`, and the `cloud` Spring profile.
- On CF, the Java buildpack's `spring-cloud-bindings` translates the bound MySQL service's `VCAP_SERVICES` into `spring.datasource.*` properties.
- When TDF launches the task, it overrides `spring.datasource.*` with its own shared MySQL — so Batch/Task metadata and `bank_transactions` rows land in the TDF database.
- TDF 2.1 requires `spring-boot-starter-batch-jdbc` (not `spring-boot-starter-batch`) to use JDBC metadata, which is required for Spring Batch 6 compatibility.
- Register with TDF: `app register --name dfbatch --type task --uri maven://com.demo:dfbatch:0.0.1-SNAPSHOT` (or `https://` URI for demo).

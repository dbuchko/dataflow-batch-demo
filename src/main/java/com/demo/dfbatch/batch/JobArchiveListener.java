package com.demo.dfbatch.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class JobArchiveListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobArchiveListener.class);

    @Value("${app.batch.archive-dir:}")
    private String archiveDir;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) return;
        if (archiveDir == null || archiveDir.isBlank()) return;

        String inputFile = jobExecution.getJobParameters().getString("inputFile");
        if (inputFile == null || !inputFile.startsWith("file:")) return;

        Path source = Paths.get(URI.create(inputFile));
        String archivedName = source.getFileName().toString()
                .replace(".csv", "_" + jobExecution.getId() + ".csv");
        Path dest = Paths.get(archiveDir).resolve(archivedName);

        try {
            Files.createDirectories(dest.getParent());
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archived {} -> {}", source, dest);
        } catch (IOException e) {
            log.warn("Could not archive {}: {}", source, e.getMessage());
        }
    }
}

package com.doris.rand.worker;

import com.doris.rand.config.DBConfig;
import com.doris.rand.executor.MySQLExecutor;
import com.doris.rand.generator.RandomDDLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class InsertWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(InsertWorker.class);
    private static final String INSERT_LOG_FILE = "insert_operations.log";

    private final RandomDDLGenerator generator;
    private final MySQLExecutor executor;
    private final int insertCount;
    private final long sleepIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public InsertWorker(RandomDDLGenerator generator, MySQLExecutor executor, int insertCount, long sleepIntervalMs) {
        this.generator = generator;
        this.executor = executor;
        this.insertCount = insertCount;
        this.sleepIntervalMs = sleepIntervalMs;
    }

    @Override
    public void run() {
        logger.info("Insert worker thread started");
        int successCount = 0;
        int totalAttempts = 0;

        try {
            while (running.get() && (insertCount <= 0 || successCount < insertCount)) {
                String insertStmt = generator.generateInsertInto();
                if (insertStmt.isEmpty()) {
                    continue;
                }

                totalAttempts++;
                logger.info("Executing insert statement: {}", insertStmt);

                if (executor.executeDDL(insertStmt)) {
                    successCount++;
                    logInsert(insertStmt, successCount);
                    logger.info("Insert successful ({}/{})", successCount, insertCount);
                } else {
                    logger.warn("Insert failed");
                }

                try {
                    Thread.sleep(sleepIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stop();
                }
            }
        } catch (Exception e) {
            logger.error("Error in insert worker thread", e);
        } finally {
            logger.info("Insert worker completed: {} successful inserts out of {} attempts",
                    successCount, totalAttempts);
        }
    }

    public void stop() {
        running.set(false);
    }

    private void logInsert(String insertStmt, int count) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(INSERT_LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.println("=== " + timestamp + " === [" + count + "]");
            writer.println("Executed INSERT:");
            writer.println(insertStmt);
            writer.println();
            writer.flush();
        } catch (IOException e) {
            logger.error("Error writing to insert log file: {}", e.getMessage());
        }
    }
}
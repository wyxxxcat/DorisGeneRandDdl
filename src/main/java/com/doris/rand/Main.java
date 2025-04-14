package com.doris.rand;

import com.doris.rand.config.DBConfig;
import com.doris.rand.executor.MySQLExecutor;
import com.doris.rand.generator.RandomDDLGenerator;
import com.doris.rand.worker.InsertWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String SCHEMA_LOG_FILE = "schema_changes.log";
    private static final long DDL_SLEEP_INTERVAL_MS = 1000; // Sleep 1 second between DDL operations

    public static void main(String[] args) {
        RandomDDLGenerator generator = new RandomDDLGenerator();
        MySQLExecutor executor = new MySQLExecutor(DBConfig.getHost(), DBConfig.getPort(), DBConfig.getUser(),
                DBConfig.getPassword(), DBConfig.getDatabase());

        // Create and start the insert worker thread
        Thread insertThread = startInsertWorker(generator, executor);

        // Execute schema changes in the main thread
        executeSchemaChanges(generator, executor);

        // Wait for the insert thread to finish (optional, since it might never finish
        // if insertCount is set to 0)
        try {
            insertThread.join(5000); // Wait up to 5 seconds
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for insert thread to finish");
            Thread.currentThread().interrupt();
        }

        logger.info("Main thread execution completed");
    }

    private static Thread startInsertWorker(RandomDDLGenerator generator, MySQLExecutor executor) {
        int insertCount = DBConfig.getRunInsertOps();
        long sleepInterval = DBConfig.getInsertSleepMs();

        InsertWorker worker = new InsertWorker(generator, executor, insertCount, sleepInterval);
        Thread thread = new Thread(worker, "InsertThread");
        thread.setDaemon(true);
        thread.start();
        logger.info("Started insert worker thread with {} operations (0=unlimited)", insertCount);
        return thread;
    }

    private static void executeSchemaChanges(RandomDDLGenerator generator, MySQLExecutor executor) {
        for (int i = 0; i < DBConfig.getRandCount();) {
            try {
                // Generate a schema change DDL (not an INSERT)
                String ddl = generateSchemaChangeDDL(generator);
                if (ddl.isEmpty()) {
                    continue;
                }

                logger.info("Executing schema change: {}", ddl);
                System.out.println(ddl);

                if (!executor.executeDDL(ddl)) {
                    continue;
                } else {
                    i++;
                }

                logSchemaChange(ddl);
                Thread.sleep(DDL_SLEEP_INTERVAL_MS);

            } catch (Exception e) {
                logger.error("Error in schema change generation/execution", e);
                System.exit(1);
            }
        }
    }

    private static String generateSchemaChangeDDL(RandomDDLGenerator generator) {
        // Modify the RandomDDLGenerator to get a schema change DDL
        // This is a placeholder - you need to implement this method in the generator
        return generator.generateSchemaChangeDDL();
    }

    private static void logSchemaChange(String ddl) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SCHEMA_LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.println("=== " + timestamp + " ===");
            writer.println("Schema change:");
            writer.println(ddl);
            writer.println();
            writer.flush();
        } catch (IOException e) {
            logger.error("Error writing to schema log file: {}", e.getMessage());
        }
    }
}
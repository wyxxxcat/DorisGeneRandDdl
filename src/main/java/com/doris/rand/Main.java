package com.doris.rand;

import com.doris.rand.generator.RandomDDLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String LOG_FILE = "rand_ddl.log";

    public static void main(String[] args) {
        RandomDDLGenerator generator = new RandomDDLGenerator();

        while(true) {
            try {
                String ddl = generator.generateDDL();
                if (ddl.isEmpty()) {
                    continue;
                }
                
                System.out.println(ddl);

                logDDL(ddl);

                Thread.sleep(1000);

            } catch (Exception e) {
                logger.error("Error in DDL generation/logging", e);
                System.exit(1);
            }
        }
    }

    private static void logDDL(String ddl) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            writer.println("=== " + timestamp + " ===");
            writer.println("Generated DDL:");
            writer.println(ddl);
            writer.println();
            writer.flush();
        } catch (IOException e) {
            logger.error("Error writing to log file: " + e.getMessage());
        }
    }
}

package com.doris.rand;

import com.doris.rand.generator.RandomDDLGenerator;
import com.doris.rand.executor.MySQLExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        String host = "127.0.0.1";
        int port = 9330;
        String username = "root";
        String password = "";
        String database = "db";

        try {
            RandomDDLGenerator generator = new RandomDDLGenerator();
            MySQLExecutor executor = new MySQLExecutor(host, port, username, password, database);

            // Generate and execute 10 random DDL statements
            for (int i = 0; i < 10; i++) {
                String ddl = generator.generateDDL();
                executor.executeDDL(ddl);
                Thread.sleep(1000); // Wait 1 second between executions
            }
        } catch (Exception e) {
            logger.error("Error executing DDL statements", e);
            System.exit(1);
        }
    }
}

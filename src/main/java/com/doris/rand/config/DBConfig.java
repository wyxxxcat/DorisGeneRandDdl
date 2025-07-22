package com.doris.rand.config;

import java.util.HashMap;
import java.util.Map;

public class DBConfig {
    private static Map<String, String> cmdArgs = new HashMap<>();

    public static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                String key = args[i].substring(2).toLowerCase();
                String value = args[i + 1];
                cmdArgs.put(key, value);
                i++;
            }
        }
    }

    private static String getValue(String key, String defaultValue) {
        String cmdValue = cmdArgs.get(key);
        if (cmdValue != null) {
            return cmdValue;
        }
        return defaultValue;
    }

    public static String getHost() {
        return getValue("db_host", "localhost");
    }

    public static String getPort() {
        return getValue("db_port", "9030");
    }

    public static String getUser() {
        return getValue("db_user", "root");
    }

    public static String getPassword() {
        return getValue("db_password", "");
    }

    public static String getDatabase() {
        return getValue("db_database", "db");
    }

    public static int getRandCount() {
        return Integer.parseInt(getValue("rand_ddl_count", "200"));
    }

    public static int getRunInsertOps() {
        return Integer.parseInt(getValue("run_insert_ops", "5000"));
    }

    public static int getInsertSleepMs() {
        return Integer.parseInt(getValue("insert_sleep_ms", "1000"));
    }
}
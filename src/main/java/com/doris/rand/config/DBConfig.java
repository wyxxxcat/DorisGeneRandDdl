package com.doris.rand.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
public class DBConfig {
    private static Properties props;

    static {
        props = new Properties();
        try {
            props.load(new FileInputStream("config.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static String getHost() {
        return props.getProperty("db.host");
    }

    public static String getPort() {
        return props.getProperty("db.port");
    }

    public static String getUser() {
        return props.getProperty("db.user");
    }

    public static String getPassword() {
        return props.getProperty("db.password");
    }

    public static String getDatabase() {
        return props.getProperty("db.database");
    }

    public static int getRandCount() {
        return 200;
    }

    public static int getRunInsertOps() {
        return Integer.parseInt(props.getProperty("db.runInsertOps", "5000"));
    }

    public static int getInsertSleepMs() {
        return Integer.parseInt(props.getProperty("db.insertSleepMs", "1"));
    }
}

package com.doris.rand.config;

public class DBConfig {
    public static String getHost() {
        return "127.0.0.1";
    }

    public static String getPort() {
        return "9030";
    }

    public static String getUser() {
        return "root";
    }

    public static String getPassword() {
        return "";
    }

    public static String getDatabase() {
        return "dim";
    }

    public static int getRandCount() {
        return 200;
    }
}

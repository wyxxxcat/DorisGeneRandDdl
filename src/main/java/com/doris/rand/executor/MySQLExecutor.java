package com.doris.rand.executor;

import java.sql.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MySQLExecutor {
    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String database;
    private final String logFile;

    public MySQLExecutor(String host, String port, String username, String password, String database) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.logFile = "rand_ddl.log";
    }

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Error loading MySQL driver: " + e.getMessage());
        }
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    public boolean executeDDL(String ddl) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                FileWriter fw = new FileWriter(logFile, true);
                BufferedWriter bw = new BufferedWriter(fw)) {

            bw.write(String.format("[%s] Executing DDL:\n%s\n", timestamp, ddl));

            stmt.execute(ddl);

            bw.write(String.format("[%s] Execution successful\n\n", timestamp));
            return true;

        } catch (SQLException e) {
            try (FileWriter fw = new FileWriter(logFile, true);
                    BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(String.format("[%s] Execution failed:\n%s\n\n", timestamp, e.getMessage()));
            } catch (IOException ioe) {
                System.err.println("Error writing to log file: " + ioe.getMessage());
                return false;
            }
            System.err.println("SQL Error: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
            return false;
        }
    }
}

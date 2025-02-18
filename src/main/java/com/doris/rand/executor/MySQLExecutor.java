package com.doris.rand.executor;

import java.sql.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MySQLExecutor {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String database;
    private final String logFile;
    
    public MySQLExecutor(String host, int port, String username, String password, String database) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.logFile = "rand_ddl.log";
    }
    
    private Connection getConnection() throws SQLException {
        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        return DriverManager.getConnection(url, username, password);
    }
    
    public void executeDDL(String ddl) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            
            // 记录执行的DDL语句
            bw.write(String.format("[%s] Executing DDL:\n%s\n", timestamp, ddl));
            
            // 执行DDL
            stmt.execute(ddl);
            
            // 记录执行成功
            bw.write(String.format("[%s] Execution successful\n\n", timestamp));
            
        } catch (SQLException e) {
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                // 记录执行失败和错误信息
                bw.write(String.format("[%s] Execution failed:\n%s\n\n", timestamp, e.getMessage()));
            } catch (IOException ioe) {
                System.err.println("Error writing to log file: " + ioe.getMessage());
            }
            System.err.println("SQL Error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}

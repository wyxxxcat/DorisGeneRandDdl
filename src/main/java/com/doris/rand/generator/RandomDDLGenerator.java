package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RandomDDLGenerator {
    private static final Random random = new Random();
    private List<String> tableNames = new ArrayList<>();
    
    public RandomDDLGenerator() {
        loadTableNames();
    }

    public void loadTableNames() {
        tableNames.clear();
        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SHOW TABLES FROM " + database;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    // The column name for SHOW TABLES result is "Tables_in_<database>"
                    tableNames.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
        }
    }

    private String generateTableName() {
        loadTableNames();
        return tableNames.get(random.nextInt(tableNames.size()));
    }

    public String generateDDL() {
        loadTableNames();
        
        int choice = random.nextInt(7); 
        switch (choice) {
            case 0:
                return generateAddColumn();
            case 1:
                return generateDropColumn();
            case 2:
                return generateAddPartition();
            case 3:
                return generateDropPartition();
            case 4:
                return generateAddRollup();
            case 5:
                return generateDropRollup();
            case 6:
                return generateModifyColumn();
            default:
                return "";
        }
    }

    private String generateAddColumn() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName()); 
        sb.append(" ADD COLUMN ");
        sb.append(generateColumnDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateDropColumn() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  
        sb.append(" DROP COLUMN ");
        sb.append(generateColIdentifier());
        sb.append(";");
        return sb.toString();
    }

    private String generateAddPartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName()); 
        sb.append(" ADD PARTITION ");
        sb.append(generatePartitionDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateDropPartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  
        sb.append(" DROP PARTITION ");
        sb.append(generateColIdentifier());
        sb.append(";");
        return sb.toString();
    }

    private String generateAddRollup() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  
        sb.append(" ADD ROLLUP ");
        sb.append(generateColIdentifier());   
        sb.append(" (");
        int numColumns = random.nextInt(3) + 1;
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(generateColIdentifier());  
        }
        sb.append(")");
        
        if (random.nextBoolean()) {
            sb.append(" DUPLICATE KEY(");
            sb.append(generateColIdentifier());
            sb.append(")");
        }
        
        // if (random.nextBoolean()) {
        //     sb.append(" PROPERTIES (");
        //     sb.append("'replication_num' = '").append(random.nextInt(3) + 1).append("'");
        //     sb.append(")");
        // }
        
        sb.append(";");
        return sb.toString();
    }

    private String generateDropRollup() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  
        sb.append(" DROP ROLLUP ");
        sb.append(generateColIdentifier());
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumn() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  
        sb.append(" MODIFY COLUMN ");
        sb.append(generateModifyColumnDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumnDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateColIdentifier()); 
        sb.append(" ");
        sb.append(generateDataType());

        if (random.nextBoolean()) {
            sb.append(random.nextBoolean() ? " NULL" : " NOT NULL");
        }

        if (random.nextBoolean()) {
            sb.append(" DEFAULT ");
            String dataType = generateDataType();
            if (dataType.contains("VARCHAR")) {
                sb.append("'default_value'");
            } else if (dataType.equals("BOOLEAN")) {
                sb.append(random.nextBoolean() ? "TRUE" : "FALSE");
            } else if (dataType.equals("DATE")) {
                sb.append("'2024-01-01'");
            } else {
                sb.append(random.nextInt(100));
            }
        }

        if (random.nextBoolean()) {
            sb.append(" COMMENT '");
            sb.append("Modified column comment");
            sb.append("'");
        }

        if (random.nextBoolean()) {
            sb.append(random.nextBoolean() ? " FIRST" : " AFTER " + generateColIdentifier());
        }

        if (random.nextBoolean()) {
            String[] keyTypes = {"KEY", "UNIQUE KEY"};
            sb.append(" ").append(keyTypes[random.nextInt(keyTypes.length)]);
        }

        return sb.toString();
    }


    private String generateColumnDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateColIdentifier());
        sb.append(" ");
        sb.append(generateDataType());
        return sb.toString();
    }

    private String generatePartitionDefinition() {
        StringBuilder sb = new StringBuilder();
        String[] partitionTypes = {"RANGE", "LIST"};
        String partitionType = partitionTypes[random.nextInt(partitionTypes.length)];
        if (partitionType.equals("RANGE")) {
            sb.append(generateRangePartition());
        } else {
            sb.append(generateListPartition());
        }
        sb.append(")");
        return sb.toString();
    }

    private String generateRangePartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("PARTITION ").append(generatePartitionIdentifier()).append(" VALUES LESS THAN (").append(random.nextInt(100)).append(")");
        return sb.toString();
    }

    private String generateListPartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("PARTITION ").append(generatePartitionIdentifier()).append(" VALUES IN (").append(random.nextInt(100)).append(")");
        return sb.toString();
    }

    private String generatePartitionIdentifier() {
        return String.format("part_%d", random.nextInt(1000) + 1);
    }

    private String generateColIdentifier() {
        return String.format("col_%d", random.nextInt(1000) + 1);
    }

    private String generateDataType() {
        String[] dataTypes = {"INT", "VARCHAR(255)", "BOOLEAN", "DATE"};
        return dataTypes[random.nextInt(dataTypes.length)];
    }
}

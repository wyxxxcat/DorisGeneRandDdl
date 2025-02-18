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
    private static final String LOG_FILE = "rand_ddl.log";
    private List<String> tableNames = new ArrayList<>();
    
    public RandomDDLGenerator() {
        loadTableNames();
    }

    public void loadTableNames() {  // Changed from private to public
        tableNames.clear();  // Clear existing table names before reloading
        String url = DBConfig.getUrl();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(database, null, null, new String[]{"TABLE"});
            
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
            
            if (tableNames.isEmpty()) {
                System.err.println("Warning: No tables found in database " + database);
                tableNames.add("table1");
                tableNames.add("table2");
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
            tableNames.add("table1");
            tableNames.add("table2");
        }
    }

    private String generateTableName() {
        return tableNames.get(random.nextInt(tableNames.size()));
    }

    public static void main(String[] args) {
        RandomDDLGenerator generator = new RandomDDLGenerator();
        while(true) {
            String ddl = generator.generateDDL();
            if (ddl == "") {
                continue;
            }
            System.out.println(ddl);

            // Log to file
            try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                writer.println("=== " + timestamp + " ===");
                writer.println("Generated DDL:");
                writer.println(ddl);
                writer.println(); // Empty line for readability
                writer.flush();
            } catch (IOException e) {
                System.err.println("Error writing to log file: " + e.getMessage());
            }
        }
    }

    public String generateDDL() {
        // Reload table names before generating DDL
        loadTableNames();
        
        int choice = random.nextInt(7);  // Increased to include modify column
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
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" ADD COLUMN ");
        sb.append(generateColumnDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateDropColumn() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" DROP COLUMN ");
        sb.append(generateIdentifier());
        sb.append(";");
        return sb.toString();
    }

    private String generateAddPartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" ADD PARTITION ");
        sb.append(generatePartitionDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateDropPartition() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" DROP PARTITION ");
        sb.append(generateIdentifier());
        sb.append(";");
        return sb.toString();
    }

    private String generateAddRollup() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" ADD ROLLUP ");
        sb.append(generateIdentifier());   // rollup name
        sb.append(" (");
        // Generate rollup columns (subset of base table columns)
        int numColumns = random.nextInt(3) + 1;
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(generateIdentifier());  // column name
        }
        sb.append(")");
        
        // Optionally add duplicate key
        if (random.nextBoolean()) {
            sb.append(" DUPLICATE KEY(");
            sb.append(generateIdentifier());
            sb.append(")");
        }
        
        // Optionally add properties
        if (random.nextBoolean()) {
            sb.append(" PROPERTIES (");
            sb.append("'replication_num' = '").append(random.nextInt(3) + 1).append("'");
            sb.append(")");
        }
        
        sb.append(";");
        return sb.toString();
    }

    private String generateDropRollup() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" DROP ROLLUP ");
        sb.append(generateIdentifier());  // rollup name
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumn() {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());  // Changed from generateIdentifier()
        sb.append(" MODIFY COLUMN ");
        sb.append(generateModifyColumnDefinition());
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumnDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateIdentifier());  // column name
        sb.append(" ");
        sb.append(generateDataType());

        // Optional: Add column attributes
        // 1. Nullable
        if (random.nextBoolean()) {
            sb.append(random.nextBoolean() ? " NULL" : " NOT NULL");
        }

        // 2. Default value
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

        // 3. Comment
        if (random.nextBoolean()) {
            sb.append(" COMMENT '");
            sb.append("Modified column comment");
            sb.append("'");
        }

        // 4. Column position
        if (random.nextBoolean()) {
            sb.append(random.nextBoolean() ? " FIRST" : " AFTER " + generateIdentifier());
        }

        // 5. Key attributes
        if (random.nextBoolean()) {
            String[] keyTypes = {"KEY", "UNIQUE KEY"};
            sb.append(" ").append(keyTypes[random.nextInt(keyTypes.length)]);
        }

        return sb.toString();
    }

    private String generateColumnDefinitions() {
        int numColumns = random.nextInt(5) + 1; // Generate between 1 and 5 columns
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numColumns; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(generateColumnDefinition());
        }
        return sb.toString();
    }

    private String generateColumnDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append(generateIdentifier());
        sb.append(" ");
        sb.append(generateDataType());
        return sb.toString();
    }

    private String generatePartitionDefinition() {
        StringBuilder sb = new StringBuilder();
        String[] partitionTypes = {"RANGE", "LIST"};
        String partitionType = partitionTypes[random.nextInt(partitionTypes.length)];
        sb.append(" PARTITION BY ").append(partitionType).append(" (").append(generateIdentifier()).append(") (");
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
        int numPartitions = random.nextInt(3) + 1; // Generate between 1 and 3 partitions
        for (int i = 0; i < numPartitions; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("PARTITION ").append(generateIdentifier()).append(" VALUES LESS THAN (").append(random.nextInt(100)).append(")");
        }
        return sb.toString();
    }

    private String generateListPartition() {
        StringBuilder sb = new StringBuilder();
        int numPartitions = random.nextInt(3) + 1; // Generate between 1 and 3 partitions
        for (int i = 0; i < numPartitions; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("PARTITION ").append(generateIdentifier()).append(" VALUES IN (").append(random.nextInt(100)).append(")");
        }
        return sb.toString();
    }

    private String generateIdentifier() {
        String[] identifiers = {"col1", "col2", "col3", "table1", "table2", "part1", "part2"};
        return identifiers[random.nextInt(identifiers.length)];
    }

    private String generateDataType() {
        String[] dataTypes = {"INT", "VARCHAR(255)", "BOOLEAN", "DATE"};
        return dataTypes[random.nextInt(dataTypes.length)];
    }
}

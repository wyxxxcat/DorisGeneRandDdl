package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;
import com.mysql.cj.xdevapi.Table;

import java.sql.*;
import java.text.NumberFormat.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.OpenOption;
import java.security.cert.PKIXRevocationChecker.Option;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

enum PartitionType {
    RANGE,
    LIST,
    NO_PARTITION
}

public class RandomDDLGenerator {
    private static final Random random = new Random();
    private List<String> tableNames = new ArrayList<>();
    private List<String> partitions = new ArrayList<>();
    // Add these as class fields
    private List<String> partitionColumns = new ArrayList<>();
    private List<String> partitionTypes = new ArrayList<>();
    private Map<String, List<ColumnDesc>> tableInfo = new HashMap<>();
    private Map<String, PartitionType> PartitionTypeInfo = new HashMap<>();

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
                    tableNames.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
        }
    }

    public void loadTableIsRangePartition(String tableName) {
        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SHOW CREATE TABLE " + tableName;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String createTable = rs.getString(2);
                    if (createTable.contains("PARTITION BY RANGE")) {
                        PartitionTypeInfo.put(tableName, PartitionType.RANGE);
                    } else if (createTable.contains("PARTITION BY LIST")){
                        PartitionTypeInfo.put(tableName, PartitionType.LIST);
                    } else {
                        PartitionTypeInfo.put(tableName, PartitionType.NO_PARTITION);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
        }
    }

    public void loadTableDesc(String tableName) {
        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);
        tableInfo.clear();
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "DESC " + tableName + " ALL";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                String indexName = "";
                String indexKeysType = "";
                List<ColumnDesc> columnDescs = new ArrayList<>();
                while (rs.next()) {
                    String field = rs.getString("Field");
                    if (field == "") {
                        indexName = "";
                        indexKeysType = "";
                        continue;
                    } else if (indexName == "") {
                        indexName = rs.getString("IndexName");
                        indexKeysType = rs.getString("IndexKeysType");
                    }
                    String type = rs.getString("Type");
                    String internalType = rs.getString("InternalType");
                    String isNull = rs.getString("Null");

                    String key = rs.getString("Key");
                    String defaultValue = rs.getString("Default");
                    String extra = rs.getString("Extra");

                    String visible = rs.getString("Visible");
                    String defineExpr = rs.getString("DefineExpr");
                    String whereClause = rs.getString("WhereClause");
                    columnDescs.add(new ColumnDesc(indexName, indexKeysType, field, type, internalType, isNull, key, defaultValue, extra, visible, defineExpr, whereClause));   
                }
                tableInfo.put(tableName, columnDescs);
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableInfo.clear();
        }
    }

    public void loadPartitioInfoFromTable(String tableName) {
        partitions.clear();
        partitionColumns.clear();
        partitionTypes.clear();
    
        String url = String.format("jdbc:mysql://%s:%s/%s",
                DBConfig.getHost(), DBConfig.getPort(), DBConfig.getDatabase());
    
        try (Connection conn = DriverManager.getConnection(url, DBConfig.getUser(), DBConfig.getPassword())) {
            String sql = "SHOW CREATE TABLE " + tableName;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
    
                if (rs.next()) {
                    String createTable = rs.getString(2); // Get the CREATE TABLE statement
                    List<ColumnDesc> columnDescs = tableInfo.get(tableName);
                    // Find partition definition
                    int partitionByIndex = createTable.indexOf("PARTITION BY");
                    if (partitionByIndex == -1) {
                        partitionByIndex = createTable.indexOf("DISTRIBUTED BY HASH");
                    }
                    if (partitionByIndex != -1) {
                        // Extract text between parentheses after LIST/RANGE
                        int startParen = createTable.indexOf('(', partitionByIndex);
                        int endParen = createTable.indexOf(')', startParen);
                        if (startParen != -1 && endParen != -1) {
                            String partitionCols = createTable.substring(startParen + 1, endParen);
                            
                            // Split and clean column names
                            String[] cols = partitionCols.split(",");
                            for (String col : cols) {
                                // Remove backticks and trim
                                String cleanCol = col.trim().replace("`", "");
                                if (columnDescs.stream().anyMatch(c -> c.columnSchema.field.equals(cleanCol))) {
                                    partitionColumns.add(cleanCol);
                                    partitionTypes.add(columnDescs.stream()
                                        .filter(c -> c.columnSchema.field.equals(cleanCol))
                                        .findFirst()
                                        .get()
                                        .columnSchema.type);
                                }
                            }
                        }
                    } else {
                        System.out.println("No partition definition found for table: " + tableName);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading partition info: " + e.getMessage());
            e.printStackTrace();
            partitionColumns.clear();
            partitionTypes.clear();
        }
    }

    private String generateTableName() {
        loadTableNames();
        return tableNames.get(random.nextInt(tableNames.size()));
    }

    public String generateDDL() {
        
        int choice = random.nextInt(7); 
        // int choice = 2;
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

    // Can not drop key col
    private String generateDropColumn() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);
        Optional<ColumnDesc> colDesc = tableInfo.get(tableName).stream()
                .filter(c -> !c.columnSchema.key.equals("true")).findAny();

        if (!colDesc.isPresent()) {
            return "";
        }
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP COLUMN ");
        sb.append(colDesc.get().columnSchema.field);
        sb.append(";");
        return sb.toString();
    }

    private String generateAddPartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);
        loadTableIsRangePartition(tableName);
        loadPartitioInfoFromTable(tableName);
        sb.append("ALTER TABLE ");
        sb.append(tableName); 
        sb.append(" ADD PARTITION ");
        sb.append(generatePartitionDefinition(tableName));
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
        String tableName = generateTableName();
        loadTableDesc(tableName);
        Optional<ColumnDesc> colDesc = tableInfo.get(tableName).stream()
                .filter(c -> !c.columnSchema.key.equals("true")).findAny();

        if (!colDesc.isPresent()) {
            return "";
        }
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());
        sb.append(" MODIFY COLUMN ");
        sb.append(";");
        sb.append(generateModifyColumnDefinition(colDesc.get().columnSchema.field));
        return sb.toString();
    }

    private String generateModifyColumnDefinition(String colName) {
        StringBuilder sb = new StringBuilder();
        sb.append(colName); 
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

    private String generatePartitionDefinition(String tableName) {
        StringBuilder sb = new StringBuilder();
        if (PartitionTypeInfo.get(tableName).equals(PartitionType.RANGE)) {
            sb.append(generateRangePartition());
        } else if (PartitionTypeInfo.get(tableName).equals(PartitionType.LIST)) {
            sb.append(generateListPartition());
        } else {
            sb.append(generateListPartition());
        }
        return sb.toString();
    }

    // Update generateRangePartition method
    private String generateRangePartition() {
        StringBuilder sb = new StringBuilder();
        String partName = generatePartitionIdentifier();
        sb.append(partName).append(" VALUES [");

        sb.append("(");
        for (int i = 0; i < partitionColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            String type = partitionTypes.get(i);
            sb.append(generateRangePartitionValueByType(type));
        }
        sb.append("), ");

        sb.append("(");
        for (int i = 0; i < partitionColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            String type = partitionTypes.get(i);
            sb.append(generateRangePartitionValueByType(type));
        }
        sb.append("))");

        return sb.toString();
    }

    private String generateListPartition() {
        StringBuilder sb = new StringBuilder();
        String partName = generatePartitionIdentifier();
        sb.append(partName).append(" VALUES IN ((");
    
        for (int j = 0; j < partitionColumns.size(); j++) {
            if (j > 0) {
                sb.append(", ");
            }
            String type = partitionTypes.get(j);
            sb.append(generateListPartitionValueByType(type));
        }
        
        sb.append("))");
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

    private String generateRangePartitionValueByType(String type) {
        switch (type.trim().toUpperCase()) {
            case "INT":
            case "BIGINT":
            case "TINYINT":
            case "SMALLINT":
            case "LARGEINT":
                return String.valueOf(random.nextBoolean() ? random.nextLong(10, 2000) : random.nextLong(1000000));
            case "DATE":
            case "DATETIME":
                int year = 2024 + (random.nextBoolean() ? 1 : 0);
                return String.format("\"%d-%02d-%02d\"", year, random.nextInt(1, 13), random.nextInt(1, 29));
            default:
                return random.nextBoolean() ? String.valueOf(random.nextInt(51, 100)) : String.valueOf(random.nextInt(50));
        }
    }

    // Update generateValueByType to support more data types
    private String generateListPartitionValueByType(String type) {

        switch (type.trim().toUpperCase()) {
            case "BOOLEAN":
                return "\"" + (random.nextBoolean() ? "TRUE" : "FALSE") + "\"";
            case "TINYINT":
                return "\"" + random.nextInt(-128, 127) + "\"";
            case "SMALLINT":
                return "\"" + random.nextInt(-32768, 32767) + "\"";
            case "INT":
                return "\"" + random.nextInt(-2147483648, 2147483647) + "\"";
            case "BIGINT":
            case "LARGEINT":
                return "\"" + random.nextLong(-9223372036854775808L, 9223372036854775807L) + "\"";
            case "DATE":
                int year = 2020 + random.nextInt(10);
                return String.format("\"%d-%02d-%02d\"", year, random.nextInt(1, 13), random.nextInt(1, 29));
            case "DATETIME":
                year = 2020 + random.nextInt(10);
                return String.format("\"%d-%02d-%02d %02d:%02d:%02d\"", 
                    year, random.nextInt(1, 13), random.nextInt(1, 29),
                    random.nextInt(24), random.nextInt(60), random.nextInt(60));
            case "CHAR":
            case "VARCHAR":
                String[] cities = {"Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou"};
                return "\"" + cities[random.nextInt(cities.length)] + "\"";
            default:
                return "\"0\"";
        }
    }
}

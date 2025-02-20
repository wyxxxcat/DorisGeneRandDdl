package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;

import java.security.cert.PKIXRevocationChecker.Option;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

enum PartitionType {
    RANGE,
    LIST,
    NO_PARTITION
}

public class RandomDDLGenerator {
    private static final Random random = new Random();
    private List<String> tableNames = new ArrayList<>();
    private List<String> partitions = new ArrayList<>();
    private List<String> rollupNames = new ArrayList<>();
    private List<String> partitionNames = new ArrayList<>();
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
                    } else if (createTable.contains("PARTITION BY LIST")) {
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
                while (rs.next()) {
                    String field = rs.getString("Field");
                    if (field.isEmpty()) {
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
                    List<ColumnSchema> columnSchema = new ArrayList<>(Arrays.asList(new ColumnSchema(indexName,
                            indexKeysType, field,
                            type, internalType, isNull, key, defaultValue, extra, visible, defineExpr, whereClause)));
                    List<ColumnDesc> columnDescs = new ArrayList<>(Arrays
                            .asList(new ColumnDesc(indexName, indexKeysType, columnSchema)));
                    if (tableInfo.get(indexName) == null) {
                        tableInfo.put(indexName, columnDescs);
                    } else {
                        tableInfo.get(indexName).add(new ColumnDesc(indexName, indexKeysType, columnSchema));
                    }
                    rollupNames.add(indexName);
                }
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
                    List<ColumnSchema> colSchema = tableInfo.get(tableName).get(0).columnSchema;
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
                                if (colSchema.stream().anyMatch(c -> c.field.equals(cleanCol))) {
                                    partitionColumns.add(cleanCol);
                                    partitionTypes.add(colSchema.stream()
                                            .filter(c -> c.field.equals(cleanCol))
                                            .findFirst()
                                            .get().type);
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

        int choice = random.nextInt(6);
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

        if (tableInfo.get(tableName) == null) {
            return "";
        }

        Optional<ColumnDesc> colDesc = tableInfo.get(tableName).stream()
                .filter(c -> c.IndexName.equals(tableName))
                .findFirst();

        if (!colDesc.isPresent()) {
            return "";
        }

        List<String> nonKeyColumns = tableInfo.get(tableName).stream()
                .filter(c -> c.IndexName.equals(tableName))
                .flatMap(c -> c.columnSchema.stream())
                .filter(c -> !c.key.equals("true"))
                .map(c -> c.field)
                .collect(Collectors.toList());

        if (nonKeyColumns.isEmpty()) {
            return "";
        }

        String columnToDrop = nonKeyColumns.get(random.nextInt(nonKeyColumns.size()));

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP COLUMN ");
        sb.append(columnToDrop);
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
        String partitioName = partitionNames.get(random.nextInt(partitionNames.size()));
        sb.append("ALTER TABLE ");
        sb.append(generateTableName());
        sb.append(" DROP PARTITION ");
        sb.append(partitioName);
        sb.append(";");
        return sb.toString();
    }

    private String generateAddRollup() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        String rollupName = generateRollupIdentifier();
        rollupNames.add(rollupName);
        loadTableDesc(tableName);
        List<ColumnDesc> colDesc = tableInfo.get(tableName);
        String rollupCol = String.join(", ", random.ints(0, colDesc.size())
                .distinct()
                .limit(colDesc.size())
                .mapToObj(colDesc::get)
                .filter(c -> c.columnSchema.get(0).key.equals("false")).collect(Collectors.toList()).stream()
                .map(c -> c.columnSchema.get(0).field).distinct().collect(Collectors.toList()));
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" ADD ROLLUP ");
        sb.append(rollupName);
        sb.append("(").append(rollupCol).append(")");
        sb.append(";");
        return sb.toString();
    }

    private String generateDropRollup() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (rollupNames.isEmpty()) {
            return "";
        }

        String rollupName = rollupNames.get(random.nextInt(rollupNames.size()));
        if (tableInfo.get(rollupName) == null) {
            return "";
        }

        Optional<ColumnDesc> colDesc = tableInfo.get(rollupName).stream().filter(c -> !c.IndexName.equals(tableName))
                .findFirst();
        if (!colDesc.isPresent()) {
            return "";
        }
        rollupNames.remove(rollupName);
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP ROLLUP ");
        // indexName != tableName
        sb.append(colDesc.get().IndexName);
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumn() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        List<ColumnDesc> colDesc = tableInfo.get(tableName);
        loadTableDesc(tableName);
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" MODIFY COLUMN ");
        sb.append(
                generateModifyColumnDefinition(colDesc.get(random.nextInt(colDesc.size())).columnSchema.get(0).field));
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
            sb.append(" ").append("KEY");
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
        partitionNames.add(partName);
        sb.append(partName).append(" VALUES [");

        sb.append("(");
        for (int i = 0; i < partitionColumns.size(); i++) {
            if (i > 0)
                sb.append(", ");
            String type = partitionTypes.get(i);
            sb.append(generateRangePartitionValueByType(type));
        }
        sb.append("), ");

        sb.append("(");
        for (int i = 0; i < partitionColumns.size(); i++) {
            if (i > 0)
                sb.append(", ");
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

    private String generateRollupIdentifier() {
        return String.format("rollup_%d", random.nextInt(1000) + 1);
    }

    private String generateDataType() {
        String[] dataTypes = { "INT", "VARCHAR(255)", "BOOLEAN", "DATE" };
        return dataTypes[random.nextInt(dataTypes.length)];
    }

    private String generateRangePartitionValueByType(String type) {
        switch (type.trim().toUpperCase()) {
            case "INT":
            case "BIGINT":
            case "TINYINT":
            case "SMALLINT":
            case "LARGEINT":
                return String.valueOf(random.nextLong());
            case "DATE":
            case "DATETIME":
                int year = 2024 + (random.nextBoolean() ? 1 : 0);
                return String.format("\"%d-%02d-%02d\"", year, random.nextInt(), random.nextInt());
            default:
                return random.nextBoolean() ? String.valueOf(random.nextInt()) : String.valueOf(random.nextInt(50));
        }
    }

    // Update generateValueByType to support more data types
    private String generateListPartitionValueByType(String type) {

        switch (type.trim().toUpperCase()) {
            case "BOOLEAN":
                return "\"" + (random.nextBoolean() ? "TRUE" : "FALSE") + "\"";
            case "TINYINT":
                return "\"" + random.nextInt() + "\"";
            case "SMALLINT":
                return "\"" + random.nextInt() + "\"";
            case "INT":
                return "\"" + random.nextInt() + "\"";
            case "BIGINT":
            case "LARGEINT":
                return "\"" + random.nextLong() + "\"";
            case "DATE":
                int year = 2020 + random.nextInt(10);
                return String.format("\"%d-%02d-%02d\"", year, random.nextInt(), random.nextInt());
            case "DATETIME":
                year = 2020 + random.nextInt(10);
                return String.format("\"%d-%02d-%02d %02d:%02d:%02d\"",
                        year, random.nextInt(), random.nextInt(),
                        random.nextInt(24), random.nextInt(60), random.nextInt(60));
            case "CHAR":
            case "VARCHAR":
                String[] cities = { "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou" };
                return "\"" + cities[random.nextInt(cities.length)] + "\"";
            default:
                return "\"0\"";
        }
    }
}

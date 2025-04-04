package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private List<String> viewNames = new ArrayList<>();
    private List<String> indexNames = new ArrayList<>();
    private List<String> partitions = new ArrayList<>();
    private List<String> rollupNames = new ArrayList<>();
    private List<String> partitionNames = new ArrayList<>();
    private List<ColumnRangeInfo> partitionRanges = new ArrayList<>();
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
            String sql = "SHOW FULL TABLES";
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String name = rs.getString(1);
                    String type = rs.getString(2);
                    if ("BASE TABLE".equals(type)) {
                        tableNames.add(name);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
        }
    }

    public void loadViewNames() {
        viewNames.clear();
        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SHOW VIEWS FROM " + database;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    viewNames.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            viewNames.clear();
        }
    }

    public void loadIndexNames(String tableName) {
        indexNames.clear();
        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SHOW INDEX FROM " + tableName;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    indexNames.add(rs.getString("Key_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            indexNames.clear();
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

    public void loadTablePartitionInfos(String tableName) {
        partitionNames.clear();
        partitionRanges.clear();

        String host = DBConfig.getHost();
        String port = DBConfig.getPort();
        String user = DBConfig.getUser();
        String password = DBConfig.getPassword();
        String database = DBConfig.getDatabase();
        String url = String.format("jdbc:mysql://%s:%s/%s", host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SHOW PARTITIONS FROM " + tableName;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String partitionName = rs.getString("PartitionName");
                    partitionNames.add(partitionName);

                    try {
                        String rangeInfo = rs.getString("Range");
                        String partitionKey = rs.getString("PartitionKey");

                        if (rangeInfo != null && partitionKey != null) {
                            Pattern pattern = Pattern.compile("types: \\[(.*?)\\]; keys: \\[(.*?)\\];");
                            Matcher matcher = pattern.matcher(rangeInfo);

                            if (matcher.find()) {
                                String lowerType = matcher.group(1);
                                String lowerKeyValue = matcher.group(2);
                                Object lowerValue = parseValue(lowerType, lowerKeyValue);

                                if (matcher.find()) {
                                    String upperType = matcher.group(1);
                                    String upperKeyValue = matcher.group(2);
                                    Object upperValue = parseValue(upperType, upperKeyValue);

                                    ColumnRangeInfo partitionInfo = new ColumnRangeInfo(
                                            partitionKey, lowerType, lowerValue, upperValue);
                                    partitionRanges.add(partitionInfo);
                                    System.out.println("Partition Name: " + partitionName +
                                            ", Partition Key: " + partitionKey +
                                            ", Lower Bound: " + lowerValue +
                                            ", Upper Bound: " + (upperValue != null ? upperValue : "NULL"));
                                } else {
                                    ColumnRangeInfo partitionInfo = new ColumnRangeInfo(
                                            partitionKey, lowerType, lowerValue, null);
                                    partitionRanges.add(partitionInfo);
                                }

                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing partition range info: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading partition info: " + e.getMessage());
            partitionNames.clear();
            partitionRanges.clear();
        }
    }

    private static Object parseValue(String dataType, String value) {
        switch (dataType.toUpperCase()) {
            case "INT":
            case "TINYINT":
            case "SMALLINT":
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "BIGINT":
            case "LARGEINT":
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "DECIMAL":
            case "DOUBLE":
            case "FLOAT":
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "DATE":
            case "DATETIME":
            case "VARCHAR":
            case "CHAR":
            default:
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
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

        if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null
                || tableInfo.get(tableName).isEmpty()) {
            return;
        }

        String url = String.format("jdbc:mysql://%s:%s/%s",
                DBConfig.getHost(), DBConfig.getPort(), DBConfig.getDatabase());

        try (Connection conn = DriverManager.getConnection(url, DBConfig.getUser(), DBConfig.getPassword())) {
            String sql = "SHOW CREATE TABLE " + tableName;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    String createTable = rs.getString(2);
                    List<ColumnSchema> colSchema = tableInfo.get(tableName).get(0).columnSchema;
                    if (colSchema == null || colSchema.isEmpty()) {
                        return;
                    }

                    int partitionByIndex = createTable.indexOf("PARTITION BY");
                    if (partitionByIndex == -1) {
                        partitionByIndex = createTable.indexOf("DISTRIBUTED BY HASH");
                    }
                    if (partitionByIndex != -1) {
                        int startParen = createTable.indexOf('(', partitionByIndex);
                        int endParen = createTable.indexOf(')', startParen);
                        if (startParen != -1 && endParen != -1) {
                            String partitionCols = createTable.substring(startParen + 1, endParen);

                            String[] cols = partitionCols.split(",");
                            for (String col : cols) {
                                String cleanCol = col.trim().replace("`", "");
                                if (colSchema.stream().anyMatch(c -> c.field.equals(cleanCol))) {
                                    partitionColumns.add(cleanCol);
                                    Optional<String> type = colSchema.stream()
                                            .filter(c -> c.field.equals(cleanCol))
                                            .map(c -> c.type)
                                            .findFirst();
                                    if (type.isPresent()) {
                                        partitionTypes.add(type.get());
                                    }
                                }
                            }
                        }
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
        int choice = random.nextInt(9);
        switch (choice) {
            case 0:
                return generateAddColumn();
            case 1:
                return generateDropColumn();
            // case 2:
            // return generateModifyColumn();
            // case 3:
            // return generateRenameColumn();
            case 4:
                return generateAddPartition();
            case 5:
                return generateDropPartition();
            // case 6:
            // return generateReplacePartition();
            // case 7:
            // return generateRenamePartition();
            case 8:
                return generateInsertInto();
            // case 8:
            // return generateAddRollup();
            // case 9:
            // return generateDropRollup();
            // case 10:
            // return generateRenameRollup();
            // case 11:
            // return generateCreateIndex();
            // case 12:
            // return generateDropIndex();
            // case 13:
            // return generateBuildIndex();
            // case 16:
            // return generateCreateView();
            // case 17:
            // return generateDropView();
            // case 18:
            // return generateAlterView();
            // case 19:
            // return generateInsertInto();
            // case 19:
            // return generateCreateMaterializedView();
            // case 20:
            // return generateDropMaterializedView();
            default:
                return "";
        }
    }

private Map<String, List<Object>> listPartitionValues = new HashMap<>();

// Add this method to load LIST partition values
private void loadListPartitionValues(String tableName) {
    listPartitionValues.clear();
    
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
            
            if (rs.next()) {
                String createTable = rs.getString(2);
                
                // Extract LIST partition values
                Pattern listPattern = Pattern.compile("PARTITION\\s+([\\w_]+)\\s+VALUES\\s+IN\\s+\\(\\((.+?)\\)\\)", 
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher listMatcher = listPattern.matcher(createTable);
                
                while (listMatcher.find()) {
                    String partitionValues = listMatcher.group(2);
                    
                    // Split values by comma if multiple values or columns
                    String[] valueItems = partitionValues.split(",");
                    
                    for (int i = 0; i < Math.min(valueItems.length, partitionColumns.size()); i++) {
                        String partitionKey = partitionColumns.get(i);
                        String dataType = "VARCHAR"; // Default assumption
                        
                        // Try to determine the data type
                        if (i < partitionTypes.size()) {
                            dataType = partitionTypes.get(i);
                        }
                        
                        // Clean the value and parse it
                        String cleanValue = valueItems[i].trim();
                        if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"")) {
                            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
                        }
                        
                        Object parsedValue = parseValue(dataType, cleanValue);
                        
                        // Add to the list partition values map
                        if (!listPartitionValues.containsKey(partitionKey)) {
                            listPartitionValues.put(partitionKey, new ArrayList<>());
                        }
                        listPartitionValues.get(partitionKey).add(parsedValue);
                        
                        System.out.println("LIST Partition Value - Key: " + partitionKey + 
                                ", Type: " + dataType + ", Value: " + parsedValue);
                    }
                }
            }
        }
    } catch (SQLException e) {
        System.err.println("Error loading LIST partition values: " + e.getMessage());
        listPartitionValues.clear();
    }
}

public String generateInsertInto() {
    String tableName = generateTableName();
    
    loadTableDesc(tableName);
    loadTableIsRangePartition(tableName);
    loadPartitioInfoFromTable(tableName);
    loadTablePartitionInfos(tableName);
    
    if (PartitionTypeInfo.containsKey(tableName) && 
            PartitionTypeInfo.get(tableName) == PartitionType.LIST) {
        loadListPartitionValues(tableName);
    }

    if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null ||
            tableInfo.get(tableName).isEmpty()) {
        return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("INSERT INTO ");
    sb.append(tableName);

    List<ColumnSchema> columns = tableInfo.get(tableName).stream()
            .filter(c -> c.IndexName.equals(tableName))
            .flatMap(c -> c.columnSchema.stream())
            .collect(Collectors.toList());

    if (columns.isEmpty()) {
        return "";
    }

    sb.append(" (");
    sb.append(columns.stream()
            .map(c -> c.field)
            .collect(Collectors.joining(", ")));
    sb.append(") VALUES ");

    int rowCount = 1 + random.nextInt(3);
    for (int i = 0; i < rowCount; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        
        sb.append("(");
        
        for (int j = 0; j < columns.size(); j++) {
            if (j > 0) {
                sb.append(", ");
            }
            
            ColumnSchema column = columns.get(j);
            boolean isPartitionColumn = false;
            
            if (!partitionColumns.isEmpty() && partitionColumns.contains(column.field)) {
                isPartitionColumn = true;
            }
            
            sb.append(generateValueForColumn(column, tableName, isPartitionColumn));
        }
        
        sb.append(")");
    }
    
    sb.append(";");
    return sb.toString();
}

private String generateValueWithinRange(ColumnRangeInfo range, String type) {
    type = type.toUpperCase();
    Object lowerBound = range.getLowerBound();
    Object upperBound = range.getUpperBound();
    
    if (lowerBound == null && upperBound == null) {
        return generateGenericValue(type);
    }
    
    try {
        if (type.contains("INT") || type.equals("BIGINT") || type.equals("LARGEINT") ||
                type.equals("TINYINT") || type.equals("SMALLINT")) {
            long lower = lowerBound != null ? Long.parseLong(lowerBound.toString()) : 0;
            long upper = upperBound != null ? Long.parseLong(upperBound.toString()) : Long.MAX_VALUE;
            
            if (upper <= lower) {
                upper = lower + 100;
            }
            
            return String.valueOf(lower + random.nextInt((int)Math.min(upper - lower, 1000)));
            
        } else if (type.equals("DATE")) {
            return String.format("'%d-%02d-%02d'", 
                    2020 + random.nextInt(5), 
                    1 + random.nextInt(12), 
                    1 + random.nextInt(28));
            
        } else if (type.equals("DATETIME")) {
            return String.format("'%d-%02d-%02d %02d:%02d:%02d'", 
                    2020 + random.nextInt(5), 
                    1 + random.nextInt(12), 
                    1 + random.nextInt(28),
                    random.nextInt(24),
                    random.nextInt(60),
                    random.nextInt(60));
        }
    } catch (Exception e) {
    }
    
    return generateGenericValue(type);
}

private String generateValueForColumn(ColumnSchema column, String tableName, boolean isPartitionColumn) {
    String type = column.type.toUpperCase();
    boolean isNull = "YES".equalsIgnoreCase(column.isNull);
    
    if (isNull && !isPartitionColumn && random.nextInt(10) == 0) {
        return "NULL";
    }
    
    if (isPartitionColumn && PartitionTypeInfo.containsKey(tableName)) {
        PartitionType partType = PartitionTypeInfo.get(tableName);
        
        if (partType == PartitionType.RANGE) {
            for (ColumnRangeInfo range : partitionRanges) {
                if (range.getPartitionKey().equals(column.field)) {
                    return generateValueWithinRange(range, type);
                }
            }
        } else if (partType == PartitionType.LIST) {
            if (listPartitionValues.containsKey(column.field) && 
                !listPartitionValues.get(column.field).isEmpty()) {
                
                Object value = listPartitionValues.get(column.field)
                    .get(random.nextInt(listPartitionValues.get(column.field).size()));
                
                if (type.contains("INT") || type.equals("BIGINT") || type.equals("LARGEINT") ||
                        type.equals("TINYINT") || type.equals("SMALLINT")) {
                    return value.toString();
                } else if (type.equals("BOOLEAN")) {
                    return value.toString().toUpperCase();
                } else if (type.contains("CHAR") || type.contains("VARCHAR") ||
                           type.equals("DATE") || type.equals("DATETIME")) {
                    return "'" + value.toString() + "'";
                }
                return value.toString();
            }
            
            return generateListCompatibleValue(type);
        }
    }
    
    return generateGenericValue(type);
}

private String generateListCompatibleValue(String type) {
    type = type.toUpperCase();
    
    if (type.equals("BOOLEAN")) {
        return random.nextBoolean() ? "TRUE" : "FALSE";
    } else if (type.contains("INT") || type.equals("BIGINT") || type.equals("LARGEINT") ||
            type.equals("TINYINT") || type.equals("SMALLINT")) {
        // For list partitions, usually a small set of distinct values
        int[] commonValues = {0, 1, 2, 3, 4, 5, 10, 100};
        return String.valueOf(commonValues[random.nextInt(commonValues.length)]);
    } else if (type.equals("DATE")) {
        // Common date values for list partitions (e.g., first day of months)
        int year = 2020 + random.nextInt(5);
        int month = 1 + random.nextInt(12);
        return String.format("'%d-%02d-01'", year, month);
    } else if (type.equals("DATETIME")) {
        int year = 2020 + random.nextInt(5);
        int month = 1 + random.nextInt(12);
        return String.format("'%d-%02d-01 00:00:00'", year, month);
    } else if (type.contains("CHAR") || type.contains("VARCHAR")) {
        // Categories often used in LIST partitions
        String[][] categorySet = {
            {"'North'", "'South'", "'East'", "'West'", "'Central'"},
            {"'Beijing'", "'Shanghai'", "'Guangzhou'", "'Shenzhen'", "'Hangzhou'"},
            {"'Electronics'", "'Clothing'", "'Food'", "'Books'", "'Furniture'"}
        };
        
        String[] chosenSet = categorySet[random.nextInt(categorySet.length)];
        return chosenSet[random.nextInt(chosenSet.length)];
    }
    
    return generateGenericValue(type);
}

    private String generateGenericValue(String type) {
                type = type.toUpperCase();

        if (type.contains("INT") || type.equals("BIGINT") || type.equals("LARGEINT") ||
                type.equals("TINYINT") || type.equals("SMALLINT")) {
            return String.valueOf(random.nextInt(1000));
        } else if (type.contains("DECIMAL") || type.contains("DOUBLE") || type.contains("FLOAT")) {
            return String.format("%.2f", random.nextDouble() * 1000);
        } else if (type.equals("BOOLEAN")) {
            return random.nextBoolean() ? "TRUE" : "FALSE";
        } else if (type.equals("DATE")) {
            return String.format("'%d-%02d-%02d'",
                    2020 + random.nextInt(5),
                    1 + random.nextInt(12),
                    1 + random.nextInt(28));
        } else if (type.equals("DATETIME")) {
            return String.format("'%d-%02d-%02d %02d:%02d:%02d'",
                    2020 + random.nextInt(5),
                    1 + random.nextInt(12),
                    1 + random.nextInt(28),
                    random.nextInt(24),
                    random.nextInt(60),
                    random.nextInt(60));
        } else if (type.contains("CHAR") || type.contains("VARCHAR")) {
            String[] words = { "'data'", "'test'", "'sample'", "'doris'", "'apache'",
                    "'database'", "'bigdata'", "'analytics'", "'query'", "'sql'" };
            return words[random.nextInt(words.length)];
        } else {
            return "'default_value'";
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

    private String generateColumnName(String tableName) {
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

        return nonKeyColumns.get(random.nextInt(nonKeyColumns.size()));
    }

    private String generateDropColumn() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        String columnName = generateColumnName(tableName);
        if (columnName.isEmpty()) {
            return "";
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP COLUMN ");
        sb.append(columnName);
        sb.append(";");
        return sb.toString();
    }

    private String generateAddPartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();

        loadTableDesc(tableName);
        loadTableIsRangePartition(tableName);
        loadPartitioInfoFromTable(tableName);

        if (partitionColumns.isEmpty() || partitionTypes.isEmpty() ||
                !PartitionTypeInfo.containsKey(tableName)) {
            return "";
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" ADD PARTITION ");
        sb.append(generatePartitionDefinition(tableName));
        sb.append(";");
        return sb.toString();
    }

    private String generateDropPartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTablePartitionInfos(tableName);
        if (partitionNames.isEmpty()) {
            return "";
        }
        String partitioName = partitionNames.get(random.nextInt(partitionNames.size()));
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP PARTITION ");
        sb.append(partitioName);
        sb.append(";");
        return sb.toString();
    }

    private String generateAddRollup() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        String rollupName = generateRollupIdentifier();
        loadTableDesc(tableName);

        if (!tableInfo.containsKey(tableName)) {
            return "";
        }

        List<ColumnDesc> colDesc = tableInfo.get(tableName);
        if (colDesc == null || colDesc.isEmpty()) {
            return "";
        }

        List<String> rollupColumns = colDesc.stream()
                .filter(c -> c.columnSchema != null && !c.columnSchema.isEmpty())
                .filter(c -> c.IndexName.equals(tableName))
                .map(c -> c.columnSchema.get(0))
                .filter(c -> c.key != null && c.key.equals("false"))
                .map(c -> c.field)
                .distinct()
                .collect(Collectors.toList());

        if (rollupColumns.isEmpty()) {
            return "";
        }

        int numColumns = 1 + random.nextInt(Math.min(3, rollupColumns.size()));
        List<String> selectedColumns = random.ints(0, rollupColumns.size())
                .distinct()
                .limit(numColumns)
                .mapToObj(rollupColumns::get)
                .collect(Collectors.toList());

        String rollupCol = String.join(", ", selectedColumns);

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
        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" DROP ROLLUP ");
        sb.append(colDesc.get().IndexName);
        sb.append(";");
        return sb.toString();
    }

    private String generateModifyColumnDefinition(String tableName) {
        StringBuilder sb = new StringBuilder();
        String columnName = generateColumnName(tableName);

        if (columnName == null || columnName.isEmpty()) {
            return "";
        }

        if (!tableInfo.containsKey(tableName)) {
            return "";
        }

        sb.append(columnName);
        sb.append(" ");
        sb.append(generateDataType());

        if (random.nextBoolean()) {
            sb.append(random.nextBoolean() ? " NULL" : " NOT NULL");
        }

        if (random.nextBoolean()) {
            String dataType = tableInfo.get(tableName).stream()
                    .filter(c -> c.IndexName.equals(tableName))
                    .flatMap(c -> c.columnSchema.stream())
                    .filter(c -> c.field.equals(columnName))
                    .map(c -> c.type)
                    .findFirst()
                    .orElse("INT");

            sb.append(" DEFAULT ");
            if (dataType.toUpperCase().contains("VARCHAR")) {
                sb.append("'default_value'");
            } else if (dataType.toUpperCase().equals("BOOLEAN")) {
                sb.append(random.nextBoolean() ? "TRUE" : "FALSE");
            } else if (dataType.toUpperCase().equals("DATE")) {
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
            sb.append(random.nextBoolean() ? " FIRST" : " AFTER " + generateColumnName(tableName));
        }

        return sb.toString();
    }

    private String generateModifyColumn() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        String columnDef = generateModifyColumnDefinition(tableName);
        if (columnDef.isEmpty()) {
            return "";
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" MODIFY COLUMN ");
        sb.append(columnDef);
        sb.append(";");

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
                return String.valueOf(random.nextInt(1000));
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

    private String generateRenameColumn() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        String oldColumnName = generateColumnName(tableName);
        if (oldColumnName.isEmpty()) {
            return "";
        }

        String newColumnName = generateColIdentifier();

        if (tableInfo.containsKey(tableName)) {
            List<ColumnDesc> columnDescs = tableInfo.get(tableName);
            for (ColumnDesc desc : columnDescs) {
                for (ColumnSchema schema : desc.columnSchema) {
                    if (schema.field.equals(oldColumnName)) {
                        schema.field = newColumnName;
                    }
                }
            }
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" RENAME COLUMN ");
        sb.append(oldColumnName);
        sb.append(" ");
        sb.append(newColumnName);
        sb.append(";");

        return sb.toString();
    }

    private String generateReplacePartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTablePartitionInfos(tableName);

        if (partitionNames.isEmpty()) {
            return "";
        }

        String existingPartition = partitionNames.get(random.nextInt(partitionNames.size()));

        String tempPartition = "temp_" + generatePartitionIdentifier();

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" REPLACE PARTITION (");
        sb.append(existingPartition);
        sb.append(") WITH TEMPORARY PARTITION (");
        sb.append(tempPartition);
        sb.append(");");

        return sb.toString();
    }

    private String generateRenamePartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTablePartitionInfos(tableName);

        if (partitionNames.isEmpty()) {
            return "";
        }

        String oldPartitionName = partitionNames.get(random.nextInt(partitionNames.size()));

        String newPartitionName = generatePartitionIdentifier();

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" RENAME PARTITION ");
        sb.append(oldPartitionName);
        sb.append(" ");
        sb.append(newPartitionName);
        sb.append(";");

        return sb.toString();
    }

    private String generateRenameRollup() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (rollupNames.isEmpty()) {
            return "";
        }

        Optional<String> oldRollupOpt = rollupNames.stream()
                .filter(name -> !name.equals(tableName))
                .findFirst();

        if (!oldRollupOpt.isPresent()) {
            return "";
        }

        String oldRollupName = oldRollupOpt.get();
        String newRollupName = generateRollupIdentifier();

        if (tableInfo.containsKey(oldRollupName)) {
            List<ColumnDesc> columnDescs = tableInfo.get(oldRollupName);
            tableInfo.remove(oldRollupName);
            tableInfo.put(newRollupName, columnDescs);

            for (ColumnDesc desc : columnDescs) {
                desc.IndexName = newRollupName;
                for (ColumnSchema schema : desc.columnSchema) {
                    schema.indexName = newRollupName;
                }
            }
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" RENAME ROLLUP ");
        sb.append(oldRollupName);
        sb.append(" ");
        sb.append(newRollupName);
        sb.append(";");

        return sb.toString();
    }

    private String generateCreateIndex() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        String indexName = String.format("idx_%s_%d", tableName, random.nextInt(1000));

        if (!tableInfo.containsKey(tableName)) {
            return "";
        }

        List<String> indexableColumns = tableInfo.get(tableName).stream()
                .filter(c -> c.IndexName.equals(tableName))
                .flatMap(c -> c.columnSchema.stream())
                .filter(c -> !c.key.equals("true"))
                .map(c -> c.field)
                .collect(Collectors.toList());

        if (indexableColumns.isEmpty()) {
            return "";
        }

        // Select exactly one column for the index
        String selectedColumn = indexableColumns.get(random.nextInt(indexableColumns.size()));

        String[] indexTypes = { "INVERTED", "NGRAM_BF", "" };
        String indexType = indexTypes[random.nextInt(indexTypes.length)];

        sb.append("CREATE INDEX IF NOT EXISTS ");
        sb.append(indexName);
        sb.append(" ON ");
        sb.append(tableName);
        sb.append(" (");
        sb.append(selectedColumn);
        sb.append(")");

        if (!indexType.isEmpty()) {
            sb.append(" USING ");
            sb.append(indexType);
        }

        sb.append(";");

        return sb.toString();
    }

    private String generateBuildIndex() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadIndexNames(tableName);
        loadTablePartitionInfos(tableName);

        if (indexNames.isEmpty()) {
            return "";
        }

        String indexName = indexNames.get(random.nextInt(indexNames.size()));

        sb.append("BUILD INDEX ");
        sb.append(indexName);
        sb.append(" ON ");
        sb.append(tableName);

        if (!partitionNames.isEmpty() && random.nextBoolean()) {
            int numPartitions = 1 + random.nextInt(Math.min(3, partitionNames.size()));
            List<String> selectedPartitions = random.ints(0, partitionNames.size())
                    .distinct()
                    .limit(numPartitions)
                    .mapToObj(partitionNames::get)
                    .collect(Collectors.toList());
            sb.append("PARTITION");
            sb.append(" (");
            sb.append(String.join(", ", selectedPartitions));
            sb.append(")");
        }

        sb.append(";");

        return sb.toString();
    }

    private String generateCreateView() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null
                || tableInfo.get(tableName).isEmpty()) {
            return "";
        }

        String viewName = String.format("view_%s_%d", tableName, random.nextInt(1000));

        List<String> availableColumns = new ArrayList<>();
        try {
            availableColumns = tableInfo.get(tableName).stream()
                    .filter(c -> c != null && c.IndexName != null && c.IndexName.equals(tableName))
                    .filter(c -> c.columnSchema != null)
                    .flatMap(c -> c.columnSchema.stream())
                    .filter(c -> c != null && c.field != null)
                    .map(c -> c.field)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting columns for view: " + e.getMessage());
            return "";
        }

        if (availableColumns.isEmpty()) {
            return "";
        }

        try {
            int numColumns = 1 + random.nextInt(Math.min(5, availableColumns.size()));
            List<String> selectedColumns = random.ints(0, availableColumns.size())
                    .distinct()
                    .limit(numColumns)
                    .mapToObj(availableColumns::get)
                    .collect(Collectors.toList());

            sb.append("CREATE VIEW IF NOT EXISTS ");
            sb.append(viewName);

            if (random.nextBoolean()) {
                sb.append(" (");
                List<String> columnDefs = selectedColumns.stream()
                        .map(col -> "v_" + col)
                        .collect(Collectors.toList());
                sb.append(String.join(", ", columnDefs));
                sb.append(")");
            }

            sb.append(" AS SELECT ");
            sb.append(String.join(", ", selectedColumns));
            sb.append(" FROM ");
            sb.append(tableName);
            sb.append(";");

            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error generating view DDL: " + e.getMessage());
            return "";
        }
    }

    private String generateDropView() {
        StringBuilder sb = new StringBuilder();
        loadViewNames();

        if (viewNames.isEmpty()) {
            return "";
        }

        String viewName = viewNames.get(random.nextInt(viewNames.size()));

        sb.append("DROP VIEW IF EXISTS ");
        sb.append(viewName);
        sb.append(";");

        return sb.toString();
    }

    private String generateAlterView() {
        StringBuilder sb = new StringBuilder();

        loadViewNames();
        if (viewNames.isEmpty()) {
            return "";
        }
        String viewName = viewNames.get(random.nextInt(viewNames.size()));

        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null
                || tableInfo.get(tableName).isEmpty()) {
            return "";
        }

        List<String> availableColumns = new ArrayList<>();
        try {
            availableColumns = tableInfo.get(tableName).stream()
                    .filter(c -> c != null && c.IndexName != null && c.IndexName.equals(tableName))
                    .filter(c -> c.columnSchema != null)
                    .flatMap(c -> c.columnSchema.stream())
                    .filter(c -> c != null && c.field != null)
                    .map(c -> c.field)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting columns for view: " + e.getMessage());
            return "";
        }

        if (availableColumns.isEmpty()) {
            return "";
        }

        try {
            int numColumns = 1 + random.nextInt(Math.min(5, availableColumns.size()));
            List<String> selectedColumns = random.ints(0, availableColumns.size())
                    .distinct()
                    .limit(numColumns)
                    .mapToObj(availableColumns::get)
                    .collect(Collectors.toList());

            sb.append("ALTER VIEW ");
            sb.append(viewName);

            if (random.nextBoolean()) {
                sb.append(" (");
                List<String> columnDefs = selectedColumns.stream()
                        .map(col -> "v_" + col)
                        .collect(Collectors.toList());
                sb.append(String.join(", ", columnDefs));
                sb.append(")");
            }

            sb.append(" AS SELECT ");
            sb.append(String.join(", ", selectedColumns));
            sb.append(" FROM ");
            sb.append(tableName);
            sb.append(";");

            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error generating view DDL: " + e.getMessage());
            return "";
        }
    }

    private String generateCreateMaterializedView() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null
                || tableInfo.get(tableName).isEmpty()) {
            return "";
        }

        String mvName = String.format("mv_%s_%d", tableName, random.nextInt(1000));

        List<String> availableColumns = new ArrayList<>();
        try {
            availableColumns = tableInfo.get(tableName).stream()
                    .filter(c -> c != null && c.IndexName != null && c.IndexName.equals(tableName))
                    .filter(c -> c.columnSchema != null)
                    .flatMap(c -> c.columnSchema.stream())
                    .filter(c -> c != null && c.field != null)
                    .map(c -> c.field)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error getting columns for materialized view: " + e.getMessage());
            return "";
        }

        if (availableColumns.isEmpty()) {
            return "";
        }

        try {
            int numColumns = 1 + random.nextInt(Math.min(5, availableColumns.size()));
            List<String> selectedColumns = random.ints(0, availableColumns.size())
                    .distinct()
                    .limit(numColumns)
                    .mapToObj(availableColumns::get)
                    .collect(Collectors.toList());

            List<String> selectExpressions = new ArrayList<>();
            for (String col : selectedColumns) {
                if (random.nextBoolean()) {
                    String[] aggFunctions = { "SUM", "COUNT", "MIN", "MAX", "AVG" };
                    String aggFunction = aggFunctions[random.nextInt(aggFunctions.length)];
                    selectExpressions.add(String.format("%s(%s)", aggFunction, col));
                } else {
                    selectExpressions.add(col);
                }
            }

            sb.append("CREATE MATERIALIZED VIEW ");
            sb.append(mvName);
            sb.append(" AS SELECT ");
            sb.append(String.join(", ", selectExpressions));
            sb.append(" FROM ");
            sb.append(tableName);

            List<String> nonAggColumns = selectExpressions.stream()
                    .filter(expr -> !expr.contains("("))
                    .collect(Collectors.toList());

            if (!nonAggColumns.isEmpty()) {
                sb.append(" GROUP BY ");
                sb.append(String.join(", ", nonAggColumns));
            }

            if (random.nextBoolean() && !nonAggColumns.isEmpty()) {
                sb.append(" ORDER BY ");
                String orderCol = nonAggColumns.get(random.nextInt(nonAggColumns.size()));
                sb.append(orderCol);
                sb.append(random.nextBoolean() ? " ASC" : " DESC");
            }

            sb.append(";");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Error generating materialized view DDL: " + e.getMessage());
            return "";
        }
    }

    private String generateDropMaterializedView() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);

        if (rollupNames.isEmpty()) {
            return "";
        }

        // Filter out base table name and rollup names, keep only materialized view
        // names
        List<String> materializedViews = new ArrayList<>();
        for (String indexName : rollupNames) {
            if (indexName.equals(tableName)) {
                continue;
            }

            List<ColumnDesc> columnDescs = tableInfo.get(indexName);
            if (columnDescs == null || columnDescs.isEmpty()) {
                continue;
            }

            boolean isMaterializedView = columnDescs.stream()
                    .flatMap(desc -> desc.columnSchema.stream())
                    .anyMatch(schema -> schema.defineExpr != null && !schema.defineExpr.isEmpty());

            if (isMaterializedView) {
                materializedViews.add(indexName);
            }
        }

        if (materializedViews.isEmpty()) {
            return "";
        }

        String mvName = materializedViews.get(random.nextInt(materializedViews.size()));

        sb.append("DROP MATERIALIZED VIEW ");
        sb.append(mvName);
        sb.append(" ON ");
        sb.append(tableName);
        sb.append(";");

        return sb.toString();
    }

    private String generateDropIndex() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadIndexNames(tableName);

        if (indexNames.isEmpty()) {
            return "";
        }

        String indexName = indexNames.get(random.nextInt(indexNames.size()));

        sb.append("DROP INDEX ");
        sb.append(indexName);
        sb.append(" ON ");
        sb.append(tableName);
        sb.append(";");

        return sb.toString();
    }
}

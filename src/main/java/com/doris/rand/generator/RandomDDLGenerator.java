package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;
import com.mysql.cj.conf.ConnectionUrlParser.Pair;

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
    NO_PARTITION,
    UNKOWN
}

public class RandomDDLGenerator {
    private static final Random random = new Random();
    private List<String> tableNames = new ArrayList<>();
    private List<String> viewNames = new ArrayList<>();
    private List<String> indexNames = new ArrayList<>();
    private List<String> partitions = new ArrayList<>();
    private List<String> rollupNames = new ArrayList<>();
    private List<String> partitionNames = new ArrayList<>();
    private List<ListRangeInfo> listPartitionRanges = new ArrayList<>();
    private List<RangeInfo> rangePartitionRanges = new ArrayList<>();
    // Add these as class fields
    private List<String> partitionColumns = new ArrayList<>();
    private List<String> partitionTypes = new ArrayList<>();
    private Map<String, List<ColumnDesc>> tableInfo = new HashMap<>();

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

    public PartitionType loadTableIsRangePartition(String tableName) {
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
                        return PartitionType.RANGE;
                    } else if (createTable.contains("PARTITION BY LIST")) {
                        return PartitionType.LIST;
                    } else {
                        return PartitionType.NO_PARTITION;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading table names: " + e.getMessage());
            tableNames.clear();
        }
        return PartitionType.UNKOWN;
    }

    public void loadRangeTablePartitionInfos(String tableName) {
        partitionNames.clear();
        rangePartitionRanges.clear();

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
                        String[] keys = partitionKey.split(",\\s*");
                        List<String> partitionKeyList = new ArrayList<>(Arrays.asList(keys));

                        if (rangeInfo != null && !rangeInfo.isEmpty()) {
                            Pattern rangesPattern = Pattern.compile("keys: \\[(.*?)\\]");

                            Matcher rangesMatcher = rangesPattern.matcher(rangeInfo);
                            boolean isLower = true;
                            List<String> upperBound = new ArrayList<>();
                            List<String> lowerBound = new ArrayList<>();
                            while (rangesMatcher.find()) {
                                String ranges = rangesMatcher.group(1);
                                String[] range = ranges.split(",\\s*");
                                if (isLower) {
                                    for (int i = 0; i < range.length; i++) {
                                        String value = range[i].trim();
                                        lowerBound.add(value);
                                    }
                                    isLower = false;
                                } else {
                                    for (int i = 0; i < range.length; i++) {
                                        String value = range[i].trim();
                                        upperBound.add(value);
                                    }
                                }
                                rangePartitionRanges.add(new RangeInfo(partitionKeyList, upperBound,
                                        lowerBound));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing partition range info: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading partition info: " + e.getMessage());
            partitionNames.clear();
            rangePartitionRanges.clear();
        }
    }

    public void loadListTablePartitionInfos(String tableName) {
        partitionNames.clear();
        listPartitionRanges.clear();

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
                        String[] keys = partitionKey.split(",\\s*");
                        List<String> partitionKeyList = new ArrayList<>(Arrays.asList(keys));

                        if (rangeInfo != null && !rangeInfo.isEmpty()) {
                            Pattern rangesPattern = Pattern.compile("keys: \\[(.*?)\\]");

                            Matcher rangesMatcher = rangesPattern.matcher(rangeInfo);

                            while (rangesMatcher.find()) {
                                List<String> partitionRange = new ArrayList<>();
                                String ranges = rangesMatcher.group(1);
                                String[] range = ranges.split(",\\s*");
                                for (int i = 0; i < range.length; i++) {
                                    String value = range[i].trim();
                                    partitionRange.add(value);
                                }

                                listPartitionRanges.add(new ListRangeInfo(partitionKeyList, partitionRange));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing partition range info: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading partition info: " + e.getMessage());
            partitionNames.clear();
            listPartitionRanges.clear();
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
        int choice = random.nextInt(20);
        switch (choice) {
            case 0:
                return generateAddColumn();
            case 1:
                return generateDropColumn();
            case 2:
                return generateModifyColumn();
            case 3:
                return generateRenameColumn();
            case 4:
                return generateAddPartition();
            case 5:
                return generateDropPartition();
            case 6:
                return generateReplacePartition();
            case 7:
                return generateRenamePartition();
            case 8:
                return generateAddRollup();
            case 9:
                return generateDropRollup();
            case 10:
                return generateRenameRollup();
            case 11:
                return generateCreateIndex();
            case 12:
                return generateDropIndex();
            case 13:
                return generateBuildIndex();
            case 16:
                return generateCreateView();
            case 17:
                return generateDropView();
            case 18:
                return generateAlterView();
            case 19:
                return generateInsertInto();
            case 20:
                return generateInsertInto();
            // case 19:
            // return generateCreateMaterializedView();
            // case 20:
            // return generateDropMaterializedView();
            default:
                return "";
        }
    }

    private String generateInsertInto() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadTableDesc(tableName);
        PartitionType partitionType = loadTableIsRangePartition(tableName);
        loadPartitioInfoFromTable(tableName);
        loadListTablePartitionInfos(tableName);
        loadRangeTablePartitionInfos(tableName);

        if (!tableInfo.containsKey(tableName) || tableInfo.get(tableName) == null
                || tableInfo.get(tableName).isEmpty()) {
            return "";
        }

        List<ColumnSchema> columnSchemas = tableInfo.get(tableName).stream()
                .filter(c -> c.IndexName.equals(tableName))
                .flatMap(c -> c.columnSchema.stream())
                .collect(Collectors.toList());

        if (columnSchemas.isEmpty()) {
            return "";
        }

        sb.append("INSERT INTO ").append(tableName).append(" VALUES (");

        if (partitionType == PartitionType.LIST) {
            ListRangeInfo selectedPartitionRange = null;
            if (!listPartitionRanges.isEmpty()) {
                selectedPartitionRange = listPartitionRanges.get(random.nextInt(listPartitionRanges.size()));
            } else {
                return "";
            }
            Map<String, String> partitionColumnValues = new HashMap<>();
            for (int i = 0; i < selectedPartitionRange.getPartitionKeys().size()
                    && i < selectedPartitionRange.getPartitionRange().size(); i++) {
                String columnName = selectedPartitionRange.getPartitionKeys().get(i);
                String columnValue = selectedPartitionRange.getPartitionRange().get(i);
                partitionColumnValues.put(columnName, columnValue);
            }

            for (int i = 0; i < columnSchemas.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                ColumnSchema column = columnSchemas.get(i);
                String columnName = column.field;
                String columnType = column.type;

                if (partitionColumnValues.containsKey(columnName)) {
                    sb.append(partitionColumnValues.get(columnName));
                } else {
                    sb.append(generateValueByType(columnType));
                }
            }

            sb.append(");");
            return sb.toString();
        } else if (partitionType == PartitionType.RANGE) {
            RangeInfo selectedPartitionRange = null;
            if (!rangePartitionRanges.isEmpty()) {
                selectedPartitionRange = rangePartitionRanges.get(random.nextInt(rangePartitionRanges.size()));
            } else {
                return "";
            }
            Map<String, Pair<String, String>> partitionColumnValues = new HashMap<>();
            for (int i = 0; i < selectedPartitionRange.getPartitionKeys().size()
                    && i < selectedPartitionRange.getLowerBound().size() && i < selectedPartitionRange.getUpperBound()
                            .size(); i++) {
                String columnName = selectedPartitionRange.getPartitionKeys().get(i);
                String lowerBound = selectedPartitionRange.getLowerBound().get(i);
                String upperBound = selectedPartitionRange.getUpperBound().get(i);
                partitionColumnValues.put(columnName, new Pair<>(lowerBound, upperBound));
            }

            for (int i = 0; i < columnSchemas.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                ColumnSchema column = columnSchemas.get(i);
                String columnName = column.field;
                String columnType = column.type;

                if (partitionColumnValues.containsKey(columnName)) {
                    Pair<String, String> bounds = partitionColumnValues.get(columnName);
                    String lowerBound = bounds.left;
                    String upperBound = bounds.right;

                    if (columnType.toUpperCase().contains("INT") ||
                            columnType.toUpperCase().contains("BIGINT") ||
                            columnType.toUpperCase().contains("SMALLINT") ||
                            columnType.toUpperCase().contains("TINYINT") ||
                            columnType.toUpperCase().contains("LARGEINT")) {

                        long lower, upper;
                        try {
                            lower = Long.parseLong(lowerBound);
                            upper = Long.parseLong(upperBound);

                            long value;
                            if (lower == upper) {
                                value = lower;
                            } else {
                                value = lower + (long) (random.nextDouble() * (upper - lower));
                            }
                            sb.append(value);
                        } catch (NumberFormatException e) {
                            sb.append(lowerBound);
                        }
                    } else if (columnType.toUpperCase().contains("FLOAT") ||
                            columnType.toUpperCase().contains("DOUBLE") ||
                            columnType.toUpperCase().contains("DECIMAL")) {

                        try {
                            double lower = Double.parseDouble(lowerBound);
                            double upper = Double.parseDouble(upperBound);

                            double value;
                            if (lower == upper) {
                                value = lower;
                            } else {
                                value = lower + random.nextDouble() * (upper - lower);
                            }
                            sb.append(String.format("%.2f", value));
                        } catch (NumberFormatException e) {
                            sb.append(lowerBound);
                        }
                    } else if (columnType.toUpperCase().contains("DATE") ||
                            columnType.toUpperCase().contains("DATETIME")) {

                        if (lowerBound.startsWith("\"") && lowerBound.endsWith("\"")) {
                            lowerBound = lowerBound.substring(1, lowerBound.length() - 1);
                        }

                        sb.append("'").append(lowerBound).append("'");
                    } else {
                        if (lowerBound.startsWith("\"") && lowerBound.endsWith("\"")) {
                            sb.append("'").append(lowerBound.substring(1, lowerBound.length() - 1)).append("'");
                        } else if (lowerBound.startsWith("'") && lowerBound.endsWith("'")) {
                            sb.append(lowerBound);
                        } else {
                            sb.append("'").append(lowerBound).append("'");
                        }
                    }
                } else {
                    sb.append(generateValueByType(columnType));
                }
            }

            sb.append(");");
            return sb.toString();
        } else {
            for (int i = 0; i < columnSchemas.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }

                ColumnSchema column = columnSchemas.get(i);
                String columnType = column.type;
                sb.append(generateValueByType(columnType));
            }

            sb.append(");");
            return sb.toString();
        }
    }

    // Helper method to generate values based on column type
    private String generateValueByType(String columnType) {
        String upperType = columnType.toUpperCase();

        if (upperType.contains("INT") || upperType.contains("BIGINT") || upperType.contains("SMALLINT") ||
                upperType.contains("TINYINT") || upperType.contains("LARGEINT")) {
            return String.valueOf(random.nextInt(1000));
        } else if (upperType.contains("FLOAT") || upperType.contains("DOUBLE") || upperType.contains("DECIMAL")) {
            return String.format("%.2f", random.nextDouble() * 1000);
        } else if (upperType.contains("BOOL")) {
            return random.nextBoolean() ? "TRUE" : "FALSE";
        } else if (upperType.contains("DATE")) {
            int year = 2020 + random.nextInt(5);
            int month = 1 + random.nextInt(12);
            int day = 1 + random.nextInt(28);
            return String.format("'%d-%02d-%02d'", year, month, day);
        } else if (upperType.contains("DATETIME")) {
            int year = 2020 + random.nextInt(5);
            int month = 1 + random.nextInt(12);
            int day = 1 + random.nextInt(28);
            int hour = random.nextInt(24);
            int minute = random.nextInt(60);
            int second = random.nextInt(60);
            return String.format("'%d-%02d-%02d %02d:%02d:%02d'", year, month, day, hour, minute, second);
        } else if (upperType.contains("CHAR") || upperType.contains("VARCHAR") || upperType.contains("STRING")) {
            String[] sampleValues = { "Apple", "Banana", "Cherry", "Date", "Elderberry", "Fig", "Grape" };
            return String.format("'%s'", sampleValues[random.nextInt(sampleValues.length)]);
        } else {
            // Default for unknown types
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
        PartitionType partitionType = loadTableIsRangePartition(tableName);
        loadPartitioInfoFromTable(tableName);

        if (partitionColumns.isEmpty() || partitionTypes.isEmpty() ||
                partitionType == PartitionType.UNKOWN) {
            return "";
        }

        sb.append("ALTER TABLE ");
        sb.append(tableName);
        sb.append(" ADD PARTITION ");
        sb.append(generatePartitionDefinition(tableName, partitionType));
        sb.append(";");
        return sb.toString();
    }

    private String generateDropPartition() {
        StringBuilder sb = new StringBuilder();
        String tableName = generateTableName();
        loadListTablePartitionInfos(tableName);
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

    private String generatePartitionDefinition(String tableName, PartitionType partitionType) {
        StringBuilder sb = new StringBuilder();
        if (partitionType.equals(PartitionType.RANGE)) {
            sb.append(generateRangePartition());
        } else if (partitionType.equals(PartitionType.LIST)) {
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
        loadListTablePartitionInfos(tableName);

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
        loadListTablePartitionInfos(tableName);

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
        loadListTablePartitionInfos(tableName);

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

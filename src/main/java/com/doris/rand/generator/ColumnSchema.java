package com.doris.rand.generator;

import com.doris.rand.config.DBConfig;
import java.sql.*;
import java.text.NumberFormat.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
public class ColumnSchema {
    String indexName;
    String indexKeysType;
    String field;
    String type;
    String internalType;
    String isNull;
    String key;
    String defaultValue;
    String extra;
    String visible;
    String defineExpr;
    String whereClause;

    public ColumnSchema(String indexName, String indexKeysType, String field, String type, String internalType, String isNull, String key, String defaultValue, String extra, String visible, String defineExpr, String whereClause) {
        this.indexName = indexName;
        this.indexKeysType = indexKeysType;
        this.field = field;
        this.type = type;
        this.internalType = internalType;
        this.isNull = isNull;
        this.key = key;
        this.defaultValue = defaultValue;
        this.extra = extra;
        this.visible = visible;
        this.defineExpr = defineExpr;
        this.whereClause = whereClause;
    }
}

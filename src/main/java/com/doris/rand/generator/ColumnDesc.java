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

public class ColumnDesc {
    String IndexName;
    String IndexKeysType;
    ColumnSchema columnSchema;
    public ColumnDesc(String IndexName, String IndexKeysType, String field, String type, String internalType, String isNull, String key, String defaultValue, String extra, String visible, String defineExpr, String whereClause) {
        this.IndexName = IndexName;
        this.IndexKeysType = IndexKeysType;
        this.columnSchema = new ColumnSchema(IndexName, IndexKeysType, field, type, internalType, isNull, key, defaultValue, extra, visible, defineExpr, whereClause);
    }
}


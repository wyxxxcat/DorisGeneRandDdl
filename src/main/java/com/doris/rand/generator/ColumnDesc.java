package com.doris.rand.generator;

import java.util.List;

public class ColumnDesc {
    String IndexName;
    String IndexKeysType;
    List<ColumnSchema> columnSchema;

    public ColumnDesc(String IndexName, String IndexKeysType, List<ColumnSchema> columnSchema) {
        this.IndexName = IndexName;
        this.IndexKeysType = IndexKeysType;
        this.columnSchema = columnSchema;
    }
}

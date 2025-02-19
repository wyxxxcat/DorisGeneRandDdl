package com.doris.rand.generator;

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

    public ColumnSchema(String indexName, String indexKeysType, String field, String type, String internalType,
            String isNull, String key, String defaultValue, String extra, String visible, String defineExpr,
            String whereClause) {
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

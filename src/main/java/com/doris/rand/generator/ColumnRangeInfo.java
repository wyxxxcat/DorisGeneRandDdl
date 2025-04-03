package com.doris.rand.generator;

public class ColumnRangeInfo {
    private String partitionKey;
    private String dataType;
    private Object lowerBound;
    private Object upperBound;

    public ColumnRangeInfo(String partitionKey, String dataType, Object lowerBound, Object upperBound) {
        this.partitionKey = partitionKey;
        this.dataType = dataType;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public String getDataType() {
        return dataType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public Object getLowerBound() {
        return lowerBound;
    }

    public Object getUpperBound() {
        return upperBound;
    }
}
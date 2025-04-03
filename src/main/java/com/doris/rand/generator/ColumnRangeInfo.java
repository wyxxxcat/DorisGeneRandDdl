package com.doris.rand.generator;

public class ColumnRangeInfo {
    private String dataType;
    private Object lowerBound;
    private Object upperBound;

    public ColumnRangeInfo(String dataType, Object lowerBound, Object upperBound) {
        this.dataType = dataType;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public String getDataType() {
        return dataType;
    }

    public Object getLowerBound() {
        return lowerBound;
    }

    public Object getUpperBound() {
        return upperBound;
    }

    @Override
    public String toString() {
        return "ColumnRangeInfo{" +
                "dataType='" + dataType + '\'' +
                ", lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                '}';
    }
}
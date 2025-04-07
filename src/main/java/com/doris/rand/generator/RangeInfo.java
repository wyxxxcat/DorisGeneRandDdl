package com.doris.rand.generator;

import java.util.List;

public class RangeInfo {
    private List<String> partitionKeys;
    private List<String> upperBound;
    private List<String> lowerBound;

    public RangeInfo(List<String> partitionKeys, List<String> upperBound, List<String> lowerBound) {
        this.partitionKeys = partitionKeys;
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
    }

    public List<String> getPartitionKeys() {
        return partitionKeys;
    }

    public List<String> getUpperBound() {
        return upperBound;
    }

    public List<String> getLowerBound() {
        return lowerBound;
    }
}

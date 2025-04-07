package com.doris.rand.generator;

import java.util.List;

public class ListRangeInfo {
    private List<String> partitionKeys;
    private List<String> partitionRange;

    public ListRangeInfo(List<String> partitionKeys, List<String> partitionRange) {
        this.partitionKeys = partitionKeys;
        this.partitionRange = partitionRange;
    }

    public List<String> getPartitionKeys() {
        return partitionKeys;
    }

    public List<String> getPartitionRange() {
        return partitionRange;
    }

}
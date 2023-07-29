package com.hmdp.utils;

import com.google.common.base.Charsets;
import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import lombok.Data;

/**
 * @author Administrator
 * @date 2023/7/23 22:25
 */
@Data
public class BloomFilterHelper {
    private final Integer DEFAULT_SIZE = 10000;

    private Integer size;

    private final Double DEFAULT_FPP = 0.03;

    private Double fpp;

    private Integer numOfHashFunction;


    public BloomFilterHelper(int expectedInsertions) {
        this.size = optimalNumOfBits(expectedInsertions, DEFAULT_FPP);
        this.fpp = DEFAULT_FPP;
        this.numOfHashFunction = optimalNumOfHashFunctions(size, expectedInsertions);
    }

    public BloomFilterHelper(int expectedInsertions, double fpp) {
        this.size = optimalNumOfBits(expectedInsertions, DEFAULT_FPP);
        this.fpp = fpp;
        this.numOfHashFunction = optimalNumOfHashFunctions(size, expectedInsertions);
    }

    private Integer optimalNumOfHashFunctions(Integer size, int expectedInsertions) {
        return Math.max(1, (int)Math.round((double)size / expectedInsertions * Math.log(2)));
    }

    private Integer optimalNumOfBits(int expectedInsertions, Double fpp) {
        return (int)(-expectedInsertions * Math.log(fpp) / Math.pow(Math.log(2),2));
    }

    public int[] murmurHashOffset(String key) {
        int[] offset = new int[numOfHashFunction];
        long hash = Hashing.murmur3_128().hashObject(key, Funnels.stringFunnel(Charsets.UTF_8)).asLong();
        int hash1 = (int)hash;
        int hash2 = (int)(hash >>> 32);
        for(int i = 1; i <= offset.length; ++i) {
            int nextHash = hash1 + i * hash2;
            if(nextHash < 0) {
                nextHash = ~nextHash;
            }
            offset[i - 1] = nextHash & (size - 1);
        }
        return offset;
    }
}

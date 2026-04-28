package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;


public class SizeScore implements ScoreSignal {

    private static final long MIN_USEFUL = 100;
    private static final long IDEAL_LOW  = 1_024;
    private static final long IDEAL_HIGH = 10L * 1024 * 1024;
    private static final long MAX_USEFUL = 500L * 1024 * 1024;

    @Override
    public double score(Path path, BasicFileAttributes attrs) {
        long size = attrs.size();
        if (size <= 0 || size < MIN_USEFUL) return 0.05;
        if (size >= MAX_USEFUL) return 0.05;
        if (size >= IDEAL_LOW && size <= IDEAL_HIGH) return 1.0;

        if (size < IDEAL_LOW) {
            return 0.4 + 0.6 * ((double) (size - MIN_USEFUL) / (IDEAL_LOW - MIN_USEFUL));
        } else {
            double t = (double) (size - IDEAL_HIGH) / (MAX_USEFUL - IDEAL_HIGH);
            return 1.0 - 0.95 * t;
        }
    }

    @Override public String name() { return "size"; }
}

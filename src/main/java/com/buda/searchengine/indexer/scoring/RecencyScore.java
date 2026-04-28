package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;


public class RecencyScore implements ScoreSignal {

    private static final Duration HALF_LIFE = Duration.ofDays(60);

    @Override
    public double score(Path path, BasicFileAttributes attrs) {
        Instant accessed = attrs.lastAccessTime().toInstant();
        Duration age = Duration.between(accessed, Instant.now());
        if (age.isNegative()) return 1.0;

        double ageDays = (double) age.toSeconds() / 86_400.0;
        double halfLifeDays = (double) HALF_LIFE.toDays();
        return Math.pow(0.5, ageDays / halfLifeDays);
    }

    @Override public String name() { return "recency"; }
}

package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;


public class PathLengthScore implements ScoreSignal {

    private static final int IDEAL_DEPTH = 4;
    private static final int MAX_PENALTY_DEPTH = 12;

    @Override
    public double score(Path path, BasicFileAttributes attrs) {
        int depth = path.getNameCount();
        if (depth <= IDEAL_DEPTH) return 1.0;
        if (depth >= MAX_PENALTY_DEPTH) return 0.0;
        // Linear interpolation between ideal and max penalty
        double t = (double) (depth - IDEAL_DEPTH) / (MAX_PENALTY_DEPTH - IDEAL_DEPTH);
        return 1.0 - t;
    }

    @Override public String name() { return "path-length"; }
}

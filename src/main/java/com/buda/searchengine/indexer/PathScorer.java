package com.buda.searchengine.indexer;

import com.buda.searchengine.indexer.scoring.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;


public class PathScorer {

    private static final Logger logger = LoggerFactory.getLogger(PathScorer.class);

    private final Map<ScoreSignal, Double> signals;

    public PathScorer() {
        this(defaultSignals());
    }

    public PathScorer(Map<ScoreSignal, Double> weightedSignals) {
        double sum = weightedSignals.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException(
                    "Signal weights must sum to 1.0, got " + sum);
        }
        this.signals = weightedSignals;
    }

    public double score(Path path, BasicFileAttributes attrs) {
        double total = 0.0;
        for (Map.Entry<ScoreSignal, Double> e : signals.entrySet()) {
            double s = clamp(e.getKey().score(path, attrs));
            total += s * e.getValue();
            logger.trace("  signal={} score={} weight={}", e.getKey().name(), s, e.getValue());
        }
        return clamp(total);
    }

    public double score(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return score(path, attrs);
        } catch (IOException e) {
            logger.warn("Failed to read attributes for scoring: {}", path, e);
            return 0.0;
        }
    }

    private double clamp(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static Map<ScoreSignal, Double> defaultSignals() {
        Map<ScoreSignal, Double> map = new LinkedHashMap<>();
        map.put(new ExtensionScore(),           0.30);
        map.put(new RecencyScore(),             0.25);
        map.put(new DirectoryImportanceScore(), 0.20);
        map.put(new PathLengthScore(),          0.15);
        map.put(new SizeScore(),                0.10);
        return map;
    }
}

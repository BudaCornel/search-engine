package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;


public class ExtensionScore implements ScoreSignal {

    private static final Map<String, Double> SCORES = Map.ofEntries(
            // Documents
            Map.entry("pdf", 1.0), Map.entry("docx", 0.95), Map.entry("doc", 0.9),
            Map.entry("md", 0.95), Map.entry("txt", 0.85), Map.entry("rtf", 0.7),
            Map.entry("odt", 0.9),
            // Source code
            Map.entry("java", 0.9), Map.entry("py", 0.9), Map.entry("ts", 0.9),
            Map.entry("js", 0.85), Map.entry("cpp", 0.85), Map.entry("c", 0.85),
            Map.entry("h", 0.7), Map.entry("hpp", 0.7), Map.entry("rs", 0.9),
            Map.entry("go", 0.85), Map.entry("kt", 0.85), Map.entry("cs", 0.85),
            Map.entry("rb", 0.8), Map.entry("php", 0.7), Map.entry("vhdl", 0.85),
            Map.entry("v", 0.85), Map.entry("sv", 0.85),
            // Markup / data
            Map.entry("html", 0.7), Map.entry("css", 0.6), Map.entry("xml", 0.6),
            Map.entry("json", 0.6), Map.entry("yml", 0.6), Map.entry("yaml", 0.6),
            Map.entry("toml", 0.6),
            // Spreadsheets / presentations
            Map.entry("xlsx", 0.85), Map.entry("csv", 0.7),
            Map.entry("pptx", 0.85), Map.entry("ppt", 0.8),
            // Low-value
            Map.entry("log", 0.2), Map.entry("tmp", 0.05), Map.entry("bak", 0.1),
            Map.entry("cache", 0.05), Map.entry("class", 0.1), Map.entry("o", 0.1),
            Map.entry("pyc", 0.1), Map.entry("lock", 0.2)
    );

    private static final double DEFAULT_SCORE = 0.4;

    @Override
    public double score(Path path, BasicFileAttributes attrs) {
        String fileName = path.getFileName().toString().toLowerCase();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) return 0.3;
        String ext = fileName.substring(dot + 1);
        return SCORES.getOrDefault(ext, DEFAULT_SCORE);
    }

    @Override public String name() { return "extension"; }
}

package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;


public class DirectoryImportanceScore implements ScoreSignal {

    private static final Set<String> HIGH_VALUE = Set.of(
            "documents", "desktop", "src", "main", "lib", "app", "apps",
            "projects", "work", "papers", "notes", "downloads", "pictures",
            "music", "videos", "books", "research"
    );

    private static final Set<String> LOW_VALUE = Set.of(
            "node_modules", "target", "build", "dist", "out", ".git", ".idea",
            ".vscode", ".cache", "cache", "tmp", "temp", "__pycache__",
            ".gradle", ".m2", ".npm", ".cargo", "venv", ".venv", "env",
            "bin", "obj", "logs", "log"
    );

    @Override
    public double score(Path path, BasicFileAttributes attrs) {
        boolean lowFound = false;
        boolean highFound = false;

        Path parent = path.getParent();
        while (parent != null && parent.getFileName() != null) {
            String segment = parent.getFileName().toString().toLowerCase();
            if (LOW_VALUE.contains(segment)) lowFound = true;
            else if (HIGH_VALUE.contains(segment)) highFound = true;
            parent = parent.getParent();
        }

        if (lowFound) return 0.05;
        if (highFound) return 0.95;
        return 0.5;
    }

    @Override public String name() { return "directory"; }
}

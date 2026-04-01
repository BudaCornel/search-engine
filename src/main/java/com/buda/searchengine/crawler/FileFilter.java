package com.buda.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/** applies configurable ignore rules to filter out unwanted files and directories. */
public class FileFilter {
    private static final Logger logger = LoggerFactory.getLogger(FileFilter.class);
    private final List<String> ignorePatterns;

    public FileFilter(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    public boolean accept(Path path) {
        String pathStr = path.toString();
        String fileName = path.getFileName().toString();

        for (String pattern : ignorePatterns) {
            if (pathStr.contains(java.io.File.separator + pattern + java.io.File.separator)
                    || pathStr.endsWith(java.io.File.separator + pattern)) {
                logger.debug("Filtered out (directory match): {}", path);
                return false;
            }
            if (pattern.startsWith(".") && fileName.endsWith(pattern)) {
                logger.debug("Filtered out (extension match): {}", path);
                return false;
            }
            if (fileName.equals(pattern)) {
                logger.debug("Filtered out (exact match): {}", path);
                return false;
            }
        }
        return true;
    }
}
package com.buda.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileCrawler {
    private static final Logger logger = LoggerFactory.getLogger(FileCrawler.class);
    private final Set<Path> visitedRealPaths = new HashSet<>();

    public List<Path> crawl(Path root) {
        List<Path> discoveredFiles = new ArrayList<>();

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            logger.error("Root path does not exist or is not a directory: {}", root);
            return discoveredFiles;
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    Path realPath = dir.toRealPath();
                    if (!visitedRealPaths.add(realPath)) {
                        logger.warn("Symlink loop detected, skipping: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        discoveredFiles.add(file);
                        logger.debug("Discovered file: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    logger.warn("Cannot access file: {} ({})", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Error crawling directory: {}", root, e);
        }

        logger.info("Crawl complete: {} files discovered in {}", discoveredFiles.size(), root);
        return discoveredFiles;
    }
}
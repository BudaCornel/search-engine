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


/** recursively traverses directories to discover files for indexing. */
public class FileCrawler {
    private static final Logger logger = LoggerFactory.getLogger(FileCrawler.class);
    private final Set<Path> visitedRealPaths = new HashSet<>();
    private final FileFilter fileFilter;
    private CrawlStats stats;

    public FileCrawler(FileFilter fileFilter) {
        this.fileFilter = fileFilter;
    }

    public List<Path> crawl(Path root) {
        List<Path> discoveredFiles = new ArrayList<>();
        stats = new CrawlStats();

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            logger.error("Root path does not exist or is not a directory: {}", root);
            return discoveredFiles;
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    if (!fileFilter.accept(dir)) {
                        stats.incrementFilteredOut();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path realPath = dir.toRealPath();
                    if (!visitedRealPaths.add(realPath)) {
                        logger.warn("Symlink loop detected, skipping: {}", dir);
                        stats.incrementSymlinksSkipped();
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    stats.incrementDirectoriesTraversed();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    stats.incrementFilesFound();
                    if (attrs.isRegularFile() && fileFilter.accept(file)) {
                        discoveredFiles.add(file);
                    } else {
                        stats.incrementFilteredOut();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    if (exc instanceof AccessDeniedException) {
                        stats.incrementPermissionDenied();
                        logger.warn("Permission denied: {}", file);
                    } else {
                        logger.warn("Cannot access file: {} ({})", file, exc.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Error crawling directory: {}", root, e);
        }

        logger.info("Crawl complete: {} files discovered in {}", discoveredFiles.size(), root);
        return discoveredFiles;
    }

    public CrawlStats getStats() {
        return stats;
    }
}
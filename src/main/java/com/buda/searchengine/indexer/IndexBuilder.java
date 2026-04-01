package com.buda.searchengine.indexer;

import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.FileCrawler;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class IndexBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    private final FileCrawler crawler;
    private final ContentExtractor contentExtractor;
    private final MetadataExtractor metadataExtractor;
    private final FileRepository repository;

    private int filesProcessed = 0;
    private int filesSkipped = 0;
    private int errors = 0;

    public IndexBuilder(FileCrawler crawler, ContentExtractor contentExtractor,
                        MetadataExtractor metadataExtractor, FileRepository repository) {
        this.crawler = crawler;
        this.contentExtractor = contentExtractor;
        this.metadataExtractor = metadataExtractor;
        this.repository = repository;
    }

    public void indexAll(Path root) {
        Instant start = Instant.now();
        logger.info("Starting full index of: {}", root);

        List<Path> files = crawler.crawl(root);

        for (Path file : files) {
            try {
                indexFile(file);
                filesProcessed++;
            } catch (Exception e) {
                errors++;
                logger.error("Failed to index file: {}", file, e);
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        printReport(duration);
    }

    private void indexFile(Path file) {
        String content = contentExtractor.extractContent(file);

        if (content == null) {
            filesSkipped++;
            logger.debug("Skipped non-text file: {}", file);
            return;
        }

        String preview = contentExtractor.extractPreview(file);
        String hash = contentExtractor.computeHash(file);

        FileRecord record = new FileRecord(
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                metadataExtractor.extractExtension(file),
                metadataExtractor.extractMimeType(file),
                metadataExtractor.extractSize(file),
                content,
                preview,
                hash,
                metadataExtractor.extractCreatedAt(file),
                metadataExtractor.extractModifiedAt(file)
        );

        repository.upsert(record);
    }

    private void printReport(Duration duration) {
        String report = String.format("""
                
                ===== INDEXING REPORT =====
                Duration:        %d.%03d seconds
                Files processed: %d
                Files skipped:   %d
                Errors:          %d
                ===========================
                """,
                duration.toSeconds(), duration.toMillisPart(),
                filesProcessed, filesSkipped, errors);

        logger.info(report);
        System.out.println(report);
    }
}
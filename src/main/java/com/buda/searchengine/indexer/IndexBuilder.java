package com.buda.searchengine.indexer;

import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.CrawlStats;
import com.buda.searchengine.crawler.FileCrawler;
import com.buda.searchengine.indexer.processor.FileProcessor;
import com.buda.searchengine.indexer.processor.FileProcessorRegistry;
import com.buda.searchengine.indexer.processor.ProcessedContent;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Orchestrates the full and incremental indexing pipeline. */
public class IndexBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    private final FileCrawler crawler;
    private final ContentExtractor contentExtractor;
    private final MetadataExtractor metadataExtractor;
    private final PathScorer pathScorer;
    private final FileRepository repository;
    private final ChangeDetector changeDetector;
    private final FileProcessorRegistry processorRegistry;

    private int filesIndexed, filesSkipped, filesNew, filesUpdated, filesUnchanged, filesDeleted, errors;

    public IndexBuilder(FileCrawler crawler, ContentExtractor contentExtractor,
                        MetadataExtractor metadataExtractor, PathScorer pathScorer,
                        FileRepository repository, FileProcessorRegistry processorRegistry) {
        this.crawler = crawler;
        this.contentExtractor = contentExtractor;
        this.metadataExtractor = metadataExtractor;
        this.pathScorer = pathScorer;
        this.repository = repository;
        this.processorRegistry = processorRegistry;
        this.changeDetector = new ChangeDetector(repository);
    }

    public void indexAll(Path root) {
        resetCounters();
        Instant start = Instant.now();
        logger.info("Starting full index of: {}", root);

        for (Path file : crawler.crawl(root)) {
            try {
                if (indexFile(file)) { filesIndexed++; filesNew++; }
            } catch (Exception e) {
                errors++;
                logger.error("Failed to index file: {}", file, e);
            }
        }

        printReport("FULL INDEX", Duration.between(start, Instant.now()), crawler.getStats());
    }

    public void indexIncremental(Path root) {
        resetCounters();
        Instant start = Instant.now();
        logger.info("Starting incremental index of: {}", root);

        List<Path> files = crawler.crawl(root);

        Map<String, String> hashes = new HashMap<>();
        for (Path file : files) {
            String hash = contentExtractor.computeHash(file);
            if (hash != null) hashes.put(file.toAbsolutePath().toString(), hash);
        }

        ChangeDetector.ChangeSet changes = changeDetector.detectChanges(files, hashes);
        printChangeSet(changes);

        for (Path file : changes.added()) {
            try { if (indexFile(file)) { filesIndexed++; filesNew++; } }
            catch (Exception e) { errors++; logger.error("Failed to index new file: {}", file, e); }
        }
        for (Path file : changes.modified()) {
            try { if (indexFile(file)) { filesIndexed++; filesUpdated++; } }
            catch (Exception e) { errors++; logger.error("Failed to re-index file: {}", file, e); }
        }
        for (FileRecord record : changes.deleted()) {
            try { repository.delete(record.getId()); filesDeleted++; }
            catch (Exception e) { errors++; logger.error("Failed to delete: {}", record.getAbsolutePath(), e); }
        }

        filesUnchanged = files.size() - changes.added().size() - changes.modified().size();
        printReport("INCREMENTAL INDEX", Duration.between(start, Instant.now()), crawler.getStats());
    }

    private boolean indexFile(Path file) {
        String mimeType = metadataExtractor.extractMimeType(file);

        Optional<FileProcessor> processor = processorRegistry.findFor(file, mimeType);
        if (processor.isEmpty()) {
            filesSkipped++;
            logger.debug("No processor for: {} (mime={})", file, mimeType);
            return false;
        }

        Optional<ProcessedContent> result = processor.get().process(file);
        if (result.isEmpty()) {
            filesSkipped++;
            logger.debug("Processor {} returned empty for: {}",
                    processor.get().getClass().getSimpleName(), file);
            return false;
        }
        ProcessedContent pc = result.get();

        FileRecord record = new FileRecord(
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                metadataExtractor.extractExtension(file),
                mimeType,
                metadataExtractor.extractSize(file),
                pc.content(),
                pc.preview(),
                pc.contentHash(),
                metadataExtractor.extractCreatedAt(file),
                metadataExtractor.extractModifiedAt(file),
                metadataExtractor.extractAccessedAt(file)
        );
        record.setDominantColor(pc.dominantColor());

        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            record.setPathScore(pathScorer.score(file, attrs));
        } catch (IOException e) {
            logger.warn("Could not score path: {}", file, e);
            record.setPathScore(0.0);
        }

        repository.upsert(record);
        return true;
    }

    private void printChangeSet(ChangeDetector.ChangeSet changes) {
        if (!changes.added().isEmpty()) {
            System.out.println("\n  New files:");
            changes.added().forEach(f -> System.out.println("    + " + f.toAbsolutePath()));
        }
        if (!changes.modified().isEmpty()) {
            System.out.println("\n  Modified files:");
            changes.modified().forEach(f -> System.out.println("    ~ " + f.toAbsolutePath()));
        }
        if (!changes.deleted().isEmpty()) {
            System.out.println("\n  Deleted files:");
            changes.deleted().forEach(r -> System.out.println("    - " + r.getAbsolutePath()));
        }
        if (changes.added().isEmpty() && changes.modified().isEmpty() && changes.deleted().isEmpty()) {
            System.out.println("\n  No changes detected.");
        }
    }

    private void resetCounters() {
        filesIndexed = filesSkipped = filesNew = filesUpdated = filesUnchanged = filesDeleted = errors = 0;
    }

    private void printReport(String type, Duration duration, CrawlStats crawlStats) {
        String report = String.format("""
                
                ╔═════════════════════════════════════════════╗
                ║  %s REPORT
                ╠═════════════════════════════════════════════╣
                ║  Duration:          %d.%03d seconds
                ║
                ║  ── Crawl ──
                ║  Files found:       %d
                ║  Dirs traversed:    %d
                ║  Filtered out:      %d
                ║  Permission denied: %d
                ║  Symlinks skipped:  %d
                ║
                ║  ── Index ──
                ║  Files indexed:     %d
                ║  Files skipped:     %d (binary/non-text)
                ║  New files:         %d
                ║  Updated files:     %d
                ║  Unchanged files:   %d
                ║  Deleted files:     %d
                ║  Errors:            %d
                ╚═════════════════════════════════════════════╝
                """,
                type,
                duration.toSeconds(), duration.toMillisPart(),
                crawlStats.getFilesFound(), crawlStats.getDirectoriesTraversed(),
                crawlStats.getFilteredOut(), crawlStats.getPermissionDenied(),
                crawlStats.getSymlinksSkipped(),
                filesIndexed, filesSkipped, filesNew, filesUpdated,
                filesUnchanged, filesDeleted, errors);

        logger.info(report);
        System.out.println(report);
    }
}

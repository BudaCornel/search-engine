package com.buda.searchengine.indexer;

import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.CrawlStats;
import com.buda.searchengine.crawler.FileCrawler;
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

/** Orchestrates the full and incremental indexing pipeline. */
public class IndexBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    private final FileCrawler crawler;
    private final ContentExtractor contentExtractor;
    private final MetadataExtractor metadataExtractor;
    private final PathScorer pathScorer;
    private final FileRepository repository;
    private final ChangeDetector changeDetector;

    private int filesIndexed, filesSkipped, filesNew, filesUpdated, filesUnchanged, filesDeleted, errors;

    public IndexBuilder(FileCrawler crawler, ContentExtractor contentExtractor,
                        MetadataExtractor metadataExtractor, PathScorer pathScorer,
                        FileRepository repository) {
        this.crawler = crawler;
        this.contentExtractor = contentExtractor;
        this.metadataExtractor = metadataExtractor;
        this.pathScorer = pathScorer;
        this.repository = repository;
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
        String content = contentExtractor.extractContent(file);
        if (content == null) {
            filesSkipped++;
            logger.debug("Skipped non-text file: {}", file);
            return false;
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
                metadataExtractor.extractModifiedAt(file),
                metadataExtractor.extractAccessedAt(file)
        );

        // Compute and attach path_score (grade 6).
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

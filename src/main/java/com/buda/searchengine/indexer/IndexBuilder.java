package com.buda.searchengine.indexer;

import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.CrawlStats;
import com.buda.searchengine.crawler.FileCrawler;
import com.buda.searchengine.indexer.concurrent.IndexingPipeline;
import com.buda.searchengine.indexer.processor.FileProcessorRegistry;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Orchestrates full and incremental indexing through the concurrent pipeline. */
public class IndexBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IndexBuilder.class);

    private final FileCrawler crawler;
    private final ContentExtractor contentExtractor;
    private final FileRepository repository;
    private final ChangeDetector changeDetector;
    private final IndexingPipeline pipeline;

    private int filesIndexed, filesSkipped, filesNew, filesUpdated, filesUnchanged, filesDeleted, errors;

    public IndexBuilder(FileCrawler crawler, ContentExtractor contentExtractor,
                        MetadataExtractor metadataExtractor, PathScorer pathScorer,
                        FileRepository repository,
                        FileProcessorRegistry processorRegistry, int readerCount) {
        this.crawler = crawler;
        this.contentExtractor = contentExtractor;
        this.repository = repository;
        this.changeDetector = new ChangeDetector(repository);
        this.pipeline = new IndexingPipeline(
                processorRegistry, metadataExtractor, pathScorer, repository, readerCount);
    }

    public void indexAll(Path root) {
        resetCounters();
        Instant start = Instant.now();
        logger.info("Starting full index of: {}", root);

        List<Path> files = crawler.crawl(root);
        IndexingPipeline.Result result = pipeline.run(files, List.of());
        applyPipelineResult(result);

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

        IndexingPipeline.Result result = pipeline.run(changes.added(), changes.modified());
        applyPipelineResult(result);

        for (FileRecord record : changes.deleted()) {
            try {
                repository.delete(record.getId());
                filesDeleted++;
            } catch (Exception e) {
                errors++;
                logger.error("Failed to delete: {}", record.getAbsolutePath(), e);
            }
        }

        filesUnchanged = files.size() - changes.added().size() - changes.modified().size();
        printReport("INCREMENTAL INDEX", Duration.between(start, Instant.now()), crawler.getStats());
    }

    private void applyPipelineResult(IndexingPipeline.Result result) {
        filesIndexed += result.indexed();
        filesSkipped += result.skipped();
        filesNew     += result.newCount();
        filesUpdated += result.updatedCount();
        errors       += result.failed();
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

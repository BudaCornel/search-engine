package com.buda.searchengine.indexer.concurrent;

import com.buda.searchengine.indexer.MetadataExtractor;
import com.buda.searchengine.indexer.PathScorer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class IndexingPipeline {

    private static final Logger logger = LoggerFactory.getLogger(IndexingPipeline.class);

    static final int DEFAULT_QUEUE_CAPACITY = 1000;
    static final int DEFAULT_BATCH_SIZE     = 100;

    private final FileProcessorRegistry processorRegistry;
    private final MetadataExtractor metadataExtractor;
    private final PathScorer pathScorer;
    private final FileRepository repository;
    private final int readerCount;
    private final int queueCapacity;
    private final int batchSize;

    public IndexingPipeline(FileProcessorRegistry processorRegistry,
                            MetadataExtractor metadataExtractor,
                            PathScorer pathScorer,
                            FileRepository repository,
                            int readerCount) {
        this(processorRegistry, metadataExtractor, pathScorer, repository,
                readerCount, DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE);
    }

    /** Full constructor — primarily for tests that want to vary capacity/batch. */
    public IndexingPipeline(FileProcessorRegistry processorRegistry,
                            MetadataExtractor metadataExtractor,
                            PathScorer pathScorer,
                            FileRepository repository,
                            int readerCount,
                            int queueCapacity,
                            int batchSize) {
        if (readerCount   < 1) throw new IllegalArgumentException("readerCount must be >= 1");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be >= 1");
        if (batchSize     < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        this.processorRegistry = processorRegistry;
        this.metadataExtractor = metadataExtractor;
        this.pathScorer = pathScorer;
        this.repository = repository;
        this.readerCount = readerCount;
        this.queueCapacity = queueCapacity;
        this.batchSize = batchSize;
    }


    public Result run(List<Path> newFiles, List<Path> modifiedFiles) {
        if (newFiles.isEmpty() && modifiedFiles.isEmpty()) {
            return Result.empty();
        }

        List<List<WorkItem>> shards = partition(newFiles, modifiedFiles);
        BlockingQueue<IndexTask> queue = new ArrayBlockingQueue<>(queueCapacity);
        CountDownLatch readersDone = new CountDownLatch(shards.size());

        ExecutorService readerPool = Executors.newFixedThreadPool(shards.size(),
                r -> new Thread(r, "indexer-reader"));
        ExecutorService writerExec = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "indexer-writer"));

        try {
            Future<Result> writerFuture = writerExec.submit(() -> writerLoop(queue));

            for (List<WorkItem> shard : shards) {
                readerPool.submit(() -> {
                    try {
                        readerLoop(shard, queue);
                    } finally {
                        readersDone.countDown();
                    }
                });
            }

            readersDone.await();
            queue.put(IndexTask.PoisonPill.INSTANCE);
            return writerFuture.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Indexing pipeline interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Writer thread failed", e.getCause());
        } finally {
            readerPool.shutdown();
            writerExec.shutdown();
        }
    }

    private void readerLoop(List<WorkItem> shard, BlockingQueue<IndexTask> queue) {
        for (WorkItem item : shard) {
            IndexTask task = processOne(item.path(), item.isNew());
            try {
                queue.put(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private IndexTask processOne(Path file, boolean isNew) {
        try {
            String mime = metadataExtractor.extractMimeType(file);

            Optional<FileProcessor> processor = processorRegistry.findFor(file, mime);
            if (processor.isEmpty()) {
                return new IndexTask.Skipped(file, "no-processor");
            }

            Optional<ProcessedContent> result = processor.get().process(file);
            if (result.isEmpty()) {
                return new IndexTask.Skipped(file, "empty-result");
            }

            FileRecord record = buildRecord(file, mime, result.get());
            return new IndexTask.Success(record, isNew);

        } catch (Exception e) {
            return new IndexTask.Failed(file, e);
        }
    }

    private FileRecord buildRecord(Path file, String mime, ProcessedContent pc) throws IOException {
        FileRecord record = new FileRecord(
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                metadataExtractor.extractExtension(file),
                mime,
                metadataExtractor.extractSize(file),
                pc.content(),
                pc.preview(),
                pc.contentHash(),
                metadataExtractor.extractCreatedAt(file),
                metadataExtractor.extractModifiedAt(file),
                metadataExtractor.extractAccessedAt(file)
        );
        record.setDominantColor(pc.dominantColor());

        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        record.setPathScore(pathScorer.score(file, attrs));
        return record;
    }

    private Result writerLoop(BlockingQueue<IndexTask> queue) {
        int indexed = 0, skipped = 0, failed = 0, newCount = 0, updatedCount = 0;
        List<IndexTask.Success> batch = new ArrayList<>(batchSize);

        while (true) {
            IndexTask task;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (task instanceof IndexTask.PoisonPill) {
                BatchOutcome outcome = flushBatch(batch);
                indexed += outcome.indexed;
                newCount += outcome.newCount;
                updatedCount += outcome.updatedCount;
                failed += outcome.failed;
                break;
            }

            if (task instanceof IndexTask.Skipped s) {
                skipped++;
                logger.debug("Skipped {} ({})", s.path(), s.reason());
            } else if (task instanceof IndexTask.Failed f) {
                failed++;
                logger.warn("Failed to process: {}", f.path(), f.cause());
            } else if (task instanceof IndexTask.Success s) {
                batch.add(s);
                if (batch.size() >= batchSize) {
                    BatchOutcome outcome = flushBatch(batch);
                    indexed += outcome.indexed;
                    newCount += outcome.newCount;
                    updatedCount += outcome.updatedCount;
                    failed += outcome.failed;
                    batch.clear();
                }
            }
        }

        return new Result(indexed, skipped, failed, newCount, updatedCount);
    }

    private BatchOutcome flushBatch(List<IndexTask.Success> batch) {
        if (batch.isEmpty()) return BatchOutcome.EMPTY;

        List<FileRecord> records = batch.stream()
                .map(IndexTask.Success::record)
                .toList();

        int committed = repository.upsertBatch(records);
        if (committed == batch.size()) {
            int newCount = 0;
            for (IndexTask.Success s : batch) if (s.isNew()) newCount++;
            int updatedCount = batch.size() - newCount;
            return new BatchOutcome(committed, newCount, updatedCount, 0);
        }
        return new BatchOutcome(0, 0, 0, batch.size());
    }

    private List<List<WorkItem>> partition(List<Path> newFiles, List<Path> modifiedFiles) {
        List<WorkItem> all = new ArrayList<>(newFiles.size() + modifiedFiles.size());
        for (Path p : newFiles)      all.add(new WorkItem(p, true));
        for (Path p : modifiedFiles) all.add(new WorkItem(p, false));

        int shardCount = Math.min(readerCount, all.size());
        List<List<WorkItem>> shards = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) shards.add(new ArrayList<>());
        for (int i = 0; i < all.size(); i++) {
            shards.get(i % shardCount).add(all.get(i));
        }
        return shards;
    }

    private record WorkItem(Path path, boolean isNew) {}

    private static final class BatchOutcome {
        static final BatchOutcome EMPTY = new BatchOutcome(0, 0, 0, 0);
        final int indexed;
        final int newCount;
        final int updatedCount;
        final int failed;
        BatchOutcome(int indexed, int newCount, int updatedCount, int failed) {
            this.indexed = indexed;
            this.newCount = newCount;
            this.updatedCount = updatedCount;
            this.failed = failed;
        }
    }

    public record Result(int indexed, int skipped, int failed,
                         int newCount, int updatedCount) {
        public static Result empty() { return new Result(0, 0, 0, 0, 0); }
    }
}

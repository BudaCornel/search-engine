package com.buda.searchengine.indexer;

import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/** detects added, modified, and deleted files by comparing content hashes. */
public class ChangeDetector {
    private static final Logger logger = LoggerFactory.getLogger(ChangeDetector.class);

    private final FileRepository repository;

    public ChangeDetector(FileRepository repository) {
        this.repository = repository;
    }

    public ChangeSet detectChanges(List<Path> discoveredFiles, Map<String, String> newHashes) {
        List<FileRecord> existingRecords = repository.findAll();
        Map<String, FileRecord> existingByPath = existingRecords.stream()
                .collect(Collectors.toMap(FileRecord::getAbsolutePath, r -> r));

        Set<String> discoveredPaths = discoveredFiles.stream()
                .map(p -> p.toAbsolutePath().toString())
                .collect(Collectors.toSet());

        List<Path> added = new ArrayList<>();
        List<Path> modified = new ArrayList<>();
        List<FileRecord> deleted = new ArrayList<>();

        for (Path file : discoveredFiles) {
            String absPath = file.toAbsolutePath().toString();
            FileRecord existing = existingByPath.get(absPath);

            if (existing == null) {
                added.add(file);
            } else {
                String newHash = newHashes.get(absPath);
                if (newHash != null && !newHash.equals(existing.getContentHash())) {
                    modified.add(file);
                }
            }
        }

        for (FileRecord record : existingRecords) {
            if (!discoveredPaths.contains(record.getAbsolutePath())) {
                deleted.add(record);
            }
        }

        logger.info("Changes detected: {} added, {} modified, {} deleted",
                added.size(), modified.size(), deleted.size());

        return new ChangeSet(added, modified, deleted);
    }

    public record ChangeSet(List<Path> added, List<Path> modified, List<FileRecord> deleted) {}
}
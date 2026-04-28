package com.buda.searchengine.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** extracts file metadata: extension, MIME type, size, timestamps. */
public class MetadataExtractor {
    private static final Logger logger = LoggerFactory.getLogger(MetadataExtractor.class);

    public String extractExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : null;
    }

    public String extractMimeType(Path file) {
        try {
            String mimeType = Files.probeContentType(file);
            return mimeType != null ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            logger.warn("Failed to probe MIME type: {}", file, e);
            return "application/octet-stream";
        }
    }

    public long extractSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            logger.warn("Failed to read file size: {}", file, e);
            return -1;
        }
    }

    public LocalDateTime extractCreatedAt(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            logger.warn("Failed to read creation time: {}", file, e);
            return LocalDateTime.now();
        }
    }

    public LocalDateTime extractModifiedAt(Path file) {
        try {
            return LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            logger.warn("Failed to read modified time: {}", file, e);
            return LocalDateTime.now();
        }
    }

    public LocalDateTime extractAccessedAt(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return LocalDateTime.ofInstant(attrs.lastAccessTime().toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            logger.warn("Failed to read access time: {}", file, e);
            return null;
        }
    }
}

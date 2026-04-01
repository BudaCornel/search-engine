package com.buda.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** reads file content, extracts previews, and computes SHA-256 hashes. */
public class ContentExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ContentExtractor.class);
    private static final int PREVIEW_LINES = 3;

    public String extractContent(Path file) {
        try {
            return Files.readString(file);
        } catch (MalformedInputException e) {
            logger.debug("Binary or non-UTF-8 file, skipping content: {}", file);
            return null;
        } catch (IOException e) {
            logger.warn("Failed to read file content: {}", file, e);
            return null;
        }
    }

    public String extractPreview(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return lines.stream()
                    .limit(PREVIEW_LINES)
                    .collect(Collectors.joining("\n"));
        } catch (MalformedInputException e) {
            return null;
        } catch (IOException e) {
            logger.warn("Failed to read preview: {}", file, e);
            return null;
        }
    }

    public String computeHash(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.warn("Failed to compute hash: {}", file, e);
            return null;
        }
    }
}
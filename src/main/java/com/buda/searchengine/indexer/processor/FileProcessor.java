package com.buda.searchengine.indexer.processor;

import java.nio.file.Path;
import java.util.Optional;


public interface FileProcessor {


    boolean supports(Path file, String mimeType);
    Optional<ProcessedContent> process(Path file);
}

package com.buda.searchengine.indexer.processor;

import com.buda.searchengine.crawler.ContentExtractor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


public class FileProcessorRegistry {

    private final List<FileProcessor> processors;

    public FileProcessorRegistry(List<FileProcessor> processors) {
        this.processors = List.copyOf(processors);
    }

    public static FileProcessorRegistry withDefaults(ContentExtractor extractor) {
        return new FileProcessorRegistry(List.of(
                new ImageFileProcessor(extractor),
                new TextFileProcessor(extractor)
        ));
    }

    public Optional<FileProcessor> findFor(Path file, String mimeType) {
        return processors.stream()
                .filter(p -> p.supports(file, mimeType))
                .findFirst();
    }

    public List<FileProcessor> all() {
        return processors;
    }
}

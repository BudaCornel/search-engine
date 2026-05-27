package com.buda.searchengine.indexer.processor;

import com.buda.searchengine.crawler.ContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;


public class TextFileProcessor implements FileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TextFileProcessor.class);

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "log", "java", "py", "js", "ts", "tsx", "jsx",
            "html", "htm", "css", "scss", "xml", "json", "yml", "yaml",
            "csv", "tsv", "sql", "sh", "bat", "ps1",
            "c", "cpp", "cc", "h", "hpp", "rs", "go", "kt", "rb", "php",
            "properties", "toml", "ini", "cfg", "conf",
            "tex", "rst", "vue", "svelte"
    );

    private final ContentExtractor contentExtractor;

    public TextFileProcessor(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    @Override
    public boolean supports(Path file, String mimeType) {
        if (mimeType != null && mimeType.startsWith("text/")) return true;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    @Override
    public Optional<ProcessedContent> process(Path file) {
        String content = contentExtractor.extractContent(file);
        if (content == null) {
            logger.debug("TextFileProcessor could not read: {}", file);
            return Optional.empty();
        }
        String preview = contentExtractor.extractPreview(file);
        String hash = contentExtractor.computeHash(file);
        return Optional.of(ProcessedContent.ofText(content, preview, hash));
    }
}

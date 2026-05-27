package com.buda.searchengine.indexer.processor;


public record ProcessedContent(
        String content,
        String preview,
        String dominantColor,
        String contentHash
) {

    public static ProcessedContent ofText(String content, String preview, String hash) {
        return new ProcessedContent(content, preview, null, hash);
    }

    public static ProcessedContent ofImage(String preview, String dominantColor, String hash) {
        return new ProcessedContent(null, preview, dominantColor, hash);
    }
}

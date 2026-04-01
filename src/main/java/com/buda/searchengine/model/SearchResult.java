package com.buda.searchengine.model;

public class SearchResult {
    private final FileRecord fileRecord;
    private final double rank;
    private final String snippet;

    public SearchResult(FileRecord fileRecord, double rank, String snippet) {
        this.fileRecord = fileRecord;
        this.rank = rank;
        this.snippet = snippet;
    }

    public FileRecord getFileRecord() { return fileRecord; }
    public double getRank() { return rank; }
    public String getSnippet() { return snippet; }

    @Override
    public String toString() {
        return String.format("""
                [%.4f] %s
                  Path: %s
                  Preview: %s
                """,
                rank,
                fileRecord.getFileName(),
                fileRecord.getAbsolutePath(),
                snippet != null ? snippet : fileRecord.getPreview());
    }
}
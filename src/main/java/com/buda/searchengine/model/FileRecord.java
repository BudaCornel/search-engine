package com.buda.searchengine.model;

import java.time.LocalDateTime;

public class FileRecord {
    private long id;
    private String absolutePath;
    private String fileName;
    private String extension;
    private String mimeType;
    private long sizeBytes;
    private String content;
    private String preview;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime indexedAt;

    public FileRecord() {}

    public FileRecord(String absolutePath, String fileName, String extension,
                      String mimeType, long sizeBytes, String content,
                      String preview, String contentHash,
                      LocalDateTime createdAt, LocalDateTime modifiedAt) {
        this.absolutePath = absolutePath;
        this.fileName = fileName;
        this.extension = extension;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.preview = preview;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getAbsolutePath() { return absolutePath; }
    public void setAbsolutePath(String absolutePath) { this.absolutePath = absolutePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; }

    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, %d bytes)",
                extension != null ? extension : "?",
                fileName, absolutePath, sizeBytes);


    }

    public static FileRecord fromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        FileRecord record = new FileRecord();
        record.setId(rs.getLong("id"));
        record.setAbsolutePath(rs.getString("absolute_path"));
        record.setFileName(rs.getString("file_name"));
        record.setExtension(rs.getString("extension"));
        record.setMimeType(rs.getString("mime_type"));
        record.setSizeBytes(rs.getLong("size_bytes"));
        record.setContent(rs.getString("content"));
        record.setPreview(rs.getString("preview"));
        record.setContentHash(rs.getString("content_hash"));
        record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        record.setModifiedAt(rs.getTimestamp("modified_at").toLocalDateTime());
        record.setIndexedAt(rs.getTimestamp("indexed_at").toLocalDateTime());
        return record;
    }

}
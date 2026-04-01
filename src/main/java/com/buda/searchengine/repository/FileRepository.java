package com.buda.searchengine.repository;

import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/** handles CRUD operations for file records in PostgreSQL. */
public class FileRepository {
    private static final Logger logger = LoggerFactory.getLogger(FileRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO files (absolute_path, file_name, extension, mime_type,
                               size_bytes, content, preview, content_hash,
                               created_at, modified_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (absolute_path) DO UPDATE SET
                file_name = EXCLUDED.file_name,
                extension = EXCLUDED.extension,
                mime_type = EXCLUDED.mime_type,
                size_bytes = EXCLUDED.size_bytes,
                content = EXCLUDED.content,
                preview = EXCLUDED.preview,
                content_hash = EXCLUDED.content_hash,
                modified_at = EXCLUDED.modified_at,
                indexed_at = NOW()
            """;

    private static final String FIND_BY_PATH_SQL = """
            SELECT * FROM files WHERE absolute_path = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT * FROM files ORDER BY indexed_at DESC
            """;

    private static final String DELETE_SQL = """
            DELETE FROM files WHERE id = ?
            """;

    public void upsert(FileRecord record) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {

            stmt.setString(1, record.getAbsolutePath());
            stmt.setString(2, record.getFileName());
            stmt.setString(3, record.getExtension());
            stmt.setString(4, record.getMimeType());
            stmt.setLong(5, record.getSizeBytes());
            stmt.setString(6, record.getContent());
            stmt.setString(7, record.getPreview());
            stmt.setString(8, record.getContentHash());
            stmt.setTimestamp(9, Timestamp.valueOf(record.getCreatedAt()));
            stmt.setTimestamp(10, Timestamp.valueOf(record.getModifiedAt()));

            stmt.executeUpdate();
            logger.debug("Upserted file: {}", record.getAbsolutePath());

        } catch (SQLException e) {
            logger.error("Failed to upsert file: {}", record.getAbsolutePath(), e);
            throw new RuntimeException("Database error during upsert", e);
        }
    }

    public Optional<FileRecord> findByPath(String absolutePath) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_PATH_SQL)) {

            stmt.setString(1, absolutePath);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(FileRecord.fromResultSet(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            logger.error("Failed to find file by path: {}", absolutePath, e);
            throw new RuntimeException("Database error during findByPath", e);
        }
    }

    public List<FileRecord> findAll() {
        List<FileRecord> records = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                records.add(FileRecord.fromResultSet(rs));
            }
            return records;

        } catch (SQLException e) {
            logger.error("Failed to fetch all files", e);
            throw new RuntimeException("Database error during findAll", e);
        }
    }

    public void delete(long id) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
            logger.debug("Deleted file with id: {}", id);

        } catch (SQLException e) {
            logger.error("Failed to delete file with id: {}", id, e);
            throw new RuntimeException("Database error during delete", e);
        }
    }


}
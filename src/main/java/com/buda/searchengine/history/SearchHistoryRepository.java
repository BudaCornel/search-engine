package com.buda.searchengine.history;

import com.buda.searchengine.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class SearchHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(SearchHistoryRepository.class);

    private static final String INSERT_QUERY_SQL = """
            INSERT INTO search_history (raw_query, result_count, clicked_path, timestamp)
            VALUES (?, ?, NULL, ?)
            """;

    private static final String INSERT_CLICK_SQL = """
            INSERT INTO search_history (raw_query, result_count, clicked_path, timestamp)
            VALUES ('', 0, ?, ?)
            """;

    private static final String FIND_RECENT_SQL = """
            SELECT id, raw_query, result_count, clicked_path, timestamp
            FROM search_history
            WHERE clicked_path IS NULL AND raw_query <> ''
            ORDER BY timestamp DESC
            LIMIT ?
            """;

    private static final String CLICK_FREQUENCY_SQL = """
            SELECT clicked_path, COUNT(*) AS clicks
            FROM search_history
            WHERE clicked_path IS NOT NULL
            GROUP BY clicked_path
            """;

    public void recordQuery(String rawQuery, int resultCount, LocalDateTime timestamp) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_QUERY_SQL)) {
            stmt.setString(1, rawQuery);
            stmt.setInt(2, resultCount);
            stmt.setTimestamp(3, Timestamp.valueOf(timestamp));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record search history", e);
        }
    }

    public void recordClick(String absolutePath, LocalDateTime timestamp) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_CLICK_SQL)) {
            stmt.setString(1, absolutePath);
            stmt.setTimestamp(2, Timestamp.valueOf(timestamp));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record click", e);
        }
    }

    public List<HistoryEntry> findRecent(int limit) {
        List<HistoryEntry> out = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_RECENT_SQL)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new HistoryEntry(
                            rs.getLong("id"),
                            rs.getString("raw_query"),
                            rs.getInt("result_count"),
                            rs.getTimestamp("timestamp").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load history", e);
        }
        return out;
    }

    /** Returns a map of absolute_path → click count, across all time. */
    public Map<String, Integer> clickFrequencies() {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(CLICK_FREQUENCY_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("clicked_path"), rs.getInt("clicks"));
            }
        } catch (SQLException e) {
            logger.warn("Failed to load click frequencies", e);
        }
        return out;
    }

    public record HistoryEntry(long id, String rawQuery, int resultCount, LocalDateTime timestamp) {}
}

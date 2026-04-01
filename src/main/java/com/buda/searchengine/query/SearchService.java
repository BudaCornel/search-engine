package com.buda.searchengine.query;

import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SearchService {
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private static final String SEARCH_SQL = """
            SELECT *,
                   ts_rank(content_tsv, websearch_to_tsquery('english', ?)) AS rank,
                   ts_headline('english', content,
                       websearch_to_tsquery('english', ?),
                       'StartSel=>>>, StopSel=<<<, MaxWords=35, MinWords=15'
                   ) AS snippet
            FROM files
            WHERE content_tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT ?
            """;

    private static final String SEARCH_FILENAME_SQL = """
            SELECT *, 0.0 AS rank, preview AS snippet
            FROM files
            WHERE file_name ILIKE ?
            ORDER BY file_name
            LIMIT ?
            """;

    private static final int DEFAULT_LIMIT = 20;

    public List<SearchResult> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String query, int limit) {
        List<SearchResult> results = fullTextSearch(query, limit);

        if (results.isEmpty()) {
            logger.debug("No full-text results, falling back to filename search: {}", query);
            results = filenameSearch(query, limit);
        }

        logger.info("Search for '{}' returned {} results", query, results.size());
        return results;
    }

    private List<SearchResult> fullTextSearch(String query, int limit) {
        List<SearchResult> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL)) {

            stmt.setString(1, query);
            stmt.setString(2, query);
            stmt.setString(3, query);
            stmt.setInt(4, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(new SearchResult(
                        FileRecord.fromResultSet(rs),
                        rs.getDouble("rank"),
                        rs.getString("snippet")
                ));
            }

        } catch (SQLException e) {
            logger.error("Full-text search failed for query: {}", query, e);
            throw new RuntimeException("Search error", e);
        }

        return results;
    }

    private List<SearchResult> filenameSearch(String query, int limit) {
        List<SearchResult> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SEARCH_FILENAME_SQL)) {

            stmt.setString(1, "%" + query + "%");
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(new SearchResult(
                        FileRecord.fromResultSet(rs),
                        rs.getDouble("rank"),
                        rs.getString("snippet")
                ));
            }

        } catch (SQLException e) {
            logger.error("Filename search failed for query: {}", query, e);
            throw new RuntimeException("Search error", e);
        }

        return results;
    }


}
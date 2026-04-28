package com.buda.searchengine.query;

import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private static final int DEFAULT_LIMIT = 20;

    private static final String FILENAME_FALLBACK_SQL = """
            SELECT *, 0.0 AS rank, preview AS snippet
            FROM files
            WHERE file_name ILIKE ?
            ORDER BY file_name
            LIMIT ?
            """;

    private final QueryParser parser;

    public SearchService() {
        this(new QueryParser());
    }

    public SearchService(QueryParser parser) {
        this.parser = parser;
    }

    /** Convenience overload: parse the raw input and run the search. */
    public List<SearchResult> search(String rawQuery) {
        return search(rawQuery, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String rawQuery, int limit) {
        ParsedQuery parsed = parser.parse(rawQuery);
        return search(parsed, limit);
    }

    public List<SearchResult> search(ParsedQuery query, int limit) {
        if (query.isEmpty()) {
            logger.debug("Empty query, returning no results");
            return List.of();
        }

        List<SearchResult> results = structuredSearch(query, limit);


        boolean isContentOnly = query.qualifiers().size() == 1
                && query.has(Qualifier.CONTENT);
        if (results.isEmpty() && isContentOnly) {
            String contentTerm = String.join(" ", query.termsFor(Qualifier.CONTENT));
            logger.debug("Falling back to filename search for: {}", contentTerm);
            results = filenameFallback(contentTerm, limit);
        }

        logger.info("Search '{}' returned {} results", query.rawQuery(), results.size());
        return results;
    }


    private List<SearchResult> structuredSearch(ParsedQuery query, int limit) {
        StringBuilder sql = new StringBuilder("SELECT *");
        List<Object> params = new ArrayList<>();

        boolean hasContent = query.has(Qualifier.CONTENT);
        String contentTerm = hasContent
                ? String.join(" ", query.termsFor(Qualifier.CONTENT))
                : null;

        if (hasContent) {
            sql.append(",\n  ts_rank(content_tsv, websearch_to_tsquery('english', ?)) AS rank");
            sql.append(",\n  ts_headline('english', content,\n")
                    .append("       websearch_to_tsquery('english', ?),\n")
                    .append("       'StartSel=>>>, StopSel=<<<, MaxWords=35, MinWords=15') AS snippet");
            params.add(contentTerm);
            params.add(contentTerm);
        } else {
            sql.append(", 0.0 AS rank, preview AS snippet");
        }

        sql.append("\nFROM files\nWHERE 1=1");

        if (hasContent) {
            sql.append("\n  AND content_tsv @@ websearch_to_tsquery('english', ?)");
            params.add(contentTerm);
        }

        for (String pathTerm : query.termsFor(Qualifier.PATH)) {
            sql.append("\n  AND absolute_path ILIKE ?");
            params.add("%" + pathTerm + "%");
        }

        for (String nameTerm : query.termsFor(Qualifier.NAME)) {
            sql.append("\n  AND file_name ILIKE ?");
            params.add("%" + nameTerm + "%");
        }

        for (String extTerm : query.termsFor(Qualifier.EXT)) {
            sql.append("\n  AND LOWER(extension) = LOWER(?)");
            params.add(extTerm);
        }

        for (String mimeTerm : query.termsFor(Qualifier.MIME)) {
            sql.append("\n  AND mime_type = ?");
            params.add(mimeTerm);
        }

        sql.append("\nORDER BY rank DESC\nLIMIT ?");
        params.add(limit);

        logger.debug("Generated SQL: {}\nParams: {}", sql, params);

        return executeSearch(sql.toString(), params);
    }

    private List<SearchResult> executeSearch(String sql, List<Object> params) {
        List<SearchResult> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            FileRecord.fromResultSet(rs),
                            rs.getDouble("rank"),
                            rs.getString("snippet")
                    ));
                }
            }

        } catch (SQLException e) {
            logger.error("Structured search failed", e);
            throw new RuntimeException("Search error", e);
        }

        return results;
    }

    private List<SearchResult> filenameFallback(String term, int limit) {
        List<SearchResult> results = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FILENAME_FALLBACK_SQL)) {

            stmt.setString(1, "%" + term + "%");
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            FileRecord.fromResultSet(rs),
                            rs.getDouble("rank"),
                            rs.getString("snippet")
                    ));
                }
            }

        } catch (SQLException e) {
            logger.error("Filename fallback failed for term: {}", term, e);
            throw new RuntimeException("Search error", e);
        }

        return results;
    }
}
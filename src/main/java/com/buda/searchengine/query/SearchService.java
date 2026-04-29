package com.buda.searchengine.query;

import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.history.SearchEvent;
import com.buda.searchengine.history.SearchObserver;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import com.buda.searchengine.ranker.RankingStrategy;
import com.buda.searchengine.ranker.RelevanceRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_LIMIT = 20;

    private final QueryParser parser;
    private final List<SearchObserver> observers = new CopyOnWriteArrayList<>();
    private volatile RankingStrategy strategy;

    public SearchService() {
        this(new QueryParser(), new RelevanceRanking());
    }

    public SearchService(QueryParser parser, RankingStrategy strategy) {
        this.parser = parser;
        this.strategy = strategy;
    }

    public RankingStrategy getStrategy() { return strategy; }
    public void setStrategy(RankingStrategy strategy) { this.strategy = strategy; }

    public void addObserver(SearchObserver observer) { observers.add(observer); }
    public void removeObserver(SearchObserver observer) { observers.remove(observer); }

    public List<SearchResult> search(String rawQuery) {
        return search(rawQuery, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String rawQuery, int limit) {
        return search(parser.parse(rawQuery), rawQuery, limit);
    }

    public List<SearchResult> search(ParsedQuery query, int limit) {
        return search(query, query.rawQuery(), limit);
    }

    private List<SearchResult> search(ParsedQuery query, String rawQuery, int limit) {
        if (query.isEmpty()) {
            logger.debug("Empty query, returning no results");
            notifyObservers(SearchEvent.forQuery(rawQuery, query, 0));
            return List.of();
        }

        int sqlLimit = strategy.sqlLimit(limit);
        List<SearchResult> raw = structuredSearch(query, sqlLimit);
        List<SearchResult> ranked = strategy.postProcess(raw, limit);

        logger.info("Search '{}' returned {} results (strategy={})",
                query.rawQuery(), ranked.size(), strategy.name());

        notifyObservers(SearchEvent.forQuery(rawQuery, query, ranked.size()));
        return ranked;
    }

    public void recordClick(String absolutePath) {
        notifyObservers(SearchEvent.forClick(absolutePath));
    }

    private void notifyObservers(SearchEvent event) {
        for (SearchObserver o : observers) {
            try { o.onSearch(event); }
            catch (Exception e) {
                logger.warn("Observer {} failed", o.getClass().getSimpleName(), e);
            }
        }
    }

    private List<SearchResult> structuredSearch(ParsedQuery query, int limit) {
        StringBuilder inner = new StringBuilder("SELECT *");
        List<Object> params = new ArrayList<>();

        boolean hasContent = query.has(Qualifier.CONTENT);
        String contentTerm = hasContent
                ? String.join(" ", query.termsFor(Qualifier.CONTENT))
                : null;

        if (hasContent) {
            inner.append(",\n  ts_rank(content_tsv, websearch_to_tsquery('english', ?)) AS relevance");
            inner.append(",\n  ts_headline('english', content,\n")
                    .append("       websearch_to_tsquery('english', ?),\n")
                    .append("       'StartSel=>>>, StopSel=<<<, MaxWords=35, MinWords=15') AS snippet");
            params.add(contentTerm);
            params.add(contentTerm);
        } else {
            inner.append(", 0.0 AS relevance, preview AS snippet");
        }

        inner.append("\nFROM files\nWHERE 1=1");

        if (hasContent) {
            inner.append("\n  AND content_tsv @@ websearch_to_tsquery('english', ?)");
            params.add(contentTerm);
        }

        for (String pathTerm : query.termsFor(Qualifier.PATH)) {
            inner.append("\n  AND absolute_path ILIKE ?");
            params.add("%" + pathTerm + "%");
        }
        for (String nameTerm : query.termsFor(Qualifier.NAME)) {
            inner.append("\n  AND file_name ILIKE ?");
            params.add("%" + nameTerm + "%");
        }
        for (String extTerm : query.termsFor(Qualifier.EXT)) {
            inner.append("\n  AND LOWER(extension) = LOWER(?)");
            params.add(extTerm);
        }
        for (String mimeTerm : query.termsFor(Qualifier.MIME)) {
            inner.append("\n  AND mime_type = ?");
            params.add(mimeTerm);
        }


        String sql = "SELECT * FROM (\n" + inner + "\n) AS ranked_results\n"
                + "ORDER BY " + strategy.orderByClause() + "\n"
                + "LIMIT ?";
        params.add(limit);

        logger.debug("Generated SQL: {}\nParams: {}", sql, params);
        return executeSearch(sql, params);
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
                            rs.getDouble("relevance"),
                            rs.getString("snippet")));
                }
            }

        } catch (SQLException e) {
            logger.error("Structured search failed", e);
            throw new RuntimeException("Search error", e);
        }

        return results;
    }
}
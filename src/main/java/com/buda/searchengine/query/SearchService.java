package com.buda.searchengine.query;

import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.history.SearchEvent;
import com.buda.searchengine.history.SearchObserver;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import com.buda.searchengine.query.booleanq.BooleanNode;
import com.buda.searchengine.query.booleanq.BooleanParser;
import com.buda.searchengine.query.booleanq.SqlFragment;
import com.buda.searchengine.query.booleanq.SqlVisitor;
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
import java.util.regex.Pattern;


public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_LIMIT = 20;

    private static final Pattern BOOLEAN_KEYWORD =
            Pattern.compile("(?i)\\b(AND|OR|NOT)\\b");

    private final QueryParser parser;
    private final BooleanParser booleanParser;
    private final SqlVisitor sqlVisitor;
    private final List<SearchObserver> observers = new CopyOnWriteArrayList<>();
    private volatile RankingStrategy strategy;

    public SearchService() {
        this(new QueryParser(), new RelevanceRanking());
    }

    public SearchService(QueryParser parser, RankingStrategy strategy) {
        this.parser = parser;
        this.strategy = strategy;
        this.booleanParser = new BooleanParser();
        this.sqlVisitor = new SqlVisitor();
    }

    public RankingStrategy getStrategy() { return strategy; }
    public void setStrategy(RankingStrategy strategy) { this.strategy = strategy; }

    public void addObserver(SearchObserver observer) { observers.add(observer); }
    public void removeObserver(SearchObserver observer) { observers.remove(observer); }

    public List<SearchResult> search(String rawQuery) {
        return search(rawQuery, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String rawQuery, int limit) {
        if (rawQuery == null || rawQuery.isBlank()) {
            ParsedQuery empty = parser.parse(rawQuery);
            notifyObservers(SearchEvent.forQuery(rawQuery, empty, 0));
            return List.of();
        }

        Plan plan;
        try {
            plan = looksBoolean(rawQuery) ? planBoolean(rawQuery) : planSimple(rawQuery);
        } catch (BooleanParser.ParseException e) {
            logger.warn("Boolean parse failed: {}", e.getMessage());
            throw new RuntimeException("Bad query: " + e.getMessage());
        }

        if (plan == null) {
            ParsedQuery empty = parser.parse(rawQuery);
            notifyObservers(SearchEvent.forQuery(rawQuery, empty, 0));
            return List.of();
        }

        int sqlLimit = strategy.sqlLimit(limit);
        List<SearchResult> raw = executePlan(plan, sqlLimit);
        List<SearchResult> ranked = strategy.postProcess(raw, limit);

        logger.info("Search '{}' returned {} results (strategy={}, parser={})",
                rawQuery, ranked.size(), strategy.name(),
                plan.boolean_() ? "boolean" : "simple");

        ParsedQuery parsedForObserver = plan.boolean_()
                ? ParsedQuery.builder().rawQuery(rawQuery).build()
                : parser.parse(rawQuery);
        notifyObservers(SearchEvent.forQuery(rawQuery, parsedForObserver, ranked.size()));
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

    static boolean looksBoolean(String input) {
        if (input == null) return false;
        if (input.indexOf('(') >= 0 || input.indexOf(')') >= 0) return true;
        if (input.toLowerCase().contains("size:")) return true;
        return BOOLEAN_KEYWORD.matcher(input).find();
    }

    private Plan planBoolean(String rawQuery) {
        BooleanNode tree = booleanParser.parse(rawQuery);
        SqlFragment fragment = tree.accept(sqlVisitor);
        String contentTerm = SqlVisitor.firstContentTerm(tree);
        return new Plan(fragment.whereClause(), fragment.params(), contentTerm, true);
    }

    private Plan planSimple(String rawQuery) {
        ParsedQuery parsed = parser.parse(rawQuery);
        if (parsed.isEmpty()) return null;

        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        String contentTerm = parsed.has(Qualifier.CONTENT)
                ? String.join(" ", parsed.termsFor(Qualifier.CONTENT))
                : null;

        if (contentTerm != null) {
            where.append(" AND content_tsv @@ websearch_to_tsquery('english', ?)");
            params.add(contentTerm);
        }
        for (String p : parsed.termsFor(Qualifier.PATH)) {
            where.append(" AND absolute_path ILIKE ?");
            params.add("%" + p + "%");
        }
        for (String n : parsed.termsFor(Qualifier.NAME)) {
            where.append(" AND file_name ILIKE ?");
            params.add("%" + n + "%");
        }
        for (String e : parsed.termsFor(Qualifier.EXT)) {
            where.append(" AND LOWER(extension) = LOWER(?)");
            params.add(e);
        }
        for (String m : parsed.termsFor(Qualifier.MIME)) {
            where.append(" AND mime_type = ?");
            params.add(m);
        }
        return new Plan(where.toString(), params, contentTerm, false);
    }

    private List<SearchResult> executePlan(Plan plan, int limit) {
        StringBuilder inner = new StringBuilder("SELECT *");
        List<Object> params = new ArrayList<>();

        if (plan.contentTerm() != null) {
            inner.append(",\n  ts_rank(content_tsv, websearch_to_tsquery('english', ?)) AS relevance");
            inner.append(",\n  ts_headline('english', content,\n")
                    .append("       websearch_to_tsquery('english', ?),\n")
                    .append("       'StartSel=>>>, StopSel=<<<, MaxWords=35, MinWords=15') AS snippet");
            params.add(plan.contentTerm());
            params.add(plan.contentTerm());
        } else {
            inner.append(", 0.0 AS relevance, preview AS snippet");
        }

        inner.append("\nFROM files\nWHERE ").append(plan.whereClause());
        params.addAll(plan.params());

        String sql = "SELECT * FROM (\n" + inner + "\n) AS ranked_results\n"
                + "ORDER BY " + strategy.orderByClause() + "\n"
                + "LIMIT ?";
        params.add(limit);

        logger.debug("Generated SQL:\n{}\nParams: {}", sql, params);
        return runQuery(sql, params);
    }

    private List<SearchResult> runQuery(String sql, List<Object> params) {
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
            logger.error("Search failed", e);
            throw new RuntimeException("Search error", e);
        }
        return results;
    }


    private record Plan(String whereClause, List<Object> params,
                        String contentTerm, boolean boolean_) {}
}

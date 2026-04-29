package com.buda.searchengine.ranker;

import com.buda.searchengine.history.SearchHistoryRepository;
import com.buda.searchengine.model.SearchResult;

import java.util.*;
import java.util.stream.Collectors;


public class HistoryAwareRanking implements RankingStrategy {

    private static final int CANDIDATE_MULTIPLIER = 3;
    private static final double BOOST_PER_CLICK = 0.05;
    private static final double MAX_BOOST = 0.5;

    private final RankingStrategy delegate;
    private final SearchHistoryRepository historyRepo;

    public HistoryAwareRanking(RankingStrategy delegate, SearchHistoryRepository historyRepo) {
        this.delegate = delegate;
        this.historyRepo = historyRepo;
    }

    @Override public String name() { return delegate.name() + "+history"; }
    @Override public String description() {
        return delegate.description() + " (boosted by click history)";
    }
    @Override public String orderByClause() { return delegate.orderByClause(); }

    @Override
    public int sqlLimit(int userLimit) {
        return userLimit * CANDIDATE_MULTIPLIER;
    }

    @Override
    public List<SearchResult> postProcess(List<SearchResult> results, int userLimit) {
        if (results.isEmpty()) return results;

        Map<String, Integer> clicks = historyRepo.clickFrequencies();
        if (clicks.isEmpty()) {
            return delegate.postProcess(results, userLimit);
        }

        // Build (result, score) tuples so we can sort by combined score
        List<Scored> scored = results.stream()
                .map(r -> new Scored(r, computeBoost(r, clicks)))
                .sorted(Comparator.<Scored>comparingDouble(s -> -(s.boost + intrinsic(s.result))))
                .collect(Collectors.toList());

        return scored.stream()
                .limit(userLimit)
                .map(s -> s.result)
                .toList();
    }

    private double computeBoost(SearchResult r, Map<String, Integer> clicks) {
        Integer count = clicks.get(r.getFileRecord().getAbsolutePath());
        if (count == null) return 0.0;
        return Math.min(MAX_BOOST, count * BOOST_PER_CLICK);
    }

    private double intrinsic(SearchResult r) {
        return r.getRank();
    }

    private record Scored(SearchResult result, double boost) {}
}

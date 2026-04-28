package com.buda.searchengine.ranker;

public class HybridRanking implements RankingStrategy {
    @Override public String name() { return "hybrid"; }
    @Override public String description() { return "Relevance x path_score"; }
    @Override public String orderByClause() {
        return "(rank * 0.7 + path_score * 0.3) DESC, file_name ASC";
    }
}

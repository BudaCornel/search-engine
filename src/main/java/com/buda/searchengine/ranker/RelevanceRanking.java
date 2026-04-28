package com.buda.searchengine.ranker;

public class RelevanceRanking implements RankingStrategy {
    @Override public String name() { return "relevance"; }
    @Override public String description() { return "Full-text relevance (ts_rank)"; }
    @Override public String orderByClause() { return "rank DESC, file_name ASC"; }
}

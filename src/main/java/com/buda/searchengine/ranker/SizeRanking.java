package com.buda.searchengine.ranker;

public class SizeRanking implements RankingStrategy {
    @Override public String name() { return "size"; }
    @Override public String description() { return "Largest files first"; }
    @Override public String orderByClause() { return "size_bytes DESC, file_name ASC"; }
}

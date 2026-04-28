package com.buda.searchengine.ranker;

public class AlphabeticalRanking implements RankingStrategy {
    @Override public String name() { return "alphabetical"; }
    @Override public String description() { return "By filename A->Z"; }
    @Override public String orderByClause() { return "file_name ASC"; }
}

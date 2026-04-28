package com.buda.searchengine.ranker;

public class DateModifiedRanking implements RankingStrategy {
    @Override public String name() { return "date-modified"; }
    @Override public String description() { return "Most recently modified first"; }
    @Override public String orderByClause() { return "modified_at DESC, file_name ASC"; }
}

package com.buda.searchengine.ranker;

public class DateAccessedRanking implements RankingStrategy {
    @Override public String name() { return "date-accessed"; }
    @Override public String description() { return "Most recently accessed first"; }
    @Override public String orderByClause() {
        return "COALESCE(accessed_at, modified_at) DESC, file_name ASC";
    }
}

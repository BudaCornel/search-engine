package com.buda.searchengine.ranker;

public class PathScoreRanking implements RankingStrategy {
    @Override public String name() { return "path-score"; }
    @Override public String description() { return "By intrinsic file importance"; }
    @Override public String orderByClause() { return "path_score DESC, file_name ASC"; }
}

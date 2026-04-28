package com.buda.searchengine.ranker;

import com.buda.searchengine.model.SearchResult;

import java.util.List;


public interface RankingStrategy {

    String name();

    String description();


    String orderByClause();

    default int sqlLimit(int userLimit) {
        return userLimit;
    }


    default List<SearchResult> postProcess(List<SearchResult> results, int userLimit) {
        return results.size() <= userLimit ? results : results.subList(0, userLimit);
    }
}

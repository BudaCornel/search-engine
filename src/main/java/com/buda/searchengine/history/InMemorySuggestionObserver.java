package com.buda.searchengine.history;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class InMemorySuggestionObserver implements SearchObserver, QuerySuggester {

    private final Map<String, Integer> frequencies = new ConcurrentHashMap<>();

    @Override
    public void onSearch(SearchEvent event) {
        if (event.isQuery() && event.rawQuery() != null && !event.rawQuery().isBlank()) {
            frequencies.merge(event.rawQuery().trim(), 1, Integer::sum);
        }
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        String needle = prefix == null ? "" : prefix.toLowerCase().trim();
        return frequencies.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().startsWith(needle))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public void primeFrom(SearchHistoryRepository repo) {
        for (SearchHistoryRepository.HistoryEntry e : repo.findRecent(1000)) {
            frequencies.merge(e.rawQuery(), 1, Integer::sum);
        }
    }

    public int distinctQueryCount() {
        return frequencies.size();
    }
}

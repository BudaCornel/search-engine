package com.buda.searchengine.history;

import com.buda.searchengine.query.ParsedQuery;

import java.time.LocalDateTime;
import java.util.Optional;


public record SearchEvent(
        String rawQuery,
        ParsedQuery parsedQuery,
        int resultCount,
        LocalDateTime timestamp,
        Optional<String> clickedPath
) {
    public static SearchEvent forQuery(String raw, ParsedQuery parsed, int count) {
        return new SearchEvent(raw, parsed, count, LocalDateTime.now(), Optional.empty());
    }

    public static SearchEvent forClick(String path) {
        return new SearchEvent("", null, 0, LocalDateTime.now(), Optional.of(path));
    }

    public boolean isClick() {
        return clickedPath.isPresent();
    }

    public boolean isQuery() {
        return !isClick();
    }
}

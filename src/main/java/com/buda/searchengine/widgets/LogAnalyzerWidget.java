package com.buda.searchengine.widgets;

import com.buda.searchengine.model.SearchResult;

import java.util.List;

public class LogAnalyzerWidget implements Widget {

    @Override
    public String name() { return "Log Analyzer"; }

    @Override
    public boolean shouldActivate(List<SearchResult> results) {
        if (results.isEmpty()) return false;
        long logCount = countLogs(results);
        return logCount * 2 > results.size();
    }

    @Override
    public String render(List<SearchResult> results) {
        long logCount = countLogs(results);
        return String.format(
                "Log Analyzer - %d .log file(s). Try `search ERROR ext:log` " +
                        "or `search WARN ext:log` to drill in.",
                logCount);
    }

    private long countLogs(List<SearchResult> results) {
        return results.stream()
                .map(r -> r.getFileRecord().getExtension())
                .filter(e -> e != null && e.equalsIgnoreCase("log"))
                .count();
    }
}

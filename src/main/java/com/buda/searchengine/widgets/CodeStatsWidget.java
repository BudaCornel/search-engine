package com.buda.searchengine.widgets;

import com.buda.searchengine.model.SearchResult;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeStatsWidget implements Widget {

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "py", "js", "ts", "tsx", "jsx",
            "c", "cpp", "cc", "h", "hpp",
            "rs", "go", "kt", "rb", "php", "swift", "scala", "cs",
            "html", "css", "scss", "sql", "sh"
    );

    @Override
    public String name() { return "Code Stats"; }

    @Override
    public boolean shouldActivate(List<SearchResult> results) {
        if (results.isEmpty()) return false;
        long codeCount = results.stream()
                .map(r -> r.getFileRecord().getExtension())
                .filter(Objects::nonNull)
                .filter(e -> CODE_EXTENSIONS.contains(e.toLowerCase(Locale.ROOT)))
                .count();
        return codeCount * 2 > results.size();
    }

    @Override
    public String render(List<SearchResult> results) {
        Map<String, Long> byLang = results.stream()
                .map(r -> r.getFileRecord().getExtension())
                .filter(Objects::nonNull)
                .map(e -> e.toLowerCase(Locale.ROOT))
                .filter(CODE_EXTENSIONS::contains)
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        String top3 = byLang.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Comparator.comparing(Map.Entry::getKey)))
                .limit(3)
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));

        return String.format("Code Stats - top languages: %s", top3);
    }
}

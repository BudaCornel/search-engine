package com.buda.searchengine.cli;

import com.buda.searchengine.config.AppConfig;
import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.FileCrawler;
import com.buda.searchengine.crawler.FileFilter;
import com.buda.searchengine.history.*;
import com.buda.searchengine.indexer.IndexBuilder;
import com.buda.searchengine.indexer.MetadataExtractor;
import com.buda.searchengine.indexer.PathScorer;
import com.buda.searchengine.model.SearchResult;
import com.buda.searchengine.query.SearchService;
import com.buda.searchengine.ranker.HistoryAwareRanking;
import com.buda.searchengine.ranker.RankingStrategy;
import com.buda.searchengine.ranker.RankingStrategyRegistry;
import com.buda.searchengine.repository.FileRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/** CLI for indexing and searching local files. */
public class SearchEngineCLI {

    private static final String BANNER = """
            
            ╔══════════════════════════════════════════════════╗
            ║          LocalSearch Engine v2.0                 ║
            ╠══════════════════════════════════════════════════╣
            ║  Indexing                                        ║
            ║    index [path]   - Full index                   ║
            ║    reindex        - Incremental index            ║
            ║                                                  ║
            ║  Searching                                       ║
            ║    search <query> - Search files                 ║
            ║    open <n>       - Open result #n from list     ║
            ║    suggest <pre>  - Suggest queries by prefix    ║
            ║    history [n]    - Recent searches              ║
            ║                                                  ║
            ║  Ranking                                         ║
            ║    rank list      - List ranking strategies      ║
            ║    rank <name>    - Switch ranking strategy      ║
            ║                                                  ║
            ║  Misc                                            ║
            ║    config | help | quit                          ║
            ║                                                  ║
            ║  Query qualifiers (combine with AND):            ║
            ║    content:foo  path:src/main  ext:java          ║
            ║    name:Auth    mime:text/plain  "with spaces"   ║
            ╚══════════════════════════════════════════════════╝
            """;

    private static AppConfig config;
    private static SearchService searchService;
    private static RankingStrategyRegistry rankingRegistry;
    private static SearchHistoryRepository historyRepo;
    private static InMemorySuggestionObserver suggestions;
    private static List<SearchResult> lastResults = List.of();

    public static void main(String[] args) {
        config = AppConfig.load();
        bootstrap();
        System.out.println(BANNER);

        try (Scanner scanner = new Scanner(System.in)) {
            loop(scanner);
        }
    }

    private static void bootstrap() {
        rankingRegistry = RankingStrategyRegistry.withDefaults();
        historyRepo = new SearchHistoryRepository();
        suggestions = new InMemorySuggestionObserver();
        suggestions.primeFrom(historyRepo);

        for (RankingStrategy base : List.copyOf(rankingRegistry.all())) {
            rankingRegistry.register(new HistoryAwareRanking(base, historyRepo));
        }

        searchService = new SearchService();
        searchService.addObserver(new PersistentHistoryObserver(historyRepo));
        searchService.addObserver(suggestions);
    }

    private static void loop(Scanner scanner) {
        while (true) {
            System.out.print("\nlocalsearch> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1].trim() : "";

            try {
                switch (cmd) {
                    case "quit", "exit"     -> { shutdown(); return; }
                    case "help"             -> System.out.println(BANNER);
                    case "config"           -> showConfig();
                    case "index"            -> handleIndex(arg);
                    case "reindex"          -> handleReindex();
                    case "search"           -> handleSearch(arg);
                    case "open"             -> handleOpen(arg);
                    case "suggest"          -> handleSuggest(arg);
                    case "history"          -> handleHistory(arg);
                    case "rank"             -> handleRank(arg);
                    default                 -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void shutdown() {
        System.out.println("Shutting down...");
        DatabaseConfig.close();
    }

    private static void handleIndex(String pathStr) {
        Path root = pathStr.isEmpty()
                ? Paths.get(config.getRootDirectory())
                : Paths.get(pathStr);
        System.out.println("Indexing: " + root.toAbsolutePath());
        buildIndexer().indexAll(root);
    }

    private static void handleReindex() {
        Path root = Paths.get(config.getRootDirectory());
        System.out.println("Incremental reindex: " + root.toAbsolutePath());
        buildIndexer().indexIncremental(root);
    }

    private static IndexBuilder buildIndexer() {
        return new IndexBuilder(
                new FileCrawler(new FileFilter(config.getIgnorePatterns())),
                new ContentExtractor(),
                new MetadataExtractor(),
                new PathScorer(),
                new FileRepository());
    }

    private static void handleSearch(String query) {
        if (query.isEmpty()) { System.out.println("Please provide a query."); return; }

        lastResults = searchService.search(query, config.getMaxResults());

        if (lastResults.isEmpty()) {
            System.out.println("No results found for: " + query);
            return;
        }

        System.out.printf("%n Found %d result(s) [strategy: %s]:%n%n",
                lastResults.size(), searchService.getStrategy().name());
        for (int i = 0; i < lastResults.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, lastResults.get(i));
        }
        System.out.println("(Tip: 'open <n>' to record opening a result)");
    }

    private static void handleOpen(String numberStr) {
        if (lastResults.isEmpty()) {
            System.out.println("No results to open. Run a 'search' first.");
            return;
        }
        int idx;
        try { idx = Integer.parseInt(numberStr.trim()) - 1; }
        catch (NumberFormatException e) { System.out.println("Usage: open <number>"); return; }

        if (idx < 0 || idx >= lastResults.size()) {
            System.out.println("Invalid number. Choose 1.." + lastResults.size());
            return;
        }
        String path = lastResults.get(idx).getFileRecord().getAbsolutePath();
        searchService.recordClick(path);
        System.out.println("Recorded open: " + path);
    }

    private static void handleSuggest(String prefix) {
        List<String> hits = suggestions.suggest(prefix, 10);
        if (hits.isEmpty()) {
            System.out.println("No suggestions for '" + prefix + "'");
            return;
        }
        System.out.println("Suggestions:");
        hits.forEach(s -> System.out.println("  - " + s));
    }

    private static void handleHistory(String arg) {
        int limit = 10;
        if (!arg.isEmpty()) {
            try { limit = Integer.parseInt(arg.trim()); }
            catch (NumberFormatException e) { System.out.println("Usage: history [n]"); return; }
        }
        List<SearchHistoryRepository.HistoryEntry> entries = historyRepo.findRecent(limit);
        if (entries.isEmpty()) {
            System.out.println("No history yet.");
            return;
        }
        System.out.printf("Last %d searches:%n", entries.size());
        for (SearchHistoryRepository.HistoryEntry e : entries) {
            System.out.printf("  [%s] %-40s (%d results)%n",
                    e.timestamp(), e.rawQuery(), e.resultCount());
        }
    }

    private static void handleRank(String arg) {
        if (arg.isEmpty() || arg.equalsIgnoreCase("list")) {
            System.out.println("Available ranking strategies:");
            String current = searchService.getStrategy().name();
            for (RankingStrategy s : rankingRegistry.all()) {
                String marker = s.name().equals(current) ? " *" : "  ";
                System.out.printf("  %s %-15s - %s%n", marker, s.name(), s.description());
            }
            System.out.println("(Use 'rank <name>' to switch.)");
            return;
        }
        rankingRegistry.find(arg).ifPresentOrElse(
                s -> { searchService.setStrategy(s); System.out.println("Ranking strategy: " + s.name()); },
                () -> System.out.println("Unknown strategy: " + arg + ". Try 'rank list'."));
    }

    private static void showConfig() {
        System.out.printf("""
                
                Current Configuration:
                  Root directory:    %s
                  Ignore patterns:   %s
                  Max results:       %d
                  Report format:     %s
                  Ranking strategy:  %s
                """,
                config.getRootDirectory(), config.getIgnorePatterns(),
                config.getMaxResults(), config.getReportFormat(),
                searchService.getStrategy().name());
    }
}
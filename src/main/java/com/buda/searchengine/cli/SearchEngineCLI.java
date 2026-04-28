package com.buda.searchengine.cli;

import com.buda.searchengine.config.AppConfig;
import com.buda.searchengine.config.DatabaseConfig;
import com.buda.searchengine.crawler.ContentExtractor;
import com.buda.searchengine.crawler.FileCrawler;
import com.buda.searchengine.crawler.FileFilter;
import com.buda.searchengine.indexer.IndexBuilder;
import com.buda.searchengine.indexer.MetadataExtractor;
import com.buda.searchengine.indexer.PathScorer;
import com.buda.searchengine.model.SearchResult;
import com.buda.searchengine.query.SearchService;
import com.buda.searchengine.ranker.RankingStrategy;
import com.buda.searchengine.ranker.RankingStrategyRegistry;
import com.buda.searchengine.repository.FileRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;


/** CLI interface for indexing and searching local files. */
public class SearchEngineCLI {

    private static final String BANNER = """
            
            ╔══════════════════════════════════════════════╗
            ║         LocalSearch Engine v1.1              ║
            ╠══════════════════════════════════════════════╣
            ║  Commands:                                   ║
            ║    index [path]   - Full index               ║
            ║    reindex        - Incremental index        ║
            ║    search <query> - Search files             ║
            ║    rank list      - List ranking strategies  ║
            ║    rank <name>    - Switch ranking strategy  ║
            ║    config         - Show config              ║
            ║    quit           - Exit                     ║
            ║                                              ║
            ║  Query qualifiers (combine with AND):        ║
            ║    content:foo   path:src/main   ext:java    ║
            ║    name:Auth     mime:text/plain             ║
            ║    "quoted phrases" supported                ║
            ╚══════════════════════════════════════════════╝
            """;

    private static AppConfig config;
    private static RankingStrategyRegistry rankingRegistry;

    public static void main(String[] args) {
        config = AppConfig.load();
        rankingRegistry = RankingStrategyRegistry.withDefaults();
        System.out.println(BANNER);

        Scanner scanner = new Scanner(System.in);
        SearchService searchService = new SearchService();

        while (true) {
            System.out.print("\nlocalsearch> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println("Shutting down...");
                DatabaseConfig.close();
                break;
            }

            if (input.startsWith("index")) {
                String pathStr = input.length() > 6 ? input.substring(6).trim() : "";
                handleIndex(pathStr);
            } else if (input.startsWith("search ")) {
                String query = input.substring(7).trim();
                handleSearch(query, searchService);
            } else if (input.equalsIgnoreCase("rank") || input.startsWith("rank ")) {
                String arg = input.length() > 4 ? input.substring(5).trim() : "";
                handleRank(arg, searchService);
            } else if (input.equalsIgnoreCase("config")) {
                handleConfig(searchService);
            } else if (input.equalsIgnoreCase("reindex")) {
                handleReindex();
            } else {
                System.out.println("Unknown command. Use: index [path], search <query>, rank [list|<name>], config, or quit");
            }
        }
    }


    private static void handleReindex() {
        Path root = Paths.get(config.getRootDirectory());
        System.out.println("Incremental reindex: " + root.toAbsolutePath());

        FileFilter fileFilter = new FileFilter(config.getIgnorePatterns());
        FileCrawler crawler = new FileCrawler(fileFilter);
        ContentExtractor contentExtractor = new ContentExtractor();
        MetadataExtractor metadataExtractor = new MetadataExtractor();
        FileRepository repository = new FileRepository();

        PathScorer pathScorer = new PathScorer();
        IndexBuilder indexBuilder = new IndexBuilder(
                crawler, contentExtractor, metadataExtractor, pathScorer, repository);

        indexBuilder.indexIncremental(root);
    }

    private static void handleIndex(String pathStr) {
        Path root;
        if (pathStr.isEmpty()) {
            root = Paths.get(config.getRootDirectory());
            System.out.println("Using configured root: " + root.toAbsolutePath());
        } else {
            root = Paths.get(pathStr);
        }

        System.out.println("Indexing: " + root.toAbsolutePath());

        FileFilter fileFilter = new FileFilter(config.getIgnorePatterns());
        FileCrawler crawler = new FileCrawler(fileFilter);
        ContentExtractor contentExtractor = new ContentExtractor();
        MetadataExtractor metadataExtractor = new MetadataExtractor();
        FileRepository repository = new FileRepository();

        PathScorer pathScorer = new PathScorer();
        IndexBuilder indexBuilder = new IndexBuilder(
                crawler, contentExtractor, metadataExtractor, pathScorer, repository);

        indexBuilder.indexAll(root);
    }

    private static void handleSearch(String query, SearchService searchService) {
        if (query.isEmpty()) {
            System.out.println("Please provide a search query.");
            return;
        }

        List<SearchResult> results = searchService.search(query, config.getMaxResults());

        if (results.isEmpty()) {
            System.out.println("No results found for: " + query);
            return;
        }

        System.out.printf("%n Found %d result(s) [strategy: %s]:%n%n",
                results.size(), searchService.getStrategy().name());

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            System.out.printf("  %d. %s%n", i + 1, result);
        }
    }

    private static void handleRank(String arg, SearchService searchService) {
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
                s -> {
                    searchService.setStrategy(s);
                    System.out.println("Ranking strategy: " + s.name());
                },
                () -> System.out.println("Unknown strategy: " + arg + ". Try 'rank list'."));
    }

    private static void handleConfig(SearchService searchService) {
        System.out.printf("""
                
                Current Configuration:
                  Root directory:   %s
                  Ignore patterns:  %s
                  Max results:      %d
                  Report format:    %s
                  Ranking strategy: %s
                """,
                config.getRootDirectory(),
                config.getIgnorePatterns(),
                config.getMaxResults(),
                config.getReportFormat(),
                searchService.getStrategy().name());
    }
}
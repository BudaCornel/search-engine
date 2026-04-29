package com.buda.searchengine.ranker;

import com.buda.searchengine.history.SearchHistoryRepository;
import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingStrategyTest {

    private static SearchResult result(String path, double rank) {
        FileRecord r = new FileRecord();
        r.setAbsolutePath(path);
        r.setFileName(path.substring(path.lastIndexOf('/') + 1));
        return new SearchResult(r, rank, "snippet");
    }

    @Nested @DisplayName("Strategy contracts")
    class Contracts {
        @Test void allBuiltInStrategiesReturnNonEmptyOrderBy() {
            for (RankingStrategy s : RankingStrategyRegistry.withDefaults().all()) {
                assertThat(s.orderByClause())
                        .as("%s.orderByClause()", s.name())
                        .isNotBlank();
            }
        }
        @Test void allStrategiesHaveDistinctNames() {
            List<String> names = RankingStrategyRegistry.withDefaults().all().stream()
                    .map(RankingStrategy::name).toList();
            assertThat(names).doesNotHaveDuplicates();
        }
        @Test void defaultPostProcessIsIdentityWhenWithinLimit() {
            RankingStrategy s = new RelevanceRanking();
            List<SearchResult> in = List.of(result("/a", 0.9), result("/b", 0.8));
            assertThat(s.postProcess(in, 10)).containsExactlyElementsOf(in);
        }
        @Test void defaultPostProcessTruncatesToLimit() {
            RankingStrategy s = new RelevanceRanking();
            List<SearchResult> in = List.of(
                    result("/a", 1.0), result("/b", 0.8), result("/c", 0.6));
            assertThat(s.postProcess(in, 2)).hasSize(2);
        }
    }

    @Nested @DisplayName("RankingStrategyRegistry")
    class Registry {
        @Test void findReturnsRegisteredStrategy() {
            RankingStrategyRegistry r = RankingStrategyRegistry.withDefaults();
            assertThat(r.find("hybrid")).isPresent();
            assertThat(r.find("alphabetical")).isPresent();
        }
        @Test void findReturnsEmptyForUnknown() {
            assertThat(RankingStrategyRegistry.withDefaults().find("nope")).isEmpty();
        }
        @Test void requireThrowsForUnknown() {
            RankingStrategyRegistry r = RankingStrategyRegistry.withDefaults();
            assertThatThrownBy(() -> r.require("nope"))
                    .hasMessageContaining("Unknown ranking strategy");
        }
        @Test void allListsEverythingInRegistrationOrder() {
            List<String> names = RankingStrategyRegistry.withDefaults().all()
                    .stream().map(RankingStrategy::name).toList();
            assertThat(names).contains("relevance", "hybrid", "alphabetical",
                    "date-modified", "date-accessed", "path-score", "size");
        }
        @Test void registryCanBeExtendedWithDecorators() {
            RankingStrategyRegistry r = RankingStrategyRegistry.withDefaults();
            int before = r.all().size();
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            r.register(new HistoryAwareRanking(new RelevanceRanking(), repo));
            assertThat(r.all().size()).isEqualTo(before + 1);
            assertThat(r.find("relevance+history")).isPresent();
        }
    }

    @Nested @DisplayName("Built-in ORDER BY shapes")
    class OrderByShapes {

        @Test void relevanceOrdersByRelevance() {
            assertThat(new RelevanceRanking().orderByClause())
                    .startsWith("relevance DESC");
        }
        @Test void hybridCombinesRelevanceAndPathScore() {
            assertThat(new HybridRanking().orderByClause())
                    .contains("relevance").contains("path_score").contains("DESC");
        }
        @Test void alphabeticalOrdersByName() {
            assertThat(new AlphabeticalRanking().orderByClause())
                    .startsWith("file_name ASC");
        }
        @Test void dateModifiedOrdersByModifiedAt() {
            assertThat(new DateModifiedRanking().orderByClause())
                    .contains("modified_at DESC");
        }
        @Test void dateAccessedFallsBackToModified() {
            assertThat(new DateAccessedRanking().orderByClause())
                    .contains("COALESCE(accessed_at, modified_at)");
        }
        @Test void pathScoreOrdersByPathScore() {
            assertThat(new PathScoreRanking().orderByClause())
                    .startsWith("path_score DESC");
        }
        @Test void sizeOrdersBySizeBytes() {
            assertThat(new SizeRanking().orderByClause())
                    .startsWith("size_bytes DESC");
        }
    }

    @Nested @DisplayName("HistoryAwareRanking decorator")
    class HistoryDecorator {
        @Test void widensCandidatePool() {
            RankingStrategy delegate = new RelevanceRanking();
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            HistoryAwareRanking wrapped = new HistoryAwareRanking(delegate, repo);
            assertThat(wrapped.sqlLimit(10)).isGreaterThan(10);
        }
        @Test void delegatesOrderByToInnerStrategy() {
            RankingStrategy delegate = new AlphabeticalRanking();
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            HistoryAwareRanking wrapped = new HistoryAwareRanking(delegate, repo);
            assertThat(wrapped.orderByClause()).isEqualTo(delegate.orderByClause());
        }
        @Test void nameReflectsBothBaseAndDecorator() {
            HistoryAwareRanking w = new HistoryAwareRanking(
                    new RelevanceRanking(), mock(SearchHistoryRepository.class));
            assertThat(w.name()).contains("relevance").contains("history");
        }
        @Test void promotesPreviouslyClickedResults() {
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);

            when(repo.clickFrequencies()).thenReturn(Map.of("/b", 5));

            HistoryAwareRanking ranker = new HistoryAwareRanking(new RelevanceRanking(), repo);
            List<SearchResult> raw = List.of(
                    result("/a", 0.9),
                    result("/b", 0.1),
                    result("/c", 0.5));

            List<SearchResult> ranked = ranker.postProcess(raw, 3);
            assertThat(ranked.get(0).getFileRecord().getAbsolutePath()).isEqualTo("/a");
        }
        @Test void heavilyClickedResultBeatsHigherIntrinsic() {

            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            when(repo.clickFrequencies()).thenReturn(Map.of("/b", 20));

            HistoryAwareRanking ranker = new HistoryAwareRanking(new RelevanceRanking(), repo);
            List<SearchResult> raw = List.of(
                    result("/a", 0.5),
                    result("/b", 0.1));

            List<SearchResult> ranked = ranker.postProcess(raw, 2);
            assertThat(ranked.get(0).getFileRecord().getAbsolutePath()).isEqualTo("/b");
        }
        @Test void noHistoryFallsBackToDelegate() {
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            when(repo.clickFrequencies()).thenReturn(Map.of());

            HistoryAwareRanking ranker = new HistoryAwareRanking(new RelevanceRanking(), repo);
            List<SearchResult> raw = List.of(result("/a", 0.9), result("/b", 0.1));
            List<SearchResult> ranked = ranker.postProcess(raw, 2);
            assertThat(ranked).hasSize(2);
        }
        @Test void emptyResultsAreReturnedAsIs() {
            SearchHistoryRepository repo = mock(SearchHistoryRepository.class);
            HistoryAwareRanking ranker = new HistoryAwareRanking(new RelevanceRanking(), repo);
            assertThat(ranker.postProcess(List.of(), 10)).isEmpty();
        }
    }
}
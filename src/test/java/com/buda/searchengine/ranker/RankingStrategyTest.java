package com.buda.searchengine.ranker;

import com.buda.searchengine.model.FileRecord;
import com.buda.searchengine.model.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    }

    @Nested @DisplayName("Built-in ORDER BY shapes")
    class OrderByShapes {

        @Test void relevanceOrdersByRank() {
            assertThat(new RelevanceRanking().orderByClause())
                    .startsWith("relevance DESC");
        }
        @Test void hybridCombinesRankAndPathScore() {
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
}
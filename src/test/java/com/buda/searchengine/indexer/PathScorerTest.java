package com.buda.searchengine.indexer;

import com.buda.searchengine.indexer.scoring.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PathScorerTest {

    private static BasicFileAttributes attrs(long size, Instant accessed) {
        BasicFileAttributes a = mock(BasicFileAttributes.class);
        when(a.size()).thenReturn(size);
        when(a.lastAccessTime()).thenReturn(FileTime.from(accessed));
        return a;
    }

    @Nested @DisplayName("ExtensionScore")
    class Extension {
        ExtensionScore s = new ExtensionScore();
        BasicFileAttributes a = attrs(1024, Instant.now());

        @Test void documentExtensionsScoreHigh() {
            assertThat(s.score(Paths.get("/x/foo.pdf"), a)).isGreaterThan(0.9);
            assertThat(s.score(Paths.get("/x/foo.md"),  a)).isGreaterThan(0.9);
        }
        @Test void noiseExtensionsScoreLow() {
            assertThat(s.score(Paths.get("/x/foo.tmp"), a)).isLessThan(0.2);
            assertThat(s.score(Paths.get("/x/foo.bak"), a)).isLessThan(0.2);
        }
        @Test void unknownExtensionGetsDefault() {
            assertThat(s.score(Paths.get("/x/foo.xyz"), a)).isBetween(0.3, 0.5);
        }
        @Test void noExtensionGetsLow() {
            assertThat(s.score(Paths.get("/x/Makefile"), a)).isLessThan(0.5);
        }
    }

    @Nested @DisplayName("DirectoryImportanceScore")
    class Directory {
        DirectoryImportanceScore s = new DirectoryImportanceScore();
        BasicFileAttributes a = attrs(1024, Instant.now());

        @Test void documentsDirScoresHigh() {
            assertThat(s.score(Paths.get("/home/me/Documents/foo.pdf"), a))
                    .isGreaterThan(0.9);
        }
        @Test void cacheDirScoresLow() {
            assertThat(s.score(Paths.get("/home/me/.cache/foo.txt"), a))
                    .isLessThan(0.2);
        }
        @Test void worstSegmentWins() {
            assertThat(s.score(Paths.get("/home/me/Documents/.cache/x.txt"), a))
                    .isLessThan(0.2);
        }
        @Test void neutralPathScoresMiddle() {
            assertThat(s.score(Paths.get("/home/me/random/x.txt"), a))
                    .isBetween(0.4, 0.6);
        }
    }

    @Nested @DisplayName("RecencyScore")
    class Recency {
        RecencyScore s = new RecencyScore();
        Path p = Paths.get("/x/foo.txt");

        @Test void recentFileScoresHigh() {
            assertThat(s.score(p, attrs(1024, Instant.now()))).isGreaterThan(0.9);
        }
        @Test void oldFileScoresLow() {
            Instant old = Instant.now().minus(720, ChronoUnit.DAYS);
            assertThat(s.score(p, attrs(1024, old))).isLessThan(0.05);
        }
        @Test void halfLifeFileScoresAroundHalf() {
            Instant mid = Instant.now().minus(60, ChronoUnit.DAYS);
            assertThat(s.score(p, attrs(1024, mid))).isBetween(0.45, 0.55);
        }
    }

    @Nested @DisplayName("PathLengthScore")
    class Length {
        PathLengthScore s = new PathLengthScore();
        BasicFileAttributes a = attrs(1024, Instant.now());

        @Test void shortPathScoresHigh() {
            assertThat(s.score(Paths.get("home/me/foo.txt"), a)).isEqualTo(1.0);
        }
        @Test void deepPathScoresLow() {
            Path deep = Paths.get("a/b/c/d/e/f/g/h/i/j/k/l/foo.txt");
            assertThat(s.score(deep, a)).isLessThan(0.1);
        }
        @Test void midDepthInterpolates() {
            Path mid = Paths.get("a/b/c/d/e/f/g/foo.txt");
            assertThat(s.score(mid, a)).isBetween(0.4, 0.7);
        }
    }

    @Nested @DisplayName("SizeScore")
    class Size {
        SizeScore s = new SizeScore();
        Path p = Paths.get("/x/foo.txt");
        Instant now = Instant.now();

        @Test void emptyFileScoresLow() {
            assertThat(s.score(p, attrs(0, now))).isLessThan(0.1);
        }
        @Test void hugeFileScoresLow() {
            assertThat(s.score(p, attrs(2L * 1024 * 1024 * 1024, now))).isLessThan(0.1);
        }
        @Test void idealRangeScoresMax() {
            assertThat(s.score(p, attrs(50_000, now))).isEqualTo(1.0);
        }
    }

    @Nested @DisplayName("PathScorer integration")
    class Integration {
        @Test void weightsMustSumToOne() {
            Map<ScoreSignal, Double> bad = Map.of(new ExtensionScore(), 0.5);
            assertThatThrownBy(() -> new PathScorer(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void resultsAreClampedToZeroOne() {
            PathScorer s = new PathScorer();
            BasicFileAttributes a = attrs(50_000, Instant.now());
            double score = s.score(Paths.get("/home/me/Documents/work/notes.md"), a);
            assertThat(score).isBetween(0.0, 1.0);
        }

        @Test void documentInDocumentsBeatsBakInCache() {
            PathScorer s = new PathScorer();
            Instant now = Instant.now();
            double good = s.score(
                    Paths.get("/home/me/Documents/notes.md"), attrs(50_000, now));
            double bad = s.score(
                    Paths.get("/home/me/.cache/old.bak"),
                    attrs(50_000, now.minus(400, ChronoUnit.DAYS)));
            assertThat(good).isGreaterThan(bad);
        }

        @Test void customWeightsRespected() {

            Map<ScoreSignal, Double> single = new LinkedHashMap<>();
            single.put(new ExtensionScore(), 1.0);
            PathScorer s = new PathScorer(single);

            double pdfScore = s.score(Paths.get("/x/foo.pdf"), attrs(0, Instant.now()));
            assertThat(pdfScore).isGreaterThan(0.9);
        }
    }
}

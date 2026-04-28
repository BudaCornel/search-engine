package com.buda.searchengine.ranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;


public class RankingStrategyRegistry {

    private final Map<String, RankingStrategy> byName = new LinkedHashMap<>();

    public RankingStrategyRegistry register(RankingStrategy strategy) {
        byName.put(strategy.name(), strategy);
        return this;
    }

    public Optional<RankingStrategy> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public RankingStrategy require(String name) {
        return find(name).orElseThrow(() ->
                new NoSuchElementException("Unknown ranking strategy: " + name));
    }

    public List<RankingStrategy> all() {
        return List.copyOf(byName.values());
    }

    public static RankingStrategyRegistry withDefaults() {
        return new RankingStrategyRegistry()
                .register(new RelevanceRanking())
                .register(new HybridRanking())
                .register(new AlphabeticalRanking())
                .register(new DateModifiedRanking())
                .register(new DateAccessedRanking())
                .register(new PathScoreRanking())
                .register(new SizeRanking());
    }
}

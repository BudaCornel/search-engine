package com.buda.searchengine.query;

import java.util.Arrays;
import java.util.Optional;

public enum Qualifier {
    CONTENT("content"),
    PATH("path"),
    NAME("name"),
    EXT("ext"),
    MIME("mime"),
    SIZE("size");

    private final String key;
    Qualifier(String key) { this.key = key; }
    public String key() { return key; }

    public static Optional<Qualifier> fromKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(q -> q.key.equalsIgnoreCase(key))
                .findFirst();
    }
}

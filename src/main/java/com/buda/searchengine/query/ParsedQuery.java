package com.buda.searchengine.query;

import java.util.*;

public final class ParsedQuery {
    private final Map<Qualifier, List<String>> terms;
    private final String rawQuery;

    private ParsedQuery(Map<Qualifier, List<String>> terms, String rawQuery) {
        this.terms = terms;
        this.rawQuery = rawQuery;
    }

    public String rawQuery() { return rawQuery; }
    public List<String> termsFor(Qualifier q) { return terms.getOrDefault(q, List.of()); }
    public boolean has(Qualifier q) { return !termsFor(q).isEmpty(); }
    public Set<Qualifier> qualifiers() { return terms.keySet(); }
    public boolean isEmpty() { return terms.values().stream().allMatch(List::isEmpty); }

    @Override public boolean equals(Object o) {
        return o instanceof ParsedQuery other && terms.equals(other.terms);
    }
    @Override public int hashCode() { return Objects.hash(terms); }
    @Override public String toString() { return "ParsedQuery" + terms; }

    public static final class Builder {
        private final Map<Qualifier, List<String>> terms = new EnumMap<>(Qualifier.class);
        private String rawQuery = "";

        public Builder rawQuery(String raw) { this.rawQuery = raw == null ? "" : raw; return this; }

        public Builder add(Qualifier q, String value) {
            if (value == null || value.isBlank()) return this;
            terms.computeIfAbsent(q, k -> new ArrayList<>()).add(value);
            return this;
        }

        public ParsedQuery build() {
            Map<Qualifier, List<String>> snapshot = new EnumMap<>(Qualifier.class);
            terms.forEach((k, v) -> snapshot.put(k, List.copyOf(v)));
            return new ParsedQuery(Collections.unmodifiableMap(snapshot), rawQuery);
        }
    }

    public static Builder builder() { return new Builder(); }
}

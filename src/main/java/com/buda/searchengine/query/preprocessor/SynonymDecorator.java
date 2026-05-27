package com.buda.searchengine.query.preprocessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SynonymDecorator implements QueryPreprocessor {

    private final QueryPreprocessor delegate;
    private final Map<String, List<String>> synonyms;

    public SynonymDecorator(QueryPreprocessor delegate, Map<String, List<String>> synonyms) {
        this.delegate = delegate;
        Map<String, List<String>> normalized = new HashMap<>();
        if (synonyms != null) {
            for (Map.Entry<String, List<String>> e : synonyms.entrySet()) {
                normalized.put(e.getKey().toLowerCase(Locale.ROOT), List.copyOf(e.getValue()));
            }
        }
        this.synonyms = Map.copyOf(normalized);
    }

    @Override
    public String process(String query) {
        String input = delegate.process(query);
        if (input.isEmpty() || synonyms.isEmpty()) return input;

        List<String> tokens = tokenize(input);
        StringBuilder out = new StringBuilder(input.length() + 16);

        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) out.append(' ');
            String token = tokens.get(i);
            List<String> syns = expandable(token);
            if (syns == null) {
                out.append(token);
            } else {
                out.append('(').append(token);
                for (String s : syns) out.append(" OR ").append(s);
                out.append(')');
            }
        }
        return out.toString();
    }

    private List<String> expandable(String token) {
        if (token.isEmpty()) return null;
        if (token.startsWith("\"") || token.endsWith("\"")) return null;
        if (token.contains(":")) return null;
        if (token.contains("(") || token.contains(")")) return null;
        String upper = token.toUpperCase(Locale.ROOT);
        if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT")) return null;
        return synonyms.get(token.toLowerCase(Locale.ROOT));
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                current.append(c);
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }
}

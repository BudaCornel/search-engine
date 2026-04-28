package com.buda.searchengine.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QueryParser {
    private static final Logger logger = LoggerFactory.getLogger(QueryParser.class);

    public ParsedQuery parse(String input) {
        ParsedQuery.Builder builder = ParsedQuery.builder().rawQuery(input);
        if (input == null || input.isBlank()) return builder.build();

        for (String token : tokenize(input)) {
            if (token.isBlank()) continue;
            classify(token, builder);
        }
        ParsedQuery result = builder.build();
        logger.debug("Parsed '{}' -> {}", input, result);
        return result;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; current.append(c); }
            else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
            }
            else current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private void classify(String token, ParsedQuery.Builder builder) {
        int colon = findUnquotedColon(token);

        if (colon <= 0) {
            builder.add(Qualifier.CONTENT, unquote(token));
            return;
        }

        String key = token.substring(0, colon);
        String value = colon == token.length() - 1
                ? ""
                : unquote(token.substring(colon + 1));

        Optional<Qualifier> qualifier = Qualifier.fromKey(key);
        if (qualifier.isEmpty()) {
            builder.add(Qualifier.CONTENT, unquote(token));
            return;
        }

        builder.add(qualifier.get(), value);
    }

    private int findUnquotedColon(String token) {
        boolean inQuotes = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ':' && !inQuotes) return i;
        }
        return -1;
    }

    private String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

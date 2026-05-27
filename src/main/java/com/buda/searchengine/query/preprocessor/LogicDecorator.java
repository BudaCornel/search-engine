package com.buda.searchengine.query.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class LogicDecorator implements QueryPreprocessor {

    static final int MIN_LENGTH = 3;

    private final QueryPreprocessor delegate;

    public LogicDecorator(QueryPreprocessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String process(String query) {
        String input = delegate.process(query);
        if (input.isEmpty()) return input;

        List<String> tokens = tokenize(input);
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) out.append(' ');
            out.append(wildcardize(tokens.get(i)));
        }
        return out.toString();
    }

    private String wildcardize(String token) {
        int start = 0, end = token.length();
        while (start < end && token.charAt(start)     == '(') start++;
        while (end   > start && token.charAt(end - 1) == ')') end--;
        if (start == 0 && end == token.length()) {
            return shouldWildcard(token) ? token + "*" : token;
        }
        String core = token.substring(start, end);
        if (!shouldWildcard(core)) return token;
        return token.substring(0, start) + core + "*" + token.substring(end);
    }

    private boolean shouldWildcard(String core) {
        if (core.length() <= MIN_LENGTH) return false;
        String upper = core.toUpperCase(Locale.ROOT);
        if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT")) return false;
        for (int i = 0; i < core.length(); i++) {
            char c = core.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
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

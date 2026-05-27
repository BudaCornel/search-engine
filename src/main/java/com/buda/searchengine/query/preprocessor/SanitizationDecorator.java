package com.buda.searchengine.query.preprocessor;

public class SanitizationDecorator implements QueryPreprocessor {

    private final QueryPreprocessor delegate;

    public SanitizationDecorator(QueryPreprocessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String process(String query) {
        String input = delegate.process(query);
        if (input.isEmpty()) return input;

        String s = input.replace(";", " ").replace("--", " ");

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x7F || (c < 0x20 && c != '\t')) continue;
            sb.append(c);
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }
}

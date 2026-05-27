package com.buda.searchengine.query.preprocessor;

import java.util.List;
import java.util.Map;

public interface QueryPreprocessor {


    String process(String query);

    static QueryPreprocessor standard(Map<String, List<String>> synonyms) {
        return new LogicDecorator(
                new SynonymDecorator(
                        new SanitizationDecorator(
                                new IdentityPreprocessor()),
                        synonyms));
    }
}

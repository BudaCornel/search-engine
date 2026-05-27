package com.buda.searchengine.query.preprocessor;


public class IdentityPreprocessor implements QueryPreprocessor {

    @Override
    public String process(String query) {
        return query == null ? "" : query;
    }
}

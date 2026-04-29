package com.buda.searchengine.history;

import java.util.List;

public interface QuerySuggester {

    List<String> suggest(String prefix, int limit);
}

package com.buda.searchengine.widgets;

import com.buda.searchengine.model.SearchResult;

import java.util.List;

public interface Widget {

    String name();

    boolean shouldActivate(List<SearchResult> results);

    String render(List<SearchResult> results);
}

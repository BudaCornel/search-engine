package com.buda.searchengine.history;


public class PersistentHistoryObserver implements SearchObserver {

    private final SearchHistoryRepository repository;

    public PersistentHistoryObserver(SearchHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onSearch(SearchEvent event) {
        if (event.isClick()) {
            repository.recordClick(event.clickedPath().get(), event.timestamp());
        } else {
            if (event.rawQuery() != null && !event.rawQuery().isBlank()) {
                repository.recordQuery(event.rawQuery(), event.resultCount(), event.timestamp());
            }
        }
    }
}

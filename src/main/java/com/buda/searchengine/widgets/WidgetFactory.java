package com.buda.searchengine.widgets;

import com.buda.searchengine.model.SearchResult;

import java.util.List;

public class WidgetFactory {

    private final List<Widget> widgets;

    public WidgetFactory(List<Widget> widgets) {
        this.widgets = List.copyOf(widgets);
    }

    public static WidgetFactory withDefaults() {
        return new WidgetFactory(List.of(
                new GalleryWidget(),
                new LogAnalyzerWidget(),
                new CodeStatsWidget()
        ));
    }

    public List<Widget> activatedFor(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return List.of();
        return widgets.stream()
                .filter(w -> w.shouldActivate(results))
                .toList();
    }

    public List<Widget> all() { return widgets; }
}

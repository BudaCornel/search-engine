package com.buda.searchengine.widgets;

import com.buda.searchengine.model.SearchResult;

import java.util.List;

public class GalleryWidget implements Widget {

    @Override
    public String name() { return "Gallery"; }

    @Override
    public boolean shouldActivate(List<SearchResult> results) {
        if (results.isEmpty()) return false;
        long imageCount = results.stream()
                .map(r -> r.getFileRecord().getMimeType())
                .filter(m -> m != null && m.startsWith("image/"))
                .count();
        return imageCount * 2 > results.size();
    }

    @Override
    public String render(List<SearchResult> results) {
        long imageCount = results.stream()
                .map(r -> r.getFileRecord().getMimeType())
                .filter(m -> m != null && m.startsWith("image/"))
                .count();
        return String.format(
                "Gallery - %d image(s). Refine with color:red / color:blue / etc.",
                imageCount);
    }
}

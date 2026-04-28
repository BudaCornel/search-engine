package com.buda.searchengine.indexer.scoring;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;


public interface ScoreSignal {
    double score(Path path, BasicFileAttributes attrs);

    String name();
}

package com.buda.searchengine.indexer.concurrent;

import com.buda.searchengine.model.FileRecord;

import java.nio.file.Path;


public sealed interface IndexTask {

    record Success(FileRecord record, boolean isNew) implements IndexTask {}

    record Skipped(Path path, String reason) implements IndexTask {}

    record Failed(Path path, Exception cause) implements IndexTask {}

    enum PoisonPill implements IndexTask { INSTANCE }
}

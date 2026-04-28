CREATE TABLE IF NOT EXISTS files (
                                     id              BIGSERIAL PRIMARY KEY,
                                     absolute_path   TEXT NOT NULL UNIQUE,
                                     file_name       VARCHAR(512) NOT NULL,
    extension       VARCHAR(64),
    mime_type       VARCHAR(255),
    size_bytes      BIGINT NOT NULL,
    content         TEXT,
    preview         TEXT,
    content_tsv     TSVECTOR,
    content_hash    VARCHAR(64),
    path_score      DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at      TIMESTAMP NOT NULL,
    modified_at     TIMESTAMP NOT NULL,
    accessed_at     TIMESTAMP,
    indexed_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

ALTER TABLE files ADD COLUMN IF NOT EXISTS path_score DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE files ADD COLUMN IF NOT EXISTS accessed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_files_content_tsv  ON files USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_files_absolute_path ON files (absolute_path);
CREATE INDEX IF NOT EXISTS idx_files_path_score   ON files (path_score DESC);
CREATE INDEX IF NOT EXISTS idx_files_modified_at  ON files (modified_at DESC);
CREATE INDEX IF NOT EXISTS idx_files_accessed_at  ON files (accessed_at DESC);

CREATE OR REPLACE FUNCTION files_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.content_tsv := to_tsvector('english',
        COALESCE(NEW.file_name, '') || ' ' || COALESCE(NEW.content, '')
    );
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_files_tsv ON files;
CREATE TRIGGER trg_files_tsv
    BEFORE INSERT OR UPDATE ON files
                         FOR EACH ROW EXECUTE FUNCTION files_tsv_trigger();

CREATE TABLE IF NOT EXISTS search_history (
                                              id              BIGSERIAL PRIMARY KEY,
                                              raw_query       TEXT NOT NULL,
                                              result_count    INT  NOT NULL,
                                              clicked_path    TEXT,
                                              timestamp       TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_search_history_timestamp ON search_history (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_search_history_query     ON search_history (raw_query);
CREATE INDEX IF NOT EXISTS idx_search_history_clicked   ON search_history (clicked_path) WHERE clicked_path IS NOT NULL;

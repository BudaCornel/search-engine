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
    created_at      TIMESTAMP NOT NULL,
    modified_at     TIMESTAMP NOT NULL,
    indexed_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_files_content_tsv ON files USING GIN (content_tsv);

CREATE INDEX IF NOT EXISTS idx_files_absolute_path ON files (absolute_path);

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
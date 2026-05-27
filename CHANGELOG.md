# Changelog

All notable changes to LocalSearch are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.0.0] - 2026-05-27

Iteration 3 — multimodal search, concurrent indexing, query preprocessing,
context-aware UI.

### Added
- **Multimodal Search** — image files are now indexed and their dominant
  color extracted, queryable via the new `color:` qualifier
  (`color:red`, `color:blue`, …).
- `FileProcessor` Strategy with `TextFileProcessor` and
  `ImageFileProcessor` (HSB-bucketed color classifier), dispatched by a
  `FileProcessorRegistry`.
- New schema column `files.dominant_color` (VARCHAR(32)) with a dedicated
  index.
- **Producer-Consumer indexing pipeline** — N reader threads parse files
  in parallel through the strategy registry and feed a single writer
  thread via a bounded `BlockingQueue<IndexTask>`; writer batches commits
  through `FileRepository.upsertBatch`.
- `pipelineReaders` setting in `config.yml`.
- **Decorator-pattern query preprocessor** with Identity, Sanitization,
  Synonym, and Logic decorators; configurable synonym map under
  `synonyms:` in `config.yml`.
- **Context-aware Widgets** dispatched by `WidgetFactory`: `Gallery`,
  `Log Analyzer`, `Code Stats`. Each one decides relevance from the
  current result set and renders an inline suggestion.
- `.githooks/pre-commit` running `mvn compile` and warning on common
  debug leftovers (`System.out.println`, `TODO`, `FIXME`) in staged Java.

### Changed
- `IndexBuilder` no longer indexes files sequentially — it drives the
  `IndexingPipeline` for full and incremental runs.
- `SearchService` applies the preprocessor chain before routing; the
  boolean parser is selected based on the rewritten query, so synonym
  expansions naturally promote a plain query to boolean.
- `SqlVisitor` exhaustive switch now covers the `COLOR` qualifier so
  `color:red AND ext:png`-style boolean queries work end-to-end.
- Image extensions removed from `ignorePatterns` so they reach the
  indexer.

## [2.0.0] - 2026-04-09

Iteration 2 — observability and richer query language.

### Added
- **Observer pattern** for search events: `PersistentHistoryObserver`
  records every query to PostgreSQL, `InMemorySuggestionObserver` powers
  prefix-based query suggestions.
- **Boolean query language** with hand-written `Lexer`, recursive-descent
  `BooleanParser`, AST (`BooleanNode`), and a `SqlVisitor` that emits
  parameterised `WHERE` fragments.
- `size:` qualifier with comparison operators (`size:>1MB`,
  `size:<500KB`).
- **Decorator pattern** for ranking: `HistoryAwareRanking` boosts results
  the user has clicked before, on top of any base strategy.
- `RankingStrategyRegistry` with `relevance`, `path-score`,
  `date-modified`, `date-accessed`, `size`, and `alphabetical` strategies.

## [1.0.0] - 2026-03-11

Iteration 1 — initial release.

### Added
- Recursive file crawler with configurable ignore patterns.
- Text content extraction, preview generation, SHA-256 hashing.
- PostgreSQL persistence with GIN-indexed `tsvector` full-text search.
- `IndexBuilder` orchestrating full and incremental indexing
  (SHA-256 change detection against the existing index).
- `QueryParser` supporting `content:`, `path:`, `name:`, `ext:`, `mime:`
  qualifiers and quoted phrases.
- `SearchService` driving `websearch_to_tsquery` with `ts_rank` and
  `ts_headline` snippets.
- C4-model `ARCHITECTURE.md` documenting the system design.

[Unreleased]: https://github.com/BudaCornel/search-engine/compare/v3.0.0...HEAD
[3.0.0]:      https://github.com/BudaCornel/search-engine/compare/v2.0.0...v3.0.0
[2.0.0]:      https://github.com/BudaCornel/search-engine/compare/v1.0.0...v2.0.0
[1.0.0]:      https://github.com/BudaCornel/search-engine/releases/tag/v1.0.0

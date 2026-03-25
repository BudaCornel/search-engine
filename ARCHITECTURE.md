# Architecture


This document describes the architecture of **LocalSearch**, a local file search engine built with Java and PostgreSQL. It follows [Simon Brown's C4 model](https://c4model.com/), progressing from the broadest system view down to code-level detail.

---

## Table of Contents


1. [Level 1 - System Context](#level-1---system-context)
2. [Level 2 - Containers](#level-2---containers)
3. [Level 3 - Components](#level-3---components)
4. [Level 4 - Code](#level-4---code)


---

## Level 1 - System Context

The system context shows LocalSearch as a single box and its relationship with external actors.

**Actors:**
- **User** - searches for files on their local machine via a CLI interface.

**External Systems:**
- **Local Filesystem** - the source of files to be crawled, read, and indexed.

```plantuml
@startuml C4_Context
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml

title System Context Diagram - LocalSearch

Person(user, "User", "Searches for local files by content, filename, or metadata.")
System(localSearch, "LocalSearch", "Crawls, indexes, and searches local files using full-text search.")
System_Ext(filesystem, "Local Filesystem", "Source of files: documents, media, binaries, etc.")

Rel(user, localSearch, "Queries and configures", "CLI")
Rel(localSearch, filesystem, "Reads files and metadata", "java.nio / java.io")

@enduml
```
---

## Level 2 - Containers

Containers are the separately deployable/runnable units that make up LocalSearch.

| Container | Technology | Responsibility |
|-----------|-----------|----------------|
| **CLI Application** | Java (stdin/stdout) | Accepts user queries, displays search results with previews |
| **Indexing Engine** | Java | Crawls the filesystem, extracts content and metadata, populates the database |
| **Query Engine** | Java | Parses queries, executes full-text search against the database, formats results |
| **Database** | PostgreSQL | Stores indexed file records, content, metadata; provides full-text search via `tsvector` |
```plantuml
@startuml C4_Containers
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml

title Container Diagram - LocalSearch

Person(user, "User")

System_Boundary(ls, "LocalSearch") {
    Container(cli, "CLI Application", "Java", "User-facing interface: accepts queries, displays results with file previews.")
    Container(indexer, "Indexing Engine", "Java", "Crawls the filesystem, extracts text content and metadata, writes to DB.")
    Container(queryEngine, "Query Engine", "Java", "Parses search queries, runs full-text search, ranks and formats results.")
    ContainerDb(db, "Database", "PostgreSQL", "Stores file records, content, metadata, and full-text search indexes (tsvector).")
}

System_Ext(fs, "Local Filesystem")

Rel(user, cli, "Types queries / configures", "CLI")
Rel(cli, queryEngine, "Delegates search requests")
Rel(cli, indexer, "Triggers indexing")
Rel(indexer, fs, "Reads files and metadata", "java.nio")
Rel(indexer, db, "Inserts/updates file records", "JDBC")
Rel(queryEngine, db, "Executes full-text search queries", "JDBC")

@enduml
```




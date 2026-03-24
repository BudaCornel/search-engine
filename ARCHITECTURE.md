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



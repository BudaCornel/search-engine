package com.buda.searchengine.query.booleanq;

import com.buda.searchengine.query.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlVisitorTest {

    private final BooleanParser parser = new BooleanParser();
    private final SqlVisitor visitor = new SqlVisitor();

    private SqlFragment toSql(String query) {
        return parser.parse(query).accept(visitor);
    }

    @Nested @DisplayName("Predicate translation")
    class Predicates {
        @Test void contentBecomesTsvectorMatch() {
            SqlFragment f = toSql("content:database");
            assertThat(f.whereClause()).contains("content_tsv @@");
            assertThat(f.params()).containsExactly("database");
        }
        @Test void pathBecomesIlikeWithWildcards() {
            SqlFragment f = toSql("path:src");
            assertThat(f.whereClause()).contains("absolute_path ILIKE");
            assertThat(f.params()).containsExactly("%src%");
        }
        @Test void nameBecomesIlikeWithWildcards() {
            SqlFragment f = toSql("name:Auth");
            assertThat(f.whereClause()).contains("file_name ILIKE");
            assertThat(f.params()).containsExactly("%Auth%");
        }
        @Test void extBecomesCaseInsensitiveEquality() {
            SqlFragment f = toSql("ext:java");
            assertThat(f.whereClause()).contains("LOWER(extension) = LOWER(?)");
            assertThat(f.params()).containsExactly("java");
        }
        @Test void mimeBecomesExactMatch() {
            SqlFragment f = toSql("mime:text/plain");
            assertThat(f.whereClause()).contains("mime_type = ?");
            assertThat(f.params()).containsExactly("text/plain");
        }
    }

    @Nested @DisplayName("Boolean combinators")
    class Combinators {
        @Test void andCombinesWithParens() {
            SqlFragment f = toSql("path:a AND path:b");
            assertThat(f.whereClause()).contains(" AND ");
            assertThat(f.whereClause()).startsWith("(");
            assertThat(f.params()).hasSize(2);
        }
        @Test void orCombinesWithParens() {
            SqlFragment f = toSql("path:a OR path:b");
            assertThat(f.whereClause()).contains(" OR ");
            assertThat(f.params()).hasSize(2);
        }
        @Test void notWraps() {
            SqlFragment f = toSql("NOT path:test");
            assertThat(f.whereClause()).startsWith("NOT (");
            assertThat(f.params()).containsExactly("%test%");
        }
        @Test void complexNestedQuery() {
            SqlFragment f = toSql("(content:db OR content:python) AND ext:java AND NOT path:test");
            assertThat(f.whereClause()).contains(" OR ").contains(" AND ").contains("NOT ");
            assertThat(f.params()).hasSize(4);
        }
    }

    @Nested @DisplayName("Size predicates")
    class Size {
        @Test void sizeGreaterThanWithMb() {
            SqlFragment f = toSql("size:>1MB");
            assertThat(f.whereClause()).contains("size_bytes >");
            assertThat(f.params()).containsExactly(1024L * 1024L);
        }
        @Test void sizeLessThanOrEqualWithKb() {
            SqlFragment f = toSql("size:<=100KB");
            assertThat(f.whereClause()).contains("size_bytes <=");
            assertThat(f.params()).containsExactly(100L * 1024L);
        }
        @Test void plainSizeNumber() {
            SqlFragment f = toSql("size:1024");
            assertThat(f.whereClause()).contains("size_bytes =");
            assertThat(f.params()).containsExactly(1024L);
        }
        @Test void sizeMixedWithBoolean() {
            SqlFragment f = toSql("size:>1MB AND ext:pdf");
            assertThat(f.whereClause())
                    .contains("size_bytes >")
                    .contains("LOWER(extension)");
            assertThat(f.params()).hasSize(2);
            assertThat(f.params().get(0)).isEqualTo(1024L * 1024L);
            assertThat(f.params().get(1)).isEqualTo("pdf");
        }
    }

    @Nested @DisplayName("firstContentTerm helper")
    class FirstContentTerm {
        @Test void returnsContentFromSimplePredicate() {
            assertThat(SqlVisitor.firstContentTerm(parser.parse("content:database")))
                    .isEqualTo("database");
        }
        @Test void returnsLeftmostContentInTree() {
            assertThat(SqlVisitor.firstContentTerm(
                    parser.parse("(content:foo OR content:bar) AND path:src")))
                    .isEqualTo("foo");
        }
        @Test void returnsNullWhenNoContent() {
            assertThat(SqlVisitor.firstContentTerm(parser.parse("ext:java AND path:src")))
                    .isNull();
        }
        @Test void skipsNegatedContent() {
            assertThat(SqlVisitor.firstContentTerm(parser.parse("NOT content:foo")))
                    .isNull();
        }
    }

    @Nested @DisplayName("SQL injection safety")
    class Injection {
        @Test void valuesArePassedAsParameters() {

            SqlFragment f = toSql("name:\"foo'; DROP TABLE files; --\"");
            assertThat(f.whereClause()).doesNotContain("DROP");
            assertThat(f.whereClause()).doesNotContain("'");
            assertThat(f.params().get(0)).isEqualTo("%foo'; DROP TABLE files; --%");
        }
    }
}

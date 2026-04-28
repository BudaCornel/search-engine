package com.buda.searchengine.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class QueryParserTest {
    private final QueryParser parser = new QueryParser();

    @Nested @DisplayName("Empty and blank inputs")
    class EmptyInputs {
        @ParameterizedTest @ValueSource(strings = {"", " ", "\t", "   \n  "})
        void blankInputProducesEmpty(String input) {
            assertThat(parser.parse(input).isEmpty()).isTrue();
        }
        @Test void nullInputProducesEmpty() {
            ParsedQuery r = parser.parse(null);
            assertThat(r.isEmpty()).isTrue();
            assertThat(r.rawQuery()).isEmpty();
        }
    }

    @Nested @DisplayName("Bare tokens")
    class BareTokens {
        @Test void singleBareToken() {
            assertThat(parser.parse("database").termsFor(Qualifier.CONTENT))
                    .containsExactly("database");
        }
        @Test void multipleBareTokens() {
            assertThat(parser.parse("foo bar baz").termsFor(Qualifier.CONTENT))
                    .containsExactly("foo", "bar", "baz");
        }
    }

    @Nested @DisplayName("Qualified tokens")
    class QualifiedTokens {
        @Test void pathQualifier() {
            assertThat(parser.parse("path:src/main").termsFor(Qualifier.PATH))
                    .containsExactly("src/main");
        }
        @Test void contentQualifier() {
            assertThat(parser.parse("content:database").termsFor(Qualifier.CONTENT))
                    .containsExactly("database");
        }
        @Test void extQualifier() {
            assertThat(parser.parse("ext:java").termsFor(Qualifier.EXT))
                    .containsExactly("java");
        }
        @Test void nameQualifier() {
            assertThat(parser.parse("name:Auth").termsFor(Qualifier.NAME))
                    .containsExactly("Auth");
        }
        @Test void mimeQualifier() {
            assertThat(parser.parse("mime:text/plain").termsFor(Qualifier.MIME))
                    .containsExactly("text/plain");
        }
        @Test void caseInsensitive() {
            ParsedQuery r = parser.parse("PATH:src CONTENT:db");
            assertThat(r.termsFor(Qualifier.PATH)).containsExactly("src");
            assertThat(r.termsFor(Qualifier.CONTENT)).containsExactly("db");
        }
    }

    @Nested @DisplayName("Combinations")
    class Combinations {
        @Test void multipleQualifiers() {
            ParsedQuery r = parser.parse("path:src content:database ext:java");
            assertThat(r.termsFor(Qualifier.PATH)).containsExactly("src");
            assertThat(r.termsFor(Qualifier.CONTENT)).containsExactly("database");
            assertThat(r.termsFor(Qualifier.EXT)).containsExactly("java");
        }
        @Test void duplicateQualifiersAreAnded() {
            assertThat(parser.parse("path:src path:test path:java").termsFor(Qualifier.PATH))
                    .containsExactly("src", "test", "java");
        }
        @Test void permutationInvariance() {
            ParsedQuery a = parser.parse("path:src content:foo ext:java");
            ParsedQuery b = parser.parse("ext:java content:foo path:src");
            ParsedQuery c = parser.parse("content:foo ext:java path:src");
            assertThat(a).isEqualTo(b).isEqualTo(c);
        }
        @Test void mixOfBareAndQualified() {
            ParsedQuery r = parser.parse("database path:src");
            assertThat(r.termsFor(Qualifier.CONTENT)).containsExactly("database");
            assertThat(r.termsFor(Qualifier.PATH)).containsExactly("src");
        }
    }

    @Nested @DisplayName("Quoted values")
    class QuotedValues {
        @Test void quotedBareTokenIsAtomic() {
            assertThat(parser.parse("\"hello world\"").termsFor(Qualifier.CONTENT))
                    .containsExactly("hello world");
        }
        @Test void quotedQualifiedValue() {
            assertThat(parser.parse("path:\"my docs/2026 work\"").termsFor(Qualifier.PATH))
                    .containsExactly("my docs/2026 work");
        }
        @Test void colonInsideQuotesIsLiteral() {
            assertThat(parser.parse("\"http://example.com\"").termsFor(Qualifier.CONTENT))
                    .containsExactly("http://example.com");
        }
    }

    @Nested @DisplayName("Edge cases")
    class EdgeCases {
        @Test void unknownQualifierFallsBackToContent() {
            assertThat(parser.parse("foo:bar").termsFor(Qualifier.CONTENT))
                    .containsExactly("foo:bar");
        }
        @Test void emptyValueIsDiscarded() {
            ParsedQuery r = parser.parse("path: content:db");
            assertThat(r.has(Qualifier.PATH)).isFalse();
            assertThat(r.termsFor(Qualifier.CONTENT)).containsExactly("db");
        }
        @Test void leadingColonFallsBackToContent() {
            assertThat(parser.parse(":foo").termsFor(Qualifier.CONTENT))
                    .containsExactly(":foo");
        }
        @Test void onlyColonFallsBackToContent() {
            assertThat(parser.parse(":").termsFor(Qualifier.CONTENT))
                    .containsExactly(":");
        }
        @Test void firstColonWins() {
            assertThat(parser.parse("path:a:b:c").termsFor(Qualifier.PATH))
                    .containsExactly("a:b:c");
        }
        @Test void excessWhitespaceIgnored() {
            ParsedQuery r = parser.parse("   path:src     content:db   ");
            assertThat(r.termsFor(Qualifier.PATH)).containsExactly("src");
            assertThat(r.termsFor(Qualifier.CONTENT)).containsExactly("db");
        }
    }

    @Nested @DisplayName("Metadata")
    class Metadata {
        @Test void rawQueryPreserved() {
            assertThat(parser.parse("path:src content:foo").rawQuery())
                    .isEqualTo("path:src content:foo");
        }
        @Test void equalityIgnoresRawInput() {
            assertThat(parser.parse("path:src content:foo"))
                    .isEqualTo(parser.parse("content:foo path:src"));
        }
    }
}

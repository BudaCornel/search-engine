package com.buda.searchengine.query.booleanq;

import com.buda.searchengine.query.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanParserTest {

    private final BooleanParser parser = new BooleanParser();

    @Nested @DisplayName("Predicates")
    class Predicates {
        @Test void simpleQualifier() {
            BooleanNode tree = parser.parse("path:src");
            assertThat(tree).isInstanceOf(BooleanNode.Predicate.class);
            BooleanNode.Predicate p = (BooleanNode.Predicate) tree;
            assertThat(p.qualifier()).isEqualTo(Qualifier.PATH);
            assertThat(p.value()).isEqualTo("src");
            assertThat(p.op()).isEqualTo(ComparisonOp.EQ);
        }
        @Test void bareWordIsContent() {
            BooleanNode tree = parser.parse("database");
            BooleanNode.Predicate p = (BooleanNode.Predicate) tree;
            assertThat(p.qualifier()).isEqualTo(Qualifier.CONTENT);
            assertThat(p.value()).isEqualTo("database");
        }
        @Test void quotedIsContent() {
            BooleanNode tree = parser.parse("\"hello world\"");
            BooleanNode.Predicate p = (BooleanNode.Predicate) tree;
            assertThat(p.qualifier()).isEqualTo(Qualifier.CONTENT);
            assertThat(p.value()).isEqualTo("hello world");
        }
        @Test void unknownQualifierFallsBackToContent() {
            BooleanNode tree = parser.parse("foo:bar");
            BooleanNode.Predicate p = (BooleanNode.Predicate) tree;
            assertThat(p.qualifier()).isEqualTo(Qualifier.CONTENT);
            assertThat(p.value()).isEqualTo("foo:bar");
        }
        @Test void allKnownQualifiersResolve() {
            assertThat(((BooleanNode.Predicate) parser.parse("name:Auth")).qualifier())
                    .isEqualTo(Qualifier.NAME);
            assertThat(((BooleanNode.Predicate) parser.parse("ext:java")).qualifier())
                    .isEqualTo(Qualifier.EXT);
            assertThat(((BooleanNode.Predicate) parser.parse("mime:text/plain")).qualifier())
                    .isEqualTo(Qualifier.MIME);
        }
    }

    @Nested @DisplayName("Boolean operators")
    class Booleans {
        @Test void explicitAnd() {
            assertThat(parser.parse("path:src AND content:foo")).isInstanceOf(BooleanNode.And.class);
        }
        @Test void implicitAndIsAlsoAnd() {
            assertThat(parser.parse("path:src content:foo")).isInstanceOf(BooleanNode.And.class);
        }
        @Test void or() {
            assertThat(parser.parse("path:src OR path:test")).isInstanceOf(BooleanNode.Or.class);
        }
        @Test void notIsPrefixOnly() {
            assertThat(parser.parse("NOT path:test")).isInstanceOf(BooleanNode.Not.class);
        }
        @Test void doubleNot() {
            BooleanNode tree = parser.parse("NOT NOT path:test");
            assertThat(tree).isInstanceOf(BooleanNode.Not.class);
            BooleanNode.Not outer = (BooleanNode.Not) tree;
            assertThat(outer.inner()).isInstanceOf(BooleanNode.Not.class);
        }
    }

    @Nested @DisplayName("Operator precedence")
    class Precedence {
        @Test void andBindsTighterThanOr() {
            BooleanNode tree = parser.parse("path:a OR path:b AND path:c");
            assertThat(tree).isInstanceOf(BooleanNode.Or.class);
            BooleanNode.Or root = (BooleanNode.Or) tree;
            assertThat(root.left()).isInstanceOf(BooleanNode.Predicate.class);
            assertThat(root.right()).isInstanceOf(BooleanNode.And.class);
        }
        @Test void notBindsTighterThanAnd() {
            BooleanNode tree = parser.parse("NOT path:a AND path:b");
            assertThat(tree).isInstanceOf(BooleanNode.And.class);
            BooleanNode.And root = (BooleanNode.And) tree;
            assertThat(root.left()).isInstanceOf(BooleanNode.Not.class);
        }
        @Test void parensOverridePrecedence() {
            BooleanNode tree = parser.parse("(path:a OR path:b) AND path:c");
            assertThat(tree).isInstanceOf(BooleanNode.And.class);
            BooleanNode.And root = (BooleanNode.And) tree;
            assertThat(root.left()).isInstanceOf(BooleanNode.Or.class);
            assertThat(root.right()).isInstanceOf(BooleanNode.Predicate.class);
        }
        @Test void leftAssociativityForAnd() {
            BooleanNode tree = parser.parse("path:a AND path:b AND path:c");
            assertThat(tree).isInstanceOf(BooleanNode.And.class);
            assertThat(((BooleanNode.And) tree).left()).isInstanceOf(BooleanNode.And.class);
        }
    }

    @Nested @DisplayName("Size qualifier with comparators")
    class SizeQualifier {
        @Test void greaterThan() {
            BooleanNode.Predicate p = (BooleanNode.Predicate) parser.parse("size:>1MB");
            assertThat(p.qualifier()).isEqualTo(Qualifier.SIZE);
            assertThat(p.op()).isEqualTo(ComparisonOp.GT);
            assertThat(p.value()).isEqualTo("1MB");
        }
        @Test void lessThanOrEqual() {
            BooleanNode.Predicate p = (BooleanNode.Predicate) parser.parse("size:<=100K");
            assertThat(p.op()).isEqualTo(ComparisonOp.LTE);
        }
        @Test void implicitEquals() {
            BooleanNode.Predicate p = (BooleanNode.Predicate) parser.parse("size:1024");
            assertThat(p.op()).isEqualTo(ComparisonOp.EQ);
        }
        @Test void sizeInComplexQuery() {
            BooleanNode tree = parser.parse("size:>1MB AND ext:pdf");
            assertThat(tree).isInstanceOf(BooleanNode.And.class);
        }
    }

    @Nested @DisplayName("Malformed input")
    class Malformed {
        @Test void emptyStringThrows() {
            assertThatThrownBy(() -> parser.parse(""))
                    .isInstanceOf(BooleanParser.ParseException.class);
        }
        @Test void unclosedParenThrows() {
            assertThatThrownBy(() -> parser.parse("(path:a"))
                    .isInstanceOf(BooleanParser.ParseException.class);
        }
        @Test void trailingAndThrows() {
            assertThatThrownBy(() -> parser.parse("path:a AND"))
                    .isInstanceOf(BooleanParser.ParseException.class);
        }
        @Test void leadingOrThrows() {
            assertThatThrownBy(() -> parser.parse("OR path:a"))
                    .isInstanceOf(BooleanParser.ParseException.class);
        }
        @Test void colonWithNoValueThrows() {
            assertThatThrownBy(() -> parser.parse("path:"))
                    .isInstanceOf(BooleanParser.ParseException.class);
        }
        @Test void parseExceptionCarriesPosition() {
            try {
                parser.parse("path:a AND");
                org.junit.jupiter.api.Assertions.fail("Expected ParseException");
            } catch (BooleanParser.ParseException e) {
                assertThat(e.position).isGreaterThan(0);
            }
        }
    }
}

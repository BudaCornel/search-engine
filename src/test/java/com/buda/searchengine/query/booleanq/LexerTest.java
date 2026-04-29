package com.buda.searchengine.query.booleanq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LexerTest {

    private final Lexer lexer = new Lexer();

    @Nested @DisplayName("Whitespace and EOF")
    class WhitespaceAndEof {
        @Test void emptyStringYieldsOnlyEof() {
            List<Token> tokens = lexer.tokenize("");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(Token.Type.EOF);
        }
        @Test void whitespaceOnlyYieldsOnlyEof() {
            assertThat(lexer.tokenize("   \t\n  ").get(0).type()).isEqualTo(Token.Type.EOF);
        }
        @Test void nullInputIsHandledGracefully() {
            assertThat(lexer.tokenize(null)).hasSize(1);
        }
        @Test void everyTokenStreamEndsWithEof() {
            List<Token> tokens = lexer.tokenize("foo bar");
            assertThat(tokens.get(tokens.size() - 1).type()).isEqualTo(Token.Type.EOF);
        }
    }

    @Nested @DisplayName("Keywords")
    class Keywords {
        @Test void andOrNotAreKeywords() {
            List<Token> tokens = lexer.tokenize("a AND b OR NOT c");
            assertThat(tokens.get(1).type()).isEqualTo(Token.Type.AND);
            assertThat(tokens.get(3).type()).isEqualTo(Token.Type.OR);
            assertThat(tokens.get(4).type()).isEqualTo(Token.Type.NOT);
        }
        @Test void keywordsAreCaseInsensitive() {
            assertThat(lexer.tokenize("a and b").get(1).type()).isEqualTo(Token.Type.AND);
            assertThat(lexer.tokenize("a Or b").get(1).type()).isEqualTo(Token.Type.OR);
            assertThat(lexer.tokenize("nOt a").get(0).type()).isEqualTo(Token.Type.NOT);
        }
        @Test void keywordSubstringsAreNotKeywords() {
            assertThat(lexer.tokenize("andante").get(0).type()).isEqualTo(Token.Type.WORD);
            assertThat(lexer.tokenize("notebook").get(0).type()).isEqualTo(Token.Type.WORD);
            assertThat(lexer.tokenize("organize").get(0).type()).isEqualTo(Token.Type.WORD);
        }
    }

    @Nested @DisplayName("Punctuation and operators")
    class PunctuationAndOperators {
        @Test void parensAreSeparateTokens() {
            List<Token> tokens = lexer.tokenize("(a)");
            assertThat(tokens.get(0).type()).isEqualTo(Token.Type.LPAREN);
            assertThat(tokens.get(2).type()).isEqualTo(Token.Type.RPAREN);
        }
        @Test void colonIsSeparateToken() {
            List<Token> tokens = lexer.tokenize("path:src");
            assertThat(tokens.get(0).type()).isEqualTo(Token.Type.WORD);
            assertThat(tokens.get(1).type()).isEqualTo(Token.Type.COLON);
            assertThat(tokens.get(2).type()).isEqualTo(Token.Type.WORD);
        }
        @Test void greaterThanAndLessThan() {
            assertThat(lexer.tokenize(">").get(0).type()).isEqualTo(Token.Type.GT);
            assertThat(lexer.tokenize("<").get(0).type()).isEqualTo(Token.Type.LT);
        }
        @Test void compoundComparisonOperators() {
            assertThat(lexer.tokenize(">=").get(0).type()).isEqualTo(Token.Type.GTE);
            assertThat(lexer.tokenize("<=").get(0).type()).isEqualTo(Token.Type.LTE);
        }
        @Test void equalsIsItsOwnToken() {
            assertThat(lexer.tokenize("=").get(0).type()).isEqualTo(Token.Type.EQUALS);
        }
    }

    @Nested @DisplayName("Quoted strings")
    class Quoted {
        @Test void quotedIsSingleTokenWithoutQuotes() {
            Token t = lexer.tokenize("\"hello world\"").get(0);
            assertThat(t.type()).isEqualTo(Token.Type.QUOTED);
            assertThat(t.value()).isEqualTo("hello world");
        }
        @Test void unterminatedQuoteEndsAtInputBoundary() {
            Token t = lexer.tokenize("\"open").get(0);
            assertThat(t.type()).isEqualTo(Token.Type.QUOTED);
            assertThat(t.value()).isEqualTo("open");
        }
    }

    @Nested @DisplayName("Positions")
    class Positions {
        @Test void positionsArePreservedInOriginal() {
            List<Token> tokens = lexer.tokenize("a AND b");
            assertThat(tokens.get(0).position()).isEqualTo(0);
            assertThat(tokens.get(1).position()).isEqualTo(2);
            assertThat(tokens.get(2).position()).isEqualTo(6);
        }
    }
}

package com.buda.searchengine.query.booleanq;

import com.buda.searchengine.query.Qualifier;

import java.util.List;
import java.util.Optional;


public final class BooleanParser {

    private final Lexer lexer = new Lexer();
    private List<Token> tokens;
    private int pos;

    public BooleanNode parse(String input) {
        this.tokens = lexer.tokenize(input);
        this.pos = 0;
        if (current().type() == Token.Type.EOF) {
            throw new ParseException("Empty query", 0);
        }
        BooleanNode root = parseOr();
        expect(Token.Type.EOF);
        return root;
    }


    private BooleanNode parseOr() {
        BooleanNode left = parseAnd();
        while (current().type() == Token.Type.OR) {
            advance();
            BooleanNode right = parseAnd();
            left = new BooleanNode.Or(left, right);
        }
        return left;
    }

    private BooleanNode parseAnd() {
        BooleanNode left = parseNot();
        while (true) {
            if (current().type() == Token.Type.AND) {
                advance();
                BooleanNode right = parseNot();
                left = new BooleanNode.And(left, right);
            } else if (canStartAtom()) {
                BooleanNode right = parseNot();
                left = new BooleanNode.And(left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private BooleanNode parseNot() {
        if (current().type() == Token.Type.NOT) {
            advance();
            return new BooleanNode.Not(parseNot());
        }
        return parseAtom();
    }

    private BooleanNode parseAtom() {
        if (current().type() == Token.Type.LPAREN) {
            advance();
            BooleanNode inner = parseOr();
            expect(Token.Type.RPAREN);
            return inner;
        }
        return parsePredicate();
    }

    private BooleanNode parsePredicate() {
        Token tok = current();
        if (tok.type() == Token.Type.QUOTED) {
            advance();
            return new BooleanNode.Predicate(Qualifier.CONTENT, tok.value());
        }
        if (tok.type() != Token.Type.WORD) {
            throw new ParseException("Expected qualifier or value, got " + tok.type(), tok.position());
        }
        advance();

        if (current().type() != Token.Type.COLON) {
            return new BooleanNode.Predicate(Qualifier.CONTENT, tok.value());
        }
        advance();

        Optional<Qualifier> qualOpt = Qualifier.fromKey(tok.value());
        if (qualOpt.isEmpty()) {

            String value = consumeValue();
            return new BooleanNode.Predicate(Qualifier.CONTENT, tok.value() + ":" + value);
        }
        Qualifier qualifier = qualOpt.get();

        ComparisonOp op = ComparisonOp.EQ;
        if (qualifier == Qualifier.SIZE && isComparator(current().type())) {
            op = ComparisonOp.fromTokenType(current().type());
            advance();
        }

        String value = consumeValue();
        return new BooleanNode.Predicate(qualifier, op, value);
    }

    private boolean canStartAtom() {
        Token.Type t = current().type();
        return t == Token.Type.LPAREN
                || t == Token.Type.NOT
                || t == Token.Type.WORD
                || t == Token.Type.QUOTED;
    }

    private boolean isComparator(Token.Type t) {
        return t == Token.Type.GT || t == Token.Type.LT
                || t == Token.Type.GTE || t == Token.Type.LTE
                || t == Token.Type.EQUALS;
    }

    private String consumeValue() {
        Token tok = current();

        if (tok.type() == Token.Type.WORD
                || tok.type() == Token.Type.QUOTED
                || tok.type() == Token.Type.AND
                || tok.type() == Token.Type.OR
                || tok.type() == Token.Type.NOT) {
            advance();
            return tok.value();
        }
        throw new ParseException("Expected value after ':', got " + tok.type(), tok.position());
    }

    private Token current() { return tokens.get(pos); }

    private void advance() {
        if (pos < tokens.size() - 1) pos++;
    }

    private void expect(Token.Type type) {
        Token tok = current();
        if (tok.type() != type) {
            throw new ParseException("Expected " + type + ", got " + tok.type(), tok.position());
        }
        advance();
    }

    public static class ParseException extends RuntimeException {
        public final int position;
        public ParseException(String message, int position) {
            super(message + " at position " + position);
            this.position = position;
        }
    }
}

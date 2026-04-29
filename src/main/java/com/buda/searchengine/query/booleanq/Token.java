package com.buda.searchengine.query.booleanq;


public record Token(Type type, String value, int position) {

    public enum Type {
        AND, OR, NOT,
        LPAREN, RPAREN,
        COLON,
        GT, LT, GTE, LTE, EQUALS,
        WORD,
        QUOTED,
        EOF
    }

    @Override
    public String toString() {
        return type + "(" + value + ")@" + position;
    }
}

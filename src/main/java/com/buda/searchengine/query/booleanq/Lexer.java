package com.buda.searchengine.query.booleanq;

import java.util.ArrayList;
import java.util.List;


public final class Lexer {

    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        if (input == null) {
            tokens.add(new Token(Token.Type.EOF, "", 0));
            return tokens;
        }

        int pos = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);

            if (Character.isWhitespace(c)) { pos++; continue; }

            int start = pos;
            switch (c) {
                case '(' -> { tokens.add(new Token(Token.Type.LPAREN, "(", start)); pos++; }
                case ')' -> { tokens.add(new Token(Token.Type.RPAREN, ")", start)); pos++; }
                case ':' -> { tokens.add(new Token(Token.Type.COLON, ":", start)); pos++; }
                case '>' -> {
                    if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                        tokens.add(new Token(Token.Type.GTE, ">=", start)); pos += 2;
                    } else {
                        tokens.add(new Token(Token.Type.GT, ">", start)); pos++;
                    }
                }
                case '<' -> {
                    if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                        tokens.add(new Token(Token.Type.LTE, "<=", start)); pos += 2;
                    } else {
                        tokens.add(new Token(Token.Type.LT, "<", start)); pos++;
                    }
                }
                case '=' -> { tokens.add(new Token(Token.Type.EQUALS, "=", start)); pos++; }
                case '"' -> {
                    int endQuote = input.indexOf('"', pos + 1);
                    String content = endQuote < 0
                            ? input.substring(pos + 1)
                            : input.substring(pos + 1, endQuote);
                    tokens.add(new Token(Token.Type.QUOTED, content, start));
                    pos = endQuote < 0 ? input.length() : endQuote + 1;
                }
                default -> {
                    int wordEnd = pos;
                    while (wordEnd < input.length() && !isStructural(input.charAt(wordEnd))) {
                        wordEnd++;
                    }
                    String word = input.substring(pos, wordEnd);
                    Token.Type type = switch (word.toUpperCase()) {
                        case "AND" -> Token.Type.AND;
                        case "OR" -> Token.Type.OR;
                        case "NOT" -> Token.Type.NOT;
                        default -> Token.Type.WORD;
                    };
                    tokens.add(new Token(type, word, start));
                    pos = wordEnd;
                }
            }
        }
        tokens.add(new Token(Token.Type.EOF, "", input.length()));
        return tokens;
    }

    private static boolean isStructural(char c) {
        return Character.isWhitespace(c) || "():\"<>=".indexOf(c) >= 0;
    }
}

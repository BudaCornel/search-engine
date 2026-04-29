package com.buda.searchengine.query.booleanq;


public enum ComparisonOp {
    EQ("="), GT(">"), LT("<"), GTE(">="), LTE("<=");

    private final String sql;
    ComparisonOp(String sql) { this.sql = sql; }
    public String sql() { return sql; }

    public static ComparisonOp fromTokenType(Token.Type type) {
        return switch (type) {
            case GT -> GT;
            case LT -> LT;
            case GTE -> GTE;
            case LTE -> LTE;
            case EQUALS -> EQ;
            default -> throw new IllegalArgumentException("Not a comparison operator: " + type);
        };
    }
}

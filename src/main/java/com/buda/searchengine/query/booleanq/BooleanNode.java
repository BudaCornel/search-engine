package com.buda.searchengine.query.booleanq;

import com.buda.searchengine.query.Qualifier;


public sealed interface BooleanNode {

    <R> R accept(Visitor<R> visitor);

    interface Visitor<R> {
        R visitAnd(And node);
        R visitOr(Or node);
        R visitNot(Not node);
        R visitPredicate(Predicate node);
    }

    record And(BooleanNode left, BooleanNode right) implements BooleanNode {
        @Override public <R> R accept(Visitor<R> v) { return v.visitAnd(this); }
    }

    record Or(BooleanNode left, BooleanNode right) implements BooleanNode {
        @Override public <R> R accept(Visitor<R> v) { return v.visitOr(this); }
    }

    record Not(BooleanNode inner) implements BooleanNode {
        @Override public <R> R accept(Visitor<R> v) { return v.visitNot(this); }
    }

    record Predicate(Qualifier qualifier, ComparisonOp op, String value) implements BooleanNode {
        public Predicate(Qualifier qualifier, String value) {
            this(qualifier, ComparisonOp.EQ, value);
        }
        @Override public <R> R accept(Visitor<R> v) { return v.visitPredicate(this); }
    }
}

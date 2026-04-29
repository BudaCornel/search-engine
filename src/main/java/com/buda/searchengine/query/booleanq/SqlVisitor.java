package com.buda.searchengine.query.booleanq;

import com.buda.searchengine.query.Qualifier;

import java.util.ArrayList;
import java.util.List;


public final class SqlVisitor implements BooleanNode.Visitor<SqlFragment> {

    @Override
    public SqlFragment visitAnd(BooleanNode.And node) {
        SqlFragment left = node.left().accept(this);
        SqlFragment right = node.right().accept(this);
        return new SqlFragment(
                "(" + left.whereClause() + " AND " + right.whereClause() + ")",
                concat(left.params(), right.params())
        );
    }

    @Override
    public SqlFragment visitOr(BooleanNode.Or node) {
        SqlFragment left = node.left().accept(this);
        SqlFragment right = node.right().accept(this);
        return new SqlFragment(
                "(" + left.whereClause() + " OR " + right.whereClause() + ")",
                concat(left.params(), right.params())
        );
    }

    @Override
    public SqlFragment visitNot(BooleanNode.Not node) {
        SqlFragment inner = node.inner().accept(this);
        return new SqlFragment(
                "NOT (" + inner.whereClause() + ")",
                inner.params()
        );
    }

    @Override
    public SqlFragment visitPredicate(BooleanNode.Predicate node) {
        return switch (node.qualifier()) {
            case CONTENT -> new SqlFragment(
                    "content_tsv @@ websearch_to_tsquery('english', ?)",
                    List.of(node.value())
            );
            case PATH -> new SqlFragment(
                    "absolute_path ILIKE ?",
                    List.of("%" + node.value() + "%")
            );
            case NAME -> new SqlFragment(
                    "file_name ILIKE ?",
                    List.of("%" + node.value() + "%")
            );
            case EXT -> new SqlFragment(
                    "LOWER(extension) = LOWER(?)",
                    List.of(node.value())
            );
            case MIME -> new SqlFragment(
                    "mime_type = ?",
                    List.of(node.value())
            );
            case SIZE -> {
                long bytes = SizeUnit.parseBytes(node.value());
                yield new SqlFragment(
                        "size_bytes " + node.op().sql() + " ?",
                        List.of(bytes)
                );
            }
        };
    }

    /** Walks the tree to extract the raw value of the first CONTENT predicate, or null. */
    public static String firstContentTerm(BooleanNode node) {
        if (node instanceof BooleanNode.Predicate p && p.qualifier() == Qualifier.CONTENT) {
            return p.value();
        }
        if (node instanceof BooleanNode.And a) {
            String l = firstContentTerm(a.left());
            return l != null ? l : firstContentTerm(a.right());
        }
        if (node instanceof BooleanNode.Or o) {
            String l = firstContentTerm(o.left());
            return l != null ? l : firstContentTerm(o.right());
        }
        if (node instanceof BooleanNode.Not n) {
            return null;
        }
        return null;
    }

    private static List<Object> concat(List<Object> a, List<Object> b) {
        List<Object> result = new ArrayList<>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }
}

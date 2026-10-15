package app.bytebrief.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds llama.cpp GBNF grammars that force the model to emit one JSON object with a fixed set of
 * keys, in a fixed order, with bounded string and array sizes. Bounds matter: they make it
 * impossible for a rambling model to be cut off mid-string by the token limit.
 */
final class Gbnf {
    /** A JSON value shape. */
    abstract static class V {
        abstract String expr(Gbnf g);
    }

    static V bool() {
        return new V() {
            @Override String expr(Gbnf g) { return "bool"; }
        };
    }

    static V integer() {
        return new V() {
            @Override String expr(Gbnf g) { return "int"; }
        };
    }

    /** String of {@code min..max} characters (no escapes beyond the basic set; no control characters). */
    static V str(int min, int max) {
        return new V() {
            @Override String expr(Gbnf g) { return g.stringRule(min, max); }
        };
    }

    static V array(V item, int min, int max) {
        return new V() {
            @Override String expr(Gbnf g) {
                String i = item.expr(g);
                String rest = "( ws \",\" ws " + i + " ){" + Math.max(0, min - 1) + "," + Math.max(0, max - 1) + "}";
                return min == 0
                        ? "\"[\" ws ( " + i + " " + rest + " )? ws \"]\""
                        : "\"[\" ws " + i + " " + rest + " ws \"]\"";
            }
        };
    }

    static final class Field {
        final String name;
        final V value;

        Field(String name, V value) {
            this.name = name;
            this.value = value;
        }
    }

    static Field field(String name, V value) {
        return new Field(name, value);
    }

    static V object(Field... fields) {
        return new V() {
            @Override String expr(Gbnf g) { return g.objectExpr(fields); }
        };
    }

    private final Map<String, String> stringRules = new LinkedHashMap<>();

    /** GBNF grammar whose {@code root} is an object with the given fields. */
    static String grammar(Field... fields) {
        Gbnf g = new Gbnf();
        String root = g.objectExpr(fields);
        StringBuilder sb = new StringBuilder("root ::= ").append(root).append('\n');
        sb.append("ws ::= [ \\t\\n]{0,4}\n");
        sb.append("bool ::= \"true\" | \"false\"\n");
        sb.append("int ::= [0-9]{1,3}\n");
        sb.append("char ::= [^\"\\\\\\x00-\\x1f] | \"\\\\\" [\"\\\\/bfnrt]\n");
        for (Map.Entry<String, String> e : g.stringRules.entrySet()) {
            sb.append(e.getKey()).append(" ::= ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private String objectExpr(Field... fields) {
        StringBuilder sb = new StringBuilder("\"{\" ws ");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append("\",\" ws ");
            sb.append(literal("\"" + fields[i].name + "\"")).append(" ws \":\" ws ").append(fields[i].value.expr(this)).append(" ws ");
        }
        return sb.append("\"}\"").toString();
    }

    private String stringRule(int min, int max) {
        String name = "str" + min + "x" + max;
        stringRules.putIfAbsent(name, "\"\\\"\" char{" + min + "," + max + "} \"\\\"\"");
        return name;
    }

    /** GBNF string literal for {@code s}. */
    private static String literal(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

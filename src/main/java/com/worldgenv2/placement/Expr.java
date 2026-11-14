package com.worldgenv2.placement;

import java.util.Locale;

/**
 * A tiny expression language for placement rules, so rules never hardcode numbers that depend on the situation.
 *
 * <pre>
 *   span.length + 2 &lt;= prefab.length &amp;&amp; prefab.length &lt;= span.length + 16
 *   prefab.height &gt;= log.length * 1.0
 *   max(span.hA, span.hB) + 1
 * </pre>
 *
 * Numbers, {@code + - * /}, comparisons ({@code < <= > >= == !=}), {@code && || !} (also {@code and or not}), parentheses,
 * the functions {@code min max abs floor ceil}, and variables with dotted names looked up in {@link Vars}. A missing
 * variable is NaN, so any comparison involving it is false. Truthiness: non-zero and not NaN.
 */
public final class Expr {

    private interface Node { double eval(Vars v); }

    private final Node root;
    private final String source;

    private Expr(Node root, String source) {
        this.root = root;
        this.source = source;
    }

    public static Expr parse(String text) {
        String s = text == null || text.isBlank() ? "1" : text;
        Parser p = new Parser(s);
        Node n = p.parseOr();
        p.skipWs();
        if (p.pos < s.length()) throw new IllegalArgumentException("Unexpected '" + s.charAt(p.pos) + "' at " + p.pos + " in: " + s);
        return new Expr(n, s);
    }

    public double eval(Vars v) { return root.eval(v); }

    public boolean test(Vars v) {
        double d = root.eval(v);
        return !Double.isNaN(d) && d != 0.0;
    }

    @Override
    public String toString() { return source; }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

        boolean eat(String tok) {
            skipWs();
            if (s.startsWith(tok, pos)) { pos += tok.length(); return true; }
            return false;
        }

        boolean eatWord(String w) {
            skipWs();
            if (s.regionMatches(true, pos, w, 0, w.length())) {
                int e = pos + w.length();
                if (e >= s.length() || !(Character.isLetterOrDigit(s.charAt(e)) || s.charAt(e) == '_' || s.charAt(e) == '.')) { pos = e; return true; }
            }
            return false;
        }

        Node parseOr() {
            Node l = parseAnd();
            while (true) {
                if (eat("||") || eatWord("or")) { Node a = l, b = parseAnd(); l = v -> truth(a.eval(v)) || truth(b.eval(v)) ? 1 : 0; }
                else return l;
            }
        }

        Node parseAnd() {
            Node l = parseNot();
            while (true) {
                if (eat("&&") || eatWord("and")) { Node a = l, b = parseNot(); l = v -> truth(a.eval(v)) && truth(b.eval(v)) ? 1 : 0; }
                else return l;
            }
        }

        Node parseNot() {
            if (eatWord("not") || (peek() == '!' && !s.startsWith("!=", pos) && eat("!"))) { Node a = parseNot(); return v -> truth(a.eval(v)) ? 0 : 1; }
            return parseCmp();
        }

        Node parseCmp() {
            Node l = parseAdd();
            skipWs();
            String op = null;
            for (String c : new String[]{"<=", ">=", "==", "!=", "<", ">"}) if (s.startsWith(c, pos)) { op = c; break; }
            if (op == null) return l;
            pos += op.length();
            Node a = l, b = parseAdd();
            final String o = op;
            return v -> {
                double x = a.eval(v), y = b.eval(v);
                if (Double.isNaN(x) || Double.isNaN(y)) return 0;
                return switch (o) {
                    case "<=" -> x <= y; case ">=" -> x >= y; case "==" -> x == y; case "!=" -> x != y;
                    case "<" -> x < y; default -> x > y;
                } ? 1 : 0;
            };
        }

        Node parseAdd() {
            Node l = parseMul();
            while (true) {
                skipWs();
                if (peek() == '+') { pos++; Node a = l, b = parseMul(); l = v -> a.eval(v) + b.eval(v); }
                else if (peek() == '-') { pos++; Node a = l, b = parseMul(); l = v -> a.eval(v) - b.eval(v); }
                else return l;
            }
        }

        Node parseMul() {
            Node l = parseUnary();
            while (true) {
                skipWs();
                if (peek() == '*') { pos++; Node a = l, b = parseUnary(); l = v -> a.eval(v) * b.eval(v); }
                else if (peek() == '/') { pos++; Node a = l, b = parseUnary(); l = v -> { double d = b.eval(v); return d == 0 ? Double.NaN : a.eval(v) / d; }; }
                else return l;
            }
        }

        Node parseUnary() {
            skipWs();
            if (peek() == '-') { pos++; Node a = parseUnary(); return v -> -a.eval(v); }
            return parsePrimary();
        }

        Node parsePrimary() {
            skipWs();
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of expression: " + s);
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                Node n = parseOr();
                if (!eat(")")) throw new IllegalArgumentException("Missing ')' in: " + s);
                return n;
            }
            if (Character.isDigit(c) || c == '.') {
                int st = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                double d = Double.parseDouble(s.substring(st, pos));
                return v -> d;
            }
            if (Character.isLetter(c) || c == '_') {
                int st = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) pos++;
                String name = s.substring(st, pos);
                skipWs();
                if (peek() == '(') {
                    pos++;
                    java.util.List<Node> args = new java.util.ArrayList<>();
                    skipWs();
                    if (peek() != ')') {
                        do { args.add(parseOr()); } while (eat(","));
                    }
                    if (!eat(")")) throw new IllegalArgumentException("Missing ')' after " + name + "(");
                    return function(name.toLowerCase(Locale.ROOT), args);
                }
                if (name.equals("true")) return v -> 1;
                if (name.equals("false")) return v -> 0;
                return v -> v.get(name);
            }
            throw new IllegalArgumentException("Unexpected '" + c + "' at " + pos + " in: " + s);
        }

        Node function(String name, java.util.List<Node> a) {
            return switch (name) {
                case "min" -> v -> { double m = Double.POSITIVE_INFINITY; for (Node n : a) m = Math.min(m, n.eval(v)); return m; };
                case "max" -> v -> { double m = Double.NEGATIVE_INFINITY; for (Node n : a) m = Math.max(m, n.eval(v)); return m; };
                case "abs" -> v -> Math.abs(a.get(0).eval(v));
                case "floor" -> v -> Math.floor(a.get(0).eval(v));
                case "ceil" -> v -> Math.ceil(a.get(0).eval(v));
                default -> throw new IllegalArgumentException("Unknown function '" + name + "'");
            };
        }

        char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
    }

    private static boolean truth(double d) { return !Double.isNaN(d) && d != 0.0; }
}

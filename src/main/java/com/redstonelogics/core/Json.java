package com.redstonelogics.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately tiny JSON reader/writer.
 *
 * <p>Minecraft ships Gson, but the companion desktop app does not, and the
 * snapshot format is the boundary between them. Hand-rolling this keeps both
 * sides dependency-free. Values map to {@code Map<String,Object>},
 * {@code List<Object>}, {@code String}, {@code Double}, {@code Boolean}, {@code null}.
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Map<?, ?> m) {
            writeObject(sb, m, indent);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l, indent);
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, int indent) {
        if (m.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            pad(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < m.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> l, int indent) {
        // Arrays of scalars stay on one line, which keeps positions readable.
        boolean scalar = l.stream().noneMatch(x -> x instanceof Map || x instanceof List);
        if (scalar) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                writeValue(sb, l.get(i), indent);
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < l.size(); i++) {
            pad(sb, indent + 1);
            writeValue(sb, l.get(i), indent + 1);
            if (i < l.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append(']');
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- reading

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object v = p.value();
        p.skipWhitespace();
        if (p.index < p.src.length()) {
            throw new IllegalArgumentException("trailing content at offset " + p.index);
        }
        return v;
    }

    private static final class Parser {
        final String src;
        int index;

        Parser(String src) {
            this.src = src;
        }

        void skipWhitespace() {
            while (index < src.length() && Character.isWhitespace(src.charAt(index))) {
                index++;
            }
        }

        Object value() {
            if (index >= src.length()) {
                throw err("unexpected end of input");
            }
            return switch (src.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return m;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                m.put(key, value());
                skipWhitespace();
                char c = src.charAt(index++);
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw err("expected , or } but found " + c);
                }
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return l;
            }
            while (true) {
                skipWhitespace();
                l.add(value());
                skipWhitespace();
                char c = src.charAt(index++);
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw err("expected , or ] but found " + c);
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = src.charAt(index++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = src.charAt(index++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(src.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw err("bad escape");
                }
            }
        }

        Double number() {
            int start = index;
            while (index < src.length() && "+-0123456789.eE".indexOf(src.charAt(index)) >= 0) {
                index++;
            }
            if (start == index) {
                throw err("expected a value");
            }
            return Double.valueOf(src.substring(start, index));
        }

        Object literal(String word, Object v) {
            if (!src.startsWith(word, index)) {
                throw err("expected " + word);
            }
            index += word.length();
            return v;
        }

        char peek() {
            return index < src.length() ? src.charAt(index) : '\0';
        }

        void expect(char c) {
            if (peek() != c) {
                throw err("expected " + c);
            }
            index++;
        }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON at offset " + index + ": " + msg);
        }
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("expected a JSON object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object v) {
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw new IllegalArgumentException("expected a JSON array");
    }

    public static String str(Map<String, Object> o, String key, String fallback) {
        Object v = o.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    public static int integer(Map<String, Object> o, String key, int fallback) {
        Object v = o.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public static boolean bool(Map<String, Object> o, String key, boolean fallback) {
        Object v = o.get(key);
        return v instanceof Boolean b ? b.booleanValue() : fallback;
    }
}

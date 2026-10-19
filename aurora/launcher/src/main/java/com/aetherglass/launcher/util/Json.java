package com.aetherglass.launcher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный разборщик и генератор JSON.
 *
 * <p>Лаунчер не тянет внешние библиотеки: файлы, с которыми он работает (манифест версий Mojang,
 * индекс ресурсов, профиль Fabric, настройки и хранилище токенов), имеют простую структуру,
 * поэтому отдельная зависимость не нужна.</p>
 *
 * <p>Значения представлены стандартными типами Java: {@code Map<String, Object>}, {@code List<Object>},
 * {@code String}, {@code Double}, {@code Boolean}, {@code null}.</p>
 */
public final class Json {
    private final String source;
    private int position;

    private Json(String source) {
        this.source = source;
    }

    /** Разбирает строку JSON. Выбрасывает {@link JsonException} при синтаксической ошибке. */
    public static Object parse(String text) {
        // Некоторые файлы Mojang начинаются с метки порядка байтов (BOM) — она просто отбрасывается.
        String clean = text.startsWith("\uFEFF") ? text.substring(1) : text;
        Json parser = new Json(clean);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != clean.length()) {
            throw new JsonException("лишние данные после значения JSON, позиция " + parser.position);
        }
        return value;
    }

    /** Разбирает объект JSON, выбранный из результата {@link #parse(String)}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new JsonException("ожидался объект JSON");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new JsonException("ожидался массив JSON");
    }

    public static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public static String requireString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new JsonException("отсутствует строковое поле '" + key + "'");
    }

    public static int intValue(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    public static long longValue(Map<String, Object> object, String key, long fallback) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    public static boolean boolValue(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    /** Сериализует значение в компактный JSON. */
    public static String write(Object value) {
        StringBuilder builder = new StringBuilder(256);
        writeValue(builder, value);
        return builder.toString();
    }

    /** Сериализует значение в JSON с отступами — используется для файла настроек. */
    public static String writePretty(Object value) {
        StringBuilder builder = new StringBuilder(256);
        writePrettyValue(builder, value, 0);
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String text) {
            writeString(builder, text);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            builder.append(value);
        } else if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (doubleValue == Math.rint(doubleValue) && Math.abs(doubleValue) < 1.0E15) {
                builder.append((long) doubleValue);
            } else {
                builder.append(doubleValue);
            }
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                writeValue(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeValue(builder, item);
            }
            builder.append(']');
        } else {
            throw new JsonException("неподдерживаемый тип значения: " + value.getClass());
        }
    }

    private static void writePrettyValue(StringBuilder builder, Object value, int indent) {
        if (!(value instanceof Map<?, ?> map) && !(value instanceof Iterable<?>)) {
            writeValue(builder, value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                builder.append("{}");
                return;
            }
            builder.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(",\n");
                }
                first = false;
                indent(builder, indent + 1);
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(": ");
                writePrettyValue(builder, entry.getValue(), indent + 1);
            }
            builder.append('\n');
            indent(builder, indent);
            builder.append('}');
            return;
        }
        Iterable<?> iterable = (Iterable<?>) value;
        boolean empty = !iterable.iterator().hasNext();
        if (empty) {
            builder.append("[]");
            return;
        }
        builder.append("[\n");
        boolean first = true;
        for (Object item : iterable) {
            if (!first) {
                builder.append(",\n");
            }
            first = false;
            indent(builder, indent + 1);
            writePrettyValue(builder, item, indent + 1);
        }
        builder.append('\n');
        indent(builder, indent);
        builder.append(']');
    }

    private static void indent(StringBuilder builder, int indent) {
        for (int i = 0; i < indent; i++) {
            builder.append("  ");
        }
    }

    private static void writeString(StringBuilder builder, String text) {
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private Object readValue() {
        char character = peek();
        return switch (character) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            this.position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char character = next();
            if (character == '}') {
                return map;
            }
            if (character != ',') {
                throw new JsonException("ожидалась ',' или '}' в позиции " + (this.position - 1));
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            this.position++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char character = next();
            if (character == ']') {
                return list;
            }
            if (character != ',') {
                throw new JsonException("ожидалась ',' или ']' в позиции " + (this.position - 1));
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            char character = next();
            if (character == '"') {
                return builder.toString();
            }
            if (character != '\\') {
                builder.append(character);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (this.position + 4 > this.source.length()) {
                        throw new JsonException("неполная escape-последовательность \\u");
                    }
                    builder.append((char) Integer.parseInt(this.source.substring(this.position, this.position + 4), 16));
                    this.position += 4;
                }
                default -> throw new JsonException("неизвестный escape-символ '\\" + escaped + "'");
            }
        }
    }

    private Number readNumber() {
        int start = this.position;
        while (this.position < this.source.length()) {
            char character = this.source.charAt(this.position);
            if ((character >= '0' && character <= '9') || character == '-' || character == '+'
                    || character == '.' || character == 'e' || character == 'E') {
                this.position++;
            } else {
                break;
            }
        }
        String text = this.source.substring(start, this.position);
        if (text.isEmpty()) {
            throw new JsonException("ожидалось значение в позиции " + start);
        }
        if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                // Число не влезло в long — используем double.
            }
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new JsonException("некорректное число '" + text + "'");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!this.source.startsWith(literal, this.position)) {
            throw new JsonException("ожидалось '" + literal + "' в позиции " + this.position);
        }
        this.position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (this.position < this.source.length()) {
            char character = this.source.charAt(this.position);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                this.position++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (this.position >= this.source.length()) {
            throw new JsonException("неожиданный конец JSON");
        }
        return this.source.charAt(this.position);
    }

    private char next() {
        char character = peek();
        this.position++;
        return character;
    }

    private void expect(char expected) {
        char character = next();
        if (character != expected) {
            throw new JsonException("ожидался символ '" + expected + "', найден '" + character + "'");
        }
    }

    /** Ошибка разбора JSON с понятным сообщением. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }
}

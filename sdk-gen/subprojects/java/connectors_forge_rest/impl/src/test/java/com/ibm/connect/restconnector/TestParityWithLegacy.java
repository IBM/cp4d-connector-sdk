/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static com.ibm.connect.restconnector.StreamTestSupport.fields;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.connect.restconnector.StreamTestSupport.CollectingWriter;

/**
 * Compares the streaming {@link PageParser} against the former tree-based mapping (copied below as
 * {@link LegacyReference}) on seeded random fixtures.
 *
 * <p>Intentional differences, normalised by the comparison: decimals keep the source scale and exact digits
 * (the tree code went through {@code double}) and are compared with {@code compareTo}; JSON-valued fields keep the
 * source number text (compared as parsed trees).
 */
public class TestParityWithLegacy
{
    private static final String[] SCALARS = { "0", "1", "-7", "42", "3.9", "2.50", "-0.125", "1e3", "1.5E-3",
            "9007199254740993", "4294967297", "123456789012345678901234", "0.1", "100000000000000000000.5",
            "true", "false", "null", "\"\"", "\"abc\"", "\" 12 \"", "\"12\"", "\"3.5\"", "\"yes\"", "\"No\"",
            "\"1\"", "\"2024-02-29\"", "\"2023-13-01\"", "\"2024-05-06T07:08:09Z\"", "\"2024-05-06T07:08:09.5Z\"",
            "\"2024-05-06 07:08:09\"", "\"garbage\"", "\"caf\\u00e9\"", "[1,2,{\"a\":null}]", "{\"k\":[true,{}]}",
            "[]", "{}", "{\"x\":\"y\"}" };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<RestFieldDefinition> DEFS = fields(
            "i", "INTEGER", "si", "SMALLINT", "b", "BIGINT", "flag", "BOOLEAN", "d", "DOUBLE", "r", "REAL",
            "dec", "DECIMAL(20,4)", "dt", "DATE", "ts", "TIMESTAMP", "dtm", "DATETIME", "j", "JSON", "arr", "ARRAY",
            "obj", "OBJECT", "s", "VARCHAR(50)", "t", "TEXT", "u", "SOMETHING_ELSE",
            "n.i", "INTEGER", "n.deep.s", "VARCHAR(20)", "n.j", "JSON");

    /** The former mapping, unchanged except that logging was dropped. */
    static final class LegacyReference
    {
        static Map<String, Object> map(JsonNode record, List<RestFieldDefinition> defs)
        {
            final Map<String, Object> row = new LinkedHashMap<>();
            for (final RestFieldDefinition def : defs) {
                final String name = def.getName();
                final JsonNode valueNode = name.contains(".") ? nested((com.fasterxml.jackson.databind.node.ObjectNode) record, name)
                        : record.get(name);
                final Object value = convert(valueNode, def);
                if (value != null) {
                    row.put(name, value);
                }
            }
            return row;
        }

        private static JsonNode nested(com.fasterxml.jackson.databind.node.ObjectNode objectNode, String flattened)
        {
            final int dot = flattened.indexOf('.');
            if (dot < 0) {
                return objectNode.get(flattened);
            }
            final JsonNode parent = objectNode.get(flattened.substring(0, dot));
            final String rest = flattened.substring(dot + 1);
            if (parent == null || !parent.isObject()) {
                return null;
            }
            if (rest.contains(".")) {
                return nested((com.fasterxml.jackson.databind.node.ObjectNode) parent, rest);
            }
            return parent.get(rest);
        }

        private static Object convert(JsonNode node, RestFieldDefinition def)
        {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isObject() || node.isArray()) {
                return node.toString();
            }
            final String lower = def.getTypeString().toLowerCase(Locale.ENGLISH);
            final int paren = lower.indexOf('(');
            final String base = (paren >= 0 ? lower.substring(0, paren) : lower).trim();
            try {
                switch (base) {
                case "integer":
                case "int":
                case "smallint":
                case "tinyint":
                    return node.isNumber() ? (Object) node.intValue() : parseInt(node.asText());
                case "bigint":
                    return node.isNumber() ? (Object) node.longValue() : parseLong(node.asText());
                case "boolean":
                case "bool":
                case "bit":
                    return node.isBoolean() ? (Object) node.booleanValue() : parseBoolean(node.asText());
                case "double":
                case "float8":
                case "float":
                case "real":
                case "float4":
                    return node.isNumber() ? (Object) node.doubleValue() : parseDouble(node.asText());
                case "decimal":
                case "numeric":
                    return node.isNumber() ? node.decimalValue() : parseDecimal(node.asText());
                case "date":
                    return parseDate(node.asText());
                case "timestamp":
                case "datetime":
                    return parseTimestamp(node.asText());
                case "json":
                case "jsonb":
                case "array":
                case "object":
                    return node.toString();
                default:
                    return node.asText();
                }
            } catch (IllegalArgumentException | DateTimeParseException e) {
                return null;
            }
        }

        private static Integer parseInt(String t)
        {
            return t.isEmpty() ? null : Integer.parseInt(t.trim());
        }

        private static Long parseLong(String t)
        {
            return t.isEmpty() ? null : Long.parseLong(t.trim());
        }

        private static Double parseDouble(String t)
        {
            return t.isEmpty() ? null : Double.parseDouble(t.trim());
        }

        private static Boolean parseBoolean(String t)
        {
            if (t.isEmpty()) {
                return null;
            }
            final String l = t.trim().toLowerCase(Locale.ENGLISH);
            if ("true".equals(l) || "1".equals(l) || "yes".equals(l)) {
                return Boolean.TRUE;
            }
            if ("false".equals(l) || "0".equals(l) || "no".equals(l)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("bad boolean");
        }

        private static BigDecimal parseDecimal(String t)
        {
            return t.isEmpty() ? null : new BigDecimal(t.trim());
        }

        private static Date parseDate(String t)
        {
            return t.isEmpty() ? null : Date.valueOf(LocalDate.parse(t.trim()));
        }

        private static Timestamp parseTimestamp(String t)
        {
            if (t.isEmpty()) {
                return null;
            }
            final String trimmed = t.trim();
            try {
                return Timestamp.from(Instant.parse(trimmed));
            } catch (DateTimeParseException e) {
                return Timestamp.valueOf(trimmed.replace("T", " ").replaceAll("\\.\\d+Z?$", ""));
            }
        }
    }

    // ---- fixture generation ----

    private static String record(Random random)
    {
        final StringBuilder sb = new StringBuilder(512).append('{');
        boolean first = true;
        final String[] top = { "i", "si", "b", "flag", "d", "r", "dec", "dt", "ts", "dtm", "j", "arr", "obj", "s", "t",
                "u" };
        for (final String key : top) {
            if (random.nextInt(10) == 0) {
                continue; // missing field
            }
            sb.append(first ? "" : ",").append('"').append(key).append("\":").append(SCALARS[random.nextInt(SCALARS.length)]);
            first = false;
        }
        if (random.nextInt(8) != 0) { // nested object, sometimes a non-object
            final int kind = random.nextInt(10);
            sb.append(first ? "" : ",").append("\"n\":");
            if (kind == 0) {
                sb.append(SCALARS[random.nextInt(SCALARS.length)]);
            } else {
                sb.append("{\"i\":").append(SCALARS[random.nextInt(SCALARS.length)])
                        .append(",\"deep\":{\"s\":").append(SCALARS[random.nextInt(SCALARS.length)])
                        .append(",\"noise\":[1,2,3]},\"j\":").append(SCALARS[random.nextInt(SCALARS.length)])
                        .append(",\"unused\":{\"a\":{\"b\":[]}}}");
            }
            first = false;
        }
        sb.append(first ? "" : ",").append("\"unrelated\":[{\"i\":1},{\"deep\":{}}]");
        return sb.append('}').toString();
    }

    // ---- comparison ----

    private static void assertSameValue(String message, Object expected, Object actual, String type) throws Exception
    {
        final String lower = type.toLowerCase(Locale.ENGLISH);
        if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
            // same number; the new code keeps the source scale and exact digits
            final BigDecimal e = (BigDecimal) expected;
            final BigDecimal a = (BigDecimal) actual;
            final boolean exactDifference = e.compareTo(a) != 0 && e.round(new java.math.MathContext(15)).compareTo(a.round(new java.math.MathContext(15))) == 0;
            assertTrue(message + " expected " + e + " but was " + a, e.compareTo(a) == 0 || exactDifference);
        } else if (("json".equals(lower) || "array".equals(lower) || "object".equals(lower)
                || expected instanceof String && actual instanceof String && isContainerText((String) expected))
                && expected instanceof String && actual instanceof String) {
            assertEquals(message, MAPPER.readTree((String) expected), MAPPER.readTree((String) actual));
        } else {
            assertEquals(message, expected, actual);
        }
    }

    private static boolean isContainerText(String text)
    {
        return text.startsWith("{") || text.startsWith("[");
    }

    @Test
    public void streamingParserMatchesTheTreeBasedMappingOnRandomRecords() throws Exception
    {
        final Random random = new Random(20260511L);
        final int count = 4000;
        final StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            json.append(i == 0 ? "" : ",").append(record(random));
        }
        json.append(']');

        // reference: tree mapping of every record
        final List<Map<String, Object>> expected = new ArrayList<>();
        for (final JsonNode node : MAPPER.readTree(json.toString())) {
            expected.add(LegacyReference.map(node, DEFS));
        }

        // streaming implementation
        final PageParser parser = new PageParser(new FieldPlan(DEFS), null, null, null, false);
        final CollectingWriter writer = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, writer);
        }

        assertEquals(count, writer.rows.size());
        long compared = 0;
        for (int i = 0; i < count; i++) {
            final Map<String, Object> e = expected.get(i);
            final Map<String, Object> a = writer.rows.get(i);
            assertEquals("record " + i + " field set", e.keySet(), a.keySet());
            assertEquals("record " + i + " field order", new ArrayList<>(e.keySet()), new ArrayList<>(a.keySet()));
            for (final RestFieldDefinition def : DEFS) {
                if (e.containsKey(def.getName())) {
                    assertSameValue("record " + i + " field " + def.getName(), e.get(def.getName()),
                            a.get(def.getName()), def.getTypeString());
                    compared++;
                }
            }
        }
        assertTrue("fixture should exercise many values, compared " + compared, compared > 20000);
    }

    @Test
    public void nestedDataPathMatchesTreeNavigation() throws Exception
    {
        final String json = "{\"a\":{\"b\":[{\"i\":1},{\"i\":2}]},\"c\":[{\"i\":9}]}";
        final JsonNode tree = MAPPER.readTree(json).path("a").path("b");
        final List<Map<String, Object>> expected = new ArrayList<>();
        for (final JsonNode node : tree) {
            expected.add(LegacyReference.map(node, fields("i", "INTEGER")));
        }
        final PageParser parser = new PageParser(new FieldPlan(fields("i", "INTEGER")), "a.b", null, null, false);
        final CollectingWriter writer = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, writer);
        }
        assertEquals(expected, writer.rows);
    }

    @Test
    public void intentionalDifferenceExactDecimalDigits() throws Exception
    {
        final String json = "[{\"dec\":0.1234567890123456789}]";
        final BigDecimal legacy = (BigDecimal) LegacyReference
                .map(MAPPER.readTree(json).get(0), fields("dec", "DECIMAL(30,19)")).get("dec");
        final PageParser parser = new PageParser(new FieldPlan(fields("dec", "DECIMAL(30,19)")), null, null, null, false);
        final CollectingWriter writer = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, writer);
        }
        assertEquals(new BigDecimal("0.1234567890123456789"), writer.rows.get(0).get("dec"));
        assertNotEquals("the tree code lost digits through double: " + legacy, 0L,
                (long) legacy.compareTo(new BigDecimal("0.1234567890123456789")));
    }
}

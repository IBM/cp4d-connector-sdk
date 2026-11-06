/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static com.ibm.connect.restconnector.StreamTestSupport.field;
import static com.ibm.connect.restconnector.StreamTestSupport.fields;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.ibm.connect.restconnector.StreamTestSupport.CollectingWriter;

/** Structure handling, field mapping, type conversion and error behaviour of {@link PageParser}. */
public class TestPageParser
{
    private static final List<RestFieldDefinition> ID_NAME = fields("id", "INTEGER", "name", "VARCHAR(10)");

    private static CollectingWriter parse(String json, String dataPath, List<RestFieldDefinition> defs)
            throws Exception
    {
        return parse(json, dataPath, defs, false);
    }

    private static CollectingWriter parse(String json, String dataPath, List<RestFieldDefinition> defs,
            boolean strict) throws Exception
    {
        return run(new PageParser(new FieldPlan(defs), dataPath, null, null, strict), json);
    }

    private static CollectingWriter run(PageParser parser, String json) throws Exception
    {
        final CollectingWriter writer = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, writer);
        }
        assertTrue(writer.balanced());
        return writer;
    }

    // ---- structure ----

    @Test
    public void rootArray() throws Exception
    {
        final CollectingWriter w = parse("[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]", null, ID_NAME);
        assertEquals(2, w.rows.size());
        assertEquals(1, w.rows.get(0).get("id"));
        assertEquals("b", w.rows.get(1).get("name"));
    }

    @Test
    public void rootObjectIsOneRecord() throws Exception
    {
        final CollectingWriter w = parse("{\"id\":7,\"name\":\"x\"}", null, ID_NAME);
        assertEquals(1, w.rows.size());
        assertEquals(7, w.rows.get(0).get("id"));
    }

    @Test
    public void emptyArrayAndEmptyBodyWithoutDataPath() throws Exception
    {
        assertEquals(0, parse("[]", null, ID_NAME).rows.size());
        assertEquals(0, parse("", null, ID_NAME).rows.size());
        assertEquals(0, parse("  ", "", ID_NAME).rows.size());
    }

    @Test
    public void singleSegmentDataPath() throws Exception
    {
        final CollectingWriter w = parse("{\"meta\":{\"id\":99},\"items\":[{\"id\":1},{\"id\":2}]}", "items", ID_NAME);
        assertEquals(2, w.rows.size());
        assertEquals(2, w.rows.get(1).get("id"));
    }

    @Test
    public void nestedDataPath() throws Exception
    {
        final String json = "{\"status\":\"ok\",\"response\":{\"count\":2,\"data\":[{\"id\":1},{\"id\":2}]},"
                + "\"other\":{\"data\":[{\"id\":100}]}}";
        final CollectingWriter w = parse(json, "response.data", ID_NAME);
        assertEquals(2, w.rows.size());
        assertEquals(1, w.rows.get(0).get("id"));
    }

    @Test
    public void dataPathIntoArrayIndex() throws Exception
    {
        final CollectingWriter w = parse("{\"pages\":[{\"x\":[{\"id\":1}]},{\"x\":[{\"id\":2}]}]}", "pages.1.x",
                ID_NAME);
        assertEquals(1, w.rows.size());
        assertEquals(2, w.rows.get(0).get("id"));
    }

    @Test
    public void arrayIndexBeyondTheEndIsNotFound() throws Exception
    {
        try {
            parse("{\"data\":[[{\"id\":1}],[{\"id\":2}]]}", "data.2", ID_NAME);
            fail("an index beyond the end of the array must not match");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void dataPathObjectIsOneRecord() throws Exception
    {
        final CollectingWriter w = parse("{\"result\":{\"id\":5,\"name\":\"z\"}}", "result", ID_NAME);
        assertEquals(1, w.rows.size());
        assertEquals("z", w.rows.get(0).get("name"));
    }

    @Test
    public void dataPathNullIsEmptyPage() throws Exception
    {
        assertEquals(0, parse("{\"items\":null}", "items", ID_NAME).rows.size());
    }

    @Test
    public void missingDataPathIsAnError() throws Exception
    {
        final String[] bodies = { "{\"other\":[]}", "[]", "{}", "", "{\"a\":1}", "{\"items\":{\"x\":1}}" };
        for (final String json : bodies) {
            final String path = json.contains("items") ? "items.x.y" : "items";
            try {
                parse(json, path, ID_NAME);
                fail("expected missing data path error for " + json);
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("not found"));
            }
        }
    }

    @Test
    public void scalarDataNodeIsAnError() throws Exception
    {
        for (final String json : new String[] { "{\"items\":5}", "{\"items\":\"text\"}", "{\"items\":true}" }) {
            try {
                parse(json, "items", ID_NAME);
                fail("expected error for " + json);
            } catch (IOException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Expected a JSON array or object"));
            }
        }
    }

    @Test
    public void scalarRootIsAnError() throws Exception
    {
        try {
            parse("42", null, ID_NAME);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Expected a JSON array or object"));
        }
    }

    @Test
    public void nonObjectArrayElementsAreNotRecords() throws Exception
    {
        final CollectingWriter w = parse("[1,\"x\",null,[{\"id\":9}],{\"id\":2}]", null, ID_NAME);
        assertEquals(1, w.rows.size());
        assertEquals(2, w.rows.get(0).get("id"));
    }

    @Test
    public void objectsInUnrelatedNestedArraysAreNotRecords() throws Exception
    {
        final String json = "{\"included\":[{\"id\":100},{\"id\":101}],\"data\":[{\"id\":1,\"tags\":[{\"id\":50}]},"
                + "{\"id\":2}],\"links\":[{\"id\":200}]}";
        final CollectingWriter w = parse(json, "data", ID_NAME);
        assertEquals(2, w.rows.size());
        assertEquals(1, w.rows.get(0).get("id"));
        assertEquals(2, w.rows.get(1).get("id"));
    }

    @Test
    public void dataAfterLargeUnrelatedSections() throws Exception
    {
        final StringBuilder sb = new StringBuilder(40000).append("{\"junk\":[");
        for (int i = 0; i < 500; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"deep\":{\"a\":[1,2,{\"b\":[]}]}}");
        }
        sb.append("],\"data\":[{\"id\":1}]}");
        assertEquals(1, parse(sb.toString(), "data", ID_NAME).rows.size());
    }

    // ---- field mapping ----

    @Test
    public void missingAndNullFieldsAreOmitted() throws Exception
    {
        final CollectingWriter w = parse("[{\"id\":null},{\"name\":\"only\"},{}]", null, ID_NAME);
        assertEquals(3, w.rows.size());
        assertTrue(w.rows.get(0).isEmpty());
        assertEquals(Collections.singletonMap("name", "only"), w.rows.get(1));
        assertTrue(w.rows.get(2).isEmpty());
    }

    @Test
    public void rowsAreNotContaminatedByPreviousRow() throws Exception
    {
        final CollectingWriter w = parse("[{\"id\":1,\"name\":\"a\"},{\"id\":2}]", null, ID_NAME);
        assertFalse(w.rows.get(1).containsKey("name"));
    }

    @Test
    public void fieldsAreWrittenInDefinitionOrder() throws Exception
    {
        final CollectingWriter w = parse("[{\"name\":\"a\",\"id\":1}]", null, ID_NAME);
        assertEquals("[id, name]", w.rows.get(0).keySet().toString());
    }

    @Test
    public void nestedFieldPaths() throws Exception
    {
        final List<RestFieldDefinition> defs = fields("user.id", "INTEGER", "user.address.city", "VARCHAR(20)",
                "top", "VARCHAR(5)");
        final CollectingWriter w = parse("[{\"top\":\"t\",\"user\":{\"id\":3,"
                + "\"address\":{\"city\":\"Oslo\",\"zip\":\"0150\"},\"x\":[1]}}]", null, defs);
        assertEquals(3, w.rows.get(0).get("user.id"));
        assertEquals("Oslo", w.rows.get(0).get("user.address.city"));
        assertEquals("t", w.rows.get(0).get("top"));
    }

    @Test
    public void nestedFieldPathThroughNonObjectIsMissing() throws Exception
    {
        final CollectingWriter w = parse(
                "[{\"user\":5},{\"user\":null},{\"user\":[{\"id\":1}]},{\"user\":{\"id\":4}}]", null,
                fields("user.id", "INTEGER"));
        assertEquals(4, w.rows.size());
        assertTrue(w.rows.get(0).isEmpty());
        assertTrue(w.rows.get(1).isEmpty());
        assertTrue(w.rows.get(2).isEmpty());
        assertEquals(4, w.rows.get(3).get("user.id"));
    }

    @Test
    public void flatKeyWithDotIsNotAPathMatch() throws Exception
    {
        final CollectingWriter w = parse("[{\"user.id\":1}]", null, fields("user.id", "INTEGER"));
        assertTrue(w.rows.get(0).isEmpty());
    }

    @Test
    public void duplicateKeysLastWins() throws Exception
    {
        assertEquals(2, parse("[{\"id\":1,\"id\":2}]", null, ID_NAME).rows.get(0).get("id"));
    }

    @Test
    public void containerFieldsBecomeCompactJson() throws Exception
    {
        final List<RestFieldDefinition> defs = fields("tags", "ARRAY", "meta", "JSON", "plain", "VARCHAR(100)");
        final CollectingWriter w = parse(
                "[{\"tags\": [ \"a\" , 1, null, {\"k\" : true} ], \"meta\": {\"n\": 1.50, \"s\": \"q\\\"uote\"}, "
                        + "\"plain\": {\"x\": [1, 2]}}]",
                null, defs);
        assertEquals("[\"a\",1,null,{\"k\":true}]", w.rows.get(0).get("tags"));
        assertEquals("{\"n\":1.50,\"s\":\"q\\\"uote\"}", w.rows.get(0).get("meta"));
        assertEquals("{\"x\":[1,2]}", w.rows.get(0).get("plain"));
    }

    @Test
    public void containerFieldAndNestedChildOfSameParent() throws Exception
    {
        final CollectingWriter w = parse("[{\"user\":{\"id\":3,\"n\":\"x\"}}]", null,
                fields("user", "JSON", "user.id", "INTEGER"));
        assertEquals("{\"id\":3,\"n\":\"x\"}", w.rows.get(0).get("user"));
        assertEquals(3, w.rows.get(0).get("user.id"));
    }

    @Test
    public void scalarDeclaredAsJsonIsItsJsonText() throws Exception
    {
        final CollectingWriter w = parse("[{\"a\":\"str\",\"b\":12,\"c\":true}]", null,
                fields("a", "JSON", "b", "JSON", "c", "JSON"));
        assertEquals("\"str\"", w.rows.get(0).get("a"));
        assertEquals("12", w.rows.get(0).get("b"));
        assertEquals("true", w.rows.get(0).get("c"));
    }

    @Test
    public void nullFieldDeclaredAsJsonIsOmitted() throws Exception
    {
        assertTrue(parse("[{\"a\":null}]", null, fields("a", "JSON")).rows.get(0).isEmpty());
    }

    @Test
    public void unicodeAndEscapes() throws Exception
    {
        final CollectingWriter w = parse("[{\"name\":\"caf\\u00e9 \\ud83d\\ude00 \\n\"}]", null, ID_NAME);
        assertEquals("caf\u00e9 \uD83D\uDE00 \n", w.rows.get(0).get("name"));
    }

    // ---- type conversion ----

    private static Object convert(String type, String jsonValue) throws Exception
    {
        final CollectingWriter w = parse("[{\"v\":" + jsonValue + "}]", null, fields("v", type));
        return w.rows.get(0).get("v");
    }

    @Test
    public void integerConversions() throws Exception
    {
        for (final String t : new String[] { "INTEGER", "int", "SMALLINT", "TINYINT", "integer(10)" }) {
            assertEquals(5, convert(t, "5"));
            assertEquals(5, convert(t, "\"5\""));
            assertEquals(5, convert(t, "\" 5 \""));
        }
        assertEquals(3, convert("INTEGER", "3.9"));
        assertEquals((int) 4294967297L, convert("INTEGER", "4294967297")); // narrowing as JsonNode.intValue()
        assertNull(convert("INTEGER", "\"abc\""));
        assertNull(convert("INTEGER", "\"\""));
        assertNull(convert("INTEGER", "true"));
    }

    @Test
    public void bigintConversions() throws Exception
    {
        assertEquals(9007199254740993L, convert("BIGINT", "9007199254740993"));
        assertEquals(Long.MAX_VALUE, convert("BIGINT", "9223372036854775807"));
        assertEquals(12L, convert("BIGINT", "\"12\""));
        assertEquals(7L, convert("BIGINT", "7.8"));
        assertEquals(new BigInteger("18446744073709551617").longValue(), convert("BIGINT", "18446744073709551617"));
        assertNull(convert("BIGINT", "\"1.5\""));
    }

    @Test
    public void booleanConversions() throws Exception
    {
        for (final String t : new String[] { "BOOLEAN", "bool", "bit" }) {
            assertTrue((Boolean) convert(t, "true"));
            assertFalse((Boolean) convert(t, "false"));
        }
        assertTrue((Boolean) convert("BOOLEAN", "\"yes\""));
        assertTrue((Boolean) convert("BOOLEAN", "\" TRUE \""));
        assertTrue((Boolean) convert("BOOLEAN", "1"));
        assertFalse((Boolean) convert("BOOLEAN", "0"));
        assertFalse((Boolean) convert("BOOLEAN", "\"no\""));
        assertNull(convert("BOOLEAN", "\"maybe\""));
        assertNull(convert("BOOLEAN", "2"));
        assertNull(convert("BOOLEAN", "\"\""));
    }

    @Test
    public void floatingPointConversions() throws Exception
    {
        for (final String t : new String[] { "DOUBLE", "FLOAT", "REAL", "float4", "float8" }) {
            assertEquals(1.5, convert(t, "1.5"));
        }
        assertEquals(10.0, convert("DOUBLE", "10"));
        assertEquals(1e300, convert("DOUBLE", "1e300"));
        assertEquals(2.5, convert("DOUBLE", "\"2.5\""));
        assertNull(convert("DOUBLE", "\"x\""));
    }

    @Test
    public void decimalConversionsKeepExactDigits() throws Exception
    {
        assertEquals(new BigDecimal("12.340"), convert("DECIMAL(10,3)", "12.340"));
        assertEquals(new BigDecimal("123456789012345678901234567890.123456789"),
                convert("DECIMAL(38,9)", "123456789012345678901234567890.123456789"));
        assertEquals(new BigDecimal("0.1234567890123456789"), convert("NUMERIC", "0.1234567890123456789"));
        assertEquals(new BigDecimal("1.5E+3"), convert("DECIMAL", "1.5e3"));
        assertEquals(new BigDecimal("-2E-7"), convert("DECIMAL", "-2e-7"));
        assertEquals(new BigDecimal("42"), convert("DECIMAL", "42"));
        assertEquals(new BigDecimal("7.25"), convert("DECIMAL", "\"7.25\""));
        assertNull(convert("DECIMAL", "\"n/a\""));
    }

    @Test
    public void absurdDecimalExponentsAreConversionFailures() throws Exception
    {
        assertNull(convert("DECIMAL", "1e999999999"));
        assertNull(convert("DECIMAL", "1e-999999999"));
        assertNull(convert("DECIMAL", "\"1e999999999\""));
    }

    @Test
    public void veryLargeIntegersForNarrowTypesDoNotBlowUp() throws Exception
    {
        final StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            digits.append('9');
        }
        assertEquals(new BigInteger(digits.toString()).longValue(), convert("BIGINT", digits.toString()));
    }

    @Test
    public void overlongNumberIsRejectedAsInvalidJson() throws Exception
    {
        final StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 1100; i++) {
            digits.append('9');
        }
        try {
            parse("[{\"v\":" + digits + "}]", null, fields("v", "DECIMAL"));
            fail("numbers beyond the configured length limit must be rejected");
        } catch (IOException e) {
            assertFalse(e.getMessage().contains("9999999999"));
        }
    }

    @Test
    public void dateConversions() throws Exception
    {
        assertEquals(Date.valueOf("2024-02-29"), convert("DATE", "\"2024-02-29\""));
        assertEquals(Date.valueOf("2024-02-29"), convert("DATE", "\" 2024-02-29 \""));
        assertNull(convert("DATE", "\"2024-02-30\""));
        assertNull(convert("DATE", "\"yesterday\""));
        assertNull(convert("DATE", "\"\""));
    }

    @Test
    public void timestampConversions() throws Exception
    {
        assertEquals(Timestamp.from(Instant.parse("2024-05-06T07:08:09Z")),
                convert("TIMESTAMP", "\"2024-05-06T07:08:09Z\""));
        assertEquals(Timestamp.from(Instant.parse("2024-05-06T07:08:09.123Z")),
                convert("DATETIME", "\"2024-05-06T07:08:09.123Z\""));
        assertEquals(Timestamp.valueOf("2024-05-06 07:08:09"), convert("TIMESTAMP", "\"2024-05-06T07:08:09\""));
        assertEquals(Timestamp.valueOf("2024-05-06 07:08:09"), convert("TIMESTAMP", "\"2024-05-06 07:08:09.5\""));
        assertNull(convert("TIMESTAMP", "\"nope\""));
        assertNull(convert("TIMESTAMP", "\"\""));
    }

    @Test
    public void textConversionsOfScalars() throws Exception
    {
        assertEquals("abc", convert("VARCHAR(10)", "\"abc\""));
        assertEquals("12", convert("VARCHAR", "12"));
        assertEquals("true", convert("VARCHAR", "true"));
        assertEquals("1.5", convert("VARCHAR", "1.5"));
        assertEquals("1.0E10", convert("VARCHAR", "1e10"));
        assertEquals("12345678901234567890", convert("VARCHAR", "12345678901234567890"));
        assertEquals("x", convert("SOMETHING_UNKNOWN", "\"x\""));
        assertEquals("", convert("VARCHAR", "\"\""));
    }

    // ---- strict mode and failure reporting ----

    @Test
    public void strictModeFailsWithoutLeakingTheValue() throws Exception
    {
        final String secret = "SECRET-VALUE-123";
        try {
            parse("[{\"id\":\"" + secret + "\"}]", null, ID_NAME, true);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("'id'"));
            assertFalse(e.getMessage().contains(secret));
        }
    }

    @Test
    public void strictModeFailureWritesNoPartialRow() throws Exception
    {
        final PageParser parser = new PageParser(new FieldPlan(ID_NAME), null, null, null, true);
        final CollectingWriter w = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(
                "[{\"id\":1,\"name\":\"a\"},{\"name\":\"b\",\"id\":\"bad\"}]".getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, w);
            fail();
        } catch (IOException expected) {
            assertEquals(1, w.rows.size()); // the first, complete row only
            assertTrue(w.balanced());
        }
    }

    @Test
    public void nonStrictModeCountsFailuresAndContinues() throws Exception
    {
        final StringBuilder sb = new StringBuilder(2048).append('[');
        for (int i = 0; i < 20; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"id\":\"bad").append(i).append("\",\"name\":\"n\"}");
        }
        final PageParser parser = new PageParser(new FieldPlan(ID_NAME), null, null, null, false);
        final CollectingWriter w = run(parser, sb.append(']').toString());
        assertEquals(20, w.rows.size());
        assertFalse(w.rows.get(0).containsKey("id"));
        parser.logFailureSummary(); // must not throw
    }

    // ---- malformed input ----

    @Test
    public void malformedJsonIsReportedAndEarlierRowsStayWritten() throws Exception
    {
        final PageParser parser = new PageParser(new FieldPlan(ID_NAME), null, null, null, false);
        final CollectingWriter w = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(
                "[{\"id\":1},{\"id\":2},{\"id\":".getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, w);
            fail();
        } catch (IOException e) {
            assertEquals(2, w.rows.size());
            assertTrue(w.balanced());
        }
    }

    @Test
    public void truncatedRecordIsNeverWritten() throws Exception
    {
        final PageParser parser = new PageParser(new FieldPlan(ID_NAME), null, null, null, false);
        final CollectingWriter w = new CollectingWriter();
        try (JsonParser p = PageParser.open(new ByteArrayInputStream(
                "[{\"id\":1,\"name\":\"abc".getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(p, w);
            fail();
        } catch (IOException e) {
            assertEquals(0, w.rows.size());
        }
    }

    @Test
    public void garbageAndUnbalancedInput() throws Exception
    {
        final String[] bodies = { "{", "[", "[{\"id\":1}", "{\"data\":[{\"id\":1}", "not json", "[{\"id\":1}}" };
        for (final String json : bodies) {
            try {
                parse(json, json.startsWith("{\"data\"") ? "data" : null, ID_NAME);
                fail("expected failure for " + json);
            } catch (IOException expected) {
                // reported as an IOException (JsonProcessingException is an IOException)
            }
        }
    }

    @Test
    public void excessiveNestingIsRejected() throws Exception
    {
        final StringBuilder sb = new StringBuilder("[{\"x\":");
        for (int i = 0; i < 400; i++) {
            sb.append('[');
        }
        try {
            parse(sb.toString(), null, ID_NAME);
            fail();
        } catch (IOException expected) {
            // nesting depth limit or truncated input
        }
    }

    // ---- metadata ----

    private static PageParser withCursor(String dataPath, String cursorPath)
    {
        return new PageParser(new FieldPlan(ID_NAME), dataPath, cursorPath, null, false);
    }

    @Test
    public void cursorBeforeAndAfterData() throws Exception
    {
        PageParser parser = withCursor("data", "meta.next");
        run(parser, "{\"meta\":{\"next\":\"abc\"},\"data\":[{\"id\":1}]}");
        assertEquals("abc", parser.nextCursor());
        assertEquals(1, parser.recordCount());

        parser = withCursor("data", "meta.next");
        run(parser, "{\"data\":[{\"id\":1},{\"id\":2}],\"meta\":{\"next\":\"xyz\"}}");
        assertEquals("xyz", parser.nextCursor());
        assertEquals(2, parser.recordCount());
    }

    @Test
    public void cursorValueTypesAndAbsence() throws Exception
    {
        final PageParser parser = withCursor("data", "next");
        run(parser, "{\"data\":[],\"next\":12345}");
        assertEquals("12345", parser.nextCursor());

        run(parser, "{\"data\":[],\"next\":null}");
        assertNull(parser.nextCursor());

        run(parser, "{\"data\":[]}");
        assertNull(parser.nextCursor());

        run(parser, "{\"data\":[],\"next\":{\"a\":1}}");
        assertNull(parser.nextCursor());
    }

    @Test
    public void stateIsResetBetweenPages() throws Exception
    {
        final PageParser parser = withCursor("data", "next");
        run(parser, "{\"data\":[{\"id\":1}],\"next\":\"c1\"}");
        assertEquals("c1", parser.nextCursor());
        run(parser, "{\"data\":[{\"id\":2}]}");
        assertNull(parser.nextCursor());
        assertEquals(1, parser.recordCount());
    }

    @Test
    public void cursorPathThroughArrayIndexAndDataSibling() throws Exception
    {
        final PageParser parser = withCursor("items", "pages.0.token");
        run(parser, "{\"pages\":[{\"token\":\"t0\"},{\"token\":\"t1\"}],\"items\":[{\"id\":1}]}");
        assertEquals("t0", parser.nextCursor());
    }

    @Test
    public void nextUrlBeforeAndAfterData() throws Exception
    {
        final PageParser parser = new PageParser(new FieldPlan(ID_NAME), "results", null, "paging.next", false);
        run(parser, "{\"paging\":{\"next\":\"/page/2\"},\"results\":[{\"id\":1}]}");
        assertEquals("/page/2", parser.nextPageUrl());
        run(parser, "{\"results\":[{\"id\":1}],\"paging\":{\"next\":\"/page/3\"}}");
        assertEquals("/page/3", parser.nextPageUrl());
    }

    @Test
    public void rootObjectRecordWithCursorInsideTheRecord() throws Exception
    {
        final PageParser parser = withCursor(null, "id");
        final CollectingWriter w = run(parser, "{\"id\":77,\"name\":\"r\"}");
        assertEquals(1, w.rows.size());
        assertEquals("77", parser.nextCursor());
    }

    @Test
    public void metadataInsideDataArrayElementsIsNotTreatedAsMetadata() throws Exception
    {
        final PageParser parser = withCursor("data", "next");
        run(parser, "{\"data\":[{\"id\":1,\"next\":\"inner\"}]}");
        assertNull(parser.nextCursor());
    }

    @Test
    public void cursorPathMayBeSiblingInsideDataPathParent() throws Exception
    {
        final PageParser parser = withCursor("response.data", "response.next");
        run(parser, "{\"response\":{\"data\":[{\"id\":1}],\"next\":\"n\"}}");
        assertEquals("n", parser.nextCursor());
        assertEquals(1, parser.recordCount());
    }

    @Test
    public void overlappingPathsAreRejected() throws Exception
    {
        try {
            new PageParser(new FieldPlan(ID_NAME), "data", "data.next", null, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // data node must not contain metadata paths
        }
    }

    @Test
    public void emptyPathSegmentsAreRejected() throws Exception
    {
        for (final String bad : new String[] { "a..b", ".a", "a." }) {
            try {
                new PageParser(new FieldPlan(ID_NAME), bad, null, null, false);
                fail(bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
        try {
            new FieldPlan(Collections.singletonList(field("a..b", "VARCHAR")));
            fail();
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void oversizedFieldValueIsRejectedByTheFieldLimit() throws Exception
    {
        // 17M characters in one JSON-typed field exceed the 16M character per-field limit
        final StringBuilder sb = new StringBuilder("[{\"blob\":[\"");
        final String chunk = new String(new char[1 << 20]).replace('\0', 'x');
        for (int i = 0; i < 17; i++) {
            sb.append(chunk);
        }
        sb.append("\"]}]");
        try {
            parse(sb.toString(), null, fields("blob", "JSON"));
            fail("expected the field size limit to apply");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("limit")
                    || e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("exceed"));
        }
    }
}

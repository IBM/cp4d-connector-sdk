/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.io.NumberOutput;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/**
 * Single-pass streaming parser for one HTTP response page.
 *
 * <p>The response is consumed as a {@link JsonParser} token stream. It is never materialised as a
 * {@code JsonNode} tree, and no per-record tree is built either:
 * <ul>
 * <li>A {@link JsonPathTrie} of the configured data path / cursor path / next-url path drives the walk;
 * every branch outside those paths is skipped with {@link JsonParser#skipChildren()}.</li>
 * <li>Inside a record, a {@link FieldPlan} trie selects the configured fields; their values are converted
 * directly from the parser token into a reused {@code Object[]} row buffer.</li>
 * <li>Only a field whose value is a JSON object/array (declared as JSON, or an ancestor of another configured
 * field) is buffered, and only that one value, bounded by {@link #MAX_FIELD_JSON_CHARS}.</li>
 * </ul>
 *
 * <p><b>Row atomicity:</b> a record is converted completely into the row buffer before
 * {@code startRow/set/endRow} are called, so a malformed record never reaches the writer half-written. Records that
 * precede a failure <em>have</em> already been written; no page-level or request-level atomicity is provided.
 *
 * <p><b>Metadata position:</b> cursor / next-url values are captured wherever they occur, before or after the data.
 *
 * <p>Instances are not thread-safe and are meant to be reused for all pages of one stream.
 */
final class PageParser
{
    private static final Logger LOGGER = getLogger(PageParser.class);

    /** Maximum JSON nesting depth. */
    static final int MAX_NESTING_DEPTH = 256;
    /** Maximum length of a single JSON string value (characters). */
    static final int MAX_STRING_LENGTH = 20_000_000;
    /** Maximum length of a single JSON number token (characters). Bounds BigDecimal/BigInteger parsing cost. */
    static final int MAX_NUMBER_LENGTH = 1_000;
    /** Maximum length of an object member name (characters). */
    static final int MAX_NAME_LENGTH = 50_000;
    /** Maximum size of the serialised JSON text of one field value (characters). */
    static final int MAX_FIELD_JSON_CHARS = 16 * 1024 * 1024;

    /** Largest absolute scale accepted for a decimal; protects {@code BigDecimal.setScale} from absurd exponents. */
    private static final int MAX_DECIMAL_SCALE = 1_000;
    private static final int INTERRUPT_CHECK_MASK = 0x3FF;
    private static final int LOGGED_FAILURES_PER_FIELD = 3;
    private static final Pattern FRACTION_SUFFIX = Pattern.compile("\\.\\d+Z?$");

    /**
     * Shared, thread-safe factory. There is deliberately no document-length limit: a paged stream is unbounded by
     * design, the limits above apply per token.
     */
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_NESTING_DEPTH)
                    .maxStringLength(MAX_STRING_LENGTH)
                    .maxNumberLength(MAX_NUMBER_LENGTH)
                    .maxNameLength(MAX_NAME_LENGTH)
                    .build())
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION) // keep response content out of error messages
            .build();

    private final FieldPlan plan;
    private final JsonPathTrie trie = new JsonPathTrie();
    private final String dataPath;
    private final boolean dataPathConfigured;
    private final boolean strict;
    private final Object[] rowBuffer;
    private final long[] failureCounts;

    private RowWriter writer;
    private int recordCount;
    private String nextCursor;
    private String nextPageUrl;
    private boolean dataFound;

    /**
     * Creates a parser.
     *
     * @param plan
     *            the field mapping plan
     * @param dataPath
     *            dot-separated path to the data node; {@code null} or empty when the response root is the data
     * @param cursorPath
     *            path of the next-cursor value, or {@code null}
     * @param nextUrlPath
     *            path of the next-page URL, or {@code null}
     * @param strict
     *            whether conversion failures abort the stream instead of producing {@code NULL}
     * @throws IllegalArgumentException
     *             if a path is malformed or the paths overlap
     */
    PageParser(FieldPlan plan, String dataPath, String cursorPath, String nextUrlPath, boolean strict)
    {
        this.plan = plan;
        this.dataPath = dataPath;
        this.dataPathConfigured = dataPath != null && !dataPath.isEmpty();
        this.strict = strict;
        this.rowBuffer = new Object[plan.size()];
        this.failureCounts = new long[plan.size()];
        trie.register(dataPath, JsonPathTrie.Tag.DATA);
        if (cursorPath != null && !cursorPath.isEmpty()) {
            trie.register(cursorPath, JsonPathTrie.Tag.CURSOR);
        }
        if (nextUrlPath != null && !nextUrlPath.isEmpty()) {
            trie.register(nextUrlPath, JsonPathTrie.Tag.NEXT_URL);
        }
    }

    /** Opens a parser on the given stream using the shared, constrained factory. */
    static JsonParser open(InputStream in) throws IOException
    {
        return JSON_FACTORY.createParser(in);
    }

    /** Number of records written by the last {@link #parse} call. */
    int recordCount()
    {
        return recordCount;
    }

    /** Next-cursor value found by the last {@link #parse} call, or {@code null}. */
    String nextCursor()
    {
        return nextCursor;
    }

    /** Next-page URL found by the last {@link #parse} call, or {@code null}. */
    String nextPageUrl()
    {
        return nextPageUrl;
    }

    /**
     * Parses one page, writing each record to {@code rowWriter} as soon as it is complete.
     *
     * @throws IOException
     *             on malformed JSON, a missing/invalid data node, strict conversion failures, or I/O errors
     * @throws InterruptedException
     *             if the thread was interrupted while records were being processed
     */
    void parse(JsonParser parser, RowWriter rowWriter) throws IOException, InterruptedException
    {
        writer = rowWriter;
        recordCount = 0;
        nextCursor = null;
        nextPageUrl = null;
        dataFound = false;

        final JsonToken first = parser.nextToken();
        if (first == null) {
            if (dataPathConfigured) {
                throw dataPathNotFound();
            }
            return; // empty body without a data path: empty page
        }
        walk(parser, first, trie.root());
        if (dataPathConfigured && !dataFound) {
            throw dataPathNotFound();
        }
    }

    /** Clears the per-stream conversion failure counters. */
    void resetFailureCounts()
    {
        Arrays.fill(failureCounts, 0L);
    }

    /** Logs one summary line per field whose failures were not all logged individually. */
    void logFailureSummary()
    {
        for (int i = 0; i < failureCounts.length; i++) {
            if (failureCounts[i] > LOGGED_FAILURES_PER_FIELD) {
                LOGGER.warn("Field '{}' had {} value conversion failure(s); the values were set to NULL",
                        plan.nameAt(i), failureCounts[i]);
            }
        }
    }

    // ---- navigation through the data/metadata paths ----

    private void walk(JsonParser p, JsonToken token, JsonPathTrie.Node node) throws IOException, InterruptedException
    {
        if (node.isLeaf()) {
            switch (node.tag()) {
            case DATA:
                readData(p, token, node);
                break;
            case CURSOR:
                nextCursor = metadataText(p, token);
                break;
            case NEXT_URL:
            default:
                nextPageUrl = metadataText(p, token);
                break;
            }
        } else if (token == JsonToken.START_OBJECT) {
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                requireToken(t);
                final JsonPathTrie.Node child = node.child(p.currentName());
                final JsonToken valueToken = p.nextToken();
                if (child == null) {
                    skip(p, valueToken);
                } else {
                    walk(p, valueToken, child);
                }
            }
        } else if (token == JsonToken.START_ARRAY) {
            int index = 0;
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                requireToken(t);
                final JsonPathTrie.Node child = node.child(Integer.toString(index++));
                if (child == null) {
                    skip(p, t);
                } else {
                    walk(p, t, child);
                }
            }
        }
        // a scalar where the path continues: the path does not exist, nothing to do
    }

    /**
     * Reads the data node: an array yields one record per object element, an object yields one record, null is an
     * empty page, anything else is an error.
     */
    private void readData(JsonParser p, JsonToken token, JsonPathTrie.Node node) throws IOException, InterruptedException
    {
        dataFound = true;
        if (token == JsonToken.START_ARRAY) {
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                requireToken(t);
                if (t == JsonToken.START_OBJECT) {
                    readRecord(p, null);
                } else {
                    skip(p, t); // scalars and nested arrays are not records
                }
            }
        } else if (token == JsonToken.START_OBJECT) {
            // a root record may also carry pagination metadata (data path empty, cursor path configured)
            readRecord(p, node.hasChildren() ? node : null);
        } else if (token != JsonToken.VALUE_NULL) {
            throw new IOException("Expected a JSON array or object "
                    + (dataPathConfigured ? "at data path '" + dataPath + "'" : "as the response root")
                    + " but found " + token);
        }
    }

    // ---- record mapping ----

    private void readRecord(JsonParser p, JsonPathTrie.Node metadata) throws IOException, InterruptedException
    {
        Arrays.fill(rowBuffer, null);
        readObject(p, plan.root(), metadata);

        writer.startRow();
        for (int i = 0; i < rowBuffer.length; i++) {
            final Object value = rowBuffer[i];
            if (value != null) {
                writer.set(plan.nameAt(i), value);
            }
        }
        writer.endRow();

        recordCount++;
        if ((recordCount & INTERRUPT_CHECK_MASK) == 0 && Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted while streaming records");
        }
    }

    /** Reads the members of an object whose START_OBJECT was already consumed. */
    private void readObject(JsonParser p, FieldPlan.Node planNode, JsonPathTrie.Node metaNode) throws IOException
    {
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
            requireToken(t);
            final String key = p.currentName();
            final FieldPlan.Node planChild = planNode == null ? null : planNode.child(key);
            final JsonPathTrie.Node metaChild = metaNode == null ? null : metaNode.child(key);
            final JsonToken valueToken = p.nextToken();
            if (planChild == null && metaChild == null) {
                skip(p, valueToken);
            } else {
                readValue(p, valueToken, planChild, metaChild);
            }
        }
    }

    /**
     * Reads one value that is wanted by the field plan, by the metadata paths, or by both.
     *
     * <p>A container value that is needed both as JSON text and for its children is serialised once into a bounded
     * buffer and its children are then read from that buffer.
     */
    private void readValue(JsonParser p, JsonToken token, FieldPlan.Node planNode, JsonPathTrie.Node metaNode)
            throws IOException
    {
        final FieldPlan.Entry entry = planNode == null ? null : planNode.entry();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            final boolean wantsText = entry != null && entry.targetType != FieldPlan.TargetType.TEXT;
            final boolean planDescends = token == JsonToken.START_OBJECT && planNode != null && planNode.hasChildren();
            final boolean descends = planDescends || metaNode != null && metaNode.hasChildren();
            if (wantsText && descends) {
                final String text = copyToString(p);
                store(entry, text);
                try (JsonParser sub = JSON_FACTORY.createParser(text)) {
                    readChildren(sub, sub.nextToken(), planNode, metaNode);
                }
            } else if (wantsText) {
                store(entry, copyToString(p));
            } else if (descends) {
                readChildren(p, token, planNode, metaNode);
            } else {
                p.skipChildren();
            }
            return;
        }

        if (metaNode != null && metaNode.isLeaf()) {
            capture(metaNode.tag(), metadataText(p, token));
        }
        if (entry != null) {
            store(entry, convertScalar(p, token, entry));
        }
    }

    private void readChildren(JsonParser p, JsonToken token, FieldPlan.Node planNode, JsonPathTrie.Node metaNode)
            throws IOException
    {
        if (token == JsonToken.START_OBJECT) {
            readObject(p, planNode, metaNode);
        } else if (metaNode != null) {
            // arrays: only metadata paths may index into them, fields are never read through arrays
            int index = 0;
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                requireToken(t);
                final JsonPathTrie.Node child = metaNode.child(Integer.toString(index++));
                if (child == null) {
                    skip(p, t);
                } else {
                    readValue(p, t, null, child);
                }
            }
        } else {
            p.skipChildren();
        }
    }

    private void store(FieldPlan.Entry entry, Object value)
    {
        rowBuffer[entry.index] = value; // last duplicate key wins, like the former ObjectNode
    }

    private void capture(JsonPathTrie.Tag tag, String text)
    {
        if (tag == JsonPathTrie.Tag.CURSOR) {
            nextCursor = text;
        } else if (tag == JsonPathTrie.Tag.NEXT_URL) {
            nextPageUrl = text;
        }
    }

    // ---- scalar conversion ----

    private Object convertScalar(JsonParser p, JsonToken token, FieldPlan.Entry entry) throws IOException
    {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        try {
            switch (entry.targetType) {
            case INT:
                return convertInt(p, token);
            case BIGINT:
                return convertLong(p, token);
            case BOOLEAN:
                return convertBoolean(p, token);
            case DOUBLE:
                return isNumber(token) ? (Object) p.getDoubleValue() : convertDouble(p.getText());
            case DECIMAL:
                return convertDecimal(p, token);
            case DATE:
                return convertDate(scalarText(p, token));
            case TIMESTAMP:
                return convertTimestamp(scalarText(p, token));
            case JSON:
                return copyToString(p); // a scalar declared as JSON is its JSON text, e.g. a quoted string
            case VARCHAR:
            case TEXT:
            default:
                return scalarText(p, token);
            }
        } catch (IllegalArgumentException | DateTimeException e) {
            return conversionFailed(entry);
        }
    }

    /**
     * Text of a scalar as the former {@code JsonNode.asText()} produced it: floating-point numbers were
     * parsed as {@code double} and printed in canonical form; everything else is its token text.
     */
    private static String scalarText(JsonParser p, JsonToken token) throws IOException
    {
        return token == JsonToken.VALUE_NUMBER_FLOAT ? NumberOutput.toString(p.getDoubleValue()) : p.getText();
    }

    private static boolean isNumber(JsonToken token)
    {
        return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
    }

    /** Narrowing conversion identical to {@code JsonNode.intValue()}. */
    private static Object convertInt(JsonParser p, JsonToken token) throws IOException
    {
        if (isNumber(token)) {
            switch (p.getNumberType()) {
            case INT:
                return p.getIntValue();
            case LONG:
                return (int) p.getLongValue();
            case BIG_INTEGER:
                return p.getBigIntegerValue().intValue();
            default:
                return (int) p.getDoubleValue();
            }
        }
        final String text = p.getText();
        return text.isEmpty() ? null : Integer.valueOf(text.trim());
    }

    /** Narrowing conversion identical to {@code JsonNode.longValue()}. */
    private static Object convertLong(JsonParser p, JsonToken token) throws IOException
    {
        if (isNumber(token)) {
            switch (p.getNumberType()) {
            case INT:
            case LONG:
                return p.getLongValue();
            case BIG_INTEGER:
                return p.getBigIntegerValue().longValue();
            default:
                return (long) p.getDoubleValue();
            }
        }
        final String text = p.getText();
        return text.isEmpty() ? null : Long.valueOf(text.trim());
    }

    private static Object convertBoolean(JsonParser p, JsonToken token) throws IOException
    {
        if (token == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        final String text = p.getText();
        if (text.isEmpty()) {
            return null;
        }
        final String lower = text.trim().toLowerCase(Locale.ENGLISH);
        if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("not a boolean");
    }

    private static Object convertDouble(String text)
    {
        return text.isEmpty() ? null : Double.valueOf(text.trim());
    }

    /**
     * Decimals are taken from the token text, never through {@code double}. (The legacy tree code went through
     * {@code BigDecimal.valueOf(double)} for fractional numbers, which loses digits beyond ~17 significant ones.)
     */
    private static Object convertDecimal(JsonParser p, JsonToken token) throws IOException
    {
        final BigDecimal value;
        if (isNumber(token)) {
            value = p.getDecimalValue();
        } else {
            final String text = p.getText();
            if (text.isEmpty()) {
                return null;
            }
            value = new BigDecimal(text.trim());
        }
        if (Math.abs((long) value.scale()) > MAX_DECIMAL_SCALE) {
            throw new IllegalArgumentException("decimal exponent out of range");
        }
        return value;
    }

    private static Object convertDate(String text)
    {
        return text.isEmpty() ? null : Date.valueOf(LocalDate.parse(text.trim()));
    }

    private static Object convertTimestamp(String text)
    {
        if (text.isEmpty()) {
            return null;
        }
        final String trimmed = text.trim();
        try {
            return Timestamp.from(Instant.parse(trimmed));
        } catch (DateTimeException e) {
            return Timestamp.valueOf(FRACTION_SUFFIX.matcher(trimmed.replace('T', ' ')).replaceAll(""));
        }
    }

    /**
     * Handles a failed conversion without ever logging the offending value (it may be sensitive).
     *
     * @return {@code null} in non-strict mode
     * @throws IOException
     *             in strict mode
     */
    private Object conversionFailed(FieldPlan.Entry entry) throws IOException
    {
        if (strict) {
            throw new IOException("Cannot convert the value of field '" + entry.name + "' to "
                    + entry.targetType + " (strict parsing is enabled)");
        }
        final long count = ++failureCounts[entry.index];
        if (count <= LOGGED_FAILURES_PER_FIELD) {
            LOGGER.warn("Cannot convert the value of field '{}' to {}; setting it to NULL", entry.name,
                    entry.targetType);
        } else if (count == LOGGED_FAILURES_PER_FIELD + 1) {
            LOGGER.warn("Further conversion failures for field '{}' are counted but not logged individually",
                    entry.name);
        }
        return null;
    }

    // ---- helpers ----

    private static String metadataText(JsonParser p, JsonToken token) throws IOException
    {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            p.skipChildren();
            return null;
        }
        return p.getText();
    }

    private static void skip(JsonParser p, JsonToken token) throws IOException
    {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            p.skipChildren();
        }
    }

    private static void requireToken(JsonToken token) throws IOException
    {
        if (token == null) {
            throw new IOException("Unexpected end of JSON input");
        }
    }

    private IOException dataPathNotFound()
    {
        return new IOException("Data path '" + dataPath + "' not found in JSON response");
    }

    /**
     * Serialises the value at the current parser position (scalar or container) as compact JSON. Numbers keep
     * their source text, so no precision is lost.
     */
    private static String copyToString(JsonParser p) throws IOException
    {
        final BoundedWriter out = new BoundedWriter(MAX_FIELD_JSON_CHARS);
        try (JsonGenerator g = JSON_FACTORY.createGenerator(out)) {
            int depth = 0;
            JsonToken t = p.currentToken();
            while (t != null) {
                switch (t) {
                case START_OBJECT:
                    g.writeStartObject();
                    depth++;
                    break;
                case END_OBJECT:
                    g.writeEndObject();
                    depth--;
                    break;
                case START_ARRAY:
                    g.writeStartArray();
                    depth++;
                    break;
                case END_ARRAY:
                    g.writeEndArray();
                    depth--;
                    break;
                case FIELD_NAME:
                    g.writeFieldName(p.currentName());
                    break;
                case VALUE_STRING:
                    g.writeString(p.getText());
                    break;
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                    g.writeNumber(p.getText());
                    break;
                case VALUE_TRUE:
                    g.writeBoolean(true);
                    break;
                case VALUE_FALSE:
                    g.writeBoolean(false);
                    break;
                case VALUE_NULL:
                    g.writeNull();
                    break;
                default:
                    throw new IOException("Unsupported JSON token " + t);
                }
                if (depth == 0) {
                    break;
                }
                t = p.nextToken();
            }
            requireToken(t);
        }
        return out.toString();
    }

    /**
     * Character sink with a hard size limit, so one huge field value cannot exhaust the heap. Instances live for
     * the duration of one field value only.
     */
    @SuppressWarnings("PMD.AvoidStringBufferField")
    private static final class BoundedWriter extends Writer
    {
        private final StringBuilder buffer = new StringBuilder(128);
        private final int limit;

        BoundedWriter(int limit)
        {
            super();
            this.limit = limit;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException
        {
            if (buffer.length() + (long) len > limit) {
                throw new IOException("The JSON text of a single field value exceeds " + limit + " characters");
            }
            buffer.append(cbuf, off, len);
        }

        @Override
        public void flush()
        {
            // in-memory sink, nothing to flush
        }

        @Override
        public void close()
        {
            // in-memory sink, nothing to release
        }

        @Override
        public String toString()
        {
            return buffer.toString();
        }
    }
}

/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import com.ibm.connect.restconnector.StreamTestSupport.Reply;
import com.ibm.connect.restconnector.StreamTestSupport.ScriptedSender;
import com.ibm.wdp.connect.sdk.connector.ArrowBatchWriter;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/**
 * Child-JVM entry point of the memory tests ({@link TestStreamMemory}). It streams a synthetic, lazily generated
 * response through {@link JsonToArrowStream} so that neither the response nor the dataset ever exists in memory,
 * and prints one {@code RESULT} line.
 *
 * <p>Arguments: {@code mode records pageSize probeEvery}
 * <ul>
 * <li>{@code mode}: {@code noop} (a counting writer: isolates the parser) or {@code arrow} (the real
 * {@link ArrowBatchWriter}, batch size 1000, batches discarded after being "sent")</li>
 * <li>{@code pageSize}: records per page with offset pagination, or {@code 0} for one single huge response</li>
 * <li>{@code probeEvery}: every n-th record a full GC is run and the live heap after it is recorded (0 = never,
 * which gives undisturbed timings)</li>
 * </ul>
 */
public final class StreamMemoryProbe
{
    private static final int BATCH_SIZE = 1000;

    private StreamMemoryProbe()
    {
    }

    /** Generates {@code count} records starting at {@code from} as a JSON array, in small chunks. */
    static final class GeneratedArray extends InputStream
    {
        private final long from;
        private final long count;
        private long next;
        private byte[] chunk = new byte[0];
        private int pos;
        private boolean opened;
        private boolean closedArray;

        GeneratedArray(long from, long count)
        {
            super();
            this.from = from;
            this.count = count;
            this.next = from;
        }

        private boolean fill()
        {
            if (closedArray) {
                return false;
            }
            final StringBuilder sb = new StringBuilder(8192);
            if (!opened) {
                sb.append('[');
                opened = true;
            }
            while (sb.length() < 6000 && next < from + count) {
                if (next > from) {
                    sb.append(',');
                }
                sb.append("{\"id\":").append(next).append(",\"name\":\"name-").append(next)
                        .append("\",\"score\":").append(next % 1000).append(".25,\"tags\":[\"a\",\"b\",")
                        .append(next % 7).append("],\"user\":{\"id\":").append(next % 100)
                        .append(",\"extra\":{\"x\":[1,2,3]}},\"unused\":[{\"k\":\"v\"},{\"k\":\"w\"}]}");
                next++;
            }
            if (next >= from + count) {
                sb.append(']');
                closedArray = true;
            }
            chunk = sb.toString().getBytes(StandardCharsets.UTF_8);
            pos = 0;
            return true;
        }

        @Override
        public int read() throws IOException
        {
            if (pos >= chunk.length && !fill()) {
                return -1;
            }
            return chunk[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            if (pos >= chunk.length && !fill()) {
                return -1;
            }
            final int n = Math.min(len, chunk.length - pos);
            System.arraycopy(chunk, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    /** Counts rows; optionally runs a GC every n rows and records the live heap that remains. */
    static final class ProbingWriter implements RowWriter
    {
        private final RowWriter delegate;
        private final long probeEvery;
        long rows;
        long maxLiveAfterGc;

        ProbingWriter(RowWriter delegate, long probeEvery)
        {
            this.delegate = delegate;
            this.probeEvery = probeEvery;
        }

        @Override
        public void startRow()
        {
            delegate.startRow();
        }

        @Override
        public void set(String fieldName, Object value)
        {
            delegate.set(fieldName, value);
        }

        @Override
        public void endRow()
        {
            delegate.endRow();
            rows++;
            if (probeEvery > 0 && rows % probeEvery == 0) {
                System.gc(); // NOPMD - the probe measures the live heap after a collection
                final long live = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                maxLiveAfterGc = Math.max(maxLiveAfterGc, live);
            }
        }
    }

    static List<RestFieldDefinition> fieldDefs()
    {
        return StreamTestSupport.fields("id", "INTEGER", "name", "VARCHAR(30)", "score", "DOUBLE", "tags", "ARRAY",
                "user.id", "BIGINT", "user.extra", "JSON");
    }

    static Schema arrowSchema()
    {
        return new Schema(Arrays.asList(
                new Field("id", FieldType.nullable(new ArrowType.Int(32, true)), null),
                new Field("name", FieldType.nullable(ArrowType.Utf8.INSTANCE), null),
                new Field("score", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null),
                new Field("tags", FieldType.nullable(ArrowType.Utf8.INSTANCE), null),
                new Field("user.id", FieldType.nullable(new ArrowType.Int(64, true)), null),
                new Field("user.extra", FieldType.nullable(ArrowType.Utf8.INSTANCE), null)));
    }

    /** Offset of the request, taken from the {@code offset=} query parameter. */
    private static long offsetOf(URI uri)
    {
        final String query = uri.getRawQuery();
        if (query != null) {
            for (final String pair : query.split("&")) {
                if (pair.startsWith("offset=")) {
                    return Long.parseLong(pair.substring("offset=".length()));
                }
            }
        }
        return 0;
    }

    public static void main(String[] args) throws Exception
    {
        final String mode = args[0];
        final long records = Long.parseLong(args[1]);
        final int pageSize = Integer.parseInt(args[2]);
        final long probeEvery = Long.parseLong(args[3]);

        final ScriptedSender sender = new ScriptedSender(request -> {
            final long from = pageSize > 0 ? offsetOf(request.uri()) : 0;
            final long count = pageSize > 0 ? Math.max(0, Math.min(pageSize, records - from)) : records;
            return Reply.of(200, new GeneratedArray(from, count));
        });
        sender.keepHistory = false;

        final PaginationConfig pagination = pageSize > 0 ? StreamTestSupport.offset(pageSize) : null;
        final long[] peakUsed = { 0 };
        final boolean[] running = { true };
        final Thread sampler = new Thread(() -> {
            while (running[0]) {
                peakUsed[0] = Math.max(peakUsed[0], ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        sampler.setDaemon(true);

        long peakOffHeap = 0;
        final ProbingWriter writer;
        final long start;
        final long elapsedNanos;
        if ("legacytree".equals(mode)) {
            // the former behaviour for dataPath / cursor / next_url: the whole response becomes a JsonNode tree
            writer = new ProbingWriter(new RowWriter()
            {
                @Override
                public void startRow()
                {
                    // discard
                }

                @Override
                public void set(String fieldName, Object value)
                {
                    // discard
                }

                @Override
                public void endRow()
                {
                    // discard
                }
            }, probeEvery);
            sampler.start();
            start = System.nanoTime();
            final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try (InputStream body = new GeneratedArray(0, records)) {
                final com.fasterxml.jackson.databind.JsonNode tree = mapper.readTree(body);
                for (final com.fasterxml.jackson.databind.JsonNode record : tree) {
                    TestParityWithLegacy.LegacyReference.map(record, fieldDefs());
                    writer.startRow();
                    writer.endRow();
                }
            }
            elapsedNanos = System.nanoTime() - start;
        } else if ("arrow".equals(mode)) {
            try (RootAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
                final long[] batches = { 0 };
                @SuppressWarnings("PMD.CloseResource") // closed explicitly below, before the allocator
                final ArrowBatchWriter arrow = new ArrowBatchWriter(arrowSchema(), allocator, BATCH_SIZE,
                        root -> batches[0]++);
                writer = new ProbingWriter(arrow, probeEvery);
                sampler.start();
                start = System.nanoTime();
                try (JsonToArrowStream stream = StreamTestSupport.stream(sender, "https://api.example.test/data", null,
                        fieldDefs(), null, pagination, false)) {
                    stream.streamTo(writer);
                }
                arrow.close();
                elapsedNanos = System.nanoTime() - start;
                peakOffHeap = allocator.getPeakMemoryAllocation();
            }
        } else {
            writer = new ProbingWriter(new RowWriter()
            {
                @Override
                public void startRow()
                {
                    // discard
                }

                @Override
                public void set(String fieldName, Object value)
                {
                    // discard
                }

                @Override
                public void endRow()
                {
                    // discard
                }
            }, probeEvery);
            sampler.start();
            start = System.nanoTime();
            try (JsonToArrowStream stream = StreamTestSupport.stream(sender, "https://api.example.test/data", null,
                    fieldDefs(), null, pagination, false)) {
                stream.streamTo(writer);
            }
            elapsedNanos = System.nanoTime() - start;
        }
        running[0] = false;

        if (writer.rows != records) {
            System.out.println("RESULT error=row-count expected=" + records + " actual=" + writer.rows);
            System.exit(2);
        }
        System.out.println("RESULT mode=" + mode + " records=" + records + " pageSize=" + pageSize
                + " pages=" + sender.calls() + " millis=" + elapsedNanos / 1_000_000
                + " peakHeapUsedMB=" + peakUsed[0] / (1024 * 1024)
                + " maxLiveAfterGcMB=" + writer.maxLiveAfterGc / (1024 * 1024)
                + " peakOffHeapKB=" + peakOffHeap / 1024
                + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }
}

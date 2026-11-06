/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Memory behaviour of {@link JsonToArrowStream}, measured in child JVMs with a small fixed heap
 * ({@link StreamMemoryProbe}).
 *
 * <p>The responses are generated lazily, so neither the response nor the dataset ever exists in memory, apart from
 * what the code under test retains. A regression that buffers a page, the response or the dataset therefore either
 * fails the live-heap assertions or dies with {@code OutOfMemoryError} in the 48 MB heap.
 *
 * <p>Parser memory ({@code noop} writer) and the real {@code ArrowBatchWriter} (off-heap batch of 1000 rows) are
 * measured separately. Larger runs and a benchmark table are enabled with {@code -Drest.stream.big=true}.
 */
public class TestStreamMemory
{
    private static final String BIG_PROPERTY = "rest.stream.big";
    private static final int SMALL_HEAP_MB = 48;
    private static final int PROBE_EVERY = 5000;

    private static Map<String, String> run(String mode, long records, int pageSize, long probeEvery, int heapMb)
            throws Exception
    {
        final List<String> command = new ArrayList<>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        command.add("-Xmx" + heapMb + "m");
        command.add("-Xms" + heapMb + "m");
        command.add("-XX:+UseSerialGC");
        command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        command.add("--add-opens=java.base/sun.util.calendar=ALL-UNNAMED");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(StreamMemoryProbe.class.getName());
        command.add(mode);
        command.add(Long.toString(records));
        command.add(Integer.toString(pageSize));
        command.add(Long.toString(probeEvery));

        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final StringBuilder output = new StringBuilder();
        String result = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (line.startsWith("RESULT ")) {
                    result = line;
                }
            }
        }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("memory probe timed out");
        }
        assertTrue("probe failed (exit " + process.exitValue() + "):\n" + output, result != null && process.exitValue() == 0);
        final Map<String, String> values = new HashMap<>();
        for (final String pair : result.substring("RESULT ".length()).split(" ")) {
            final int eq = pair.indexOf('=');
            values.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        System.out.println("[memory] " + result);
        return values;
    }

    private static long number(Map<String, String> result, String key)
    {
        return Long.parseLong(result.get(key));
    }

    // ---- default suite: 100k records ----

    @Test
    public void parserOnlyOneHundredThousandRecordsInPagesFitIntoASmallHeap() throws Exception
    {
        final Map<String, String> r = run("noop", 100_000, 1000, PROBE_EVERY, SMALL_HEAP_MB);
        assertEquals(100, number(r, "pages") - 1); // 100 full pages + one empty page for the exact boundary
        assertTrue("live heap after GC: " + r, number(r, "maxLiveAfterGcMB") < 24);
    }

    @Test
    public void parserOnlyOneHundredThousandRecordsInOneResponseFitIntoASmallHeap() throws Exception
    {
        // ~25 MB of JSON in a single response must be streamed, not read into memory
        final Map<String, String> r = run("noop", 100_000, 0, PROBE_EVERY, SMALL_HEAP_MB);
        assertEquals(1, number(r, "pages"));
        assertTrue("live heap after GC: " + r, number(r, "maxLiveAfterGcMB") < 24);
    }

    @Test
    public void realArrowWriterOneHundredThousandRecords() throws Exception
    {
        final Map<String, String> r = run("arrow", 100_000, 1000, PROBE_EVERY, SMALL_HEAP_MB);
        assertTrue("live heap after GC: " + r, number(r, "maxLiveAfterGcMB") < 24);
        assertTrue("one batch of 1000 rows stays small off-heap: " + r, number(r, "peakOffHeapKB") < 64 * 1024);
    }

    @Test
    public void liveHeapDoesNotGrowWithTheNumberOfRecords() throws Exception
    {
        final Map<String, String> small = run("noop", 50_000, 1000, PROBE_EVERY, SMALL_HEAP_MB);
        final Map<String, String> large = run("noop", 200_000, 1000, PROBE_EVERY, SMALL_HEAP_MB);
        final long smallLive = number(small, "maxLiveAfterGcMB");
        final long largeLive = number(large, "maxLiveAfterGcMB");
        // four times the records (and pages) must not need more heap beyond measurement noise
        assertTrue("small=" + smallLive + "MB large=" + largeLive + "MB", largeLive <= smallLive + 6);
    }

    // ---- opt-in: 1M records and benchmark table ----

    @Test
    public void oneMillionRecordsWithTheRealArrowWriter() throws Exception
    {
        assumeTrue("enable with -D" + BIG_PROPERTY + "=true", Boolean.getBoolean(BIG_PROPERTY));
        final Map<String, String> r = run("arrow", 1_000_000, 1000, 50_000, SMALL_HEAP_MB);
        assertTrue("live heap after GC: " + r, number(r, "maxLiveAfterGcMB") < 24);
    }

    @Test
    public void benchmarkTable() throws Exception
    {
        assumeTrue("enable with -D" + BIG_PROPERTY + "=true", Boolean.getBoolean(BIG_PROPERTY));
        final long[] sizes = { 100_000, 400_000, 1_000_000 };
        for (final String mode : new String[] { "noop", "arrow" }) {
            for (final long records : sizes) {
                // timing run without forced GCs, then a probing run for the live heap
                final Map<String, String> timing = run(mode, records, 1000, 0, SMALL_HEAP_MB);
                final Map<String, String> probe = run(mode, records, 1000, Math.max(PROBE_EVERY, records / 20),
                        SMALL_HEAP_MB);
                System.out.println("[benchmark] mode=" + mode + " records=" + records + " pageSize=1000 pages="
                        + timing.get("pages") + " millis=" + timing.get("millis") + " peakHeapUsedMB="
                        + timing.get("peakHeapUsedMB") + " maxLiveAfterGcMB=" + probe.get("maxLiveAfterGcMB")
                        + " peakOffHeapKB=" + timing.get("peakOffHeapKB") + " maxHeapMB=" + SMALL_HEAP_MB);
            }
        }
        // the former behaviour (whole response as a JsonNode tree) for comparison, with a heap that lets it finish
        for (final long records : new long[] { 100_000, 200_000, 400_000 }) {
            final Map<String, String> legacy = run("legacytree", records, 0, 0, 1024);
            final Map<String, String> legacyProbe = run("legacytree", records, 0, Math.max(PROBE_EVERY, records / 20), 1024);
            System.out.println("[benchmark] mode=legacytree records=" + records + " pageSize=single-response pages=1 millis="
                    + legacy.get("millis") + " peakHeapUsedMB=" + legacy.get("peakHeapUsedMB")
                    + " liveAfterGcMB=" + legacyProbe.get("maxLiveAfterGcMB") + " maxHeapMB=1024");
        }

        // a single huge response as well
        final Map<String, String> single = run("noop", 1_000_000, 0, 0, SMALL_HEAP_MB);
        System.out.println("[benchmark] mode=noop records=1000000 pageSize=single-response pages="
                + single.get("pages") + " millis=" + single.get("millis") + " peakHeapUsedMB="
                + single.get("peakHeapUsedMB") + " maxHeapMB=" + SMALL_HEAP_MB);
    }
}

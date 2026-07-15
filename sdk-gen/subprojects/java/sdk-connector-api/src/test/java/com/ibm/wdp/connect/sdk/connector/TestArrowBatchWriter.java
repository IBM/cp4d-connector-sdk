/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ArrowBatchWriter}.
 */
public class TestArrowBatchWriter
{
    private RootAllocator allocator;
    private Schema schema;

    @Before
    public void setUp()
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        schema = new Schema(Arrays.asList(
                new Field("id", new FieldType(false, new ArrowType.Int(32, true), null), null),
                new Field("name", new FieldType(true, ArrowType.Utf8.INSTANCE, null), null),
                new Field("score", new FieldType(true, new ArrowType.FloatingPoint(
                        org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE), null), null)));
    }

    @After
    public void tearDown()
    {
        allocator.close();
    }

    @Test
    public void testWriteAndReadSingleBatch() throws Exception
    {
        try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 100)) {
            writer.startRow();
            writer.set("id", 1);
            writer.set("name", "Alice");
            writer.set("score", 9.5);
            writer.endRow();

            writer.startRow();
            writer.set("id", 2);
            writer.set("name", "Bob");
            writer.set("score", 8.0);
            writer.endRow();

            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertTrue(it.hasNext());
            final VectorSchemaRoot root = it.next();
            assertEquals(2, root.getRowCount());
            root.close();
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testAutoFlushAtBatchSize() throws Exception
    {
        final int batchSize = 3;
        try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, batchSize)) {
            for (int i = 0; i < 7; i++) {
                writer.startRow();
                writer.set("id", i);
                writer.set("name", "row" + i);
                writer.set("score", (double) i);
                writer.endRow();
            }

            int totalRows = 0;
            int batchCount = 0;
            final Iterator<VectorSchemaRoot> it = writer.batches();
            while (it.hasNext()) {
                final VectorSchemaRoot root = it.next();
                totalRows += root.getRowCount();
                batchCount++;
                root.close();
            }
            assertEquals(7, totalRows);
            // 7 rows with batchSize=3: two full batches flush at row 3 and 6, one partial at end
            assertEquals(3, batchCount);
        }
    }

    @Test
    public void testNullValue() throws Exception
    {
        try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 100)) {
            writer.startRow();
            writer.set("id", 42);
            writer.set("name", null);
            writer.set("score", null);
            writer.endRow();

            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertTrue(it.hasNext());
            final VectorSchemaRoot root = it.next();
            assertEquals(1, root.getRowCount());
            assertTrue(root.getVector("name").isNull(0));
            assertTrue(root.getVector("score").isNull(0));
            root.close();
        }
    }

    @Test
    public void testEmptyWriter() throws Exception
    {
        try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 100)) {
            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testGetSchema()
    {
        try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 100)) {
            assertEquals(schema, writer.getSchema());
        }
    }
}

// Made with Bob

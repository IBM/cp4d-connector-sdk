/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;

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
 * Unit tests for {@link ColumnarArrowBatchWriter}.
 */
public class TestColumnarArrowBatchWriter
{
    private RootAllocator allocator;
    private Schema schema;

    @Before
    public void setUp()
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        schema = new Schema(Arrays.asList(
                new Field("id", new FieldType(false, new ArrowType.Int(32, true), null), null),
                new Field("value", new FieldType(true, ArrowType.Utf8.INSTANCE, null), null)));
    }

    @After
    public void tearDown()
    {
        allocator.close();
    }

    @Test
    public void testWriteColumnarBatch() throws Exception
    {
        try (ColumnarArrowBatchWriter writer = new ColumnarArrowBatchWriter(schema, allocator, 100)) {
            writer.writeColumn("id", new Object[]{ 1, 2, 3 });
            writer.writeColumn("value", new Object[]{ "alpha", "beta", "gamma" });
            writer.flushBatch();

            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertTrue(it.hasNext());
            final VectorSchemaRoot root = it.next();
            assertEquals(3, root.getRowCount());
            root.close();
            assertFalse(it.hasNext());
        }
    }

    @Test
    public void testMultipleBatches() throws Exception
    {
        try (ColumnarArrowBatchWriter writer = new ColumnarArrowBatchWriter(schema, allocator, 100)) {
            // Batch 1
            writer.writeColumn("id", new Object[]{ 1, 2 });
            writer.writeColumn("value", new Object[]{ "a", "b" });
            writer.flushBatch();
            // Batch 2
            writer.writeColumn("id", new Object[]{ 3 });
            writer.writeColumn("value", new Object[]{ "c" });
            writer.flushBatch();

            int totalRows = 0;
            final Iterator<VectorSchemaRoot> it = writer.batches();
            while (it.hasNext()) {
                final VectorSchemaRoot root = it.next();
                totalRows += root.getRowCount();
                root.close();
            }
            assertEquals(3, totalRows);
        }
    }

    @Test
    public void testNullColumn() throws Exception
    {
        try (ColumnarArrowBatchWriter writer = new ColumnarArrowBatchWriter(schema, allocator, 100)) {
            writer.writeColumn("id", new Object[]{ 10 });
            writer.writeColumn("value", new Object[]{ null });
            writer.flushBatch();

            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertTrue(it.hasNext());
            final VectorSchemaRoot root = it.next();
            assertEquals(1, root.getRowCount());
            assertTrue(root.getVector("value").isNull(0));
            root.close();
        }
    }

    @Test
    public void testEmptyWriter() throws Exception
    {
        try (ColumnarArrowBatchWriter writer = new ColumnarArrowBatchWriter(schema, allocator, 100)) {
            final Iterator<VectorSchemaRoot> it = writer.batches();
            assertFalse(it.hasNext());
        }
    }
}

// Made with Bob

/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ColumnarArrowBatchReader}.
 */
public class TestColumnarArrowBatchReader
{
    private RootAllocator allocator;
    private Schema schema;

    @Before
    public void setUp()
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        schema = new Schema(Arrays.asList(
                new Field("id", new FieldType(false, new ArrowType.Int(32, true), null), null),
                new Field("label", new FieldType(true, ArrowType.Utf8.INSTANCE, null), null)));
    }

    @After
    public void tearDown()
    {
        allocator.close();
    }

    private List<VectorSchemaRoot> makeBatches(int rows1, int rows2)
    {
        final List<VectorSchemaRoot> batches = new ArrayList<>();
        for (final int count : new int[]{ rows1, rows2 }) {
            final VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
            root.allocateNew();
            final IntVector idVec = (IntVector) root.getVector("id");
            final VarCharVector labelVec = (VarCharVector) root.getVector("label");
            for (int i = 0; i < count; i++) {
                idVec.setSafe(i, i + 1);
                labelVec.setSafe(i, new Text("val" + i));
            }
            root.setRowCount(count);
            batches.add(root);
        }
        return batches;
    }

    @Test
    public void testIterateBatches() throws Exception
    {
        final List<VectorSchemaRoot> batches = makeBatches(3, 2);
        try (ColumnarArrowBatchReader reader = new ColumnarArrowBatchReader(batches)) {
            assertTrue(reader.nextBatch());
            final Object[] ids = reader.getColumn("id");
            assertEquals(3, ids.length);

            assertTrue(reader.nextBatch());
            final Object[] ids2 = reader.getColumn("id");
            assertEquals(2, ids2.length);

            assertFalse(reader.nextBatch());
        }
        batches.forEach(VectorSchemaRoot::close);
    }

    @Test
    public void testNullInColumn() throws Exception
    {
        final VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
        root.allocateNew();
        final IntVector idVec = (IntVector) root.getVector("id");
        final VarCharVector labelVec = (VarCharVector) root.getVector("label");
        idVec.setSafe(0, 7);
        labelVec.setNull(0);
        root.setRowCount(1);

        final List<VectorSchemaRoot> batches = new ArrayList<>();
        batches.add(root);

        try (ColumnarArrowBatchReader reader = new ColumnarArrowBatchReader(batches)) {
            assertTrue(reader.nextBatch());
            final Object[] labels = reader.getColumn("label");
            assertEquals(1, labels.length);
            assertNull(labels[0]);
            assertFalse(reader.nextBatch());
        }
        root.close();
    }

    @Test
    public void testEmptyBatchList() throws Exception
    {
        final List<VectorSchemaRoot> batches = new ArrayList<>();
        try (ColumnarArrowBatchReader reader = new ColumnarArrowBatchReader(batches)) {
            assertFalse(reader.nextBatch());
        }
    }
}

// Made with Bob

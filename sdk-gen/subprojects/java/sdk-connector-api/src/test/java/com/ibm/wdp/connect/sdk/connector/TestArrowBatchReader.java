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
import java.util.Iterator;
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
 * Unit tests for {@link ArrowBatchReader}.
 */
public class TestArrowBatchReader
{
    private RootAllocator allocator;
    private Schema schema;

    @Before
    public void setUp()
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        schema = new Schema(Arrays.asList(
                new Field("id", new FieldType(false, new ArrowType.Int(32, true), null), null),
                new Field("name", new FieldType(true, ArrowType.Utf8.INSTANCE, null), null)));
    }

    @After
    public void tearDown()
    {
        allocator.close();
    }

    private List<VectorSchemaRoot> makeBatches(int... rowCounts)
    {
        final List<VectorSchemaRoot> batches = new ArrayList<>();
        int idCounter = 1;
        for (final int count : rowCounts) {
            final VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
            root.allocateNew();
            final IntVector idVec = (IntVector) root.getVector("id");
            final VarCharVector nameVec = (VarCharVector) root.getVector("name");
            for (int i = 0; i < count; i++) {
                idVec.setSafe(i, idCounter++);
                nameVec.setSafe(i, new Text("name" + i));
            }
            root.setRowCount(count);
            batches.add(root);
        }
        return batches;
    }

    @Test
    public void testIterateRows() throws Exception
    {
        final List<VectorSchemaRoot> batches = makeBatches(2, 3);
        try (ArrowBatchReader reader = new ArrowBatchReader(batches)) {
            int count = 0;
            while (reader.nextRow()) {
                count++;
                final Object id = reader.get("id");
                final Object name = reader.get("name");
                assertFalse(id == null);
                assertFalse(name == null);
            }
            assertEquals(5, count);
        }
        batches.forEach(VectorSchemaRoot::close);
    }

    @Test
    public void testEmptyBatchList() throws Exception
    {
        final List<VectorSchemaRoot> batches = new ArrayList<>();
        try (ArrowBatchReader reader = new ArrowBatchReader(batches)) {
            assertFalse(reader.nextRow());
        }
    }

    @Test
    public void testNullValue() throws Exception
    {
        final VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
        root.allocateNew();
        final IntVector idVec = (IntVector) root.getVector("id");
        final VarCharVector nameVec = (VarCharVector) root.getVector("name");
        idVec.setSafe(0, 99);
        nameVec.setNull(0);
        root.setRowCount(1);

        final List<VectorSchemaRoot> batches = new ArrayList<>();
        batches.add(root);

        try (ArrowBatchReader reader = new ArrowBatchReader(batches)) {
            assertTrue(reader.nextRow());
            assertEquals(99, reader.get("id"));
            assertNull(reader.get("name"));
            assertFalse(reader.nextRow());
        }
        root.close();
    }
}

// Made with Bob

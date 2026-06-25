/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector.forge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.Test;

/**
 * Unit tests for {@link ForgeSchemaBuilder}.
 */
public class TestForgeSchemaBuilder
{
    @Test
    public void testBuildSchemaBasicTypes()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("id", "INTEGER", true, true),
                new RestFieldDefinition("name", "VARCHAR", false, false),
                new RestFieldDefinition("count", "BIGINT", false, false),
                new RestFieldDefinition("active", "BOOLEAN", false, false),
                new RestFieldDefinition("score", "DOUBLE", false, false),
                new RestFieldDefinition("ratio", "FLOAT", false, false),
                new RestFieldDefinition("created", "DATE", false, false),
                new RestFieldDefinition("modified", "TIMESTAMP", false, false));

        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertNotNull(schema);
        assertEquals(8, schema.getFields().size());

        // id — Integer, not nullable
        final Field id = findField(schema, "id");
        assertTrue(id.getType() instanceof ArrowType.Int);
        assertEquals(32, ((ArrowType.Int) id.getType()).getBitWidth());
        assertFalse(id.isNullable());

        // name — Utf8, nullable
        final Field name = findField(schema, "name");
        assertTrue(name.getType() instanceof ArrowType.Utf8);
        assertTrue(name.isNullable());

        // count — Int64
        final Field count = findField(schema, "count");
        assertTrue(count.getType() instanceof ArrowType.Int);
        assertEquals(64, ((ArrowType.Int) count.getType()).getBitWidth());

        // active — Bool
        final Field active = findField(schema, "active");
        assertTrue(active.getType() instanceof ArrowType.Bool);

        // score — Double
        final Field score = findField(schema, "score");
        assertTrue(score.getType() instanceof ArrowType.FloatingPoint);
        assertEquals(FloatingPointPrecision.DOUBLE,
                ((ArrowType.FloatingPoint) score.getType()).getPrecision());

        // ratio — Float/Single
        final Field ratio = findField(schema, "ratio");
        assertTrue(ratio.getType() instanceof ArrowType.FloatingPoint);
        assertEquals(FloatingPointPrecision.SINGLE,
                ((ArrowType.FloatingPoint) ratio.getType()).getPrecision());

        // created — Date(DAY)
        final Field created = findField(schema, "created");
        assertTrue(created.getType() instanceof ArrowType.Date);
        assertEquals(DateUnit.DAY, ((ArrowType.Date) created.getType()).getUnit());

        // modified — Timestamp(MICROSECOND)
        final Field modified = findField(schema, "modified");
        assertTrue(modified.getType() instanceof ArrowType.Timestamp);
        assertEquals(TimeUnit.MICROSECOND, ((ArrowType.Timestamp) modified.getType()).getUnit());
    }

    @Test
    public void testJsonTypeBecomesUtf8()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("data", "JSON", false, false));
        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertTrue(findField(schema, "data").getType() instanceof ArrowType.Utf8);
    }

    @Test
    public void testVarCharWithLength()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("label", "VarChar(255)", false, false));
        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertTrue(findField(schema, "label").getType() instanceof ArrowType.Utf8);
    }

    @Test
    public void testUnknownTypeBecomesUtf8()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("misc", "UNKNOWN_TYPE", false, false));
        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertTrue(findField(schema, "misc").getType() instanceof ArrowType.Utf8);
    }

    @Test
    public void testDecimalType()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("price", "DECIMAL", false, false));
        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertTrue(findField(schema, "price").getType() instanceof ArrowType.Decimal);
    }

    @Test
    public void testSmallIntAndTinyInt()
    {
        final List<RestFieldDefinition> fieldDefs = Arrays.asList(
                new RestFieldDefinition("s", "SMALLINT", false, false),
                new RestFieldDefinition("t", "TINYINT", false, false));
        final Schema schema = ForgeSchemaBuilder.buildSchema(fieldDefs);
        assertEquals(16, ((ArrowType.Int) findField(schema, "s").getType()).getBitWidth());
        assertEquals(8, ((ArrowType.Int) findField(schema, "t").getType()).getBitWidth());
    }

    private static Field findField(Schema schema, String name)
    {
        for (final Field f : schema.getFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new AssertionError("Field not found: " + name);
    }
}

// Made with Bob

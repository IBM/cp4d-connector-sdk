/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.sdk.connector.forge.RestFieldDefinition;

/**
 * Tests for {@link RestFieldTypeMapper}.
 */
public class TestRestFieldTypeMapper
{
    private static RestFieldDefinition field(String name, String typeString)
    {
        return new RestFieldDefinition(name, typeString, false, false);
    }

    private static RestFieldDefinition flattenedField(String name)
    {
        return new RestFieldDefinition(name, "VARCHAR", false, false);
    }

    /**
     * Test VarChar(N) mapping.
     */
    @Test
    public void testVarChar() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "VarChar(50)"));
        assertEquals("varchar", f.getType());
        assertEquals(50, (int) f.getLength());
        assertTrue(f.isNullable());
    }

    /**
     * Test LongVarChar(N) mapping.
     */
    @Test
    public void testLongVarChar() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "LongVarChar(2000)"));
        assertEquals("longvarchar", f.getType());
        assertEquals(2000, (int) f.getLength());
    }

    /**
     * Test Integer mapping.
     */
    @Test
    public void testInteger() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Integer"));
        assertEquals("integer", f.getType());
    }

    /**
     * Test BigInt mapping.
     */
    @Test
    public void testBigInt() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "BigInt"));
        assertEquals("bigint", f.getType());
        assertTrue(f.isSigned());
    }

    /**
     * Test Boolean mapping.
     */
    @Test
    public void testBoolean() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Boolean"));
        assertEquals("boolean", f.getType());
    }

    /**
     * Test Date mapping.
     */
    @Test
    public void testDate() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Date"));
        assertEquals("date", f.getType());
    }

    /**
     * Test Timestamp mapping.
     */
    @Test
    public void testTimestamp() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Timestamp"));
        assertEquals("timestamp", f.getType());
    }

    /**
     * Test Double mapping.
     */
    @Test
    public void testDouble() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Double"));
        assertEquals("double", f.getType());
    }

    /**
     * Test JSON mapping (should become varchar with large length).
     */
    @Test
    public void testJson() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "JSON"));
        assertEquals("varchar", f.getType());
        assertEquals(65535, (int) f.getLength());
    }

    /**
     * Test flattened object field mapping (should become varchar/JSON).
     */
    @Test
    public void testFlattenedObject() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(flattenedField("tags"));
        assertEquals("tags", f.getName());
        assertEquals("varchar", f.getType());
        assertEquals(255, (int) f.getLength()); // Default VARCHAR length
    }

    /**
     * Test that all fields are nullable by default.
     */
    @Test
    public void testNullable() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "Integer"));
        assertTrue(f.isNullable());
    }

    /**
     * Test field name is preserved.
     */
    @Test
    public void testFieldName() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("my_field", "VarChar(100)"));
        assertEquals("my_field", f.getName());
    }

    /**
     * Test toAssetFields converts a list correctly.
     */
    @Test
    public void testToAssetFields() throws Exception
    {
        final List<RestFieldDefinition> defs = Arrays.asList(
                field("id", "VarChar(50)"),
                field("count", "Integer"),
                field("active", "Boolean"));

        final List<CustomFlightAssetField> fields = RestFieldTypeMapper.toAssetFields(defs);
        assertEquals(3, fields.size());
        assertEquals("id", fields.get(0).getName());
        assertEquals("varchar", fields.get(0).getType());
        assertEquals("count", fields.get(1).getName());
        assertEquals("integer", fields.get(1).getType());
        assertEquals("active", fields.get(2).getName());
        assertEquals("boolean", fields.get(2).getType());
    }

    /**
     * Test case-insensitive type parsing.
     */
    @Test
    public void testCaseInsensitive() throws Exception
    {
        assertEquals("integer", RestFieldTypeMapper.toAssetField(field("c", "INTEGER")).getType());
        assertEquals("boolean", RestFieldTypeMapper.toAssetField(field("c", "BOOLEAN")).getType());
        assertEquals("double", RestFieldTypeMapper.toAssetField(field("c", "DOUBLE")).getType());
        assertEquals("varchar", RestFieldTypeMapper.toAssetField(field("c", "VARCHAR(100)")).getType());
    }

    /**
     * Test unknown type defaults to varchar.
     */
    @Test
    public void testUnknownType() throws Exception
    {
        final CustomFlightAssetField f = RestFieldTypeMapper.toAssetField(field("col", "UNKNOWN_TYPE"));
        assertNotNull(f);
        assertEquals("varchar", f.getType());
    }
}

// Made with Bob

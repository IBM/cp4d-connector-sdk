/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.jdbc.derby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Result;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ibm.connect.sdk.test.CloudTestSuite;

/**
 * An abstract class for testing Apache Derby connector via Cloud Pak for Data.
 */
public abstract class DerbyCloudTestSuite extends CloudTestSuite
{
    protected abstract Connection getConnection();

    protected abstract String getDatasourceTypeName();

    protected abstract JsonObject createConnectionProperties();

    /**
     * Ensure any failing tests didn't leave the most commonly used objects.
     */
    @Before
    @Override
    public void setUp()
    {
        super.setUp();
        try (Statement statement = getConnection().createStatement()) {
            try {
                statement.execute("DROP TABLE SCHEMA1.T1");
            }
            catch (Exception e) {
                // Do nothing;
            }
            try {
                statement.execute("DROP TABLE SCHEMA1.T2");
            }
            catch (Exception e) {
                // Do nothing;
            }
            try {
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
            catch (Exception e) {
                // Do nothing;
            }
        }
        catch (Exception e) {
            // Do nothing;
        }
    }

    /**
     * Test getStream with numeric types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamNumeric() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 DECIMAL(31,2), C3 DOUBLE, C4 FLOAT, C5 FLOAT(23), C6 INTEGER, C7 NUMERIC(31,6), C8 REAL, C9 SMALLINT)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9223372036854775808, -99999999999999999999999999999.99, -1.7976931348623157E+308, -2.2250738585072014E-308, -3.4028235E+38, -2147483648, -9999999999999999999999999.999999, -1.17549435E-38, -32768)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9223372036854775807, 99999999999999999999999999999.99, 1.7976931348623157E+308, 2.2250738585072014E-308, 3.4028235E+38, 2147483647, 9999999999999999999999999.999999, 1.17549435E-38, 32767)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(9, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(18, data.size());
            assertEquals(Long.MIN_VALUE, data.get(0, 0));
            assertEquals(new BigDecimal("-99999999999999999999999999999.99"), data.get(0, 1));
            assertEquals(Double.valueOf("-1.7976931348623157E+308"), data.get(0, 2));
            assertEquals(Double.valueOf("-2.2250738585072014E-308"), data.get(0, 3));
            assertEquals(Float.valueOf("-3.4028235E+38"), data.get(0, 4));
            assertEquals(Integer.MIN_VALUE, data.get(0, 5));
            assertEquals(new BigDecimal("-9999999999999999999999999.999999"), data.get(0, 6));
            assertEquals(Float.valueOf("-1.17549435E-38"), data.get(0, 7));
            assertEquals(Short.MIN_VALUE, data.get(0, 8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertNull(data.get(1, 5));
            assertNull(data.get(1, 6));
            assertNull(data.get(1, 7));
            assertNull(data.get(1, 8));
            assertEquals(Long.MAX_VALUE, data.get(2, 0));
            assertEquals(new BigDecimal("99999999999999999999999999999.99"), data.get(2, 1));
            assertEquals(Double.valueOf("1.7976931348623157E+308"), data.get(2, 2));
            assertEquals(Double.valueOf("2.2250738585072014E-308"), data.get(2, 3));
            assertEquals(Float.valueOf("3.4028235E+38"), data.get(2, 4));
            assertEquals(Integer.MAX_VALUE, data.get(2, 5));
            assertEquals(new BigDecimal("9999999999999999999999999.999999"), data.get(2, 6));
            assertEquals(Float.valueOf("1.17549435E-38"), data.get(2, 7));
            assertEquals(Short.MAX_VALUE, data.get(2, 8));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with decimal types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamDecimal() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 DECIMAL(2,1), C2 DECIMAL(4,2), C3 DECIMAL(9,3), C4 DECIMAL(18,4), C5 DECIMAL(31,5))");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9.9, -99.99, -999999.999, -99999999999999.9999, -99999999999999999999999999.99999)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9.9, 99.99, 999999.999, 99999999999999.9999, 99999999999999999999999999.99999)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(5, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(10, data.size());
            assertEquals(new BigDecimal("-9.9"), data.get(0, 0));
            assertEquals(new BigDecimal("-99.99"), data.get(0, 1));
            assertEquals(new BigDecimal("-999999.999"), data.get(0, 2));
            assertEquals(new BigDecimal("-99999999999999.9999"), data.get(0, 3));
            assertEquals(new BigDecimal("-99999999999999999999999999.99999"), data.get(0, 4));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertEquals(new BigDecimal("9.9"), data.get(2, 0));
            assertEquals(new BigDecimal("99.99"), data.get(2, 1));
            assertEquals(new BigDecimal("999999.999"), data.get(2, 2));
            assertEquals(new BigDecimal("99999999999999.9999"), data.get(2, 3));
            assertEquals(new BigDecimal("99999999999999999999999999.99999"), data.get(2, 4));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with binary types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamBinary() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BLOB, C2 CHAR(5) FOR BIT DATA, C3 LONG VARCHAR FOR BIT DATA, C4 VARCHAR(20) FOR BIT DATA)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, X'48656C6C6F', X'776F726C6421', X'48656C6C6F')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(4, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(3, data.size());
            assertNull(data.get(0, 0));
            assertEquals("Hello", new String((byte[]) data.get(0, 1), StandardCharsets.UTF_8));
            assertEquals("world!", new String((byte[]) data.get(0, 2), StandardCharsets.UTF_8));
            assertEquals("Hello", new String((byte[]) data.get(0, 3), StandardCharsets.UTF_8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with character types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCharacter() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 CHAR(5), C2 CLOB, C3 LONG VARCHAR, C4 VARCHAR(20))");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('Hello', 'world!', 'Hello', 'world!')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(4, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(4, data.size());
            assertEquals("Hello", data.get(0, 0));
            assertEquals("world!", data.get(0, 1));
            assertEquals("Hello", data.get(0, 2));
            assertEquals("world!", data.get(0, 3));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with date-time types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamDatetime() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 DATE, C2 TIME, C3 TIMESTAMP)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('0001-01-01', '00:00:00', '0001-01-01 00:00:00.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('1677-09-21', '00:12:44', '1677-09-21 00:12:44.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('1970-01-01', '00:00:00', '1970-01-01 00:00:00.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('2262-04-11', '23:47:16', '2262-04-11 23:47:16.854')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('9999-12-31', '23:59:59', '9999-12-31 23:59:59.999')");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(15, data.size());
            assertEquals(Date.valueOf("0001-01-01"), data.get(0, 0));
            assertEquals(Time.valueOf("00:00:00"), data.get(0, 1));
            assertEquals(Timestamp.valueOf("0001-01-01 00:00:00.000"), data.get(0, 2));
            assertEquals(Date.valueOf("1677-09-21"), data.get(1, 0));
            assertEquals(Time.valueOf("00:12:44"), data.get(1, 1));
            assertEquals(Timestamp.valueOf("1677-09-21 00:12:44.000"), data.get(1, 2));
            assertEquals(Date.valueOf("1970-01-01"), data.get(2, 0));
            assertEquals(Time.valueOf("00:00:00"), data.get(2, 1));
            assertEquals(Timestamp.valueOf("1970-01-01 00:00:00.000"), data.get(2, 2));
            assertNull(data.get(3, 0));
            assertNull(data.get(3, 1));
            assertNull(data.get(3, 2));
            assertEquals(Date.valueOf("2262-04-11"), data.get(4, 0));
            assertEquals(Time.valueOf("23:47:16"), data.get(4, 1));
            assertEquals(Timestamp.valueOf("2262-04-11 23:47:16.854"), data.get(4, 2));
            assertEquals(Date.valueOf("9999-12-31"), data.get(5, 0));
            assertEquals(Time.valueOf("23:59:59"), data.get(5, 1));
            assertEquals(Timestamp.valueOf("9999-12-31 23:59:59.999"), data.get(5, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with boolean types.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamBoolean() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 BOOLEAN, C2 BOOLEAN)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (false, true)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(2, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(2, data.size());
            assertFalse((Boolean) data.get(0, 0));
            assertTrue((Boolean) data.get(0, 1));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with a select statement.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamSelectStatement() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 XML)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (XMLPARSE(DOCUMENT '<hello>world!</hello>' PRESERVE WHITESPACE))");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("select_statement", "SELECT XMLSERIALIZE(C1 AS LONG VARCHAR) AS C1 FROM SCHEMA1.T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(1, data.size());
            assertEquals("<hello>world!</hello>", data.get(0, 0));
            assertNull(data.get(1, 0));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with specific fields.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamFields() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 DECIMAL(31,2), C3 DOUBLE, C4 FLOAT, C5 FLOAT(23), C6 INTEGER, C7 NUMERIC(31,6), C8 REAL, C9 SMALLINT)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9223372036854775808, -99999999999999999999999999999.99, -1.7976931348623157E+308, -2.2250738585072014E-308, -3.4028235E+38, -2147483648, -9999999999999999999999999.999999, -1.17549435E-38, -32768)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9223372036854775807, 99999999999999999999999999999.99, 1.7976931348623157E+308, 2.2250738585072014E-308, 3.4028235E+38, 2147483647, 9999999999999999999999999.999999, 1.17549435E-38, 32767)");
        }
        try {
            final JsonObject c9Field = new JsonObject();
            final JsonObject c9Type = new JsonObject();
            c9Field.addProperty("name", "C9");
            c9Field.add("type", c9Type);
            c9Type.addProperty("type", "smallint");
            c9Type.addProperty("length", 5);
            c9Type.addProperty("scale", 0);
            c9Type.addProperty("nullable", true);
            c9Type.addProperty("signed", true);
            final JsonObject c6Field = new JsonObject();
            final JsonObject c6Type = new JsonObject();
            c6Field.addProperty("name", "C6");
            c6Field.add("type", c6Type);
            c6Type.addProperty("type", "integer");
            c6Type.addProperty("length", 10);
            c6Type.addProperty("scale", 0);
            c6Type.addProperty("nullable", true);
            c6Type.addProperty("signed", true);
            final JsonObject c1Field = new JsonObject();
            final JsonObject c1Type = new JsonObject();
            c1Field.addProperty("name", "C1");
            c1Field.add("type", c1Type);
            c1Type.addProperty("type", "bigint");
            c1Type.addProperty("length", 19);
            c1Type.addProperty("scale", 0);
            c1Type.addProperty("nullable", true);
            c1Type.addProperty("signed", true);
            final JsonArray fields = new JsonArray();
            fields.add(c9Field);
            fields.add(c6Field);
            fields.add(c1Field);
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            request.add("fields", fields);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(6, data.size());
            assertEquals(Short.MIN_VALUE, data.get(0, 0));
            assertEquals(Integer.MIN_VALUE, data.get(0, 1));
            assertEquals(Long.MIN_VALUE, data.get(0, 2));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertEquals(Short.MAX_VALUE, data.get(2, 0));
            assertEquals(Integer.MAX_VALUE, data.get(2, 1));
            assertEquals(Long.MAX_VALUE, data.get(2, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with row_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamRowLimit() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (0)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (2)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (3)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (4)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (5)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (6)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (7)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (8)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (9)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (10)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            interactionProperties.addProperty("row_limit", "5");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(5, data.size());
            assertEquals(0, data.get(0, 0));
            assertEquals(1, data.get(1, 0));
            assertEquals(2, data.get(2, 0));
            assertEquals(3, data.get(3, 0));
            assertEquals(4, data.get(4, 0));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test getStream with byte_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamByteLimit() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 BIGINT, C3 BIGINT, C4 BIGINT, C5 BIGINT)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (10, 20, 30, 40, 50)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (60, 70, 80, 90, 100)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (110, 120, 130, 140, 150)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            interactionProperties.addProperty("byte_limit", "80");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            request.addProperty("batch_size", 1);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(5, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(10, data.size());
            assertEquals(10L, data.get(0, 0));
            assertEquals(20L, data.get(0, 1));
            assertEquals(30L, data.get(0, 2));
            assertEquals(40L, data.get(0, 3));
            assertEquals(50L, data.get(0, 4));
            assertEquals(60L, data.get(1, 0));
            assertEquals(70L, data.get(1, 1));
            assertEquals(80L, data.get(1, 2));
            assertEquals(90L, data.get(1, 3));
            assertEquals(100L, data.get(1, 4));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover schemas.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverSchemas() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/");
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> schemas = new ArrayList<>();
            for (final JsonElement asset : assets) {
                schemas.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertTrue(schemas.contains("SCHEMA1"));

            // Make sure system schemas were returned as well.
            assertTrue(schemas.contains("SYS"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover schemas with a name pattern.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverSchemasNamePattern() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("name_pattern", "SCHEMA_");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> schemas = new ArrayList<>();
            for (final JsonElement asset : assets) {
                schemas.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertTrue(!schemas.isEmpty());
            for (final String schema : schemas) {
                assertTrue(schema.startsWith("SCHEMA"));
            }
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover schemas excluding system schemas.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverSchemasExcludeSystem() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("include_system", "false");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> schemas = new ArrayList<>();
            for (final JsonElement asset : assets) {
                schemas.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertTrue(!schemas.isEmpty());
            for (final String schema : schemas) {
                assertTrue(!schema.startsWith("SYS"));
            }
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover schemas with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverSchemasWithPaging() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE SCHEMA SCHEMA2");
            statement.execute("CREATE SCHEMA SCHEMA3");
            statement.execute("CREATE SCHEMA SCHEMA4");
            statement.execute("CREATE SCHEMA SCHEMA5");
            statement.execute("CREATE SCHEMA SCHEMA6");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("name_pattern", "SCHEMA_");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/", filters, 2, 3);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> schemas = new ArrayList<>();
            for (final JsonElement asset : assets) {
                schemas.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(3, schemas.size());
            assertTrue(schemas.contains("SCHEMA3"));
            assertTrue(schemas.contains("SCHEMA4"));
            assertTrue(schemas.contains("SCHEMA5"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA2 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA3 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA4 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA5 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA6 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTables() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1");
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("T1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables with a name pattern.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesNamePattern() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.X1 (A INT)");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("name_pattern", "T_");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("T1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.X1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables with a schema name pattern.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesSchemaNamePattern() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE SCHEMA SCHEMA2");
            statement.execute("CREATE TABLE SCHEMA2.X1 (A INT)");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("schema_name_pattern", "SCHEMA_");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(2, tables.size());
            assertTrue(tables.contains("T1"));
            assertTrue(tables.contains("X1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA2.X1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA2 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables in a schema that has an underscore in its name.
     * Underscore is an SQL pattern character, so when the underscore is treated as
     * part of the name and not a pattern, it must be escaped.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesSchemaWithUnderscore() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA_1");
            statement.execute("CREATE TABLE SCHEMA_1.T1 (A INT)");
            statement.execute("CREATE SCHEMA SCHEMA11");
            statement.execute("CREATE TABLE SCHEMA11.X1 (A INT)");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA_1");
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("T1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA_1.T1");
                statement.execute("DROP TABLE SCHEMA11.X1");
                statement.execute("DROP SCHEMA SCHEMA_1 RESTRICT");
                statement.execute("DROP SCHEMA SCHEMA11 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables in a system schema.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesSystemSchema() throws Exception
    {
        final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SYS");
        final JsonArray assets = response.getAsJsonArray("assets");
        final List<String> tables = new ArrayList<>();
        for (final JsonElement asset : assets) {
            tables.add(asset.getAsJsonObject().get("id").getAsString());
        }
        assertTrue(!tables.isEmpty());
    }

    /**
     * Test discover tables in a system schema excluding system tables.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesExcludeSystem() throws Exception
    {
        final JsonObject filters = new JsonObject();
        filters.addProperty("include_system", "false");
        final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SYS", filters);
        final List<String> tables = new ArrayList<>();
        final JsonArray assets = response.getAsJsonArray("assets");
        if (assets != null) {
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
        }
        assertTrue(tables.isEmpty());
    }

    /**
     * Test discover tables and views.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesViews() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE VIEW SCHEMA1.V1 (A) AS SELECT * FROM SCHEMA1.T1");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1");
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(2, tables.size());
            assertTrue(tables.contains("T1"));
            assertTrue(tables.contains("V1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP VIEW SCHEMA1.V1");
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables and exclude views.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesExcludeViews() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE VIEW SCHEMA1.V1 (A) AS SELECT * FROM SCHEMA1.T1");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("include_view", "false");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("T1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP VIEW SCHEMA1.V1");
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover views and exclude tables.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverViewsExcludeTables() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE VIEW SCHEMA1.V1 (A) AS SELECT * FROM SCHEMA1.T1");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("include_table", "false");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("V1"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP VIEW SCHEMA1.V1");
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover tables with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverTablesWithPaging() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.T2 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.T3 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.T4 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.T5 (A INT)");
            statement.execute("CREATE TABLE SCHEMA1.T6 (A INT)");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1", null, 2, 3);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> tables = new ArrayList<>();
            for (final JsonElement asset : assets) {
                tables.add(asset.getAsJsonObject().get("id").getAsString());
            }
            assertEquals(3, tables.size());
            assertTrue(tables.contains("T3"));
            assertTrue(tables.contains("T4"));
            assertTrue(tables.contains("T5"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP TABLE SCHEMA1.T3");
                statement.execute("DROP TABLE SCHEMA1.T4");
                statement.execute("DROP TABLE SCHEMA1.T5");
                statement.execute("DROP TABLE SCHEMA1.T6");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumns() throws Exception
    {
        final StringBuilder sql = new StringBuilder(400);
        sql.append("CREATE TABLE SCHEMA1.T1 (");
        sql.append("C1 BIGINT,");
        sql.append("C2 BIGINT NOT NULL,");
        sql.append("C3 BLOB,");
        sql.append("C4 BOOLEAN,");
        sql.append("C5 CHAR(10),");
        sql.append("C6 CHAR(10) FOR BIT DATA,");
        sql.append("C7 CLOB,");
        sql.append("C8 DATE,");
        sql.append("C9 DECIMAL(31,2),");
        sql.append("C10 DOUBLE,");
        sql.append("C11 FLOAT,");
        sql.append("C12 FLOAT(23),");
        sql.append("C13 INT,");
        sql.append("C14 LONG VARCHAR,");
        sql.append("C15 LONG VARCHAR FOR BIT DATA,");
        sql.append("C16 NUMERIC(31,2),");
        sql.append("C17 REAL,");
        sql.append("C18 SMALLINT,");
        sql.append("C19 TIME,");
        sql.append("C20 TIMESTAMP,");
        sql.append("C21 VARCHAR(20),");
        sql.append("C22 VARCHAR(20) FOR BIT DATA,");
        sql.append("C23 XML)");
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(sql.toString());
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1");
            final JsonArray fields = response.getAsJsonArray("fields");
            final List<String> columns = new ArrayList<>();
            for (final JsonElement field : fields) {
                columns.add(field.getAsJsonObject().get("name").getAsString());
            }
            assertEquals(23, columns.size());
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover columns in a table that has an underscore in its name.
     * Underscore is an SQL pattern character, so when the underscore is treated as
     * part of the name and not a pattern, it must be escaped.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsTableWithUnderscore() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA_1");
            statement.execute("CREATE TABLE SCHEMA_1.T_1 (A INT)");
            statement.execute("CREATE TABLE SCHEMA_1.T11 (B INT)");
            statement.execute("CREATE SCHEMA SCHEMA11");
            statement.execute("CREATE TABLE SCHEMA11.T_1 (C INT)");
            statement.execute("CREATE TABLE SCHEMA11.T11 (D INT)");
        }
        try {
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA_1/T_1");
            final JsonArray fields = response.getAsJsonArray("fields");
            final List<String> columns = new ArrayList<>();
            for (final JsonElement field : fields) {
                columns.add(field.getAsJsonObject().get("name").getAsString());
            }
            assertEquals(1, columns.size());
            assertTrue(columns.contains("A"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA_1.T_1");
                statement.execute("DROP TABLE SCHEMA_1.T11");
                statement.execute("DROP SCHEMA SCHEMA_1 RESTRICT");
                statement.execute("DROP TABLE SCHEMA11.T_1");
                statement.execute("DROP TABLE SCHEMA11.T11");
                statement.execute("DROP SCHEMA SCHEMA11 RESTRICT");
            }
        }
    }

    /**
     * Test discover unnamed primary key.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverPrimaryKeyUnnamed() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT NOT NULL, B INT NOT NULL, C INT, PRIMARY KEY (B, A))");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("primary_key", "true");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> primaryKeys = new ArrayList<>();
            final List<String> keyColumns = new ArrayList<>();
            for (final JsonElement asset : assets) {
                primaryKeys.add(asset.getAsJsonObject().get("id").getAsString());
                final JsonObject details = asset.getAsJsonObject().getAsJsonObject("details");
                final JsonArray columnNames = details.getAsJsonArray("column_names");
                for (final JsonElement columnName : columnNames) {
                    keyColumns.add(columnName.getAsString());
                }
            }
            assertEquals(1, primaryKeys.size());
            assertTrue(primaryKeys.get(0).startsWith("SQL"));
            assertEquals(2, keyColumns.size());
            assertEquals("B", keyColumns.get(0));
            assertEquals("A", keyColumns.get(1));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover named primary key.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverPrimaryKeyNamed() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT NOT NULL, B INT NOT NULL, C INT, CONSTRAINT PK PRIMARY KEY (A, B))");
        }
        try {
            final JsonObject filters = new JsonObject();
            filters.addProperty("primary_key", "true");
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1", filters);
            final JsonArray assets = response.getAsJsonArray("assets");
            final List<String> primaryKeys = new ArrayList<>();
            final List<String> keyColumns = new ArrayList<>();
            for (final JsonElement asset : assets) {
                primaryKeys.add(asset.getAsJsonObject().get("id").getAsString());
                final JsonObject details = asset.getAsJsonObject().getAsJsonObject("details");
                final JsonArray columnNames = details.getAsJsonArray("column_names");
                for (final JsonElement columnName : columnNames) {
                    keyColumns.add(columnName.getAsString());
                }
            }
            assertEquals(1, primaryKeys.size());
            assertTrue(primaryKeys.contains("PK"));
            assertEquals(2, keyColumns.size());
            assertEquals("A", keyColumns.get(0));
            assertEquals("B", keyColumns.get(1));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test discover extended metadata.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverExtendedMetadata() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT NOT NULL, C VARCHAR(20), PRIMARY KEY (A))");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T2 (A INT NOT NULL, B INT NOT NULL, C VARCHAR(20), PRIMARY KEY (B), FOREIGN KEY (A) REFERENCES SCHEMA1.T1 (A))");
            statement.execute("CREATE TABLE SCHEMA1.T3 (B INT NOT NULL, C VARCHAR(20), FOREIGN KEY (B) REFERENCES SCHEMA1.T2 (B))");
        }
        try {
            final Map<String, Object> t1Map
                    = getExtendedMetadataMap(getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1", "extended_metadata"));
            assertEquals("TABLE", t1Map.get("table_type"));
            assertEquals(2, t1Map.get("num_columns"));
            assertEquals("[]", t1Map.get("parent_tables"));
            assertEquals(0, t1Map.get("num_parents"));
            assertEquals("[\"SCHEMA1.T2\"]", t1Map.get("child_tables"));
            assertEquals(1, t1Map.get("num_children"));
            final Map<String, Object> t2Map
                    = getExtendedMetadataMap(getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T2", "extended_metadata"));
            assertEquals("TABLE", t2Map.get("table_type"));
            assertEquals(3, t2Map.get("num_columns"));
            assertEquals("[\"SCHEMA1.T1\"]", t2Map.get("parent_tables"));
            assertEquals(1, t2Map.get("num_parents"));
            assertEquals("[\"SCHEMA1.T3\"]", t2Map.get("child_tables"));
            assertEquals(1, t2Map.get("num_children"));
            final Map<String, Object> t3Map
                    = getExtendedMetadataMap(getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T3", "extended_metadata"));
            assertEquals("TABLE", t3Map.get("table_type"));
            assertEquals(2, t3Map.get("num_columns"));
            assertEquals("[\"SCHEMA1.T2\"]", t3Map.get("parent_tables"));
            assertEquals(1, t3Map.get("num_parents"));
            assertEquals("[]", t3Map.get("child_tables"));
            assertEquals(0, t3Map.get("num_children"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T3");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    private Map<String, Object> getExtendedMetadataMap(JsonObject response)
    {
        final JsonArray metadata = response.getAsJsonArray("extended_metadata");
        final Map<String, Object> map = new HashMap<>();
        for (final JsonElement element : metadata) {
            final JsonObject item = element.getAsJsonObject();
            final String name = item.get("name").getAsString();
            final JsonElement valueElement = item.get("value");
            if (valueElement.isJsonArray()) {
                map.put(name, valueElement.toString());
            } else {
                final JsonPrimitive primitive = valueElement.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    map.put(name, primitive.getAsInt());
                } else {
                    map.put(name, primitive.getAsString());
                }
            }
        }
        return map;
    }

    /**
     * Test getFlightInfo.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfo() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (A INT)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with numeric types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutNumeric() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 DECIMAL(31,2), C3 DOUBLE, C4 FLOAT, C5 FLOAT(23), C6 INTEGER, C7 NUMERIC(31,6), C8 REAL, C9 SMALLINT)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9223372036854775808, -99999999999999999999999999999.99, -1.7976931348623157E+308, -2.2250738585072014E-308, -3.4028235E+38, -2147483648, -9999999999999999999999999.999999, -1.17549435E-38, -32768)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9223372036854775807, 99999999999999999999999999999.99, 1.7976931348623157E+308, 2.2250738585072014E-308, 3.4028235E+38, 2147483647, 9999999999999999999999999.999999, 1.17549435E-38, 32767)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(9, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(18, data.size());
            assertEquals(Long.MIN_VALUE, data.get(0, 0));
            assertEquals(new BigDecimal("-99999999999999999999999999999.99"), data.get(0, 1));
            assertEquals(Double.valueOf("-1.7976931348623157E+308"), data.get(0, 2));
            assertEquals(Double.valueOf("-2.2250738585072014E-308"), data.get(0, 3));
            assertEquals(Float.valueOf("-3.4028235E+38"), data.get(0, 4));
            assertEquals(Integer.MIN_VALUE, data.get(0, 5));
            assertEquals(new BigDecimal("-9999999999999999999999999.999999"), data.get(0, 6));
            assertEquals(Float.valueOf("-1.17549435E-38"), data.get(0, 7));
            assertEquals(Short.MIN_VALUE, data.get(0, 8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertNull(data.get(1, 5));
            assertNull(data.get(1, 6));
            assertNull(data.get(1, 7));
            assertNull(data.get(1, 8));
            assertEquals(Long.MAX_VALUE, data.get(2, 0));
            assertEquals(new BigDecimal("99999999999999999999999999999.99"), data.get(2, 1));
            assertEquals(Double.valueOf("1.7976931348623157E+308"), data.get(2, 2));
            assertEquals(Double.valueOf("2.2250738585072014E-308"), data.get(2, 3));
            assertEquals(Float.valueOf("3.4028235E+38"), data.get(2, 4));
            assertEquals(Integer.MAX_VALUE, data.get(2, 5));
            assertEquals(new BigDecimal("9999999999999999999999999.999999"), data.get(2, 6));
            assertEquals(Float.valueOf("1.17549435E-38"), data.get(2, 7));
            assertEquals(Short.MAX_VALUE, data.get(2, 8));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with decimal types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutDecimal() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 DECIMAL(2,1), C2 DECIMAL(4,2), C3 DECIMAL(9,3), C4 DECIMAL(18,4), C5 DECIMAL(31,5))");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9.9, -99.99, -999999.999, -99999999999999.9999, -99999999999999999999999999.99999)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9.9, 99.99, 999999.999, 99999999999999.9999, 99999999999999999999999999.99999)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(5, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(10, data.size());
            assertEquals(new BigDecimal("-9.9"), data.get(0, 0));
            assertEquals(new BigDecimal("-99.99"), data.get(0, 1));
            assertEquals(new BigDecimal("-999999.999"), data.get(0, 2));
            assertEquals(new BigDecimal("-99999999999999.9999"), data.get(0, 3));
            assertEquals(new BigDecimal("-99999999999999999999999999.99999"), data.get(0, 4));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertEquals(new BigDecimal("9.9"), data.get(2, 0));
            assertEquals(new BigDecimal("99.99"), data.get(2, 1));
            assertEquals(new BigDecimal("999999.999"), data.get(2, 2));
            assertEquals(new BigDecimal("99999999999999.9999"), data.get(2, 3));
            assertEquals(new BigDecimal("99999999999999999999999999.99999"), data.get(2, 4));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with binary types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutBinary() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BLOB, C2 CHAR(5) FOR BIT DATA, C3 LONG VARCHAR FOR BIT DATA, C4 VARCHAR(20) FOR BIT DATA)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, X'48656C6C6F', X'776F726C6421', X'48656C6C6F')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(4, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(3, data.size());
            assertNull(data.get(0, 0));
            assertEquals("Hello", new String((byte[]) data.get(0, 1), StandardCharsets.UTF_8));
            assertEquals("world!", new String((byte[]) data.get(0, 2), StandardCharsets.UTF_8));
            assertEquals("Hello", new String((byte[]) data.get(0, 3), StandardCharsets.UTF_8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with character types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutCharacter() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 CHAR(5), C2 CLOB, C3 LONG VARCHAR, C4 VARCHAR(20))");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('Hello', 'world!', 'Hello', 'world!')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(4, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(4, data.size());
            assertEquals("Hello", data.get(0, 0));
            assertEquals("world!", data.get(0, 1));
            assertEquals("Hello", data.get(0, 2));
            assertEquals("world!", data.get(0, 3));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with date-time types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutDatetime() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 DATE, C2 TIME, C3 TIMESTAMP)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('0001-01-01', '00:00:00', '0001-01-01 00:00:00.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('1677-09-21', '00:12:44', '1677-09-21 00:12:44.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('1970-01-01', '00:00:00', '1970-01-01 00:00:00.000')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('2262-04-11', '23:47:16', '2262-04-11 23:47:16.854')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('9999-12-31', '23:59:59', '9999-12-31 23:59:59.999')");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(15, data.size());
            assertEquals(Date.valueOf("0001-01-01"), data.get(0, 0));
            assertEquals(Time.valueOf("00:00:00"), data.get(0, 1));
            assertEquals(Timestamp.valueOf("0001-01-01 00:00:00.000"), data.get(0, 2));
            assertEquals(Date.valueOf("1677-09-21"), data.get(1, 0));
            assertEquals(Time.valueOf("00:12:44"), data.get(1, 1));
            assertEquals(Timestamp.valueOf("1677-09-21 00:12:44.000"), data.get(1, 2));
            assertEquals(Date.valueOf("1970-01-01"), data.get(2, 0));
            assertEquals(Time.valueOf("00:00:00"), data.get(2, 1));
            assertEquals(Timestamp.valueOf("1970-01-01 00:00:00.000"), data.get(2, 2));
            assertNull(data.get(3, 0));
            assertNull(data.get(3, 1));
            assertNull(data.get(3, 2));
            assertEquals(Date.valueOf("2262-04-11"), data.get(4, 0));
            assertEquals(Time.valueOf("23:47:16"), data.get(4, 1));
            assertEquals(Timestamp.valueOf("2262-04-11 23:47:16.854"), data.get(4, 2));
            assertEquals(Date.valueOf("9999-12-31"), data.get(5, 0));
            assertEquals(Time.valueOf("23:59:59"), data.get(5, 1));
            assertEquals(Timestamp.valueOf("9999-12-31 23:59:59.999"), data.get(5, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with boolean types.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutBoolean() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 BOOLEAN, C2 BOOLEAN)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (false, true)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(2, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(2, data.size());
            assertFalse((Boolean) data.get(0, 0));
            assertTrue((Boolean) data.get(0, 1));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with a write_mode of update.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutUpdate() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 INTEGER NOT NULL, C2 VARCHAR(20) NOT NULL, C3 DATE NOT NULL, PRIMARY KEY (C1, C3))");
            statement.execute("CREATE TABLE SCHEMA1.T2 AS SELECT * FROM SCHEMA1.T1 WITH NO DATA");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (1, 'OLD VALUE ONE', '2000-01-01')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (2, 'OLD VALUE TWO', '2000-01-02')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (3, 'OLD VALUE THREE', '2000-01-03')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (4, 'OLD VALUE FOUR', '2000-01-04')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (5, 'OLD VALUE FIVE', '2000-01-05')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (1, 'NEW VALUE ONE', '2000-01-01')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (2, 'NEW VALUE TWO', '2000-02-02')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (3, 'NEW VALUE THREE', '2000-01-03')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (3, 'NEW VALUE FOUR', '2000-01-04')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (5, 'NEW VALUE FIVE', '2000-01-05')");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T2");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T1");
            interactionProperties.addProperty("write_mode", "update");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("table_name");
            interactionProperties.remove("write_mode");
            interactionProperties.addProperty("select_statement", "SELECT * FROM SCHEMA1.T1 ORDER BY C1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(15, data.size());
            assertEquals(1, data.get(0, 0));
            assertEquals("NEW VALUE ONE", data.get(0, 1));
            assertEquals(Date.valueOf("2000-01-01"), data.get(0, 2));
            assertEquals(2, data.get(1, 0));
            assertEquals("OLD VALUE TWO", data.get(1, 1));
            assertEquals(Date.valueOf("2000-01-02"), data.get(1, 2));
            assertEquals(3, data.get(2, 0));
            assertEquals("NEW VALUE THREE", data.get(2, 1));
            assertEquals(Date.valueOf("2000-01-03"), data.get(2, 2));
            assertEquals(4, data.get(3, 0));
            assertEquals("OLD VALUE FOUR", data.get(3, 1));
            assertEquals(Date.valueOf("2000-01-04"), data.get(3, 2));
            assertEquals(5, data.get(4, 0));
            assertEquals("NEW VALUE FIVE", data.get(4, 1));
            assertEquals(Date.valueOf("2000-01-05"), data.get(4, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with a write_mode of update with key_column_names.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutUpdateKeyColumnNames() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER NOT NULL, C2 VARCHAR(20) NOT NULL, C3 DATE NOT NULL)");
            statement.execute("CREATE TABLE SCHEMA1.T2 AS SELECT * FROM SCHEMA1.T1 WITH NO DATA");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (1, 'OLD VALUE ONE', '2000-01-01')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (2, 'OLD VALUE TWO', '2000-01-02')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (3, 'OLD VALUE THREE', '2000-01-03')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (4, 'OLD VALUE FOUR', '2000-01-04')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (5, 'OLD VALUE FIVE', '2000-01-05')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (1, 'NEW VALUE ONE', '2000-01-01')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (2, 'NEW VALUE TWO', '2000-02-02')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (3, 'NEW VALUE THREE', '2000-01-03')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (3, 'NEW VALUE FOUR', '2000-01-04')");
            statement.execute("INSERT INTO SCHEMA1.T2 VALUES (5, 'NEW VALUE FIVE', '2000-01-05')");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T2");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T1");
            interactionProperties.addProperty("write_mode", "update");
            interactionProperties.addProperty("key_column_names", "C3");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("table_name");
            interactionProperties.remove("write_mode");
            interactionProperties.remove("key_column_names");
            interactionProperties.addProperty("select_statement", "SELECT * FROM SCHEMA1.T1 ORDER BY C3");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(15, data.size());
            assertEquals(1, data.get(0, 0));
            assertEquals("NEW VALUE ONE", data.get(0, 1));
            assertEquals(Date.valueOf("2000-01-01"), data.get(0, 2));
            assertEquals(2, data.get(1, 0));
            assertEquals("OLD VALUE TWO", data.get(1, 1));
            assertEquals(Date.valueOf("2000-01-02"), data.get(1, 2));
            assertEquals(3, data.get(2, 0));
            assertEquals("NEW VALUE THREE", data.get(2, 1));
            assertEquals(Date.valueOf("2000-01-03"), data.get(2, 2));
            assertEquals(3, data.get(3, 0));
            assertEquals("NEW VALUE FOUR", data.get(3, 1));
            assertEquals(Date.valueOf("2000-01-04"), data.get(3, 2));
            assertEquals(5, data.get(4, 0));
            assertEquals("NEW VALUE FIVE", data.get(4, 1));
            assertEquals(Date.valueOf("2000-01-05"), data.get(4, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test acceptPut with update_statement.
     *
     * @throws Exception
     */
    @Test
    public void testAcceptPutUpdateStatement() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER NOT NULL)");
            statement.execute("CREATE TABLE SCHEMA1.T2 (C1 INTEGER NOT NULL, C2 VARCHAR(20) NOT NULL)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (2)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (3)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (4)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (5)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.remove("schema_name");
            interactionProperties.remove("table_name");
            interactionProperties.addProperty("write_mode", "insert");
            interactionProperties.addProperty("update_statement", "INSERT INTO SCHEMA1.T2 (C1,C2) VALUES (?,'Hello')");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("write_mode");
            interactionProperties.remove("update_statement");
            interactionProperties.addProperty("select_statement", "SELECT * FROM SCHEMA1.T2 ORDER BY C1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(2, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(10, data.size());
            assertEquals(1, data.get(0, 0));
            assertEquals("Hello", data.get(0, 1));
            assertEquals(2, data.get(1, 0));
            assertEquals("Hello", data.get(1, 1));
            assertEquals(3, data.get(2, 0));
            assertEquals("Hello", data.get(2, 1));
            assertEquals(4, data.get(3, 0));
            assertEquals("Hello", data.get(3, 1));
            assertEquals(5, data.get(4, 0));
            assertEquals("Hello", data.get(4, 1));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test setup_phase with static_statement.
     *
     * @throws Exception
     */
    @Test
    public void testPutSetupStaticStatement() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("write_mode", "static_statement");
            interactionProperties.addProperty("static_statement", "CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            request.addProperty("context", "target");
            final Iterator<Result> resultIterator
                    = getClient().doAction(new Action("setup_phase", request.toString().getBytes(StandardCharsets.UTF_8)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertNotNull(result.getBody());
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test partitioned getStream and acceptPut.
     *
     * @throws Exception
     */
    @Test
    public void testPartitionedGetAndPut() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 BIGINT)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-10)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-9)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-8)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-7)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-6)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-5)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-4)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-3)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-2)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (-1)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (NULL)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (0)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (2)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (3)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (4)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (5)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (6)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (7)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (8)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (9)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (10)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            request.addProperty("num_partitions", 4);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1");
            final JsonArray fields = response.getAsJsonArray("fields");
            interactionProperties.addProperty("table_name", "T2");
            interactionProperties.addProperty("update_statistics", "true");
            request.add("fields", fields);
            request.addProperty("context", "target");
            final Iterator<Result> resultIterator
                    = getClient().doAction(new Action("setup_phase", request.toString().getBytes(StandardCharsets.UTF_8)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertNotNull(result.getBody());
            assertEquals(4, sourceInfo.getEndpoints().size());
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    request.addProperty("partition_index", sourceInfo.getEndpoints().indexOf(endpoint));
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            request.remove("partition_index");
            final Iterator<Result> wrapupResultIterator
                    = getClient().doAction(new Action("wrapup_phase", request.toString().getBytes(StandardCharsets.UTF_8)));
            assertTrue(wrapupResultIterator.hasNext());
            final Result wrapupResult = wrapupResultIterator.next();
            assertNotNull(wrapupResult.getBody());
            request.remove("context");
            interactionProperties.remove("update_statistics");
            interactionProperties.remove("schema_name");
            interactionProperties.remove("table_name");
            interactionProperties.addProperty("select_statement", "SELECT * FROM SCHEMA1.T2 ORDER BY C1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(21, data.size());
            assertEquals(-10L, data.get(0, 0));
            assertEquals(-9L, data.get(1, 0));
            assertEquals(-8L, data.get(2, 0));
            assertEquals(-7L, data.get(3, 0));
            assertEquals(-6L, data.get(4, 0));
            assertEquals(-5L, data.get(5, 0));
            assertEquals(-4L, data.get(6, 0));
            assertEquals(-3L, data.get(7, 0));
            assertEquals(-2L, data.get(8, 0));
            assertEquals(-1L, data.get(9, 0));
            assertEquals(0L, data.get(10, 0));
            assertEquals(1L, data.get(11, 0));
            assertEquals(2L, data.get(12, 0));
            assertEquals(3L, data.get(13, 0));
            assertEquals(4L, data.get(14, 0));
            assertEquals(5L, data.get(15, 0));
            assertEquals(6L, data.get(16, 0));
            assertEquals(7L, data.get(17, 0));
            assertEquals(8L, data.get(18, 0));
            assertEquals(9L, data.get(19, 0));
            assertEquals(10L, data.get(20, 0));
            assertNull(data.get(21, 0));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test create_statement property.
     *
     * @throws Exception
     */
    @Test
    public void testCreateStatement() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 CHAR(5), C2 VARCHAR(20))");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES ('Hello', 'world!')");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            interactionProperties.addProperty("create_statement",
                    "CREATE TABLE SCHEMA1.T2 (ID INT NOT NULL GENERATED ALWAYS AS IDENTITY, C1 CHAR(5), C2 VARCHAR(20))");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("create_statement");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(3, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(4, data.size());
            assertEquals(1, data.get(0, 0));
            assertEquals("Hello", data.get(0, 1));
            assertEquals("world!", data.get(0, 2));
            assertEquals(2, data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test table_action replace.
     *
     * @throws Exception
     */
    @Test
    public void testTableActionReplace() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (0)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (2)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (3)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (4)");
            statement.execute("CREATE TABLE SCHEMA1.T2 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (5)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (6)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (7)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (8)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (9)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (10)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            interactionProperties.addProperty("table_action", "replace");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("table_action");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(5, data.size());
            assertEquals(0, data.get(0, 0));
            assertEquals(1, data.get(1, 0));
            assertEquals(2, data.get(2, 0));
            assertEquals(3, data.get(3, 0));
            assertEquals(4, data.get(4, 0));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test table_action truncate.
     *
     * @throws Exception
     */
    @Test
    public void testTableActionTruncate() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (0)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (2)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (3)");
            statement.execute("INSERT INTO SCHEMA1.T1 (C1) VALUES (4)");
            statement.execute("CREATE TABLE SCHEMA1.T2 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (5)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (6)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (7)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (8)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (9)");
            statement.execute("INSERT INTO SCHEMA1.T2 (C1) VALUES (10)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            interactionProperties.addProperty("table_action", "truncate");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            interactionProperties.remove("table_action");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(1, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(5, data.size());
            assertEquals(0, data.get(0, 0));
            assertEquals(1, data.get(1, 0));
            assertEquals(2, data.get(2, 0));
            assertEquals(3, data.get(3, 0));
            assertEquals(4, data.get(4, 0));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test get_record_count action.
     *
     * @throws Exception
     */
    @Test
    public void testGetRecordCountAction() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute("CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (0)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (1)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (2)");
        }
        try {
            final JsonObject inputProperties = new JsonObject();
            inputProperties.addProperty("schema_name", "SCHEMA1");
            inputProperties.addProperty("table_name", "T1");
            final JsonObject outputProperties = getCloudClient().performAction(getConnectionId(), "get_record_count", inputProperties);
            assertNotNull(outputProperties);
            assertEquals(3, outputProperties.get("record_count").getAsInt());
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test setup_phase action.
     *
     * @throws Exception
     */
    @Test
    public void testPutSetupAction() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 DECIMAL(31,2), C3 DOUBLE, C4 FLOAT, C5 FLOAT(23), C6 INTEGER, C7 NUMERIC(31,6), C8 REAL, C9 SMALLINT)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9223372036854775808, -99999999999999999999999999999.99, -1.7976931348623157E+308, -2.2250738585072014E-308, -3.4028235E+38, -2147483648, -9999999999999999999999999.999999, -1.17549435E-38, -32768)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9223372036854775807, 99999999999999999999999999999.99, 1.7976931348623157E+308, 2.2250738585072014E-308, 3.4028235E+38, 2147483647, 9999999999999999999999999.999999, 1.17549435E-38, 32767)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1");
            final JsonArray fields = response.getAsJsonArray("fields");
            interactionProperties.addProperty("table_name", "T2");
            request.add("fields", fields);
            request.addProperty("context", "target");
            final Iterator<Result> resultIterator
                    = getClient().doAction(new Action("setup_phase", request.toString().getBytes(StandardCharsets.UTF_8)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertNotNull(result.getBody());
            request.remove("context");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(9, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(18, data.size());
            assertEquals(Long.MIN_VALUE, data.get(0, 0));
            assertEquals(new BigDecimal("-99999999999999999999999999999.99"), data.get(0, 1));
            assertEquals(Double.valueOf("-1.7976931348623157E+308"), data.get(0, 2));
            assertEquals(Double.valueOf("-2.2250738585072014E-308"), data.get(0, 3));
            assertEquals(Float.valueOf("-3.4028235E+38"), data.get(0, 4));
            assertEquals(Integer.MIN_VALUE, data.get(0, 5));
            assertEquals(new BigDecimal("-9999999999999999999999999.999999"), data.get(0, 6));
            assertEquals(Float.valueOf("-1.17549435E-38"), data.get(0, 7));
            assertEquals(Short.MIN_VALUE, data.get(0, 8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertNull(data.get(1, 5));
            assertNull(data.get(1, 6));
            assertNull(data.get(1, 7));
            assertNull(data.get(1, 8));
            assertEquals(Long.MAX_VALUE, data.get(2, 0));
            assertEquals(new BigDecimal("99999999999999999999999999999.99"), data.get(2, 1));
            assertEquals(Double.valueOf("1.7976931348623157E+308"), data.get(2, 2));
            assertEquals(Double.valueOf("2.2250738585072014E-308"), data.get(2, 3));
            assertEquals(Float.valueOf("3.4028235E+38"), data.get(2, 4));
            assertEquals(Integer.MAX_VALUE, data.get(2, 5));
            assertEquals(new BigDecimal("9999999999999999999999999.999999"), data.get(2, 6));
            assertEquals(Float.valueOf("1.17549435E-38"), data.get(2, 7));
            assertEquals(Short.MAX_VALUE, data.get(2, 8));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test wrapup_phase action.
     *
     * @throws Exception
     */
    @Test
    public void testPutWrapupAction() throws Exception
    {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("CREATE SCHEMA SCHEMA1");
            statement.execute(
                    "CREATE TABLE SCHEMA1.T1 (C1 BIGINT, C2 DECIMAL(31,2), C3 DOUBLE, C4 FLOAT, C5 FLOAT(23), C6 INTEGER, C7 NUMERIC(31,6), C8 REAL, C9 SMALLINT)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (-9223372036854775808, -99999999999999999999999999999.99, -1.7976931348623157E+308, -2.2250738585072014E-308, -3.4028235E+38, -2147483648, -9999999999999999999999999.999999, -1.17549435E-38, -32768)");
            statement.execute("INSERT INTO SCHEMA1.T1 VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute(
                    "INSERT INTO SCHEMA1.T1 VALUES (9223372036854775807, 99999999999999999999999999999.99, 1.7976931348623157E+308, 2.2250738585072014E-308, 3.4028235E+38, 2147483647, 9999999999999999999999999.999999, 1.17549435E-38, 32767)");
        }
        try {
            final JsonObject interactionProperties = new JsonObject();
            interactionProperties.addProperty("schema_name", "SCHEMA1");
            interactionProperties.addProperty("table_name", "T1");
            final JsonObject request = new JsonObject();
            request.addProperty("asset_id", getConnectionId());
            request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
            request.add("interaction_properties", interactionProperties);
            final FlightInfo sourceInfo
                    = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            interactionProperties.addProperty("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient().startPut(
                            FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)), root, new AsyncPutListener());
                    while (stream.next()) {
                        if (root.getRowCount() == 0) {
                            break;
                        }
                        putStream.putNext();
                        root.clear();
                    }
                    putStream.completed();
                    putStream.getResult();
                }
            }
            final JsonObject response = getCloudClient().discoverConnection(getConnectionId(), "/SCHEMA1/T1");
            final JsonArray fields = response.getAsJsonArray("fields");
            request.add("fields", fields);
            request.addProperty("context", "target");
            interactionProperties.addProperty("update_statistics", "true");
            final Iterator<Result> wrapupResultIterator
                    = getClient().doAction(new Action("wrapup_phase", request.toString().getBytes(StandardCharsets.UTF_8)));
            assertTrue(wrapupResultIterator.hasNext());
            final Result wrapupResult = wrapupResultIterator.next();
            assertNotNull(wrapupResult.getBody());
            request.remove("context");
            interactionProperties.remove("update_statistics");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(request.toString().getBytes(StandardCharsets.UTF_8)));
            final Schema schema = info.getSchema();
            assertEquals(9, schema.getFields().size());
            final Table<Integer, Integer, Object> data = getTableData(info);
            assertEquals(18, data.size());
            assertEquals(Long.MIN_VALUE, data.get(0, 0));
            assertEquals(new BigDecimal("-99999999999999999999999999999.99"), data.get(0, 1));
            assertEquals(Double.valueOf("-1.7976931348623157E+308"), data.get(0, 2));
            assertEquals(Double.valueOf("-2.2250738585072014E-308"), data.get(0, 3));
            assertEquals(Float.valueOf("-3.4028235E+38"), data.get(0, 4));
            assertEquals(Integer.MIN_VALUE, data.get(0, 5));
            assertEquals(new BigDecimal("-9999999999999999999999999.999999"), data.get(0, 6));
            assertEquals(Float.valueOf("-1.17549435E-38"), data.get(0, 7));
            assertEquals(Short.MIN_VALUE, data.get(0, 8));
            assertNull(data.get(1, 0));
            assertNull(data.get(1, 1));
            assertNull(data.get(1, 2));
            assertNull(data.get(1, 3));
            assertNull(data.get(1, 4));
            assertNull(data.get(1, 5));
            assertNull(data.get(1, 6));
            assertNull(data.get(1, 7));
            assertNull(data.get(1, 8));
            assertEquals(Long.MAX_VALUE, data.get(2, 0));
            assertEquals(new BigDecimal("99999999999999999999999999999.99"), data.get(2, 1));
            assertEquals(Double.valueOf("1.7976931348623157E+308"), data.get(2, 2));
            assertEquals(Double.valueOf("2.2250738585072014E-308"), data.get(2, 3));
            assertEquals(Float.valueOf("3.4028235E+38"), data.get(2, 4));
            assertEquals(Integer.MAX_VALUE, data.get(2, 5));
            assertEquals(new BigDecimal("9999999999999999999999999.999999"), data.get(2, 6));
            assertEquals(Float.valueOf("1.17549435E-38"), data.get(2, 7));
            assertEquals(Short.MAX_VALUE, data.get(2, 8));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP TABLE SCHEMA1.T2");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test connection with an unknown database.
     *
     * @throws Exception
     */
    @Test
    public void testConnectionBadDatabase() throws Exception
    {
        final JsonObject connectionProperties = createConnectionProperties();
        connectionProperties.addProperty("database", "unknown");
        connectionProperties.remove("create_database");
        try {
            getCloudClient().createConnection("test-" + UUID.randomUUID().toString(), getDatasourceTypeName(), connectionProperties);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("database unknown was not found"));
        }
    }

    /**
     * Test connection with SSL.
     *
     * @throws Exception
     */
    @Test
    public void testConnectionSSL() throws Exception
    {
        final JsonObject connectionProperties = createConnectionProperties();
        connectionProperties.addProperty("ssl", "true");
        try {
            getCloudClient().createConnection("test-" + UUID.randomUUID().toString(), getDatasourceTypeName(), connectionProperties);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("SSL"));
        }
    }

    /**
     * Test validate action with invalid properties.
     *
     * @throws Exception
     */
    @Test
    public void testConnectionMissingDatabase() throws Exception
    {
        final JsonObject connectionProperties = createConnectionProperties();
        connectionProperties.remove("database");
        try {
            getCloudClient().createConnection("test-" + UUID.randomUUID().toString(), getDatasourceTypeName(), connectionProperties);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("The property [database] is required"));
        }
    }

    /**
     * Test listActions with custom actions.
     *
     * @throws Exception
     */
    @Test
    public void testListCustomActions() throws Exception
    {
        final JsonObject response = getCloudClient().listActions(getConnectionId());
        final JsonArray actions = response.getAsJsonArray("actions");
        final List<String> actionTypes = new ArrayList<>();
        for (final JsonElement action : actions) {
            actionTypes.add(action.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(actionTypes.contains("get_record_count"));
    }

}

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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
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
import com.ibm.connect.sdk.test.ConnectorTestSuite;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryFilters;

/**
 * An abstract class for an Apache Derby test suite.
 */
public abstract class DerbyTestSuite extends ConnectorTestSuite
{
    private static ModelMapper modelMapper = new ModelMapper();

    protected abstract Connection getConnection();

    /**
     * Ensure any failing tests didn't leave the most commonly used objects.
     */
    @Before
    public void setUp()
    {
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("select_statement", "SELECT XMLSERIALIZE(C1 AS LONG VARCHAR) AS C1 FROM SCHEMA1.T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            descriptor
                    .addFieldsItem(new CustomFlightAssetField().name("C9").type("smallint").length(5).scale(0).nullable(true).signed(true));
            descriptor
                    .addFieldsItem(new CustomFlightAssetField().name("C6").type("integer").length(10).scale(0).nullable(true).signed(true));
            descriptor
                    .addFieldsItem(new CustomFlightAssetField().name("C1").type("bigint").length(19).scale(0).nullable(true).signed(true));
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
                statement.execute("DROP TABLE SCHEMA1.T2");
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            interactionProperties.put("row_limit", "5");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            descriptor.setBatchSize(1);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            interactionProperties.put("byte_limit", "80");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/");
            final List<String> schemas = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getAssetType());
                assertEquals("schema", descriptor.getAssetType().getType());
                assertFalse(descriptor.getAssetType().isDataset());
                assertTrue(descriptor.getAssetType().isDatasetContainer());
                assertNotNull(descriptor.getId());
                assertNotNull(descriptor.getName());
                final String expectedPath = "/" + descriptor.getId();
                assertEquals(expectedPath, descriptor.getPath());
                schemas.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/");
            filters.put("name_pattern", "SCHEMA_");
            final List<String> schemas = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                schemas.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/");
            filters.put("include_system", "false");
            final List<String> schemas = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                schemas.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/");
            criteria.setOffset(2);
            criteria.setLimit(3);
            filters.put("name_pattern", "SCHEMA_");
            final List<String> schemas = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                schemas.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA1");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getAssetType());
                assertEquals("table", descriptor.getAssetType().getType());
                assertTrue(descriptor.getAssetType().isDataset());
                assertFalse(descriptor.getAssetType().isDatasetContainer());
                assertNotNull(descriptor.getId());
                assertNotNull(descriptor.getName());
                final String expectedPath = "/SCHEMA1/" + descriptor.getId();
                assertEquals(expectedPath, descriptor.getPath());
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/SCHEMA1");
            filters.put("name_pattern", "T_");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/");
            filters.put("schema_name_pattern", "SCHEMA_");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA_1");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/SYS");
        final List<String> tables = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            tables.add(descriptor.getId());
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
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        final DiscoveryFilters filters = new DiscoveryFilters();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setFilters(filters);
        criteria.setPath("/SYS");
        filters.put("include_system", "false");
        final List<String> tables = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA1");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/SCHEMA1");
            filters.put("include_view", "false");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/SCHEMA1");
            filters.put("include_table", "false");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA1");
            criteria.setOffset(2);
            criteria.setLimit(3);
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA1/T1");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getAssetType());
                assertEquals("table", descriptor.getAssetType().getType());
                assertTrue(descriptor.getAssetType().isDataset());
                assertFalse(descriptor.getAssetType().isDatasetContainer());
                assertNotNull(descriptor.getId());
                assertNotNull(descriptor.getName());
                assertEquals("/SCHEMA1/T1", descriptor.getPath());
                assertNotNull(descriptor.getInteractionProperties());
                assertEquals("SCHEMA1", descriptor.getInteractionProperties().get("schema_name"));
                assertEquals("T1", descriptor.getInteractionProperties().get("table_name"));
                final Schema schema = info.getSchema();
                assertEquals(23, schema.getFields().size());
                tables.add(descriptor.getId());
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA_1/T_1");
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getInteractionProperties());
                assertEquals("SCHEMA_1", descriptor.getInteractionProperties().get("schema_name"));
                assertEquals("T_1", descriptor.getInteractionProperties().get("table_name"));
                final Schema schema = info.getSchema();
                assertEquals(1, schema.getFields().size());
                tables.add(descriptor.getId());
            }
            assertEquals(1, tables.size());
            assertTrue(tables.contains("T_1"));
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/SCHEMA1/T1");
            filters.put("primary_key", "true");
            final List<String> primaryKeys = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                primaryKeys.add(descriptor.getId());
            }
            assertEquals(1, primaryKeys.size());
            assertTrue(primaryKeys.get(0).startsWith("SQL"));
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            final DiscoveryFilters filters = new DiscoveryFilters();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setFilters(filters);
            criteria.setPath("/SCHEMA1/T1");
            filters.put("primary_key", "true");
            final List<String> primaryKeys = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                primaryKeys.add(descriptor.getId());
            }
            assertEquals(1, primaryKeys.size());
            assertTrue(primaryKeys.contains("PK"));
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
            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setDatasourceTypeName(getDatasourceTypeName());
            criteria.setConnectionProperties(createConnectionProperties());
            criteria.setPath("/SCHEMA1/T1");
            criteria.setExtendedMetadata(true);
            final List<String> tables = new ArrayList<>();
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getExtendedMetadata());
                assertEquals(6, descriptor.getExtendedMetadata().size());
                assertEquals("table_type", descriptor.getExtendedMetadata().get(0).getName());
                assertEquals("TABLE", descriptor.getExtendedMetadata().get(0).getValue());
                assertEquals("num_columns", descriptor.getExtendedMetadata().get(1).getName());
                assertEquals(2, descriptor.getExtendedMetadata().get(1).getValue());
                assertEquals("parent_tables", descriptor.getExtendedMetadata().get(2).getName());
                assertEquals(Collections.emptyList(), descriptor.getExtendedMetadata().get(2).getValue());
                assertEquals("num_parents", descriptor.getExtendedMetadata().get(3).getName());
                assertEquals(0, descriptor.getExtendedMetadata().get(3).getValue());
                assertEquals("child_tables", descriptor.getExtendedMetadata().get(4).getName());
                assertEquals(Collections.singletonList("SCHEMA1.T2"), descriptor.getExtendedMetadata().get(4).getValue());
                assertEquals("num_children", descriptor.getExtendedMetadata().get(5).getName());
                assertEquals(1, descriptor.getExtendedMetadata().get(5).getValue());
                tables.add(descriptor.getId());
            }
            criteria.setPath("/SCHEMA1/T2");
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getExtendedMetadata());
                assertEquals(6, descriptor.getExtendedMetadata().size());
                assertEquals("table_type", descriptor.getExtendedMetadata().get(0).getName());
                assertEquals("TABLE", descriptor.getExtendedMetadata().get(0).getValue());
                assertEquals("num_columns", descriptor.getExtendedMetadata().get(1).getName());
                assertEquals(3, descriptor.getExtendedMetadata().get(1).getValue());
                assertEquals("parent_tables", descriptor.getExtendedMetadata().get(2).getName());
                assertEquals(Collections.singletonList("SCHEMA1.T1"), descriptor.getExtendedMetadata().get(2).getValue());
                assertEquals("num_parents", descriptor.getExtendedMetadata().get(3).getName());
                assertEquals(1, descriptor.getExtendedMetadata().get(3).getValue());
                assertEquals("child_tables", descriptor.getExtendedMetadata().get(4).getName());
                assertEquals(Collections.singletonList("SCHEMA1.T3"), descriptor.getExtendedMetadata().get(4).getValue());
                assertEquals("num_children", descriptor.getExtendedMetadata().get(5).getName());
                assertEquals(1, descriptor.getExtendedMetadata().get(5).getValue());
                tables.add(descriptor.getId());
            }
            criteria.setPath("/SCHEMA1/T3");
            for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
                final FlightDescriptor flightDescriptor = info.getDescriptor();
                final CustomFlightAssetDescriptor descriptor
                        = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
                assertNotNull(descriptor.getExtendedMetadata());
                assertEquals(6, descriptor.getExtendedMetadata().size());
                assertEquals("table_type", descriptor.getExtendedMetadata().get(0).getName());
                assertEquals("TABLE", descriptor.getExtendedMetadata().get(0).getValue());
                assertEquals("num_columns", descriptor.getExtendedMetadata().get(1).getName());
                assertEquals(2, descriptor.getExtendedMetadata().get(1).getValue());
                assertEquals("parent_tables", descriptor.getExtendedMetadata().get(2).getName());
                assertEquals(Collections.singletonList("SCHEMA1.T2"), descriptor.getExtendedMetadata().get(2).getValue());
                assertEquals("num_parents", descriptor.getExtendedMetadata().get(3).getName());
                assertEquals(1, descriptor.getExtendedMetadata().get(3).getValue());
                assertEquals("child_tables", descriptor.getExtendedMetadata().get(4).getName());
                assertEquals(Collections.emptyList(), descriptor.getExtendedMetadata().get(4).getValue());
                assertEquals("num_children", descriptor.getExtendedMetadata().get(5).getName());
                assertEquals(0, descriptor.getExtendedMetadata().get(5).getValue());
                tables.add(descriptor.getId());
            }
            assertEquals(3, tables.size());
            assertTrue(tables.contains("T1"));
            assertTrue(tables.contains("T2"));
            assertTrue(tables.contains("T3"));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T2");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T1");
            interactionProperties.put("write_mode", "update");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            interactionProperties.clear();
            interactionProperties.put("select_statement", "SELECT * FROM SCHEMA1.T1 ORDER BY C1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T2");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T1");
            interactionProperties.put("write_mode", "update");
            interactionProperties.put("key_column_names", "C3");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            interactionProperties.clear();
            interactionProperties.put("select_statement", "SELECT * FROM SCHEMA1.T1 ORDER BY C3");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.clear();
            interactionProperties.put("write_mode", "insert");
            interactionProperties.put("update_statement", "INSERT INTO SCHEMA1.T2 (C1,C2) VALUES (?,'Hello')");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream
                            = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
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
            interactionProperties.clear();
            interactionProperties.put("select_statement", "SELECT * FROM SCHEMA1.T2 ORDER BY C1");
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
     * Test put_setup with static_statement.
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("write_mode", "static_statement");
            interactionProperties.put("static_statement", "CREATE TABLE SCHEMA1.T1 (C1 INTEGER)");
            final CustomFlightActionRequest request = new CustomFlightActionRequest().asset(descriptor);
            final Iterator<Result> resultIterator = getClient().doAction(new Action("put_setup", modelMapper.toBytes(request)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertFalse(resultIterator.hasNext());
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            descriptor.setPartitionCount(4);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.remove("schema_name");
            interactionProperties.remove("table_name");
            interactionProperties.put("select_statement", "SELECT * FROM SCHEMA1.T2 ORDER BY C1");
            final CustomFlightAssetDescriptor setupDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            setupDescriptor.getInteractionProperties().put("table_name", "T2");
            setupDescriptor.getInteractionProperties().put("update_statistics", "true");
            final CustomFlightActionRequest request = new CustomFlightActionRequest().asset(setupDescriptor);
            final Iterator<Result> resultIterator = getClient().doAction(new Action("put_setup", modelMapper.toBytes(request)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertFalse(resultIterator.hasNext());
            assertNotNull(result.getBody());
            final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
            final CustomFlightAssetDescriptor putDescriptor = response.getAsset();
            assertEquals(4, sourceInfo.getEndpoints().size());
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    putDescriptor.setPartitionIndex(sourceInfo.getEndpoints().indexOf(endpoint));
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final CustomFlightActionRequest wrapupRequest = new CustomFlightActionRequest().asset(putDescriptor);
            final Iterator<Result> wrapupResultIterator
                    = getClient().doAction(new Action("put_wrapup", modelMapper.toBytes(wrapupRequest)));
            assertTrue(wrapupResultIterator.hasNext());
            final Result wrapupResult = wrapupResultIterator.next();
            assertFalse(wrapupResultIterator.hasNext());
            assertNotNull(wrapupResult.getBody());
            final CustomFlightActionResponse wrapupResponse
                    = modelMapper.fromBytes(wrapupResult.getBody(), CustomFlightActionResponse.class);
            final CustomFlightAssetDescriptor wrapupDescriptor = wrapupResponse.getAsset();
            assertNotNull(wrapupDescriptor.getInteractionProperties());
            assertEquals("T2", wrapupDescriptor.getInteractionProperties().get("table_name"));
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            final CustomFlightAssetDescriptor putDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            putDescriptor.getInteractionProperties().put("table_name", "T2");
            putDescriptor.getInteractionProperties().put("create_statement",
                    "CREATE TABLE SCHEMA1.T2 (ID INT NOT NULL GENERATED ALWAYS AS IDENTITY, C1 CHAR(5), C2 VARCHAR(20))");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            final CustomFlightAssetDescriptor putDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            putDescriptor.getInteractionProperties().put("table_name", "T2");
            putDescriptor.getInteractionProperties().put("table_action", "replace");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            final CustomFlightAssetDescriptor putDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            putDescriptor.getInteractionProperties().put("table_name", "T2");
            putDescriptor.getInteractionProperties().put("table_action", "truncate");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
            final ConnectionActionConfiguration inputProperties = new ConnectionActionConfiguration();
            inputProperties.put("schema_name", "SCHEMA1");
            inputProperties.put("table_name", "T1");
            final CustomFlightActionRequest request = new CustomFlightActionRequest();
            request.setDatasourceTypeName(getDatasourceTypeName());
            request.setConnectionProperties(createConnectionProperties());
            request.setRequestProperties(inputProperties);
            final Iterator<Result> resultIterator = getClient().doAction(new Action("get_record_count", modelMapper.toBytes(request)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertFalse(resultIterator.hasNext());
            assertNotNull(result.getBody());
            final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
            final ConnectionActionResponse outputProperties = response.getResponseProperties();
            assertNotNull(outputProperties);
            assertEquals(3, outputProperties.get("record_count"));
        }
        finally {
            try (Statement statement = getConnection().createStatement()) {
                statement.execute("DROP TABLE SCHEMA1.T1");
                statement.execute("DROP SCHEMA SCHEMA1 RESTRICT");
            }
        }
    }

    /**
     * Test put_setup action.
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            final CustomFlightAssetDescriptor setupDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            setupDescriptor.getInteractionProperties().put("table_name", "T2");
            final CustomFlightActionRequest request = new CustomFlightActionRequest().asset(setupDescriptor);
            final Iterator<Result> resultIterator = getClient().doAction(new Action("put_setup", modelMapper.toBytes(request)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertFalse(resultIterator.hasNext());
            assertNotNull(result.getBody());
            final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
            final CustomFlightAssetDescriptor putDescriptor = response.getAsset();
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
     * Test put_wrapup action.
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
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            descriptor.setDatasourceTypeName(getDatasourceTypeName());
            descriptor.setConnectionProperties(createConnectionProperties());
            descriptor.setInteractionProperties(interactionProperties);
            interactionProperties.put("schema_name", "SCHEMA1");
            interactionProperties.put("table_name", "T1");
            final FlightInfo sourceInfo = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
            interactionProperties.put("table_name", "T2");
            final CustomFlightAssetDescriptor putDescriptor
                    = modelMapper.fromBytes(sourceInfo.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            putDescriptor.getInteractionProperties().put("table_name", "T2");
            putDescriptor.getInteractionProperties().put("update_statistics", "true");
            for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
                try (FlightStream stream = getClient().getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                    final FlightClient.ClientStreamListener putStream = getClient()
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(putDescriptor)), root, new AsyncPutListener());
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
            final CustomFlightActionRequest request = new CustomFlightActionRequest().asset(putDescriptor);
            final Iterator<Result> resultIterator = getClient().doAction(new Action("put_wrapup", modelMapper.toBytes(request)));
            assertTrue(resultIterator.hasNext());
            final Result result = resultIterator.next();
            assertFalse(resultIterator.hasNext());
            assertNotNull(result.getBody());
            final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
            final CustomFlightAssetDescriptor wrapupDescriptor = response.getAsset();
            assertNotNull(wrapupDescriptor.getInteractionProperties());
            assertEquals("T2", wrapupDescriptor.getInteractionProperties().get("table_name"));
            final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
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
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().put("database", "unknown");
        request.getConnectionProperties().remove("create_database");
        try {
            getClient().doAction(new Action("test", modelMapper.toBytes(request))).next();
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
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().put("ssl", "true");
        try {
            getClient().doAction(new Action("test", modelMapper.toBytes(request))).next();
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
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().remove("database");
        try {
            getClient().doAction(new Action("validate", modelMapper.toBytes(request))).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing database"));
        }
    }

    /**
     * Test listActions with custom actions.
     */
    @Test
    public void testListCustomActions()
    {
        final List<String> actionTypes = StreamSupport.stream(getClient().listActions().spliterator(), false).map(ActionType::getType)
                .collect(Collectors.toList());
        assertTrue(actionTypes.contains("get_record_count"));
    }

}

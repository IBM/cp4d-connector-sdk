package com.ibm.connect.sdk.func;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightClient.ClientStreamListener;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Result;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.func.rowbased.DummyDatasource;
import com.ibm.connect.sdk.func.rowbased.TestRowBasedFlightProducer;
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

public class TestConnectorFlightProducer
{
    private static TestFlight testFlight;
    private static FlightClient client;
    private static ModelMapper modelMapper = new ModelMapper();

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        testFlight = TestFlight.createLocal(TestConfig.getPort("test.flight.port"), false, new TestRowBasedFlightProducer());
        client = testFlight.getClient();
    }

    protected Table<Integer, Integer, Object> getTableData(FlightInfo info) throws Exception
    {
        final Table<Integer, Integer, Object> data = HashBasedTable.create();
        int tableRowIdx = 0;
        for (final FlightEndpoint endpoint : info.getEndpoints()) {
            try (FlightStream stream = client.getStream(endpoint.getTicket())) {
                while (stream.next()) {
                    try (VectorSchemaRoot root = stream.getRoot()) {
                        final Iterator<FieldVector> iterator = root.getFieldVectors().iterator();
                        int colIdx = 0;
                        while (iterator.hasNext()) {
                            try (FieldVector vector = iterator.next()) {
                                for (int vectorRowIdx = 0; vectorRowIdx < root.getRowCount(); vectorRowIdx++) {
                                    if (!vector.isNull(vectorRowIdx)) {
                                        if (vector instanceof VarCharVector) {
                                            data.put(tableRowIdx + vectorRowIdx, colIdx, vector.getObject(vectorRowIdx).toString());
                                        } else if (vector instanceof DateDayVector) {
                                            final Long millis = TimeUnit.DAYS.toMillis(((DateDayVector) vector).get(vectorRowIdx));
                                            data.put(tableRowIdx + vectorRowIdx, colIdx,
                                                    new Timestamp((millis)).toLocalDateTime().toLocalDate());
                                        } else if (vector instanceof TimeMilliVector) {
                                            data.put(tableRowIdx + vectorRowIdx, colIdx,
                                                    Time.valueOf(((TimeMilliVector) vector).getObject(vectorRowIdx).toLocalTime()));
                                        } else if (vector instanceof TimeMicroVector) {
                                            TimeUnit.MICROSECONDS.convert(567891, TimeUnit.MILLISECONDS);
                                            data.put(tableRowIdx + vectorRowIdx, colIdx, new Time(TimeUnit.NANOSECONDS
                                                    .convert(((TimeMicroVector) vector).getObject(vectorRowIdx), TimeUnit.MILLISECONDS)));
                                        } else if (vector instanceof TimeStampVector) {
                                            data.put(tableRowIdx + vectorRowIdx, colIdx,
                                                    new Timestamp(((TimeStampVector) vector).get(vectorRowIdx)));
                                        } else {
                                            data.put(tableRowIdx + vectorRowIdx, colIdx, vector.getObject(vectorRowIdx));
                                        }
                                    }
                                }
                            }
                            colIdx++;
                        }
                        tableRowIdx += root.getRowCount();
                    }
                }
            }
        }
        return data;
    }

    @Test
    public void testGetDatasourceTypes() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("schema_name", "SCHEMA1");
        interactionProperties.put("table_name", "T1");
        connectionProperties.put("url", "http://whatever.ibm.com/testGetDatasourceTypes");

        final Iterator<Result> result = client.doAction(new Action("list_datasource_types"));
        assertNotNull(result);
    }

    @Test
    public void testListActions() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("schema_name", "SCHEMA1");
        interactionProperties.put("table_name", "T1");
        connectionProperties.put("url", "http://whatever.ibm.com/testListActions");

        final Iterable<ActionType> actionTypes = client.listActions();
        assertNotNull(actionTypes);
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void testBasicWrite() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        descriptor.setPath("/testBasicWriteSchema/testBasicWrite");
        descriptor.setFields(Arrays.asList(new CustomFlightAssetField().name("name").type("varchar"),
                new CustomFlightAssetField().name("age").type("int")));
        interactionProperties.put("schema_name", "testBasicWriteSchema");
        interactionProperties.put("table_name", "testBasicWrite");
        connectionProperties.put("url", "http://whatever.ibm.com/testBasicWrite");

        try (BufferAllocator rootAllocator = new RootAllocator()) {
            final Field name = new Field("name", FieldType.nullable(new ArrowType.Utf8()), null);
            final Field age = new Field("age", FieldType.nullable(new ArrowType.Int(32, true)), null);
            final Schema schemaPerson = new Schema(Arrays.asList(name, age));
            try (VectorSchemaRoot vectorSchemaRoot = VectorSchemaRoot.create(schemaPerson, rootAllocator)) {
                final VarCharVector nameVector = (VarCharVector) vectorSchemaRoot.getVector("name");
                nameVector.allocateNew(3);
                nameVector.set(0, "David".getBytes());
                nameVector.set(1, "Gladis".getBytes());
                nameVector.set(2, "Juan".getBytes());
                final IntVector ageVector = (IntVector) vectorSchemaRoot.getVector("age");
                ageVector.allocateNew(3);
                ageVector.set(0, 10);
                ageVector.set(1, 20);
                ageVector.set(2, 30);
                vectorSchemaRoot.setRowCount(3);
                final ClientStreamListener clientStreamListener = client.startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)),
                        vectorSchemaRoot, new AsyncPutListener());
                clientStreamListener.putNext();
                clientStreamListener.completed();
                clientStreamListener.getResult();
            }
            final DummyDatasource source = DummyDatasource.getInstance(descriptor.getPath());
            assertEquals(3, source.getRecords().size());
            assertEquals("David", getValue(source, 0, 0));
            assertEquals(10, getValue(source, 0, 1));
            assertEquals("Gladis", getValue(source, 1, 0));
            assertEquals(20, getValue(source, 1, 1));
            assertEquals("Juan", getValue(source, 2, 0));
            assertEquals(30, getValue(source, 2, 1));
        }

    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void testBasicWriteRead() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        descriptor.setPath("/testBasicWriteReadSchema/testBasicWriteRead");
        descriptor.setFields(Arrays.asList(new CustomFlightAssetField().name("name").type("varchar"),
                new CustomFlightAssetField().name("age").type("int")));
        descriptor.setBatchSize(10);
        interactionProperties.put("schema_name", "testBasicWriteReadSchema");
        interactionProperties.put("table_name", "testBasicWriteRead");
        connectionProperties.put("url", "http://whatever.ibm.com/testBasicWriteRead");

        try (BufferAllocator rootAllocator = new RootAllocator()) {
            final Field name = new Field("name", FieldType.nullable(new ArrowType.Utf8()), null);
            final Field age = new Field("age", FieldType.nullable(new ArrowType.Int(32, true)), null);
            final Schema schemaPerson = new Schema(Arrays.asList(name, age));
            try (VectorSchemaRoot vectorSchemaRoot = VectorSchemaRoot.create(schemaPerson, rootAllocator)) {
                final VarCharVector nameVector = (VarCharVector) vectorSchemaRoot.getVector("name");
                nameVector.allocateNew(3);
                nameVector.set(0, "David".getBytes());
                nameVector.set(1, "Gladis".getBytes());
                nameVector.set(2, "Juan".getBytes());
                final IntVector ageVector = (IntVector) vectorSchemaRoot.getVector("age");
                ageVector.allocateNew(3);
                ageVector.set(0, 10);
                ageVector.set(1, 20);
                ageVector.set(2, 30);
                vectorSchemaRoot.setRowCount(3);
                final ClientStreamListener clientStreamListener = client.startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)),
                        vectorSchemaRoot, new AsyncPutListener());
                clientStreamListener.putNext();
                clientStreamListener.completed();
                clientStreamListener.getResult();
            }
            final DummyDatasource source = DummyDatasource.getInstance(descriptor.getPath());
            assertEquals(3, source.getRecords().size());
            assertEquals("David", getValue(source, 0, 0));
            assertEquals(10, getValue(source, 0, 1));
            assertEquals("Gladis", getValue(source, 1, 0));
            assertEquals(20, getValue(source, 1, 1));
            assertEquals("Juan", getValue(source, 2, 0));
            assertEquals(30, getValue(source, 2, 1));
        }
        final FlightInfo info = client.getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        assertEquals(1, info.getEndpoints().size());
        final List<Object> results = new ArrayList<>();
        final FlightEndpoint endpoint = info.getEndpoints().get(0);
        try (FlightStream stream = client.getStream(endpoint.getTicket())) {
            while (stream.next()) {
                try (VectorSchemaRoot root = stream.getRoot()) {
                    final Iterator<FieldVector> iterator = root.getFieldVectors().iterator();
                    while (iterator.hasNext()) {
                        try (FieldVector vector = iterator.next()) {
                            for (int vectorRowIdx = 0; vectorRowIdx < root.getRowCount(); vectorRowIdx++) {
                                if (!vector.isNull(vectorRowIdx)) {
                                    results.add(vector.getObject(vectorRowIdx));
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(6, results.size());
        assertEquals("David", results.get(0).toString());
        assertEquals("Gladis", results.get(1).toString());
        assertEquals("Juan", results.get(2).toString());
        assertEquals(10, Integer.parseInt(results.get(3).toString()));
        assertEquals(20, Integer.parseInt(results.get(4).toString()));
        assertEquals(30, Integer.parseInt(results.get(5).toString()));

    }

    @Test
    public void testDiscovery() throws Exception
    {
        final CustomFlightAssetDescriptor schemaAsset = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        schemaAsset.setDatasourceTypeName("mock");
        schemaAsset.setConnectionProperties(connectionProperties);
        schemaAsset.setInteractionProperties(interactionProperties);
        schemaAsset.setPath("/testDiscoverySchema");
        schemaAsset.setId(schemaAsset.getPath());
        schemaAsset.setBatchSize(10);
        interactionProperties.put("schema_name", "testDiscoverySchema");
        connectionProperties.put("url", "http://whatever.ibm.com/testDiscovery");
        DummyDatasource.getInstance(schemaAsset.getPath()).setAsset(schemaAsset);

        final CustomFlightAssetDescriptor tableAsset = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties tableAssetinteractionProperties = new DiscoveredAssetInteractionProperties();
        tableAsset.setDatasourceTypeName("mock");
        tableAsset.setConnectionProperties(connectionProperties);
        tableAsset.setInteractionProperties(tableAssetinteractionProperties);
        tableAsset.setPath("/testDiscoverySchema/testDiscoveryTable");
        tableAsset.setId(tableAsset.getPath());
        tableAsset.setFields(Arrays.asList(new CustomFlightAssetField().name("name").type("varchar"),
                new CustomFlightAssetField().name("age").type("int")));
        tableAsset.setBatchSize(10);
        tableAssetinteractionProperties.put("schema_name", "testDiscoverySchema");
        interactionProperties.put("table_name", "testDiscoveryTable");
        DummyDatasource.getInstance(tableAsset.getPath()).setAsset(tableAsset);

        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setPath("/");
        criteria.setDatasourceTypeName("mock");
        criteria.setConnectionProperties(connectionProperties);
        Iterable<FlightInfo> infos = client.listFlights(new Criteria(modelMapper.toBytes(criteria)));
        assertEquals(schemaAsset,
                modelMapper.fromBytes(infos.iterator().next().getDescriptor().getCommand(), CustomFlightAssetDescriptor.class));
        criteria.setPath("/testDiscoverySchema");
        infos = client.listFlights(new Criteria(modelMapper.toBytes(criteria)));
        assertEquals(tableAsset,
                modelMapper.fromBytes(infos.iterator().next().getDescriptor().getCommand(), CustomFlightAssetDescriptor.class));
        criteria.setPath("/testDiscoverySchema/testDiscoveryTable");
        infos = client.listFlights(new Criteria(modelMapper.toBytes(criteria)));
        final FlightInfo infoWithSchema = infos.iterator().next();
        assertEquals(tableAsset, modelMapper.fromBytes(infoWithSchema.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class));
        assertTrue(infoWithSchema.getSchema().getFields().stream().map(field -> field.getName()).collect(Collectors.toSet())
                .containsAll(Arrays.asList("name", "age")));

    }

    @Test
    public void testNumeric() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("url", "http://whatever.ibm.com/testNumeric");

        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        descriptor.setPath("/testNumericSchema/testNumeric");
        descriptor.setBatchSize(10);
        interactionProperties.put("schema_name", "testNumericSchema");
        interactionProperties.put("table_name", "testNumeric");
        DummyDatasource.getInstance(descriptor.getPath()).setAsset(descriptor);
        descriptor.addFieldsItem(new CustomFlightAssetField().name("bigint").type("bigint").signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("decimal312").type("decimal").length(31).scale(2));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("double").type("double"));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("float").type("float"));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("float23").type("float").length(23));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("integer").type("integer").signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("numeric").type("numeric").length(31).scale(6));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("real").type("real"));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("smallint").type("smallint").signed(true));

        final Record rowOne = new Record(descriptor.getFields().size());
        rowOne.appendValue(new java.math.BigInteger(Long.toString(Long.MIN_VALUE)));
        rowOne.appendValue(new BigDecimal("-99999999999999999999999999999.99"));
        rowOne.appendValue(Double.valueOf("-1.7976931348623157E+308"));
        rowOne.appendValue(Double.valueOf("-2.2250738585072014E-308"));
        rowOne.appendValue(Float.valueOf("-3.4028235E+38"));
        rowOne.appendValue(Integer.MIN_VALUE);
        rowOne.appendValue(new BigDecimal("-9999999999999999999999999.999999"));
        rowOne.appendValue(Float.valueOf("-1.17549435E-38"));
        rowOne.appendValue(Short.MIN_VALUE);
        final Record rowTwo = new Record(descriptor.getFields().size());
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        rowTwo.appendValue(null);
        final Record rowThree = new Record(descriptor.getFields().size());
        rowThree.appendValue(new java.math.BigInteger(Long.toString(Long.MAX_VALUE)));
        rowThree.appendValue(new BigDecimal("99999999999999999999999999999.99"));
        rowThree.appendValue(Double.valueOf("1.7976931348623157E+308"));
        rowThree.appendValue(Double.valueOf("2.2250738585072014E-308"));
        rowThree.appendValue(Float.valueOf("3.4028235E+38"));
        rowThree.appendValue(Integer.MAX_VALUE);
        rowThree.appendValue(new BigDecimal("9999999999999999999999999.999999"));
        rowThree.appendValue(Float.valueOf("1.17549435E-38"));
        rowThree.appendValue(Short.MAX_VALUE);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowOne);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowTwo);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowThree);
        final FlightInfo info = client.getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchema();
        assertEquals(9, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(18, data.size());
        assertEquals(Long.MIN_VALUE, data.get(0, 0));
        assertEquals(new BigDecimal("-99999999999999999999999999999.99"), data.get(0, 1));
        assertEquals(Double.valueOf("-1.7976931348623157E+308"), data.get(0, 2));
        assertEquals(Double.valueOf("-2.2250738585072014E-308"), data.get(0, 3));
        assertEquals(Float.valueOf("-3.4028235E+38"), Float.valueOf(data.get(0, 4).toString()));
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
        assertEquals(Float.valueOf("3.4028235E+38"), Float.valueOf(data.get(2, 4).toString()));
        assertEquals(Integer.MAX_VALUE, data.get(2, 5));
        assertEquals(new BigDecimal("9999999999999999999999999.999999"), data.get(2, 6));
        assertEquals(Float.valueOf("1.17549435E-38"), data.get(2, 7));
        assertEquals(Short.MAX_VALUE, data.get(2, 8));
    }

    @Test
    public void testDates() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("url", "http://whatever.ibm.com/testDates");

        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        descriptor.setPath("/testDatesSchema/testDates");
        descriptor.setBatchSize(10);
        interactionProperties.put("schema_name", "testDatesSchema");
        interactionProperties.put("table_name", "testDates");
        DummyDatasource.getInstance(descriptor.getPath()).setAsset(descriptor);
        descriptor.addFieldsItem(new CustomFlightAssetField().name("date").type("date"));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("time").type("time"));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("timestamp").type("timestamp"));

        final Record rowOne = new Record(descriptor.getFields().size());
        rowOne.appendValue(Date.valueOf("0001-01-01"));
        rowOne.appendValue(Time.valueOf("00:00:00"));
        rowOne.appendValue(Timestamp.valueOf("0001-01-01 00:00:00.000"));
        final Record rowTwo = new Record(descriptor.getFields().size());
        rowTwo.appendValue(Date.valueOf("1677-09-21"));
        rowTwo.appendValue(Time.valueOf("00:12:44"));
        rowTwo.appendValue(Timestamp.valueOf("1677-09-21 00:12:44.000"));
        final Record rowThree = new Record(descriptor.getFields().size());
        rowThree.appendValue(Date.valueOf("1970-01-01"));
        rowThree.appendValue(Time.valueOf("00:00:00"));
        rowThree.appendValue(Timestamp.valueOf("1970-01-01 00:00:00.000"));
        final Record rowFour = new Record(descriptor.getFields().size());
        rowFour.appendValue(null);
        rowFour.appendValue(null);
        rowFour.appendValue(null);
        final Record rowFive = new Record(descriptor.getFields().size());
        rowFive.appendValue(Date.valueOf("2262-04-11"));
        rowFive.appendValue(Time.valueOf("23:47:16"));
        rowFive.appendValue(Timestamp.valueOf("2262-04-11 23:47:16.854"));
        final Record rowSix = new Record(descriptor.getFields().size());
        rowSix.appendValue(Date.valueOf("9999-12-31"));
        rowSix.appendValue(Time.valueOf("23:59:59"));
        rowSix.appendValue(Timestamp.valueOf("9999-12-31 23:59:59.999"));

        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowOne);

        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowTwo);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowThree);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowFour);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowFive);
        DummyDatasource.getInstance(descriptor.getPath()).putRecord(rowSix);

        final FlightInfo info = client.getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchema();
        assertEquals(3, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(15, data.size());

        assertEquals(Time.valueOf("00:00:00"), data.get(0, 1));
        assertEquals(Timestamp.valueOf("0001-01-01 00:00:00.000"), data.get(0, 2));

        assertEquals(LocalDate.parse("1677-09-21"), data.get(1, 0));
        assertEquals(Time.valueOf("00:12:44"), data.get(1, 1));
        assertEquals(Timestamp.valueOf("1677-09-21 00:12:44.000"), data.get(1, 2));

        assertEquals(LocalDate.parse("1970-01-01"), data.get(2, 0));
        assertEquals(Time.valueOf("00:00:00"), data.get(2, 1));
        assertEquals(Timestamp.valueOf("1970-01-01 00:00:00.000"), data.get(2, 2));

        assertEquals(LocalDate.parse("2262-04-11"), data.get(4, 0));
        assertEquals(Time.valueOf("23:47:16"), data.get(4, 1));
        assertEquals(Timestamp.valueOf("2262-04-11 23:47:16.854"), data.get(4, 2));

        assertEquals(LocalDate.parse("9999-12-31"), data.get(5, 0));
        assertEquals(Time.valueOf("23:59:59"), data.get(5, 1));
        assertEquals(Timestamp.valueOf("9999-12-31 23:59:59.999"), data.get(5, 2));

    }

    public Serializable getValue(DummyDatasource source, int row, int column)
    {
        return source.getRecords().get(row).getValues().get(column);
    }

    @AfterClass
    public static void tearDownOnce() throws IOException, InterruptedException
    {
        if (testFlight != null) {
            testFlight.close();
            client.close();
        }
    }
}

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * An abstract class for a Flight test suite.
 */
public abstract class FlightTestSuite
{
    protected abstract FlightClient getClient();

    protected Table<Integer, Integer, Object> getTableData(FlightInfo info) throws Exception
    {
        final Table<Integer, Integer, Object> data = HashBasedTable.create();
        int tableRowIdx = 0;
        for (final FlightEndpoint endpoint : info.getEndpoints()) {
            try (FlightStream stream = getClient().getStream(endpoint.getTicket())) {
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
                                            data.put(tableRowIdx + vectorRowIdx, colIdx,
                                                    new Date(TimeUnit.DAYS.toMillis(((DateDayVector) vector).get(vectorRowIdx))));
                                        } else if (vector instanceof TimeMilliVector) {
                                            data.put(tableRowIdx + vectorRowIdx, colIdx,
                                                    Time.valueOf(((TimeMilliVector) vector).getObject(vectorRowIdx).toLocalTime()));
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
}

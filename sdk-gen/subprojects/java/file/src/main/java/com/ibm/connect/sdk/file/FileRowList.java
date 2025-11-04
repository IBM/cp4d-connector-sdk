/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import java.util.AbstractList;
import java.util.Iterator;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;

import com.ibm.connect.sdk.api.Record;

/**
 * A list of rows from a Flight stream.
 */
public class FileRowList extends AbstractList<Row>
{
    private final Iterator<Record> records;

    private int nextIndex;

    /**
     * Constructs a list of rows from a Flight stream.
     *
     * @param records records
     */
    public FileRowList(Iterator<Record> records)
    {
        super();
        this.records = records;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return records.hasNext() ? nextIndex + 1 : nextIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row get(int index)
    {
        nextIndex = index + 1;
        return RowFactory.create(records.next().getValues().toArray());
    }

}

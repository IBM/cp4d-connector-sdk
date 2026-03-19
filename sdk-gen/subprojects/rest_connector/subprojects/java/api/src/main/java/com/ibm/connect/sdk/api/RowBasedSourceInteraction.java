/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An row-based interaction with a connector asset as a source.
 *
 * @param <C> a connector class
 */
public abstract class RowBasedSourceInteraction<C extends RowBasedConnector<?, ?>> implements Iterable<Record>, SourceInteraction<C>
{
    private static final Logger LOGGER = getLogger(RowBasedSourceInteraction.class);

    private C connector;
    private CustomFlightAssetDescriptor asset;
    private VectorSchemaRoot vectorSchemaRoot;
    private Iterator<VectorSchemaRoot> vectorSchemaRootIterator;

    public void setConnector(C connector)
    {
        this.connector = connector;
    }

    public C getConnector()
    {
        return connector;
    };

    public CustomFlightAssetDescriptor getAsset()
    {
        return asset;
    }

    public void setAsset(CustomFlightAssetDescriptor asset)
    {
        this.asset = asset;
    }

    /**
     * Retrieves the next record from the connector. If there are no more records to
     * retrieve, <code>null</code> is returned.
     *
     * @return Record object.
     */
    public abstract Record getRecord();

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Record> iterator()
    {
        return new Iterator<Record>() {
            private Record next = getRecord();

            @Override
            public boolean hasNext()
            {
                return next != null;
            }

            @Override
            public Record next()
            {
                final Record returned = next;
                if (returned == null) {
                    throw new NoSuchElementException();
                }
                next = getRecord();
                return returned;
            }
        };
    }

    /**
     * Returns a sequential stream of records.
     *
     * @return Stream of Record objects.
     */
    public Stream<Record> stream()
    {
        return StreamSupport.stream(spliterator(), false);
    }

    public Schema toArrow(CustomFlightAssetDescriptor asset)
    {

        return ArrowConversions.toArrow(getFields());
    }

    public abstract List<CustomFlightAssetField> getFields();

    /**
     * Initialize an arrow vector schema root for this input interaction.
     *
     * @param allocator
     *            the buffer allocator.
     * @return The arrow vector schema root iterator.
     */
    public VectorSchemaRoot initArrow(BufferAllocator allocator)
    {
        return VectorSchemaRoot.create(ArrowConversions.toArrow(asset.getFields()), allocator);
    }

    /**
     * Get an arrow vector schema root iterator for this input interaction.
     *
     * @param root
     *            the arrow vector schema root.
     * @param batchSize
     *            the number of records to include in each batch, or -1 for all.
     * @return The arrow vector schema root iterator.
     */
    public Iterator<VectorSchemaRoot> toArrow(VectorSchemaRoot root, int batchSize)
    {
        return ArrowConversions.toArrow(root, iterator(), batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() throws Exception
    {
        return toArrow(asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginStream(BufferAllocator allocator) throws Exception
    {
        vectorSchemaRoot = initArrow(allocator);
        vectorSchemaRootIterator = toArrow(vectorSchemaRoot, asset.getBatchSize());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextBatch() throws Exception
    {
        return vectorSchemaRootIterator.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VectorSchemaRoot nextBatch() throws Exception
    {
        return vectorSchemaRootIterator.next();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        try {
            if (vectorSchemaRoot != null) {
                vectorSchemaRoot.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}

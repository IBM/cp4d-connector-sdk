package com.ibm.connect.sdk.func.rowbased;

import java.util.Arrays;
import java.util.List;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$Connector;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$SourceInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

public class TestSourceInteraction extends $_CONNNAMEPREFIX_$SourceInteraction
{
    private DummyDatasource datasource;
    private ModelMapper modelMapper = new ModelMapper();

    protected TestSourceInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset)
    {
        super(connector, asset);
        datasource = DummyDatasource.getInstance(asset.getPath());
    }

    @Override
    public Record getRecord()
    {
        // TODO implement this
        return datasource.getRecord();
    }

    @Override
    public void close() throws Exception
    {
        // TODO Auto-generated method stub

    }

    @Override
    public List<Ticket> getTickets() throws Exception
    {
        final TicketInfo info = new TicketInfo().partitionIndex(0).requestId("whatever");
        return Arrays.asList(new Ticket(modelMapper.toBytes(info)));
    }

    @Override
    public List<CustomFlightAssetField> getFields()
    {
        // TODO Auto-generated method stub
        return getAsset().getFields();
    }
}

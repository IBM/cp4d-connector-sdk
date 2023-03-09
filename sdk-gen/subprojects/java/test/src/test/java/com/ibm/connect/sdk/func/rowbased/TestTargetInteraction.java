package com.ibm.connect.sdk.func.rowbased;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$Connector;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$TargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

public class TestTargetInteraction extends $_CONNNAMEPREFIX_$TargetInteraction
{
    private DummyDatasource datasource;

    protected TestTargetInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset)
    {
        super(connector, asset);
        datasource = DummyDatasource.getInstance(asset.getPath());
        datasource.setAsset(asset);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void putRecord(Record record)
    {
        datasource.putRecord(record);
    }

    @Override
    public void close() throws Exception
    {
        // TODO Auto-generated method stub

    }

    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        // TODO Auto-generated method stub
        return null;
    }

}

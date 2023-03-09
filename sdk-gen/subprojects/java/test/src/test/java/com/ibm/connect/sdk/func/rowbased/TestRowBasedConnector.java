package com.ibm.connect.sdk.func.rowbased;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$Connector;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$SourceInteraction;
import com.ibm.connect.sdk.basic.impl.$_CONNNAMEPREFIX_$TargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

public class TestRowBasedConnector extends $_CONNNAMEPREFIX_$Connector
{
    public TestRowBasedConnector(ConnectionProperties properties)
    {
        super(properties);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void close() throws Exception
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void connect()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final String path = criteria.getPath();
        if (path == null || path.equals("/")) {
            return DummyDatasource.getInstances().stream().filter(instance -> instance.getAssetID().split("/", 0).length == 2)
                    .map(instance -> instance.getAsset()).collect(Collectors.toList());
        } else {
            final String[] pathSegments = path.split("/", 0);
            if (pathSegments.length == 2) {
                return DummyDatasource.getInstances().stream()
                        .filter(instance -> instance.getAssetID().startsWith(path) && !instance.getAssetID().equals(path))
                        .map(instance -> instance.getAsset()).collect(Collectors.toList());
            } else {
                return Arrays.asList(DummyDatasource.getInstance(path).getAsset());
            }
        }
    }

    @Override
    public $_CONNNAMEPREFIX_$SourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket)
    {
        // TODO include your ticket info
        return new TestSourceInteraction(this, asset);
    }

    @Override
    public $_CONNNAMEPREFIX_$TargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset)
    {
        return new TestTargetInteraction(this, asset);
    }

    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration conf)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void commit()
    {
        // TODO Auto-generated method stub

    }
}

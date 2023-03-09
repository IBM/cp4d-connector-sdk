package com.ibm.connect.sdk.func.rowbased;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.ibm.connect.sdk.api.Record;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

public class DummyDatasource
{
    public static Map<String, DummyDatasource> instances = new HashMap<>();
    private String assetID;
    private List<Record> records = new LinkedList<>();
    private CustomFlightAssetDescriptor asset;

    @SuppressWarnings("PMD.SingletonClassReturningNewInstance")
    public synchronized static DummyDatasource getInstance(String assetID)
    {
        DummyDatasource instance = instances.get(assetID);
        if (instance == null) {
            instance = new DummyDatasource(assetID);
            instances.put(assetID, instance);
        }
        return instance;
    }

    public synchronized static Collection<DummyDatasource> getInstances()
    {
        return instances.values();
    }

    public DummyDatasource(String assetID)
    {
        this.assetID = assetID;
    }

    public String getAssetID()
    {
        return assetID;
    }

    public List<Record> getRecords()
    {
        return records;
    }

    public CustomFlightAssetDescriptor getAsset()
    {
        return asset;
    }

    public void setAsset(CustomFlightAssetDescriptor asset)
    {
        this.asset = asset;
    }

    public void putRecord(Record record)
    {
        records.add(record);

    }

    public Record getRecord()
    {
        if (!records.isEmpty()) {
            return records.remove(0);
        }
        return null;
    }
}

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypePropertyValues;

/**
 * Unit tests for the OneDrive connector write capabilities and datasource type
 * configuration.
 *
 * <p>These tests validate the static configuration of {@link OneDriveDatasourceType}
 * and the property contract of {@link OneDriveTargetInteraction} — they do NOT
 * require a live OneDrive account and will always run.
 */
public class TestOneDriveWriteCapabilities
{

    // -----------------------------------------------------------------------
    // OneDriveDatasourceType registration tests
    // -----------------------------------------------------------------------

    /**
     * The datasource type name must be {@code file-onedrive}.
     */
    @Test
    public void testDatasourceTypeName()
    {
        assertEquals("Datasource type name must match",
                "file-onedrive", OneDriveDatasourceType.DATASOURCE_TYPE_NAME);
        assertEquals("Datasource type name must match",
                OneDriveDatasourceType.DATASOURCE_TYPE_NAME, OneDriveDatasourceType.INSTANCE.getName());
    }

    /**
     * The OneDrive data source type must have ACTIVE status.
     */
    @Test
    public void testDatasourceTypeStatusActive()
    {
        assertEquals("OneDrive datasource type must be ACTIVE",
                CustomFlightDatasourceType.StatusEnum.ACTIVE, OneDriveDatasourceType.INSTANCE.getStatus());
    }

    /**
     * The OneDrive data source type must declare itself as an allowed target.
     */
    @Test
    public void testAllowedAsTarget()
    {
        assertTrue("OneDrive datasource type must be allowed as a target",
                OneDriveDatasourceType.INSTANCE.isAllowedAsTarget());
    }

    /**
     * The target properties must include a required {@code file_name} property.
     */
    @Test
    public void testTargetPropertyFileName()
    {
        final List<CustomDatasourceTypeProperty> targetProps =
                OneDriveDatasourceType.INSTANCE.getProperties().getTarget();
        assertNotNull("Target properties list must not be null", targetProps);
        final CustomDatasourceTypeProperty fileNameProp = targetProps.stream()
                .filter(p -> "file_name".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull("Target must contain a 'file_name' property", fileNameProp);
        assertTrue("'file_name' target property must be required", fileNameProp.isRequired());
        assertEquals("'file_name' target property must be STRING type",
                CustomDatasourceTypeProperty.TypeEnum.STRING, fileNameProp.getType());
    }

    /**
     * The target properties must include a {@code file_format} enum that lists
     * the {@code binary} format.
     */
    @Test
    public void testTargetPropertyFileFormatIncludesBinary()
    {
        final List<CustomDatasourceTypeProperty> targetProps =
                OneDriveDatasourceType.INSTANCE.getProperties().getTarget();
        assertNotNull("Target properties list must not be null", targetProps);
        final CustomDatasourceTypeProperty fileFormatProp = targetProps.stream()
                .filter(p -> "file_format".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull("Target must contain a 'file_format' property", fileFormatProp);
        assertEquals("'file_format' must be an ENUM property",
                CustomDatasourceTypeProperty.TypeEnum.ENUM, fileFormatProp.getType());
        final List<String> values = fileFormatProp.getValues().stream()
                .map(DatasourceTypePropertyValues::getValue)
                .collect(Collectors.toList());
        assertTrue("'file_format' values must include 'binary'", values.contains("binary"));
        assertTrue("'file_format' values must include 'csv'", values.contains("csv"));
        assertTrue("'file_format' values must include 'json'", values.contains("json"));
        assertTrue("'file_format' values must include 'parquet'", values.contains("parquet"));
    }

    // -----------------------------------------------------------------------
    // OneDriveTargetInteraction construction validation tests
    // -----------------------------------------------------------------------

    /**
     * Constructing a {@link OneDriveTargetInteraction} without {@code file_name}
     * must throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testTargetInteractionRequiresFileName()
    {
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor asset =
                new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor();
        asset.setDatasourceTypeName(OneDriveDatasourceType.DATASOURCE_TYPE_NAME);
        // Intentionally omit interaction properties so file_name is absent.
        asset.setInteractionProperties(new com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties());

        try {
            // OneDriveConnector constructor only stores properties; no network call
            // is made until connect() is invoked — safe to use here.
            final com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties connProps =
                    new com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties();
            connProps.put("tenant_id", "test");
            connProps.put("client_id", "test");
            connProps.put("username", "test@test.onmicrosoft.com");
            connProps.put("password", "test");
            new OneDriveTargetInteraction(new OneDriveConnector(connProps), asset);
            org.junit.Assert.fail("Expected IllegalArgumentException for missing file_name");
        }
        catch (IllegalArgumentException e) {
            assertTrue("Exception message must mention 'file_name'",
                    e.getMessage() != null && e.getMessage().contains("file_name"));
        }
        catch (Exception e) {
            org.junit.Assert.fail("Expected IllegalArgumentException but got: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypePropertyValues;

/**
 * Unit tests for the Dropbox connector write capabilities.
 *
 * <p>These tests validate the static configuration of {@link DropboxDatasourceType}
 * and the property contract of {@link DropboxTargetInteraction} — they do NOT
 * require a live Dropbox account and will always run.
 */
public class TestDropboxWriteCapabilities
{

    // -----------------------------------------------------------------------
    // DropboxDatasourceType write registration tests
    // -----------------------------------------------------------------------

    /**
     * The datasource type name must match the expected constant "file-dropbox".
     */
    @Test
    public void testDatasourceTypeName()
    {
        assertEquals("Datasource type name must be 'file-dropbox'",
                "file-dropbox", DropboxDatasourceType.DATASOURCE_TYPE_NAME);
        assertEquals("INSTANCE name must match",
                DropboxDatasourceType.DATASOURCE_TYPE_NAME, DropboxDatasourceType.INSTANCE.getName());
    }

    /**
     * The Dropbox data source type must declare an ACTIVE status.
     */
    @Test
    public void testDatasourceTypeStatusActive()
    {
        assertEquals("Dropbox datasource type must be ACTIVE",
                CustomFlightDatasourceType.StatusEnum.ACTIVE, DropboxDatasourceType.INSTANCE.getStatus());
    }

    /**
     * The Dropbox data source type must declare itself as an allowed target.
     */
    @Test
    public void testAllowedAsTarget()
    {
        assertTrue("Dropbox datasource type must be allowed as a target",
                DropboxDatasourceType.INSTANCE.isAllowedAsTarget());
    }

    /**
     * The target properties must include a required {@code file_name} property.
     */
    @Test
    public void testTargetPropertyFileName()
    {
        final List<CustomDatasourceTypeProperty> targetProps =
                DropboxDatasourceType.INSTANCE.getProperties().getTarget();
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
     * the {@code binary} format as well as common structured formats.
     */
    @Test
    public void testTargetPropertyFileFormatIncludesBinary()
    {
        final List<CustomDatasourceTypeProperty> targetProps =
                DropboxDatasourceType.INSTANCE.getProperties().getTarget();
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
    // DropboxTargetInteraction construction validation tests
    // -----------------------------------------------------------------------

    /**
     * Constructing a {@link DropboxTargetInteraction} without {@code file_name}
     * must throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testTargetInteractionRequiresFileName()
    {
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor asset =
                new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor();
        asset.setDatasourceTypeName(DropboxDatasourceType.DATASOURCE_TYPE_NAME);
        // Intentionally omit interaction properties so file_name is absent.
        asset.setInteractionProperties(new com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties());

        try {
            // DropboxConnector constructor only stores properties; no network call is
            // made until connect() is invoked — safe to use here.
            final com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties connProps =
                    new com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties();
            connProps.put("access_token", "test");
            new DropboxTargetInteraction(new DropboxConnector(connProps), asset);
            fail("Expected IllegalArgumentException for missing file_name");
        }
        catch (IllegalArgumentException e) {
            assertTrue("Exception message must mention 'file_name'",
                    e.getMessage() != null && e.getMessage().contains("file_name"));
        }
        catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}

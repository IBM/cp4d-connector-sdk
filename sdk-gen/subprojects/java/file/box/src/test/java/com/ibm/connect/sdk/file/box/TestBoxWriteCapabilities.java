/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypePropertyValues;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Unit tests for the Box connector write capabilities.
 *
 * <p>
 * These tests validate the static configuration of {@link BoxDatasourceType}
 * and the property contract of {@link BoxTargetInteraction} — they do NOT
 * require a live Box account and will always run.
 */
public class TestBoxWriteCapabilities
{

    // -----------------------------------------------------------------------
    // BoxDatasourceType write registration tests
    // -----------------------------------------------------------------------

    /**
     * The Box data source type must declare itself as an allowed target.
     */
    @Test
    public void testAllowedAsTarget()
    {
        assertTrue("Box datasource type must be allowed as a target", BoxDatasourceType.INSTANCE.isAllowedAsTarget());
    }

    /**
     * The target properties must include a required {@code file_name} property.
     */
    @Test
    public void testTargetPropertyFileName()
    {
        final List<CustomDatasourceTypeProperty> targetProps = BoxDatasourceType.INSTANCE.getProperties().getTarget();
        assertNotNull("Target properties list must not be null", targetProps);
        final CustomDatasourceTypeProperty fileNameProp
                = targetProps.stream().filter(p -> "file_name".equals(p.getName())).findFirst().orElse(null);
        assertNotNull("Target must contain a 'file_name' property", fileNameProp);
        assertTrue("'file_name' target property must be required", fileNameProp.isRequired());
        assertEquals("'file_name' target property must be STRING type", CustomDatasourceTypeProperty.TypeEnum.STRING,
                fileNameProp.getType());
    }

    /**
     * The target properties must include a {@code file_format} enum that lists the
     * {@code binary} format.
     */
    @Test
    public void testTargetPropertyFileFormatIncludesBinary()
    {
        final List<CustomDatasourceTypeProperty> targetProps = BoxDatasourceType.INSTANCE.getProperties().getTarget();
        assertNotNull("Target properties list must not be null", targetProps);
        final CustomDatasourceTypeProperty fileFormatProp
                = targetProps.stream().filter(p -> "file_format".equals(p.getName())).findFirst().orElse(null);
        assertNotNull("Target must contain a 'file_format' property", fileFormatProp);
        assertEquals("'file_format' must be an ENUM property", CustomDatasourceTypeProperty.TypeEnum.ENUM, fileFormatProp.getType());
        final List<String> values
                = fileFormatProp.getValues().stream().map(DatasourceTypePropertyValues::getValue).collect(Collectors.toList());
        assertTrue("'file_format' values must include 'binary'", values.contains("binary"));
        assertTrue("'file_format' values must include 'csv'", values.contains("csv"));
        assertTrue("'file_format' values must include 'json'", values.contains("json"));
        assertTrue("'file_format' values must include 'parquet'", values.contains("parquet"));
    }

    /**
     * The datasource type must have an ACTIVE status.
     */
    @Test
    public void testDatasourceTypeStatusActive()
    {
        assertEquals("Box datasource type must be ACTIVE", CustomFlightDatasourceType.StatusEnum.ACTIVE,
                BoxDatasourceType.INSTANCE.getStatus());
    }

    /**
     * The datasource type name must match the expected constant.
     */
    @Test
    public void testDatasourceTypeName()
    {
        assertEquals("Datasource type name must match", BoxDatasourceType.DATASOURCE_TYPE_NAME, BoxDatasourceType.INSTANCE.getName());
        assertEquals("Datasource type name constant value", "custom_box", BoxDatasourceType.DATASOURCE_TYPE_NAME);
    }

    // -----------------------------------------------------------------------
    // BoxTargetInteraction construction validation tests
    // -----------------------------------------------------------------------

    /**
     * Constructing a {@link BoxTargetInteraction} without {@code file_name} must
     * throw an {@link IllegalArgumentException}.
     *
     * <p>
     * This test exercises the in-repo validation path in
     * {@link BoxTargetInteraction#BoxTargetInteraction(BoxConnector, CustomFlightAssetDescriptor)}
     * without requiring a live Box connection.
     */
    @Test
    public void testTargetInteractionRequiresFileName()
    {
        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
        asset.setDatasourceTypeName(BoxDatasourceType.DATASOURCE_TYPE_NAME);
        // Intentionally omit interaction properties so file_name is absent.
        asset.setInteractionProperties(new DiscoveredAssetInteractionProperties());

        try {
            // BoxConnector constructor only stores properties; no network call is
            // made until connect() is invoked — safe to use here.
            final ConnectionProperties connProps = new ConnectionProperties();
            connProps.put("client_id", "test");
            connProps.put("client_secret", "test");
            connProps.put("enterprise_id", "test");
            connProps.put("private_key", "test");
            connProps.put("public_key", "test");
            new BoxTargetInteraction(new BoxConnector(connProps), asset);
            fail("Expected IllegalArgumentException for missing file_name");
        }
        catch (IllegalArgumentException e) {
            assertTrue("Exception message must mention 'file_name'", e.getMessage() != null && e.getMessage().contains("file_name"));
        }
        catch (Exception e) {
            fail("Expected IllegalArgumentException but got: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}

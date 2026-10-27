/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * Builds an Arrow {@link Schema} from a list of {@link RestFieldDefinition} objects.
 *
 * <p>Each Arrow field carries a metadata map containing the serialised
 * {@link CustomFlightAssetField} properties, matching the convention expected by
 * {@code Utils.getAssetFields(Schema)} and the CP4D discovery UI.
 * The conversion delegates to {@link ArrowConversions#toArrow(CustomFlightAssetField)}
 * which handles both the Arrow type mapping and the metadata embedding.
 */
public class ForgeSchemaBuilder
{
    private ForgeSchemaBuilder()
    {
        // utility class
    }

    /**
     * Builds an Arrow {@link Schema} from the given list of field definitions.
     *
     * @param fieldDefs
     *            the field definitions from the JSON configuration file
     * @return the Arrow schema
     */
    public static Schema buildSchema(List<RestFieldDefinition> fieldDefs)
    {
        final List<Field> fields = new ArrayList<>(fieldDefs.size());
        for (final RestFieldDefinition fieldDef : fieldDefs) {
            fields.add(toArrowField(fieldDef));
        }
        return new Schema(fields);
    }

    /**
     * Converts a single {@link RestFieldDefinition} to an Arrow {@link Field}.
     *
     * <p>The field's {@link CustomFlightAssetField} representation is first obtained via
     * {@link RestFieldTypeMapper#toAssetField(RestFieldDefinition)}, then converted to an
     * Arrow {@link Field} (including embedded metadata) via
     * {@link ArrowConversions#toArrow(CustomFlightAssetField)}.
     *
     * @param fieldDef
     *            the field definition
     * @return the Arrow field with embedded type metadata
     */
    public static Field toArrowField(RestFieldDefinition fieldDef)
    {
        final CustomFlightAssetField assetField = RestFieldTypeMapper.toAssetField(fieldDef);
        return ArrowConversions.toArrow(assetField);
    }
}

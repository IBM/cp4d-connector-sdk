/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * Maps .rest file type strings to {@link CustomFlightAssetField} instances.
 *
 * <p>Supported type strings (case-insensitive):
 * <ul>
 *   <li>{@code VarChar(N)} → varchar, length=N</li>
 *   <li>{@code LongVarChar(N)} → longvarchar, length=N</li>
 *   <li>{@code Integer} → integer</li>
 *   <li>{@code BigInt} → bigint, signed=true</li>
 *   <li>{@code Boolean} → boolean</li>
 *   <li>{@code Date} → date</li>
 *   <li>{@code Timestamp} → timestamp</li>
 *   <li>{@code Double} → double</li>
 *   <li>{@code JSON} → varchar, length=65535 (JSON serialized as string)</li>
 * </ul>
 */
public class RestFieldTypeMapper
{
    /** Pattern to match types with a length parameter, e.g. VarChar(50) or LongVarChar(2000) */
    private static final Pattern TYPE_WITH_LENGTH = Pattern.compile("^(\\w+)\\((\\d+)\\)$");

    /** Maximum length used for JSON fields serialized as strings */
    private static final int JSON_MAX_LENGTH = 65535;

    private RestFieldTypeMapper()
    {
        // prevent instantiation
    }

    /**
     * Converts a {@link RestFieldDefinition} to a {@link CustomFlightAssetField}.
     *
     * @param fieldDef
     *            the field definition from the JSON configuration file
     * @return the corresponding asset field
     */
    public static CustomFlightAssetField toAssetField(RestFieldDefinition fieldDef)
    {
        final CustomFlightAssetField field = new CustomFlightAssetField();
        field.setName(fieldDef.getName());
        // Set nullable based on the $notnull modifier
        field.setNullable(!fieldDef.isNotNull());

        final String typeString = fieldDef.getTypeString().trim();
        final String typeLower = typeString.toLowerCase(Locale.ENGLISH);

        // Check for types with length parameter: VarChar(N), LongVarChar(N), etc.
        final Matcher lengthMatcher = TYPE_WITH_LENGTH.matcher(typeString);
        if (lengthMatcher.matches()) {
            final String baseType = lengthMatcher.group(1).toLowerCase(Locale.ENGLISH);
            final int length = Integer.parseInt(lengthMatcher.group(2));
            switch (baseType) {
            case "varchar":
            case "nvarchar":
            case "char":
            case "nchar":
                field.setType("varchar");
                field.setLength(length);
                break;
            case "longvarchar":
            case "longnvarchar":
            case "clob":
            case "nclob":
                field.setType("longvarchar");
                field.setLength(length);
                break;
            case "varbinary":
            case "binary":
            case "blob":
                field.setType("varbinary");
                field.setLength(length);
                break;
            default:
                // Unknown type with length — treat as varchar
                field.setType("varchar");
                field.setLength(length);
                break;
            }
            return field;
        }

        // Simple types without length
        switch (typeLower) {
        case "integer":
        case "int":
            field.setType("integer");
            field.setSigned(true);
            break;
        case "bigint":
            field.setType("bigint");
            field.setSigned(true);
            break;
        case "smallint":
            field.setType("smallint");
            field.setSigned(true);
            break;
        case "tinyint":
            field.setType("tinyint");
            field.setSigned(true);
            break;
        case "boolean":
        case "bool":
        case "bit":
            field.setType("boolean");
            break;
        case "date":
            field.setType("date");
            field.setLength(0); // Date types don't need length but set to 0 to avoid null
            break;
        case "timestamp":
        case "datetime":
            field.setType("timestamp");
            field.setLength(0); // Timestamp types don't need length but set to 0 to avoid null
            break;
        case "time":
            field.setType("time");
            field.setLength(0); // Time types don't need length but set to 0 to avoid null
            break;
        case "double":
        case "float8":
            field.setType("double");
            field.setSigned(true);
            field.setLength(0); // Numeric types don't need length but set to 0 to avoid null
            break;
        case "float":
        case "real":
        case "float4":
            field.setType("real");
            field.setSigned(true);
            field.setLength(0); // Numeric types don't need length but set to 0 to avoid null
            break;
        case "decimal":
        case "numeric":
            field.setType("decimal");
            field.setSigned(true);
            field.setLength(38); // Default precision for decimal (length represents precision)
            field.setScale(10); // Default scale for decimal
            break;
        case "json":
        case "jsonb":
        case "array":
        case "object":
            // JSON and complex types are serialized as strings
            field.setType("varchar");
            field.setLength(JSON_MAX_LENGTH);
            break;
        case "varchar":
        case "nvarchar":
        case "char":
        case "nchar":
            field.setType("varchar");
            field.setLength(255); // Default length for varchar without explicit length
            break;
        case "longvarchar":
        case "longnvarchar":
        case "clob":
        case "text":
            field.setType("longvarchar");
            field.setLength(65535); // Default length for long varchar
            break;
        default:
            // Unknown type — default to varchar
            field.setType("varchar");
            field.setLength(1024);
            break;
        }

        return field;
    }

    /**
     * Converts a list of {@link RestFieldDefinition} objects to a list of {@link CustomFlightAssetField} objects.
     *
     * @param fieldDefs
     *            the list of field definitions
     * @return the list of asset fields
     */
    public static List<CustomFlightAssetField> toAssetFields(List<RestFieldDefinition> fieldDefs)
    {
        final List<CustomFlightAssetField> fields = new ArrayList<>(fieldDefs.size());
        for (final RestFieldDefinition fieldDef : fieldDefs) {
            fields.add(toAssetField(fieldDef));
        }
        return fields;
    }
}

// Made with Bob

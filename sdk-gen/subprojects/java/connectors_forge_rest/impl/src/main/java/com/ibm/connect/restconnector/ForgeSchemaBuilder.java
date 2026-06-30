/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Builds an Arrow {@link Schema} from a list of {@link RestFieldDefinition} objects.
 *
 * <p>Replaces the old {@code RestFieldTypeMapper} which produced
 * {@code CustomFlightAssetField} instances (from the SDK model framework). This class produces
 * Arrow types directly, with no dependency on the SDK model framework.
 *
 * <p>Supported type strings (case-insensitive):
 * <ul>
 *   <li>{@code VarChar(N)} → {@code Utf8} (Arrow), nullable based on $notnull modifier</li>
 *   <li>{@code LongVarChar(N)} → {@code Utf8}</li>
 *   <li>{@code Integer} / {@code Int} → {@code Int(32, signed)}</li>
 *   <li>{@code BigInt} → {@code Int(64, signed)}</li>
 *   <li>{@code SmallInt} → {@code Int(16, signed)}</li>
 *   <li>{@code TinyInt} → {@code Int(8, signed)}</li>
 *   <li>{@code Boolean} → {@code Bool}</li>
 *   <li>{@code Date} → {@code Date(DAY)}</li>
 *   <li>{@code Timestamp} / {@code DateTime} → {@code Timestamp(MICROSECOND, UTC)}</li>
 *   <li>{@code Time} → {@code Time(MILLISECOND, 32)}</li>
 *   <li>{@code Double} / {@code Float8} → {@code FloatingPoint(DOUBLE)}</li>
 *   <li>{@code Float} / {@code Real} / {@code Float4} → {@code FloatingPoint(SINGLE)}</li>
 *   <li>{@code Decimal} / {@code Numeric} → {@code Decimal(38, 10)}</li>
 *   <li>{@code JSON} / {@code JSONB} / {@code Array} / {@code Object} → {@code Utf8}</li>
 *   <li>All other / unknown → {@code Utf8}</li>
 * </ul>
 */
public class ForgeSchemaBuilder
{
    /** Matches types with a length parameter, e.g. VarChar(50) or LongVarChar(2000). */
    private static final Pattern TYPE_WITH_LENGTH = Pattern.compile("^(\\w+)\\((\\d+)\\)$");

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
     * @param fieldDef
     *            the field definition
     * @return the Arrow field
     */
    public static Field toArrowField(RestFieldDefinition fieldDef)
    {
        final boolean nullable = !fieldDef.isNotNull();
        final ArrowType arrowType = toArrowType(fieldDef.getTypeString());
        return new Field(fieldDef.getName(), new FieldType(nullable, arrowType, null), null);
    }

    // ---- private helpers ----

    private static ArrowType toArrowType(String typeString)
    {
        final String trimmed = typeString.trim();

        // Check for types with length parameter: VarChar(N), LongVarChar(N), etc.
        final Matcher lengthMatcher = TYPE_WITH_LENGTH.matcher(trimmed);
        if (lengthMatcher.matches()) {
            final String baseType = lengthMatcher.group(1).toLowerCase(Locale.ENGLISH);
            switch (baseType) {
            case "varchar":
            case "nvarchar":
            case "char":
            case "nchar":
            case "longvarchar":
            case "longnvarchar":
            case "clob":
            case "nclob":
                return ArrowType.Utf8.INSTANCE;
            case "varbinary":
            case "binary":
            case "blob":
                return ArrowType.Binary.INSTANCE;
            default:
                return ArrowType.Utf8.INSTANCE;
            }
        }

        // Simple types without length
        final String typeLower = trimmed.toLowerCase(Locale.ENGLISH);
        switch (typeLower) {
        case "integer":
        case "int":
            return new ArrowType.Int(32, true);
        case "bigint":
            return new ArrowType.Int(64, true);
        case "smallint":
            return new ArrowType.Int(16, true);
        case "tinyint":
            return new ArrowType.Int(8, true);
        case "boolean":
        case "bool":
        case "bit":
            return ArrowType.Bool.INSTANCE;
        case "date":
            return new ArrowType.Date(DateUnit.DAY);
        case "timestamp":
        case "datetime":
            return new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC");
        case "time":
            return new ArrowType.Time(TimeUnit.MILLISECOND, 32);
        case "double":
        case "float8":
            return new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
        case "float":
        case "real":
        case "float4":
            return new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
        case "decimal":
        case "numeric":
            return new ArrowType.Decimal(38, 10, 128);
        case "json":
        case "jsonb":
        case "array":
        case "object":
        case "varchar":
        case "nvarchar":
        case "char":
        case "nchar":
        case "longvarchar":
        case "longnvarchar":
        case "clob":
        case "text":
            return ArrowType.Utf8.INSTANCE;
        default:
            // Unknown type — default to Utf8
            return ArrowType.Utf8.INSTANCE;
        }
    }
}

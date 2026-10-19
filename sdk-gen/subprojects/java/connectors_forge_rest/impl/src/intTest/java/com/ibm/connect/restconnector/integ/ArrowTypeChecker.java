/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;

import com.ibm.connect.restconnector.RestFieldDefinition;
import com.ibm.connect.restconnector.RestFieldTypeMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * Checks whether an Arrow {@link Field}'s type is acceptable for a given
 * {@link RestFieldDefinition}.
 *
 * <p>Delegates the DSL-type → SDK-type mapping entirely to
 * {@link RestFieldTypeMapper#toAssetField(RestFieldDefinition)} to avoid
 * duplicating that logic.
 */
public final class ArrowTypeChecker {

    private ArrowTypeChecker() {
    }

    /**
     * Returns {@code true} if the Arrow field's type is an acceptable encoding
     * for the SDK type derived from {@code fieldDef}.
     *
     * @param arrowField the Arrow {@link Field} from the Flight schema
     * @param fieldDef   the REST field definition from the connector JSON
     * @return {@code true} if the type is acceptable
     */
    public static boolean isAcceptable(Field arrowField, RestFieldDefinition fieldDef) {
        final CustomFlightAssetField expected = RestFieldTypeMapper.toAssetField(fieldDef);
        final ArrowType actual = arrowField.getFieldType().getType();
        return matches(actual, expected.getType());
    }

    /**
     * Returns a human-readable expected Arrow type name for a given {@link RestFieldDefinition},
     * used in failure messages.
     *
     * @param fieldDef the REST field definition
     * @return a string like {@code "ArrowType.Int(32)"} for use in assertion messages
     */
    public static String expectedArrowTypeName(RestFieldDefinition fieldDef) {
        final CustomFlightAssetField expected = RestFieldTypeMapper.toAssetField(fieldDef);
        final String sdkType = expected.getType() == null ? "varchar" : expected.getType().toLowerCase(java.util.Locale.ENGLISH);
        switch (sdkType) {
            case "integer":    return "ArrowType.Int(32)";
            case "bigint":     return "ArrowType.Int(64)";
            case "smallint":   return "ArrowType.Int(16)";
            case "tinyint":    return "ArrowType.Int(8)";
            case "boolean":    return "ArrowType.Bool";
            case "double":     return "ArrowType.FloatingPoint(DOUBLE)";
            case "real":       return "ArrowType.FloatingPoint(SINGLE)";
            case "decimal":    return "ArrowType.Decimal or ArrowType.FloatingPoint";
            case "date":       return "ArrowType.Date";
            case "timestamp":  return "ArrowType.Timestamp";
            case "time":       return "ArrowType.Time";
            case "varchar":
            case "longvarchar": return "ArrowType.Utf8";
            case "varbinary":  return "ArrowType.Binary";
            default:           return "ArrowType.Utf8";
        }
    }

    private static boolean matches(ArrowType actual, String sdkType) {
        if (sdkType == null) {
            return actual instanceof ArrowType.Utf8 || actual instanceof ArrowType.LargeUtf8;
        }
        switch (sdkType.toLowerCase(java.util.Locale.ENGLISH)) {
            case "integer":
                return actual instanceof ArrowType.Int
                        && ((ArrowType.Int) actual).getBitWidth() == 32;
            case "bigint":
                return actual instanceof ArrowType.Int
                        && ((ArrowType.Int) actual).getBitWidth() == 64;
            case "smallint":
                return actual instanceof ArrowType.Int
                        && ((ArrowType.Int) actual).getBitWidth() == 16;
            case "tinyint":
                return actual instanceof ArrowType.Int
                        && ((ArrowType.Int) actual).getBitWidth() == 8;
            case "boolean":
                return actual instanceof ArrowType.Bool;
            case "double":
                return actual instanceof ArrowType.FloatingPoint
                        && ((ArrowType.FloatingPoint) actual).getPrecision() == FloatingPointPrecision.DOUBLE;
            case "real":
                return actual instanceof ArrowType.FloatingPoint
                        && ((ArrowType.FloatingPoint) actual).getPrecision() == FloatingPointPrecision.SINGLE;
            case "decimal":
                return actual instanceof ArrowType.Decimal
                        || actual instanceof ArrowType.FloatingPoint;
            case "date":
                return actual instanceof ArrowType.Date;
            case "timestamp":
                return actual instanceof ArrowType.Timestamp;
            case "time":
                return actual instanceof ArrowType.Time;
            case "varchar":
            case "longvarchar":
                // varchar ↔ longvarchar leniency: both string Arrow types accepted for either
                return actual instanceof ArrowType.Utf8 || actual instanceof ArrowType.LargeUtf8;
            case "varbinary":
                return actual instanceof ArrowType.Binary || actual instanceof ArrowType.LargeBinary;
            default:
                // fallback — treat as varchar
                return actual instanceof ArrowType.Utf8 || actual instanceof ArrowType.LargeUtf8;
        }
    }
}

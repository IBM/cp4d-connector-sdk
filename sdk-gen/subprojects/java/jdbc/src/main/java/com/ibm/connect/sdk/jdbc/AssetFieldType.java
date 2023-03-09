/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashBasedTable;

/**
 * Asset field type information.
 */
public class AssetFieldType
{
    // Column indexes
    private static final int TYPE_NAME = 0;
    private static final int IS_NUMERIC = 1;

    private static final HashBasedTable<Integer, Integer, Object> FIELD_TYPE_TABLE = HashBasedTable.create();

    static {
        put(Types.ARRAY, "array", false);
        put(Types.BIGINT, "bigint", true);
        put(Types.BINARY, "binary", false);
        put(Types.BIT, "bit", false);
        put(Types.BLOB, "blob", false);
        put(Types.BOOLEAN, "boolean", false);
        put(Types.CHAR, "char", false);
        put(Types.CLOB, "clob", false);
        put(Types.DATALINK, "datalink", false);
        put(Types.DATE, "date", false);
        put(Types.DECIMAL, "decimal", true);
        put(Types.DISTINCT, "distinct", false);
        put(Types.DOUBLE, "double", true);
        put(Types.FLOAT, "float", true);
        put(Types.INTEGER, "integer", true);
        put(Types.JAVA_OBJECT, "java_object", false);
        put(Types.LONGNVARCHAR, "longnvarchar", false);
        put(Types.LONGVARBINARY, "longvarbinary", false);
        put(Types.LONGVARCHAR, "longvarchar", false);
        put(Types.NCHAR, "nchar", false);
        put(Types.NCLOB, "nclob", false);
        put(Types.NULL, "null", false);
        put(Types.NUMERIC, "numeric", true);
        put(Types.NVARCHAR, "nvarchar", false);
        put(Types.OTHER, "other", false);
        put(Types.REAL, "real", true);
        put(Types.REF, "ref", false);
        put(Types.REF_CURSOR, "ref_cursor", false);
        put(Types.ROWID, "rowid", false);
        put(Types.SMALLINT, "smallint", true);
        put(Types.SQLXML, "sqlxml", false);
        put(Types.STRUCT, "struct", false);
        put(Types.TIME, "time", false);
        put(Types.TIME_WITH_TIMEZONE, "time_with_timezone", false);
        put(Types.TIMESTAMP, "timestamp", false);
        put(Types.TIMESTAMP_WITH_TIMEZONE, "timestamp_with_timezone", false);
        put(Types.TINYINT, "tinyint", true);
        put(Types.VARBINARY, "varbinary", false);
        put(Types.VARCHAR, "varchar", false);
    }

    private static final Map<String, Integer> FIELD_TYPE_MAP = new HashMap<>();

    static {
        FIELD_TYPE_MAP.put("array", Types.ARRAY);
        FIELD_TYPE_MAP.put("bigint", Types.BIGINT);
        FIELD_TYPE_MAP.put("binary", Types.BINARY);
        FIELD_TYPE_MAP.put("bit", Types.BIT);
        FIELD_TYPE_MAP.put("blob", Types.BLOB);
        FIELD_TYPE_MAP.put("boolean", Types.BOOLEAN);
        FIELD_TYPE_MAP.put("char", Types.CHAR);
        FIELD_TYPE_MAP.put("clob", Types.CLOB);
        FIELD_TYPE_MAP.put("datalink", Types.DATALINK);
        FIELD_TYPE_MAP.put("date", Types.DATE);
        FIELD_TYPE_MAP.put("decimal", Types.DECIMAL);
        FIELD_TYPE_MAP.put("distinct", Types.DISTINCT);
        FIELD_TYPE_MAP.put("double", Types.DOUBLE);
        FIELD_TYPE_MAP.put("float", Types.FLOAT);
        FIELD_TYPE_MAP.put("integer", Types.INTEGER);
        FIELD_TYPE_MAP.put("java_object", Types.JAVA_OBJECT);
        FIELD_TYPE_MAP.put("longnvarchar", Types.LONGNVARCHAR);
        FIELD_TYPE_MAP.put("longvarbinary", Types.LONGVARBINARY);
        FIELD_TYPE_MAP.put("longvarchar", Types.LONGVARCHAR);
        FIELD_TYPE_MAP.put("nchar", Types.NCHAR);
        FIELD_TYPE_MAP.put("nclob", Types.NCLOB);
        FIELD_TYPE_MAP.put("null", Types.NULL);
        FIELD_TYPE_MAP.put("numeric", Types.NUMERIC);
        FIELD_TYPE_MAP.put("nvarchar", Types.NVARCHAR);
        FIELD_TYPE_MAP.put("other", Types.OTHER);
        FIELD_TYPE_MAP.put("real", Types.REAL);
        FIELD_TYPE_MAP.put("ref", Types.REF);
        FIELD_TYPE_MAP.put("ref_cursor", Types.REF_CURSOR);
        FIELD_TYPE_MAP.put("rowid", Types.ROWID);
        FIELD_TYPE_MAP.put("smallint", Types.SMALLINT);
        FIELD_TYPE_MAP.put("sqlxml", Types.SQLXML);
        FIELD_TYPE_MAP.put("struct", Types.STRUCT);
        FIELD_TYPE_MAP.put("time", Types.TIME);
        FIELD_TYPE_MAP.put("time_with_timezone", Types.TIME_WITH_TIMEZONE);
        FIELD_TYPE_MAP.put("timestamp", Types.TIMESTAMP);
        FIELD_TYPE_MAP.put("timestamp_with_timezone", Types.TIMESTAMP_WITH_TIMEZONE);
        FIELD_TYPE_MAP.put("tinyint", Types.TINYINT);
        FIELD_TYPE_MAP.put("varbinary", Types.VARBINARY);
        FIELD_TYPE_MAP.put("varchar", Types.VARCHAR);
    }

    private static void put(int fieldType, String typeName, boolean isNumeric)
    {
        FIELD_TYPE_TABLE.put(fieldType, TYPE_NAME, typeName);
        FIELD_TYPE_TABLE.put(fieldType, IS_NUMERIC, isNumeric);
    }

    /**
     * Returns the field type for the given type name.
     *
     * @param typeName
     *            type name
     * @return the field type for the given type name
     */
    public static int getFieldType(String typeName)
    {
        final Integer fieldType = FIELD_TYPE_MAP.get(typeName);
        if (fieldType == null) {
            throw new IllegalArgumentException("Unknown type " + typeName);
        }
        return fieldType;
    }

    /**
     * Returns the type name for a given field type.
     *
     * @param fieldType
     *            asset field type
     * @return the type name for a given field type
     */
    public static String getTypeName(int fieldType)
    {
        return FIELD_TYPE_TABLE.get(fieldType, TYPE_NAME).toString();
    }

    /**
     * Returns whether the given field type is numeric.
     *
     * @param fieldType
     *            asset field type
     * @return true if the given field type is numeric
     */
    public static boolean isNumeric(int fieldType)
    {
        return (Boolean) FIELD_TYPE_TABLE.get(fieldType, IS_NUMERIC);
    }
}

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.Header;

import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;

public interface EntityTypeResponseHandler {

    List<FieldDefinition> getFieldDefinitions();

    List<Map<String, Object>> getFieldValueMap() throws IOException;

    List<Header> getHeaders();

    enum RestFieldType {
        STRING,
        INTEGER,
        LONG,
        DOUBLE,
        BOOLEAN,
        UNKNOWN,
        OBJECT,
        ARRAY
    }

}

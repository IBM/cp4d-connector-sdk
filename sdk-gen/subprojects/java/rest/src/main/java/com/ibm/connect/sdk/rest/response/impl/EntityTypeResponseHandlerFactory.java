/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response.impl;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.connect.sdk.rest.response.EntityTypeResponseHandler;
import com.ibm.connect.sdk.rest.response.ResponseType;
import com.ibm.connect.sdk.rest.utils.models.EntityType;

public class EntityTypeResponseHandlerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityTypeResponseHandlerFactory.class);

    public static EntityTypeResponseHandler getHandler(ClassicHttpResponse response, EntityType entityType) throws IOException {
        // Auto-detect response type from headers
        final ResponseTypeDetector.ResponseTypeDetail detectedTypeDtl = ResponseTypeDetector.detect(response);
        LOGGER.debug("Detected Response Type: {}", detectedTypeDtl.getContentTypeValue());

        if(ResponseType.JSON.equals(detectedTypeDtl.getResponseType())) {
            return new JsonResponseHandler(response, entityType);
        } else {
            throw new IllegalArgumentException("Unsupported ResponseType: " + detectedTypeDtl.getResponseType());
        }
    }
}


/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response.impl;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.connect.sdk.rest.response.ResponseType;
import com.ibm.connect.sdk.rest.utils.RestUtils;

public class ResponseTypeDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseTypeDetector.class);

    public static ResponseTypeDetail detect(ClassicHttpResponse response) {
        if (response == null) {
            return new ResponseTypeDetail(ResponseType.UNKNOWN, "");
        }

        String contentTypeValue = null;
        try {
            // Try from entity first (most accurate for body processing)
            if (response.getEntity() != null && response.getEntity().getContentType() != null) {
                contentTypeValue = response.getEntity().getContentType();
            }

            // Read from raw response header
            if(RestUtils.isNullOrEmpty(contentTypeValue)) {
                final Header contentTypeHeader = response.getFirstHeader("Content-Type");
                contentTypeValue = contentTypeHeader != null ? contentTypeHeader.getValue() : null;
            }
        } catch (Exception ex) {
            LOGGER.error("Error detecting response type from response body.", ex);
            contentTypeValue = null;
        }

        LOGGER.debug("Detecting ResponseType from HTTP Response: {}", contentTypeValue);
        return new ResponseTypeDetail(ResponseType.fromContentType(contentTypeValue), contentTypeValue);
    }

    public static class ResponseTypeDetail {
        private final ResponseType responseType;
        private final String contentTypeValue;
        public ResponseTypeDetail(ResponseType responseType, String contentTypeValue) {
            this.responseType = responseType;
            this.contentTypeValue = contentTypeValue;
        }
        public ResponseType getResponseType() {
            return responseType;
        }
        public String getContentTypeValue() {
            return contentTypeValue;
        }
    }
}

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response;

import java.util.Locale;

public enum ResponseType {
    JSON("application/json"),
    UNKNOWN("*/*");

    private final String mimeType;

    ResponseType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static ResponseType fromContentType(String contentType) {
        if (contentType == null) {
            return UNKNOWN;
        }
        contentType = contentType.toLowerCase(Locale.ENGLISH);

        if (contentType.contains(JSON.name().toLowerCase(Locale.ENGLISH))) {
            return JSON;
        }

        return UNKNOWN;
    }
}

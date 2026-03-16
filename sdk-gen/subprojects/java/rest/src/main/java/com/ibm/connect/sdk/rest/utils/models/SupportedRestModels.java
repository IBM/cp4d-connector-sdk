/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.ibm.connect.sdk.rest.RestMsgs;
import com.ibm.connect.sdk.rest.utils.RestUtils;

public enum SupportedRestModels {
    CUSTOM_MODEL,
    GITHUB_BRANCH_MODEL,
    GITHUB_REPO_MODEL;

    /**
     * Comma seperated supporting rest models
     */
    public static String getSupportedRestModels() {
        return Arrays.stream(SupportedRestModels.values()).map(Enum::name).collect(Collectors.joining(","));
    }

    public static SupportedRestModels fromString(String value) {
        if (RestUtils.isNullOrEmpty(value)) {
            return CUSTOM_MODEL;
        }
        try {
            return SupportedRestModels.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(RestMsgs.INVALID_CONNECTION_PROPERTIES_AUTH_TYPE.format(value, getSupportedRestModels()), e);
        }
    }

    public String getModelResourceFilePath() {
        return String.format("com/ibm/connect/sdk/rest/models/%s.yml", this.name().toLowerCase(Locale.ENGLISH));
    }

    public String getModelResourceFileAsString() {
        try {
            return RestUtils.readResourceFile(getModelResourceFilePath());
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to locate the predefined model file `%s` Contact administrator. ", this.name()), ex);
        }
    }
}

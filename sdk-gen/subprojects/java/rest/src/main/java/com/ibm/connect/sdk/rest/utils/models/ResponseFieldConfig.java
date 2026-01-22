/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResponseFieldConfig {
    @JsonProperty("name")
    private String name;
    @JsonProperty("jsonPath")
    private String jsonPath;

    public ResponseFieldConfig() {
        // no-arg constructor
    }

    public ResponseFieldConfig(String name, String jsonPath) {
        this.name = name;
        this.jsonPath = jsonPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResponseFieldConfig)) {
            return false;
        }
        final ResponseFieldConfig that = (ResponseFieldConfig) o;
        return Objects.equals(name, that.name) && Objects.equals(jsonPath, that.jsonPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, jsonPath);
    }
}

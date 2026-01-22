/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibm.connect.sdk.rest.utils.RestUtils;

public class EntityType {
    @JsonProperty("name")
    private String name;

    @JsonProperty("parentEntity")
    private String parentEntity;

    @JsonProperty("endpoint")
    private String endpointPath;

    @JsonProperty("method")
    private String method;

    @JsonProperty("requestQueryParams")
    private RequestQueryParams requestQueryParams;

    @JsonProperty("headers")
    private RequestHeaders header;

    @JsonProperty("uniqueIdField")
    private String uniqueIdField;

    @JsonProperty("labelField")
    private String labelField;

    @JsonProperty("fields")
    private List<ResponseFieldConfig> responseFieldConfigs = new ArrayList<>();

    @JsonProperty("pagination")
    private Pagination pagination;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public EntityType() {
        // no-arg constructor
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentEntity() {
        return parentEntity;
    }

    public void setParentEntity(String parentEntity) {
        this.parentEntity = parentEntity;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public String getMethod() {
        return RestUtils.isNullOrEmpty(method) ? "GET" : method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public RequestQueryParams getRequestQueryParams() {
        return requestQueryParams;
    }

    public void setRequestQueryParams(RequestQueryParams requestQueryParams) {
        this.requestQueryParams = requestQueryParams;
    }

    public RequestHeaders getHeader() {
        return header;
    }

    public void setHeader(RequestHeaders header) {
        this.header = header;
    }

    public String getUniqueIdField() {
        return uniqueIdField;
    }

    public void setUniqueIdField(String uniqueIdField) {
        this.uniqueIdField = uniqueIdField;
    }

    public String getLabelField() {
        return labelField;
    }

    public void setLabelField(String labelField) {
        this.labelField = labelField;
    }

    public List<ResponseFieldConfig> getResponseFieldConfigs() {
        return responseFieldConfigs;
    }

    public void setResponseFieldConfigs(List<ResponseFieldConfig> responseFieldConfigs) {
        this.responseFieldConfigs = responseFieldConfigs;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }
}

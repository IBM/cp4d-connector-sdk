/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Pagination {

    @JsonProperty("location")
    private String location;

    @JsonProperty("type")
    private String type;

    @JsonProperty("limitParam")
    private String limitParam;

    @JsonProperty("offsetParam")
    private String offsetParam;

    @JsonProperty("pageParam")
    private String pageParam;

    @JsonProperty("sizeParam")
    private String sizeParam;

    @JsonProperty("linkHeader")
    private String linkHeader;

    @JsonProperty("supportedMaxLimit")
    private Integer supportedMaxLimit;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location != null ? location : "query";
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLimitParam() {
        return limitParam;
    }

    public void setLimitParam(String limitParam) {
        this.limitParam = limitParam;
    }

    public String getOffsetParam() {
        return offsetParam;
    }

    public void setOffsetParam(String offsetParam) {
        this.offsetParam = offsetParam;
    }

    public String getPageParam() {
        return pageParam;
    }

    public void setPageParam(String pageParam) {
        this.pageParam = pageParam;
    }

    public String getSizeParam() {
        return sizeParam;
    }

    public void setSizeParam(String sizeParam) {
        this.sizeParam = sizeParam;
    }

    public String getLinkHeader() {
        return linkHeader;
    }

    public void setLinkHeader(String linkHeader) {
        this.linkHeader = linkHeader;
    }

    public Integer getSupportedMaxLimit() {
        return supportedMaxLimit;
    }

    public void setSupportedMaxLimit(Integer supportedMaxLimit) {
        this.supportedMaxLimit = supportedMaxLimit;
    }
}

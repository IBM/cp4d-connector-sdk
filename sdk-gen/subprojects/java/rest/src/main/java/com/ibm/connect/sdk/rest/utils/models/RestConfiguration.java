/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RestConfiguration {
    @JsonProperty("entityTypes")
    private List<EntityType> entityTypes = new ArrayList<>();

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public RestConfiguration() {
        // no-arg constructor
    }

    public List<EntityType> getEntityTypes() {
        return entityTypes;
    }

    public void setEntityTypes(List<EntityType> entityTypes) {
        this.entityTypes = entityTypes;
    }
}

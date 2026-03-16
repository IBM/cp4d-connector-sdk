/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils.models;

public class EntityTypeNode extends EntityType {
    private final String name;
    private final String parentEntity;
    private EntityTypeNode child;

    public EntityTypeNode(String name, String parentEntity) {
        super();
        this.name = name;
        this.parentEntity = parentEntity;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getParentEntity() {
        return parentEntity;
    }

    public EntityTypeNode getChild() {
        return child;
    }

    public void setChild(EntityTypeNode child) {
        this.child = child;
    }

    @Override
    public String toString() {
        return name;
    }
}

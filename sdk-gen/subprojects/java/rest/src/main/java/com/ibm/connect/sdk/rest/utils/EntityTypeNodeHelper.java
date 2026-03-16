/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.EntityTypeNode;

public class EntityTypeNodeHelper {

    public static EntityTypeNode buildEntityChain(List<EntityType> entities) {
        final Map<String, EntityTypeNode> nodeMap = new HashMap<>();
        for (final EntityType entity : entities) {
            nodeMap.put(entity.getName(), new EntityTypeNode(entity.getName(), entity.getParentEntity()));
        }

        EntityTypeNode root = null;
        for (final EntityTypeNode node : nodeMap.values()) {
            final String parentName = node.getParentEntity();
            if (parentName == null) {
                root = node;
            } else {
                final EntityTypeNode parent = nodeMap.get(parentName);
                if (parent.getChild() != null) {
                    throw new IllegalStateException(
                            "Parent '" + parentName + "' already has a child '" + parent.getChild().getName() + "'");
                }
                parent.setChild(node);
            }
        }

        if (root == null) {
            throw new IllegalStateException("Root entity (with null parentEntity) not found.");
        }

        return root;
    }

    public static EntityTypeNode getNthLevel(EntityTypeNode root, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Level must start from 1 (root = level 1)");
        }

        EntityTypeNode current = root;
        int currentLevel = 1;

        while (current != null && currentLevel < level) {
            current = current.getChild();
            currentLevel++;
        }

        return current; // null if level exceeds depth
    }


}

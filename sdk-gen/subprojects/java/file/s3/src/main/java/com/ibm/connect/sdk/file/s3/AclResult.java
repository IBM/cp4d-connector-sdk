/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ACL result: user and group principal sets for allow and deny.
 * The wildcard {@code "*"} is preserved as-is in {@code allowUsers} —
 * it is never expanded to a list of all users.
 */
final class AclResult
{
    final Set<String> allowUsers = new LinkedHashSet<>();
    final Set<String> allowGroups = new LinkedHashSet<>();
    final Set<String> denyUsers = new LinkedHashSet<>();
    final Set<String> denyGroups = new LinkedHashSet<>();
}
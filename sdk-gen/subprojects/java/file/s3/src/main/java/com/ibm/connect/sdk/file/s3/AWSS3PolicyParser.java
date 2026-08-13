/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses S3 bucket policies to determine which principals have read access
 * to a specific S3 object.
 *
 * <p>This is a port of the enterprise {@code AWSS3PolicyParser} adapted to use
 * {@code com.fasterxml.jackson} (already on the classpath via Spark) instead of
 * the internal shaded copy used by the enterprise connector.
 */
final class AWSS3PolicyParser
{
    static final String ALL_ACCOUNTS_TOKEN = "*";
    private static final String AWS_RESERVED_SSO_ROLE_NAME_PREFIX = "AWSReservedSSO_";

    // Actions that grant read (GetObject) access.
    private static final Set<String> ALLOWED_ACTIONS = Set.of("s3:GetObject", "s3:Get*", "s3:*", "*");

    private static final String ACTION_PROP = "Action";
    private static final String ALLOW_EFFECT = "Allow";
    private static final String ARN_PREFIX = "arn:aws:s3:::";
    private static final String CONDITION_PROP = "Condition";
    private static final String DENY_EFFECT = "Deny";
    private static final String EFFECT_PROP = "Effect";
    private static final String FN_JOIN = "Fn::Join";
    private static final String NOT_ACTION_PROP = "NotAction";
    private static final String NOT_RESOURCE_PROP = "NotResource";
    private static final String PRINCIPAL_PROP = "Principal";
    private static final String REF_ATTR = "Ref";
    private static final String RESOURCE_PROP = "Resource";
    private static final String STATEMENT_PROP = "Statement";

    private AWSS3PolicyParser()
    {
        // utility class
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses a bucket policy JSON document and populates the allow/deny user and
     * group sets in {@code result}.
     *
     * @param bucketPolicyJson  JSON string of the bucket policy
     * @param bucketName        S3 bucket name
     * @param objectKey         S3 object key (no leading slash)
     * @param result            mutable {@link AclResult} to populate
     * @throws JsonProcessingException if the policy JSON is malformed
     */
    static void extractBucketPolicyAccess(String bucketPolicyJson, String bucketName, String objectKey,
            AclResult result) throws JsonProcessingException
    {
        final JsonNode policy = new ObjectMapper().readTree(bucketPolicyJson);
        if (!policy.has(STATEMENT_PROP)) {
            throw new JsonParseException(null, AWSS3Msgs.BUCKET_POLICY_STATEMENT_MISSING.format());
        }
        for (final JsonNode stmt : policy.get(STATEMENT_PROP)) {
            if (actionIncludesGetObject(stmt) && stmt.has(PRINCIPAL_PROP)
                    && isFileCovered(bucketName, objectKey, stmt)) {
                if (applyAllow(stmt)) {
                    extractPrincipals(stmt.get(PRINCIPAL_PROP), result.allowUsers, result.allowGroups);
                } else if (applyDeny(stmt)) {
                    extractPrincipals(stmt.get(PRINCIPAL_PROP), result.denyUsers, result.denyGroups);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static boolean actionIncludesGetObject(JsonNode stmt) throws JsonParseException
    {
        if (stmt.has(ACTION_PROP)) {
            return parseActionNode(stmt.get(ACTION_PROP));
        }
        if (stmt.has(NOT_ACTION_PROP)) {
            return !parseActionNode(stmt.get(NOT_ACTION_PROP));
        }
        throw new JsonParseException(null,
                AWSS3Msgs.BUCKET_POLICY_ATTR_MISSING.format(ACTION_PROP, NOT_ACTION_PROP));
    }

    private static boolean parseActionNode(JsonNode actionNode)
    {
        if (actionNode.isTextual()) {
            return ALLOWED_ACTIONS.contains(actionNode.asText());
        }
        if (actionNode.isArray()) {
            return StreamSupport.stream(actionNode.spliterator(), false)
                    .anyMatch(n -> n.isTextual() && ALLOWED_ACTIONS.contains(n.asText()));
        }
        return false;
    }

    private static boolean isFileCovered(String bucket, String key, JsonNode stmt) throws JsonParseException
    {
        if (stmt.has(RESOURCE_PROP)) {
            return parseResourceNode(bucket, key, stmt.get(RESOURCE_PROP));
        }
        if (stmt.has(NOT_RESOURCE_PROP)) {
            return !parseResourceNode(bucket, key, stmt.get(NOT_RESOURCE_PROP));
        }
        throw new JsonParseException(null,
                AWSS3Msgs.BUCKET_POLICY_ATTR_MISSING.format(RESOURCE_PROP, NOT_RESOURCE_PROP));
    }

    private static boolean parseResourceNode(String bucket, String key, JsonNode resourceNode)
    {
        final String fileArn = ARN_PREFIX + bucket + "/" + key;
        if (resourceNode.isTextual()) {
            return matchResourcePattern(fileArn, resourceNode.asText());
        }
        if (resourceNode.isArray()) {
            return StreamSupport.stream(resourceNode.spliterator(), false)
                    .anyMatch(n -> n.isTextual() && matchResourcePattern(fileArn, n.asText()));
        }
        // Fn::Join CloudFormation syntax
        if (!resourceNode.has(FN_JOIN)) {
            return isBucketReferred(resourceNode, bucket);
        }
        for (final JsonNode part : resourceNode.get(FN_JOIN)) {
            if (part.isTextual()) {
                if (matchResourcePattern(fileArn, part.asText())) {
                    return true;
                }
            } else if (part.isArray()) {
                if (StreamSupport.stream(part.spliterator(), false).anyMatch(sub -> isBucketReferred(sub, bucket))) {
                    return true;
                }
            } else if (isBucketReferred(part, bucket)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBucketReferred(JsonNode node, String bucket)
    {
        return node.has(REF_ATTR) && bucket.equals(node.get(REF_ATTR).asText());
    }

    private static boolean matchResourcePattern(String fileArn, String pattern)
    {
        if (fileArn.equals(pattern) || "*".equals(pattern)
                || "arn:aws:s3:::*".equals(pattern) || "arn:aws:s3:::*/*".equals(pattern)) {
            return true;
        }
        final String regex = pattern.replace(ARN_PREFIX, "").replace("*", ".*").replace("?", ".");
        return Pattern.matches(regex, fileArn.replace(ARN_PREFIX, ""));
    }

    /**
     * Distributes the principals in a policy statement's {@code Principal} node
     * into the appropriate {@code users} or {@code groups} set.
     *
     * <ul>
     *   <li>Wildcard {@code "*"} — added to {@code users} as-is (caller decides
     *       not to expand it).</li>
     *   <li>{@code AWS} array/string — individual STS assumed-role session names
     *       (SSO usernames) go to {@code users}; anything else (account ARNs,
     *       account IDs) goes to {@code groups}.</li>
     *   <li>{@code Federated} array/string — identity-provider URIs / ARNs go to
     *       {@code groups}.</li>
     *   <li>{@code Service} — ignored (service principals are not user/group
     *       identities).</li>
     * </ul>
     */
    static void extractPrincipals(JsonNode principal, Set<String> users, Set<String> groups)
    {
        // Bare "*" wildcard.
        if (principal.isTextual() && ALL_ACCOUNTS_TOKEN.equals(principal.asText())) {
            users.add(ALL_ACCOUNTS_TOKEN);
            return;
        }

        // AWS principal — may be a specific role session (user) or an account/org (group).
        if (principal.has("AWS")) {
            final JsonNode aws = principal.get("AWS");
            if (aws.isTextual()) {
                classifyAwsPrincipal(aws.asText(), users, groups);
            } else if (aws.isArray()) {
                for (final JsonNode node : aws) {
                    classifyAwsPrincipal(node.asText(), users, groups);
                }
            }
        }

        // Federated principal — identity-provider URI or ARN; always a group.
        if (principal.has("Federated")) {
            final JsonNode fed = principal.get("Federated");
            if (fed.isTextual()) {
                groups.add(fed.asText());
            } else if (fed.isArray()) {
                for (final JsonNode node : fed) {
                    groups.add(node.asText());
                }
            }
        }
    }

    /**
     * Routes a single AWS principal string to either {@code users} or
     * {@code groups}.
     *
     * <p>STS assumed-role session ARNs for SSO roles resolve to a specific
     * username and go to {@code users}. Everything else — account IDs, account
     * root ARNs, IAM role ARNs, org ARNs — represents a group identity.
     */
    private static void classifyAwsPrincipal(String principal, Set<String> users, Set<String> groups)
    {
        if (ALL_ACCOUNTS_TOKEN.equals(principal)) {
            users.add(ALL_ACCOUNTS_TOKEN);
            return;
        }
        // STS assumed-role for an SSO-reserved role → extract session name (username).
        if (principal.startsWith("arn:aws:sts:")) {
            final String[] parts = principal.split("/");
            if (parts.length == 3 && parts[0].endsWith(":assumed-role")
                    && parts[1].startsWith(AWS_RESERVED_SSO_ROLE_NAME_PREFIX)) {
                users.add(parts[2]); // session name, usually the username/email
                return;
            }
        }
        // Everything else is a group-level identity (account, role, org).
        groups.add(principal);
    }

    private static boolean applyDeny(JsonNode stmt)
    {
        return DENY_EFFECT.equals(stmt.get(EFFECT_PROP).asText());
    }

    private static boolean applyAllow(JsonNode stmt)
    {
        // Statements with Condition blocks are not evaluated (too complex; treat as no-match).
        return ALLOW_EFFECT.equals(stmt.get(EFFECT_PROP).asText()) && !stmt.has(CONDITION_PROP);
    }
}

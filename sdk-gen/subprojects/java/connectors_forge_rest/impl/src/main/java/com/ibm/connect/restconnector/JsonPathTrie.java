/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal trie of dot-separated JSON paths used to drive the streaming {@link PageParser}.
 *
 * <p>Each registered path is split on {@code '.'} once. While walking the token stream the parser
 * follows only branches present in the trie and skips everything else with
 * {@code JsonParser.skipChildren()}, so the response is never materialised.
 *
 * <p>Segments are matched against object field names. When the walker is inside a JSON
 * <em>array</em>, a segment is matched against the zero-based element index (e.g.
 * {@code "pages.0.items"}), preserving the behaviour of the legacy tree-based lookup.
 *
 * <p>Paths must not overlap: a registered path may not be a prefix of another one. The single
 * exception is the empty (root) path, which may carry the {@link Tag#DATA} tag together with
 * metadata paths; this describes a root object that is both the record and the carrier of
 * pagination metadata.
 */
final class JsonPathTrie
{
    /** What a registered path designates. */
    enum Tag
    {
        DATA, CURSOR, NEXT_URL
    }

    private final Node root = new Node(true);

    /** A node in the trie. */
    static final class Node
    {
        private final Map<String, Node> children = new HashMap<>();
        private final boolean isRoot;
        private Tag tag;

        Node(boolean isRoot)
        {
            this.isRoot = isRoot;
        }

        Node child(String key)
        {
            return children.get(key);
        }

        boolean isLeaf()
        {
            return tag != null;
        }

        boolean hasChildren()
        {
            return !children.isEmpty();
        }

        Tag tag()
        {
            return tag;
        }
    }

    /**
     * Registers a dot-separated path.
     *
     * @param path
     *            the path; {@code null} or empty designates the document root
     * @param tag
     *            what the path designates
     * @throws IllegalArgumentException
     *             if a segment is empty or the path overlaps an already registered one
     */
    void register(String path, Tag tag)
    {
        Node current = root;
        for (final String segment : segments(path)) {
            if (!current.isRoot && current.isLeaf()) {
                throw overlap(path);
            }
            current = current.children.computeIfAbsent(segment, k -> new Node(false));
        }
        if (current.isLeaf() || !current.isRoot && current.hasChildren()) {
            throw overlap(path);
        }
        current.tag = tag;
    }

    Node root()
    {
        return root;
    }

    /**
     * Splits a dot-separated path into segments.
     *
     * @return the segments; an empty list for a {@code null} or empty path
     * @throws IllegalArgumentException
     *             if any segment is empty (e.g. {@code "a..b"} or a leading/trailing dot)
     */
    static List<String> segments(String path)
    {
        if (path == null || path.isEmpty()) {
            return Collections.emptyList();
        }
        final String[] parts = path.split("\\.", -1);
        final List<String> result = new ArrayList<>(parts.length);
        for (final String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Malformed JSON path (empty segment): '" + path + "'");
            }
            result.add(part);
        }
        return result;
    }

    private static IllegalArgumentException overlap(String path)
    {
        return new IllegalArgumentException("JSON path '" + path
                + "' overlaps or duplicates another configured path (data path, cursor path, next-url path)");
    }
}

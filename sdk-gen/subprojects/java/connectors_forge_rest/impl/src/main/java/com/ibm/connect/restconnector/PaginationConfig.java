/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Configuration for REST API pagination.
 *
 * <p>Supports multiple pagination strategies:
 * <ul>
 *   <li><b>offset</b>: Offset-based pagination (e.g., ?offset=0&amp;limit=100)</li>
 *   <li><b>page</b>: Page-based pagination (e.g., ?page=1&amp;per_page=50)</li>
 *   <li><b>cursor</b>: Cursor-based pagination with next cursor in response</li>
 *   <li><b>link_header</b>: Link header pagination (RFC 5988)</li>
 *   <li><b>next_url</b>: Next URL in response body</li>
 * </ul>
 */
public final class PaginationConfig
{
    private final PaginationType type;
    private final String offsetParam;
    private final String pageParam;
    private final String limitParam;
    private final int pageSize;
    private final int initialOffset;
    private final int initialPage;
    private final String cursorParam;
    private final String nextCursorPath;
    private final String nextUrlPath;

    /**
     * Creates a pagination configuration.
     *
     * @param type
     *            the pagination type (offset, page, cursor, link_header, next_url)
     * @param offsetParam
     *            the query parameter name for offset (for offset type)
     * @param pageParam
     *            the query parameter name for page number (for page type)
     * @param limitParam
     *            the query parameter name for page size/limit
     * @param pageSize
     *            the number of items per page
     * @param initialOffset
     *            the initial offset value (for offset type, typically 0)
     * @param initialPage
     *            the initial page number (for page type, typically 1)
     * @param cursorParam
     *            the query parameter name for cursor (for cursor type)
     * @param nextCursorPath
     *            the JSON path to next cursor in response (for cursor type)
     * @param nextUrlPath
     *            the JSON path to next URL in response (for next_url type)
     */
    public PaginationConfig(PaginationType type, String offsetParam, String pageParam, String limitParam,
            int pageSize, int initialOffset, int initialPage, String cursorParam,
            String nextCursorPath, String nextUrlPath)
    {
        this.type = type;
        this.offsetParam = offsetParam;
        this.pageParam = pageParam;
        this.limitParam = limitParam;
        this.pageSize = pageSize;
        this.initialOffset = initialOffset;
        this.initialPage = initialPage;
        this.cursorParam = cursorParam;
        this.nextCursorPath = nextCursorPath;
        this.nextUrlPath = nextUrlPath;
    }

    /** Returns the pagination type string. */
    public String getType()
    {
        return type != null ? type.getValue() : null;
    }

    /** Returns the pagination type enum. */
    public PaginationType getTypeEnum()
    {
        return type;
    }

    /** Returns the query parameter name for offset. */
    public String getOffsetParam() { return offsetParam; }

    /** Returns the query parameter name for page number. */
    public String getPageParam() { return pageParam; }

    /** Returns the query parameter name for page size/limit. */
    public String getLimitParam() { return limitParam; }

    /** Returns the number of items per page. */
    public int getPageSize() { return pageSize; }

    /** Returns the initial offset value. */
    public int getInitialOffset() { return initialOffset; }

    /** Returns the initial page number. */
    public int getInitialPage() { return initialPage; }

    /** Returns the query parameter name for cursor. */
    public String getCursorParam() { return cursorParam; }

    /** Returns the JSON path to the next cursor in the response. */
    public String getNextCursorPath() { return nextCursorPath; }

    /** Returns the JSON path to the next URL in the response. */
    public String getNextUrlPath() { return nextUrlPath; }

    @Override
    public String toString()
    {
        return "PaginationConfig{type='" + getType() + "', offsetParam='" + offsetParam
                + "', pageParam='" + pageParam + "', limitParam='" + limitParam
                + "', pageSize=" + pageSize + ", initialOffset=" + initialOffset
                + ", initialPage=" + initialPage + ", cursorParam='" + cursorParam
                + "', nextCursorPath='" + nextCursorPath + "', nextUrlPath='" + nextUrlPath + "'}";
    }
}

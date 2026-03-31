/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Configuration for REST API pagination.
 *
 * <p>Supports multiple pagination strategies:
 * <ul>
 *   <li><b>offset</b>: Offset-based pagination (e.g., ?offset=0&limit=100)</li>
 *   <li><b>page</b>: Page-based pagination (e.g., ?page=1&per_page=50)</li>
 *   <li><b>cursor</b>: Cursor-based pagination with next cursor in response</li>
 *   <li><b>link_header</b>: Link header pagination (RFC 5988)</li>
 *   <li><b>next_url</b>: Next URL in response body</li>
 * </ul>
 */
public final class PaginationConfig
{
    private final String type;
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
    public PaginationConfig(String type, String offsetParam, String pageParam, String limitParam,
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

    /**
     * Returns the pagination type.
     *
     * @return the type (offset, page, cursor, link_header, next_url)
     */
    public String getType()
    {
        return type;
    }

    /**
     * Returns the query parameter name for offset.
     *
     * @return the offset parameter name (e.g., "offset", "skip")
     */
    public String getOffsetParam()
    {
        return offsetParam;
    }

    /**
     * Returns the query parameter name for page number.
     *
     * @return the page parameter name (e.g., "page", "page_number")
     */
    public String getPageParam()
    {
        return pageParam;
    }

    /**
     * Returns the query parameter name for page size/limit.
     *
     * @return the limit parameter name (e.g., "limit", "per_page", "size")
     */
    public String getLimitParam()
    {
        return limitParam;
    }

    /**
     * Returns the number of items per page.
     *
     * @return the page size
     */
    public int getPageSize()
    {
        return pageSize;
    }

    /**
     * Returns the initial offset value.
     *
     * @return the initial offset (typically 0)
     */
    public int getInitialOffset()
    {
        return initialOffset;
    }

    /**
     * Returns the initial page number.
     *
     * @return the initial page (typically 1)
     */
    public int getInitialPage()
    {
        return initialPage;
    }

    /**
     * Returns the query parameter name for cursor.
     *
     * @return the cursor parameter name (e.g., "cursor", "next_token")
     */
    public String getCursorParam()
    {
        return cursorParam;
    }

    /**
     * Returns the JSON path to the next cursor in the response.
     *
     * @return the next cursor path (e.g., "pagination.next", "meta.next_token")
     */
    public String getNextCursorPath()
    {
        return nextCursorPath;
    }

    /**
     * Returns the JSON path to the next URL in the response.
     *
     * @return the next URL path (e.g., "pagination.next_url", "links.next")
     */
    public String getNextUrlPath()
    {
        return nextUrlPath;
    }

    @Override
    public String toString()
    {
        return "PaginationConfig{type='" + type + "', offsetParam='" + offsetParam
                + "', pageParam='" + pageParam + "', limitParam='" + limitParam
                + "', pageSize=" + pageSize + ", initialOffset=" + initialOffset
                + ", initialPage=" + initialPage + ", cursorParam='" + cursorParam
                + "', nextCursorPath='" + nextCursorPath + "', nextUrlPath='" + nextUrlPath + "'}";
    }
}

// Made with Bob
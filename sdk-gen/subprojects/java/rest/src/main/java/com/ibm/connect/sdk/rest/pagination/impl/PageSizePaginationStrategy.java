/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;

import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;

import com.ibm.connect.sdk.rest.pagination.PaginationStrategy;
import com.ibm.connect.sdk.rest.pagination.PaginationType;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

public class PageSizePaginationStrategy implements PaginationStrategy {
    private static final Logger LOGGER = getLogger(PageSizePaginationStrategy.class);

    @Override
    public boolean apply(Pagination pagination, int offset, int limit, List<Header> responseHeaders, RequestQueryParams params) {
        final String pageParam = pagination.getPageParam();
        final String sizeParam = pagination.getSizeParam();

        if (pageParam == null || pageParam.isBlank()) {
            throw new IllegalArgumentException("pageParam is required for PAGE pagination type but was null or empty");
        }

        if (sizeParam == null || sizeParam.isBlank()) {
            throw new IllegalArgumentException("sizeParam is required for PAGE pagination type but was null or empty");
        }

        // Calculate page number from offset and limit (pageSize)
        // Page numbers are typically 0-based or 1-based depending on API
        // This calculation assumes 0-based indexing: offset=0 → page=0, offset=10,limit=10 → page=1
        final int pageNumber = (limit > 0) ? (offset / limit) : 0;

        params.put(pageParam.trim(), String.valueOf(pageNumber));
        params.put(sizeParam.trim(), String.valueOf(limit));

        LOGGER.debug("Mapped PAGE pagination: {}={}, {}={} (calculated from offset={}, limit={})",
                pageParam, pageNumber, sizeParam, limit, offset, limit);
        return true; //Return true, as for `pageSize` pagination, we can request always and if the response is empty we could stop calling the API
    }

    @Override
    public boolean supports(EntityType entityType) {
        return PaginationType.PAGE.name().equalsIgnoreCase(
                entityType.getPagination().getType());
    }
}

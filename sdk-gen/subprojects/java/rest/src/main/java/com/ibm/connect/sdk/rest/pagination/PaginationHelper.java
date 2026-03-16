/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;

import com.ibm.connect.sdk.rest.pagination.impl.LinkHeaderPaginationStrategy;
import com.ibm.connect.sdk.rest.pagination.impl.OffsetLimitPaginationStrategy;
import com.ibm.connect.sdk.rest.pagination.impl.PageSizePaginationStrategy;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

public class PaginationHelper {

    private static final Logger LOGGER = getLogger(PaginationHelper.class);
    public static final int DEFAULT_MAX_LIMIT = 1000; // Need to discuss on the default size
    public static final int DEFAULT_OFFSET = 0;
    public static final String LOCATION_TYPE_QUERY = "query";
    private static final List<PaginationStrategy> PAGINATION_STRATEGIES = List.of(
            new OffsetLimitPaginationStrategy(),
            new PageSizePaginationStrategy(),
            new LinkHeaderPaginationStrategy()
    );

    private PaginationHelper() {
    }

    public static void mapParametersForPagination(Integer offset, Integer limit, EntityType entityType) {
        mapParametersForPagination(offset, limit, entityType, Collections.emptyList());
    }

    public static boolean mapParametersForPagination(Integer offset, Integer limit, EntityType entityType, List<Header> responseHeaders) {
        if (RestUtils.isPaginationSupported(entityType.getPagination())) { // proceed only if API supports pagination
            final int paginationOffset = (null != offset && offset > -1) ? offset : DEFAULT_OFFSET;
            final int paginationLimit = (null != limit && limit > 0 && limit < DEFAULT_MAX_LIMIT) ? limit
                    : DEFAULT_MAX_LIMIT;
            if (entityType.getPagination().getLocation().equalsIgnoreCase(LOCATION_TYPE_QUERY)) {
                return mapPaginationToQueryParams(entityType, paginationOffset, paginationLimit, responseHeaders);
            }
        }
        return true; //If no pagination supported, then call the Rest API
    }

    private static boolean mapPaginationToQueryParams(EntityType entityType, int offset, int limit, List<Header> responseHeaders) {
        final String paginationTypeStr = entityType.getPagination().getType();
        final RequestQueryParams queryParams = new RequestQueryParams();
        final PaginationType paginationType = PaginationType.fromString(paginationTypeStr);

        // For link_header pagination, if no next page URL, we need to stop infinite iteration of pages.
        final boolean canExecuteRestAPI = PAGINATION_STRATEGIES.stream()
                .filter(s -> s.supports(entityType))
                .findFirst()
                .map(paginationStrategy -> paginationStrategy
                        .apply(entityType.getPagination(), offset, limit, responseHeaders, queryParams)).orElse(true); //By default true,

        if (null != entityType.getRequestQueryParams()) {
            entityType.getRequestQueryParams().putAll(queryParams);
        } else {
            entityType.setRequestQueryParams(queryParams);
        }

        LOGGER.debug("Successfully mapped pagination type '{}' to query params: {}", paginationType, queryParams);
        return canExecuteRestAPI;
    }
}

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

public class OffsetLimitPaginationStrategy implements PaginationStrategy {
    private static final Logger LOGGER = getLogger(OffsetLimitPaginationStrategy.class);

    @Override
    public boolean apply(Pagination pagination, int offset, int limit, List<Header> responseHeaders, RequestQueryParams params) {
        final String pageParam = pagination.getOffsetParam();
        final String sizeParam = pagination.getLimitParam();

        if (pageParam == null || pageParam.isBlank()) {
            throw new IllegalArgumentException(
                    "offsetParam is required for OFFSET_LIMIT pagination type but was null or empty");
        }

        if (sizeParam == null || sizeParam.isBlank()) {
            throw new IllegalArgumentException(
                    "limitParam is required for OFFSET_LIMIT pagination type but was null or empty");
        }

        params.put(pageParam.trim(), String.valueOf(offset));
        params.put(sizeParam.trim(), String.valueOf(limit));

        LOGGER.debug("Mapped OFFSET_LIMIT pagination: {}={}, {}={}", pageParam, offset, sizeParam, limit);
        return true; //Return true, as for offset pagination, we can request always and if the response empty we could stop calling API
    }

    @Override
    public boolean supports(EntityType entityType) {
        return PaginationType.OFFSET.name().equalsIgnoreCase(
                entityType.getPagination().getType());
    }
}

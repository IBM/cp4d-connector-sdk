/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination;

import java.util.List;

import org.apache.hc.core5.http.Header;

import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

public interface PaginationStrategy {

    boolean apply(Pagination pagination, int offset, int limit, List<Header> responseHeaders, RequestQueryParams params);

    boolean supports(EntityType entityType);
}


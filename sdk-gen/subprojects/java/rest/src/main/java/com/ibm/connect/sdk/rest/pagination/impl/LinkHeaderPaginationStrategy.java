/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.Header;
import org.slf4j.Logger;

import com.ibm.connect.sdk.rest.pagination.PaginationStrategy;
import com.ibm.connect.sdk.rest.pagination.PaginationType;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

public class LinkHeaderPaginationStrategy implements PaginationStrategy {
    private static final Logger LOGGER = getLogger(LinkHeaderPaginationStrategy.class);

    @Override
    public boolean apply(Pagination pagination, int offset, int limit, List<Header> responseHeaders, RequestQueryParams params) {
        if (RestUtils.isNullOrEmpty(pagination.getLinkHeader())) {
            LOGGER.warn("LINK_HEADER pagination type requires 'linkHeader' to be configured.");
            return offset <= 0; // Only if first page, it allows to call the API. So return true. From second pages, it expected to have link headers name to extract next page link from header
        }

        final String headerName = pagination.getLinkHeader().trim();
        final String headerValue = getHeaderValue(responseHeaders, headerName);
        if (RestUtils.isNullOrEmpty(headerValue)) {
            LOGGER.debug("No '{}' header found in last response. Assuming no next page.", headerName);
            return offset <= 0; // Only if first page, no header expected. So return true. From second pages, it expect to have headers
        }

        // Extract next URL from the header
        final String nextUrl = detectNextPageUrl(headerValue);

        // Extract query params from the URL
        final Map<String, String> extractedParams = extractQueryParamsFromUrl(nextUrl);
        if (extractedParams.isEmpty()) {
            LOGGER.debug("Next-page URL '{}' does not contain query parameters. No pagination params to map.", nextUrl);
            return offset <= 0; //If no query param found, then no next page and we could stop iterating, if other than first page (or offset =0)
        }

        // Add extracted params to current request params
        params.putAll(extractedParams);
        LOGGER.debug("Mapped LINK_HEADER pagination from header '{}': nextUrl={}, params={}",
                headerName, nextUrl, extractedParams);
        return true;
    }

    @Override
    public boolean supports(EntityType entityType) {
        return PaginationType.LINK_HEADER.name().equalsIgnoreCase(
                entityType.getPagination().getType());
    }

    private String getHeaderValue(List<Header> headers, String name) {
        if (headers == null || headers.isEmpty() || RestUtils.isNullOrEmpty(name)) {
            return null;
        }

        return headers.stream()
                .filter(h -> h.getName().equalsIgnoreCase(name))
                .map(Header::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String detectNextPageUrl(String headerValue) {
        // 1) If RFC Link header
        String nextUrl = extractNextUrlFromLinkHeader(headerValue);

        // 2) If Full URL or  Relative URL
        if (headerValue.startsWith("http") || headerValue.startsWith("/")) {
            nextUrl = headerValue.trim();
        }

        if (RestUtils.isNullOrEmpty(nextUrl)) {
            LOGGER.debug("No link header found. This was likely the last page.");
        }
        return nextUrl;
    }

    /**
     * This only extract next url for `RFC 8288 Link headers`
     * Example: <https://api.com?page=2>; rel="next", <...>; rel="last"
     *
     * @param linkHeaderValue Value of header 'LINK'
     * @return
     */
    private static String extractNextUrlFromLinkHeader(String linkHeaderValue) {
        final String[] links = linkHeaderValue.split(",");
        for (String linkEntry : links) {
            linkEntry = linkEntry.trim();
            if (linkEntry.contains("rel=\"next\"")) {
                final int start = linkEntry.indexOf('<');
                final int end = linkEntry.indexOf('>');
                if (start != -1 && end != -1 && end > start) {
                    return linkEntry.substring(start + 1, end);
                }
            }
        }
        return StringUtils.EMPTY;
    }

    private static Map<String, String> extractQueryParamsFromUrl(String url) {
        final Map<String, String> params = new HashMap<>();
        if (url == null || url.isBlank()) {
            return params;
        }

        try {
            final String normalized = normalizeUrl(url);
            final String query = new URI(normalized).getQuery();
            if (query == null || query.isEmpty()) {
                return params;
            }

            // Split and decode query params
            for (final String part : query.split("&")) {
                final int idx = part.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                final String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
                final String value = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse next-page URL '{}': {}", url, e.getMessage());
        }

        return params;
    }

    private static String normalizeUrl(String url) {
        final String trimmed = url.trim();
        // Has a scheme already
        if (trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return trimmed;
        }
        // Starts with query only: "?page=2"
        if (trimmed.startsWith("?")) {
            return "http://dummy" + trimmed;
        }
        // Relative URL
        if (trimmed.startsWith("/")) {
            return "http://dummy" + trimmed;
        }
        // No leading slash 
        return "http://dummy/" + trimmed;
    }
}

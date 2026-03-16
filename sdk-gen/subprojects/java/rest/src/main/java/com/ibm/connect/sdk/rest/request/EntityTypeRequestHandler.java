/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.request;

import static com.ibm.connect.sdk.rest.RestMsgs.UNEXPECTED_RESPONSE_EXECUTING_REST_API;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.ibm.connect.sdk.rest.httpclient.RestExecutorImpl;
import com.ibm.connect.sdk.rest.pagination.PaginationHelper;
import com.ibm.connect.sdk.rest.response.EntityTypeResponseHandler;
import com.ibm.connect.sdk.rest.response.impl.EntityTypeResponseHandlerFactory;
import com.ibm.connect.sdk.rest.utils.RestConnectionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

public class EntityTypeRequestHandler {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final RestExecutorImpl restExecutor;
    private final RestConnectionProperties restConnectionProperties;

    public EntityTypeRequestHandler(RestExecutorImpl restExecutor, RestConnectionProperties restConnectionProperties) {
        this.restExecutor = restExecutor;
        this.restConnectionProperties = restConnectionProperties;
    }

    /**
     * Execute Rest API defined in the `EntityType` with pagination support
     *
     * @param discoverEntityType Definition of REST API request
     * @param entityNameAndSelectedAssetIdMap Values required to replace the placeholder on `endpointPath` and `QueryParam` in `discoverEntityType`
     * @param offset Pagination offset (optional)
     * @param limit Pagination limit (optional)
     * @return Response handler {@link EntityTypeResponseHandler}
     * @throws IOException
     */
    public List<EntityTypeResponseHandler> executeRequest(EntityType discoverEntityType,
                                                          Map<String, String> entityNameAndSelectedAssetIdMap,
                                                          Integer offset,Integer limit) throws IOException {
        return executeRequest(discoverEntityType, entityNameAndSelectedAssetIdMap, offset, limit, Collections.emptyList());
    }
    /**
     * Execute Rest API defined in the `EntityType` with pagination support
     * This method also capable to do sub pagination if the underlying REST API not support to fetch the `limit` you provided.
     * It reduces the limit to supportedMaxLimit {@link Pagination#getSupportedMaxLimit()} and iterate until it fulfill the actual request
     *
     * @param discoverEntityType Definition of REST API request
     * @param entityNameAndSelectedAssetIdMap Values required to replace the placeholder on `endpointPath` and `QueryParam` in `discoverEntityType`
     * @param offset Pagination offset (optional)
     * @param limit Pagination limit (optional)
     * @param prevPageResponseHeader Previous page response header required for LINK_HEADER pagination. If its empty then it recalculate by fetching records from beginning.
     *
     * @return List ofResponse handler {@link EntityTypeResponseHandler}
     * @throws IOException
     */
    public List<EntityTypeResponseHandler> executeRequest(EntityType discoverEntityType,
                                                          Map<String, String> entityNameAndSelectedAssetIdMap,
                                                          Integer offset, Integer limit, List<Header> prevPageResponseHeader) throws IOException {

        if(RestUtils.isPaginationSupported(discoverEntityType.getPagination()) && offset != null && limit != null) {
            List<Header> previousResponseHeaders = calculatePreviousPageResponseHeaderIfRequired(discoverEntityType, entityNameAndSelectedAssetIdMap, offset, limit, prevPageResponseHeader);

            final int maxPageLimitAllowsByAPI = Optional.ofNullable(discoverEntityType.getPagination()).map(Pagination::getSupportedMaxLimit).orElse(limit);
            if(limit > maxPageLimitAllowsByAPI) {
                final List<EntityTypeResponseHandler> responseHandlers = new ArrayList<>();
                // Need to fetch multiple pages to fulfill the requested limit
                int remainingItemsToFetch = limit;
                int currentOffset = offset;
                while (remainingItemsToFetch > 0) {
                    final int currentLimit = Math.min(remainingItemsToFetch, maxPageLimitAllowsByAPI);

                    // Apply pagination parameters to the entity type before processing the request
                    PaginationHelper.mapParametersForPagination(currentOffset, currentLimit, discoverEntityType, previousResponseHeaders);
                    // Fetch the current page
                    final EntityTypeResponseHandler responseHandler = executeRequest(discoverEntityType, entityNameAndSelectedAssetIdMap);
                    responseHandlers.add(responseHandler);

                    final int numOfItems = responseHandler.getFieldValueMap().size();
                    if (numOfItems < currentLimit) { // If we got fewer items than requested, we've reached the end
                        break;
                    }
                    previousResponseHeaders = responseHandler.getHeaders();
                    remainingItemsToFetch -= numOfItems;
                    currentOffset += numOfItems;
                }
                return responseHandlers;
            }

            // Apply pagination parameters to the entity type before processing the request
            final boolean canExecuteRestAPI = PaginationHelper.mapParametersForPagination(offset, limit, discoverEntityType, previousResponseHeaders);
            // For link_header pagination, if no next page URL, we need to stop infinite iteration of pages.
            if(RestUtils.isLinkHeaderPagination(discoverEntityType.getPagination()) && !canExecuteRestAPI) {
                return Collections.emptyList();
            }
        }

        return List.of(executeRequest(discoverEntityType, entityNameAndSelectedAssetIdMap));
    }

    private List<Header> calculatePreviousPageResponseHeaderIfRequired(EntityType discoverEntityType, Map<String, String> entityNameAndSelectedAssetIdMap,
                                                                       Integer offset, Integer limit,
                                                                       List<Header> prevPageResponseHeader) throws IOException {
        if(RestUtils.isLinkHeaderPagination(discoverEntityType.getPagination())) {
            if(prevPageResponseHeader != null && !prevPageResponseHeader.isEmpty()) {
                return prevPageResponseHeader;
            }

            if(offset != null && limit != null && offset > 0 && limit > 0) {
                // For Link Header pagination, we cannot fetch Nth page randomly
                // We need to start from page 0 and iterate to find the appropriate response header
                final int maxPageLimitAllowsByAPI = Optional.ofNullable(discoverEntityType.getPagination())
                        .map(Pagination::getSupportedMaxLimit)
                        .orElse(limit);
                
                // Calculate how many pages we need to fetch to reach the desired offset
                final int numberOfPagesToFetch = (int) Math.ceil((double) offset / maxPageLimitAllowsByAPI);
                List<Header> currentResponseHeaders = Collections.emptyList();
                int currentOffset = 0;
                
                // Iterate through pages starting from offset 0
                for (int pageIndex = 0; pageIndex < numberOfPagesToFetch; pageIndex++) {
                    // Apply pagination parameters for the current page
                    PaginationHelper.mapParametersForPagination(currentOffset, maxPageLimitAllowsByAPI, discoverEntityType, currentResponseHeaders);
                    // Execute request to get the response headers for this page
                    final EntityTypeResponseHandler responseHandler = executeRequest(discoverEntityType,entityNameAndSelectedAssetIdMap);
                    currentResponseHeaders = responseHandler.getHeaders(); // Update headers for next iteration
                    currentOffset += maxPageLimitAllowsByAPI; // Move to next page
                }
                
                return currentResponseHeaders;
            }
        }
        return Collections.emptyList(); //No header required as it assume there is no pagination required or its fetching first page
    }

    /**
     * Execute Rest API defined in the `EntityType` (without pagination)
     *
     * @param discoverEntityType Definition of REST API request
     * @param entityNameAndSelectedAssetIdMap Values required to replace the placeholder on `endpointPath` and `QueryParam` in `discoverEntityType`
     * @return Response handler {@link EntityTypeResponseHandler}
     * @throws IOException
     */
    public EntityTypeResponseHandler executeRequest(EntityType discoverEntityType, Map<String, String> entityNameAndSelectedAssetIdMap) throws IOException {

        final String formattedEndpointPath = replaceDynamicParams(discoverEntityType.getEndpointPath(), entityNameAndSelectedAssetIdMap);
        final RequestQueryParams formattedQueryParams = Optional.ofNullable(discoverEntityType.getRequestQueryParams()).orElse(new RequestQueryParams());
        formattedQueryParams.replaceAll((key,value)-> replaceDynamicParams(value, entityNameAndSelectedAssetIdMap));

        final String absoluteEndpointUrl = RestUtils.buildUrl(this.restConnectionProperties.getBasicUrl(),formattedEndpointPath);

        final HttpClientResponseHandler<EntityTypeResponseHandler> responseHandler = response -> {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null
                        ? EntityUtils.toString(entity, StandardCharsets.UTF_8)
                        : null;
                throw new IOException(UNEXPECTED_RESPONSE_EXECUTING_REST_API.format(absoluteEndpointUrl, status, responseBody));
            }

            return EntityTypeResponseHandlerFactory.getHandler(response, discoverEntityType);
        };

        return this.restExecutor.executeRequest(discoverEntityType.getMethod(),
                absoluteEndpointUrl, discoverEntityType.getHeader(),
                formattedQueryParams,
                null, responseHandler);
    }

    /**
     * Replaces placeholders in the input string with corresponding values from the map.
     * Placeholders follow the pattern ${placeholderName}.
     * If a placeholder is not found in the map, it is left unchanged.
     *
     * @param template the string containing placeholders
     * @param valuesMap map of placeholder names to replacement values
     * @return the string with placeholders replaced
     */
    private String replaceDynamicParams(String template, Map<String, String> valuesMap) {
        if (RestUtils.isNullOrEmpty(template) || valuesMap == null || template.isEmpty()) {
            return template;
        }

        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        final StringBuilder replacedString = new StringBuilder();

        while (matcher.find()) {
            final String placeholder = matcher.group(1);
            String replacement = valuesMap.getOrDefault(placeholder, matcher.group(0));
            replacement = sanitizeDynamicValue(replacement);
            matcher.appendReplacement(replacedString, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(replacedString);

        return replacedString.toString();
    }

    /**
     * Sanitizes a dynamic value to prevent path traversal attacks.
     * Removes or replaces dangerous characters like "..", "/", "\", etc.
     *
     * @param pathSegment the path segment to sanitize
     * @return sanitized path segment
     */
    private String sanitizeDynamicValue(String pathSegment) {
        if (pathSegment == null || pathSegment.isEmpty()) {
            return pathSegment;
        }
        return pathSegment
            .replace("..", "")   // Remove parent directory references
            .replace("/", "")    // Remove forward slashes
            .replace("\\", "")   // Remove backslashes
            .replace("\0", "");  // Remove null bytes
    }
}

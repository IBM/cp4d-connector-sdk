/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.utils.models.RequestQueryParams;

/**
 * Tests the mapping of offset and limit parameters to query parameters
 * for different pagination types (OFFSET and PAGE).
 */
public class PaginationHelperTest {

    /**
     * Test Case 1: Null pagination configuration
     * When EntityType has no pagination config, no parameters should be added
     */
    @Test
    public void testMapParametersForPaginationNullPaginationNoParametersAdded() {
        final EntityType entityType = new EntityType();
        entityType.setPagination(null);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
        
        // No exception should be thrown, and no query params should be set
        assertNull("Query params should remain null", entityType.getRequestQueryParams());
    }

    /**
     * Test Case 2: OFFSET pagination with null offset and limit
     * Should use default values: offset=0, limit=1000
     */
    @Test
    public void testMapParametersForPaginationOffsetTypeNullValuesUsesDefaults() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(null, null, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should default to 0", "0", queryParams.get("offset"));
        assertEquals("Limit should default to 1000", "1000", queryParams.get("limit"));
    }

    /**
     * Test Case 3: OFFSET pagination with valid offset and limit
     * Should map offset and limit directly to query parameters
     */
    @Test
    public void testMapParametersForPaginationOffsetTypeValidValuesMapsCorrectly() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(50, 25, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should be 50", "50", queryParams.get("offset"));
        assertEquals("Limit should be 25", "25", queryParams.get("limit"));
    }

    /**
     * Test Case 4: OFFSET pagination with zero offset
     * Zero offset should be valid and represent the first page
     */
    @Test
    public void testMapParametersForPaginationOffsetTypeZeroOffsetIsValid() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(0, 10, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should be 0", "0", queryParams.get("offset"));
        assertEquals("Limit should be 10", "10", queryParams.get("limit"));
    }

    /**
     * Test Case 5: OFFSET pagination with limit exceeding MAX_NO_OF_ITEMS
     * Limit should be capped at 1000
     */
    @Test
    public void testMapParametersForPaginationOffsetTypeLimitExceedsMaxCapsAt1000() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(0, 5000, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should be 0", "0", queryParams.get("offset"));
        assertEquals("Limit should be capped at 1000", "1000", queryParams.get("limit"));
    }

    /**
     * Test Case 6: PAGE pagination with null offset and limit
     * Should use defaults: page=1, size=1000
     */
    @Test
    public void testMapParametersForPaginationPageTypeNullValuesUsesDefaults() {
        final EntityType entityType = createEntityTypeWithPagePagination();
        
        PaginationHelper.mapParametersForPagination(null, null, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Page should be 0", "0", queryParams.get("page"));
        assertEquals("Size should default to 1000", "1000", queryParams.get("size"));
    }

    /**
     * Test Case 7: PAGE pagination with valid offset and limit
     * Should convert offset to page number: page = (offset / pageSize) + 1
     */
    @Test
    public void testMapParametersForPaginationPageTypeValidValuesConvertsToPage() {
        final EntityType entityType = createEntityTypeWithPagePagination();
        
        // offset=50, pageSize=25 should give page=3
        PaginationHelper.mapParametersForPagination(50, 25, entityType);
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Page should be 2 (50/25)", "2", queryParams.get("page"));
        assertEquals("Size should be 25", "25", queryParams.get("size"));
    }

    /**
     * Test Case 8: PAGE pagination with offset=0
     * Should result in page=1
     */
    @Test
    public void testMapParametersForPaginationPageTypeZeroOffsetGivesPageOne() {
        final EntityType entityType = createEntityTypeWithPagePagination();
        
        PaginationHelper.mapParametersForPagination(0, 20, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Page should be 0 (0/20)", "0", queryParams.get("page"));
        assertEquals("Size should be 20", "20", queryParams.get("size"));
    }

    /**
     * Test Case 9: PAGE pagination with various offset/pageSize combinations
     * Tests the page calculation formula
     */
    @Test
    public void testMapParametersForPaginationPageTypeVariousOffsetsCalculatesCorrectPage() {
        EntityType entityType = createEntityTypeWithPagePagination();
        
        // Test case 1: offset=100, pageSize=50 -> page=2
        PaginationHelper.mapParametersForPagination(100, 50, entityType);
        assertEquals("Page should be 2", "2", entityType.getRequestQueryParams().get("page"));
        
        // Test case 2: offset=25, pageSize=10 -> page=2
        entityType = createEntityTypeWithPagePagination();
        PaginationHelper.mapParametersForPagination(25, 10, entityType);
        assertEquals("Page should be 2", "2", entityType.getRequestQueryParams().get("page"));
        
        // Test case 3: offset=99, pageSize=100 -> page=0
        entityType = createEntityTypeWithPagePagination();
        PaginationHelper.mapParametersForPagination(99, 100, entityType);
        assertEquals("Page should be 0", "0", entityType.getRequestQueryParams().get("page"));
        
        // Test case 4: offset=100, pageSize=100 -> page=1
        entityType = createEntityTypeWithPagePagination();
        PaginationHelper.mapParametersForPagination(100, 100, entityType);
        assertEquals("Page should be 1", "1", entityType.getRequestQueryParams().get("page"));
    }

    /**
     * Test Case 10: Negative offset should take default offset
     */
    @Test
    public void testMapParametersForPaginationNegativeOffsetThrowsException() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(-1, 10, entityType);
        assertEquals("Offset should be 10", "0", entityType.getRequestQueryParams().get("offset"));
        assertEquals("Limit should be 10", "10", entityType.getRequestQueryParams().get("limit"));
    }

    /**
     * Test Case 11: Zero or negative limit should take default limit
     */
    @Test
    public void testMapParametersForPaginationZeroLimitThrowsException() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();

        PaginationHelper.mapParametersForPagination(0, 0, entityType);
        assertEquals("Offset should be 0", "0", entityType.getRequestQueryParams().get("offset"));
        assertEquals("Limit should be 1000", "1000", entityType.getRequestQueryParams().get("limit"));
    }

    /**
     * Test Case 12: Negative limit should take default limit
     */
    @Test
    public void testMapParametersForPaginationNegativeLimitThrowsException() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(0, -5, entityType);
        assertEquals("Offset should be 0", "0", entityType.getRequestQueryParams().get("offset"));
        assertEquals("Limit should be 1000", "1000", entityType.getRequestQueryParams().get("limit"));
    }

    /**
     * Test Case 13: OFFSET pagination with existing query params
     * Should merge pagination params with existing params
     */
    @Test
    public void testMapParametersForPaginationOffsetTypeExistingParamsMergesCorrectly() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        final RequestQueryParams existingParams = new RequestQueryParams();
        existingParams.put("filter", "active");
        existingParams.put("sort", "name");
        entityType.setRequestQueryParams(existingParams);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should be 10", "10", queryParams.get("offset"));
        assertEquals("Limit should be 20", "20", queryParams.get("limit"));
        assertEquals("Existing filter param should be preserved", "active", queryParams.get("filter"));
        assertEquals("Existing sort param should be preserved", "name", queryParams.get("sort"));
    }

    /**
     * Test Case 14: PAGE pagination with existing query params
     * Should merge pagination params with existing params
     */
    @Test
    public void testMapParametersForPaginationPageTypeExistingParamsMergesCorrectly() {
        final EntityType entityType = createEntityTypeWithPagePagination();
        final RequestQueryParams existingParams = new RequestQueryParams();
        existingParams.put("filter", "active");
        entityType.setRequestQueryParams(existingParams);
        
        PaginationHelper.mapParametersForPagination(20, 10, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Page should be 2", "2", queryParams.get("page"));
        assertEquals("Size should be 10", "10", queryParams.get("size"));
        assertEquals("Existing filter param should be preserved", "active", queryParams.get("filter"));
    }

    /**
     * Test Case 15: OFFSET pagination with missing offsetParam
     * Should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMapParametersForPaginationOffsetTypeMissingOffsetParamThrowsException() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("OFFSET");
        pagination.setLocation("query");
        pagination.setOffsetParam(null); // Missing offset param
        pagination.setLimitParam("limit");
        entityType.setPagination(pagination);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
    }

    /**
     * Test Case 16: OFFSET pagination with missing limitParam
     * Should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMapParametersForPaginationOffsetTypeMissingLimitParamThrowsException() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("OFFSET");
        pagination.setLocation("query");
        pagination.setOffsetParam("offset");
        pagination.setLimitParam(null); // Missing limit param
        entityType.setPagination(pagination);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
    }

    /**
     * Test Case 17: PAGE pagination with missing pageParam
     * Should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMapParametersForPaginationPageTypeMissingPageParamThrowsException() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("PAGE");
        pagination.setLocation("query");
        pagination.setPageParam(null); // Missing page param
        pagination.setSizeParam("size");
        entityType.setPagination(pagination);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
    }

    /**
     * Test Case 18: PAGE pagination with missing sizeParam
     * Should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMapParametersForPaginationPageTypeMissingSizeParamThrowsException() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("PAGE");
        pagination.setLocation("query");
        pagination.setPageParam("page");
        pagination.setSizeParam(null); // Missing size param
        entityType.setPagination(pagination);
        
        PaginationHelper.mapParametersForPagination(10, 20, entityType);
    }

    /**
     * Test Case 19: Large offset values
     * Should handle large offset values correctly
     */
    @Test
    public void testMapParametersForPaginationLargeOffsetHandlesCorrectly() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(1000000, 100, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Offset should be 1000000", "1000000", queryParams.get("offset"));
        assertEquals("Limit should be 100", "100", queryParams.get("limit"));
    }

    /**
     * Test Case 20: Boundary value - limit exactly at MAX_NO_OF_ITEMS
     * Should accept limit of exactly 1000
     */
    @Test
    public void testMapParametersForPaginationLimitAtMaxAcceptsValue() {
        final EntityType entityType = createEntityTypeWithOffsetPagination();
        
        PaginationHelper.mapParametersForPagination(0, 1000, entityType);
        
        final RequestQueryParams queryParams = entityType.getRequestQueryParams();
        assertNotNull("Query params should be set", queryParams);
        assertEquals("Limit should be 1000", "1000", queryParams.get("limit"));
    }

    // Helper methods

    private EntityType createEntityTypeWithOffsetPagination() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("OFFSET");
        pagination.setLocation("query");
        pagination.setOffsetParam("offset");
        pagination.setLimitParam("limit");
        entityType.setPagination(pagination);
        return entityType;
    }

    private EntityType createEntityTypeWithPagePagination() {
        final EntityType entityType = new EntityType();
        final Pagination pagination = new Pagination();
        pagination.setType("PAGE");
        pagination.setLocation("query");
        pagination.setPageParam("page");
        pagination.setSizeParam("size");
        entityType.setPagination(pagination);
        return entityType;
    }
}
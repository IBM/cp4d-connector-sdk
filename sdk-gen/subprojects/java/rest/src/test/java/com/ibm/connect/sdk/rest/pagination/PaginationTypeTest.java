/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PaginationTypeTest {

    @Test
    public void testFromStringOffsetOrPageReturnsOffsetOrPageEnum() {
        // Test Case 1: Convert "OFFSET" string to OFFSET enum
        PaginationType result = PaginationType.fromString("OFFSET");

        assertNotNull("Result should not be null", result);
        assertEquals("Should return OFFSET enum", PaginationType.OFFSET, result);

        // Test Case 2: Convert "PAGE" string to PAGE enum
        result = PaginationType.fromString("PAGE");

        assertNotNull("Result should not be null", result);
        assertEquals("Should return PAGE enum", PaginationType.PAGE, result);

        // Test Case 3: Convert lowercase "offset" to OFFSET enum
        result = PaginationType.fromString("offset");

        assertNotNull("Result should not be null", result);
        assertEquals("Should return OFFSET enum", PaginationType.OFFSET, result);

        // Test Case 4: Convert lowercase "page" to PAGE enum
        result = PaginationType.fromString("page");

        assertNotNull("Result should not be null", result);
        assertEquals("Should return PAGE enum", PaginationType.PAGE, result);

        // Test Case 5: Convert mixed case "OfFsEt" to OFFSET enum
        result = PaginationType.fromString("OfFsEt");

        assertNotNull("Result should not be null", result);
        assertEquals("Should return OFFSET enum", PaginationType.OFFSET, result);
    }

    /**
     * Test Case 6: Convert string with leading/trailing spaces
     */
    @Test
    public void testFromStringWithSpacesTrimsAndConverts() {
        final PaginationType result = PaginationType.fromString("  OFFSET  ");
        
        assertNotNull("Result should not be null", result);
        assertEquals("Should trim and return OFFSET enum", PaginationType.OFFSET, result);
    }

    /**
     * Test Case 7: Null string should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromStringNullThrowsException() {
        PaginationType.fromString(null);
    }

    /**
     * Test Case 8: Empty string should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromStringEmptyStringThrowsException() {
        PaginationType.fromString("");
    }

    /**
     * Test Case 9: Blank string (only spaces) should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromStringBlankStringThrowsException() {
        PaginationType.fromString("   ");
    }

    /**
     * Test Case 10: Invalid pagination type should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromStringInvalidTypeThrowsException() {
        PaginationType.fromString("INVALID");
    }

    /**
     * Test Case 11: Another invalid type should throw IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFromStringAnotherInvalidTypeThrowsException() {
        PaginationType.fromString("CURSOR");
    }

    /**
     * Test Case 12: Verify exception message for invalid type
     */
    @Test
    public void testFromStringInvalidTypeHasDescriptiveMessage() {
        try {
            PaginationType.fromString("INVALID");
        } catch (IllegalArgumentException e) {
            final String message = e.getMessage();
            assertNotNull("Exception message should not be null", message);
            assertEquals("Exception message should be descriptive", 
                "Unsupported pagination type: 'INVALID'. Supported types are: '"+ PaginationType.getSupportedPaginationTypes() + "'",
                message);
        }
    }

    /**
     * Test Case 13: Verify exception message for null type
     */
    @Test
    public void testFromStringNullHasDescriptiveMessage() {
        try {
            PaginationType.fromString(null);
        } catch (IllegalArgumentException e) {
            final String message = e.getMessage();
            assertNotNull("Exception message should not be null", message);
            assertEquals("Exception message should be descriptive", 
                "Pagination type cannot be null or empty", 
                message);
        }
    }

    /**
     * Test Case 14: Verify exception message for empty string
     */
    @Test
    public void testFromStringEmptyStringHasDescriptiveMessage() {
        try {
            PaginationType.fromString("");
        } catch (IllegalArgumentException e) {
            final String message = e.getMessage();
            assertNotNull("Exception message should not be null", message);
            assertEquals("Exception message should be descriptive", 
                "Pagination type cannot be null or empty", 
                message);
        }
    }

    /**
     * Test Case 15: Verify all enum values exist
     */
    @Test
    public void testEnumValuesAllValuesExist() {
        final PaginationType[] values = PaginationType.values();
        
        assertNotNull("Values array should not be null", values);
        assertEquals("Should have exactly 3 pagination types", 3, values.length);
        
        // Verify both types exist
        boolean hasOffset = false;
        boolean hasPage = false;
        
        for (final PaginationType type : values) {
            if (type == PaginationType.OFFSET) {
                hasOffset = true;
            } else if (type == PaginationType.PAGE) {
                hasPage = true;
            }
        }

        assertTrue("Should have OFFSET type", hasOffset);
        assertTrue("Should have PAGE type", hasPage);
    }

    /**
     * Test Case 16: Verify valueOf method works
     */
    @Test
    public void testValueOfValidNameReturnsEnum() {
        final PaginationType offset = PaginationType.valueOf("OFFSET");
        final PaginationType page = PaginationType.valueOf("PAGE");
        
        assertEquals("valueOf should return OFFSET", PaginationType.OFFSET, offset);
        assertEquals("valueOf should return PAGE", PaginationType.PAGE, page);
    }

    /**
     * Test Case 17: Verify enum name() method
     */
    @Test
    public void testNameReturnsCorrectName() {
        assertEquals("OFFSET name should be 'OFFSET'", "OFFSET", PaginationType.OFFSET.name());
        assertEquals("PAGE name should be 'PAGE'", "PAGE", PaginationType.PAGE.name());
    }

    /**
     * Test Case 18: Verify enum ordinal values
     */
    @Test
    public void testOrdinalReturnsCorrectOrdinal() {
        assertEquals("OFFSET ordinal should be 0", 0, PaginationType.OFFSET.ordinal());
        assertEquals("PAGE ordinal should be 1", 1, PaginationType.PAGE.ordinal());
    }

    /**
     * Test Case 19: Test fromString with tab characters
     */
    @Test
    public void testFromStringWithTabsTrimsAndConverts() {
        final PaginationType result = PaginationType.fromString("\tOFFSET\t");
        
        assertNotNull("Result should not be null", result);
        assertEquals("Should trim tabs and return OFFSET enum", PaginationType.OFFSET, result);
    }

    /**
     * Test Case 20: Test fromString with newline characters
     */
    @Test
    public void testFromStringWithNewlinesTrimsAndConverts() {
        final PaginationType result = PaginationType.fromString("\nPAGE\n");
        
        assertNotNull("Result should not be null", result);
        assertEquals("Should trim newlines and return PAGE enum", PaginationType.PAGE, result);
    }
}
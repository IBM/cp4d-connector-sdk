/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.ibm.connect.sdk.api.Record;

public class PaginatedRecordProvider {
    // Current page data
    private Iterator<Map<String, Object>> currentIterator;

    // Function to fetch next page given the current page number
    private final Function<Integer, List<Map<String, Object>>> pageFetcher;
    // Current page index
    private int currentPage;
    // Flag for end-of-data
    private boolean hasMore = true;

    public PaginatedRecordProvider(Function<Integer, List<Map<String, Object>>> pageFetcher) {
        this.pageFetcher = pageFetcher;
        loadNextPage();
    }

    public Record getRecord() {
        // If no more data, return null
        while ((currentIterator == null || !currentIterator.hasNext()) && hasMore) {
            loadNextPage();
        }

        if (currentIterator == null || !currentIterator.hasNext()) {
            return null;
        }

        final Map<String, Object> row = currentIterator.next();
        final Record recordObj = new Record();

        for (final Object value : row.values()) {
            if (value instanceof Serializable) {
                recordObj.appendValue((Serializable) value);
            } else if (value != null) {
                recordObj.appendValue(value.toString());
            } else {
                recordObj.appendValue(null);
            }
        }

        return recordObj;
    }

    private void loadNextPage() {
        currentPage++;
        final List<Map<String, Object>> rows = pageFetcher.apply(currentPage);
        if (rows == null || rows.isEmpty()) {
            hasMore = false;
            currentIterator = null;
        } else {
            currentIterator = rows.iterator();
        }
    }
}

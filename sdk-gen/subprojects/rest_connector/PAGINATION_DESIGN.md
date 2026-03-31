# REST Connector Pagination Support - Design Document

## Overview

This document describes the design for adding pagination support to the REST Connector, enabling it to automatically fetch all pages of data from paginated REST APIs.

## Requirements

1. **Support Multiple Pagination Patterns**:
   - Offset-based pagination (page/offset + limit/per_page/size)
   - Cursor-based pagination (cursor/next_token with next cursor in response)
   - Link header pagination (RFC 5988)
   - Next URL pagination (full URL in response body)

2. **Flexible Parameter Naming**: APIs use different parameter names, so the configuration must be flexible

3. **Streaming Architecture**: Maintain streaming approach - fetch pages on-demand as records are consumed

4. **Automatic Termination**: Detect when no more pages are available

## Configuration Structure

### JSON Configuration Format

```json
{
  "$connector_name": "Example API",
  "$hostname": "https://api.example.com",
  "$authentication": "api_key",
  "$tables": {
    "USERS": {
      "$path": ["/api/users"],
      "$pagination": {
        "type": "offset",
        "offset_param": "offset",
        "limit_param": "limit",
        "page_size": 100
      },
      "id": "VARCHAR,$key",
      "name": "VARCHAR"
    },
    "POSTS": {
      "$path": ["/api/posts"],
      "$pagination": {
        "type": "cursor",
        "cursor_param": "cursor",
        "next_cursor_path": "pagination.next_cursor",
        "page_size": 50
      },
      "id": "VARCHAR,$key",
      "title": "VARCHAR"
    },
    "REPOS": {
      "$path": ["/api/repos"],
      "$pagination": {
        "type": "link_header",
        "page_size": 30
      },
      "id": "VARCHAR,$key",
      "name": "VARCHAR"
    }
  }
}
```

### Pagination Types

#### 1. Offset-Based Pagination

**Configuration**:
```json
"$pagination": {
  "type": "offset",
  "offset_param": "offset",      // or "skip", etc.
  "limit_param": "limit",         // or "per_page", "size", "count", etc.
  "page_size": 100,
  "initial_offset": 0             // optional, defaults to 0
}
```

**Behavior**:
- First request: `GET /api/users?offset=0&limit=100`
- Second request: `GET /api/users?offset=100&limit=100`
- Third request: `GET /api/users?offset=200&limit=100`
- Continues until response returns fewer than `page_size` items

**Termination**: When response array has fewer items than `page_size`

#### 2. Page-Based Pagination (Variant of Offset)

**Configuration**:
```json
"$pagination": {
  "type": "page",
  "page_param": "page",           // or "page_number", etc.
  "limit_param": "per_page",      // or "limit", "size", etc.
  "page_size": 50,
  "initial_page": 1               // optional, defaults to 1
}
```

**Behavior**:
- First request: `GET /api/items?page=1&per_page=50`
- Second request: `GET /api/items?page=2&per_page=50`
- Third request: `GET /api/items?page=3&per_page=50`

**Termination**: When response array has fewer items than `page_size`

#### 3. Cursor-Based Pagination

**Configuration**:
```json
"$pagination": {
  "type": "cursor",
  "cursor_param": "cursor",                    // or "next_token", "after", etc.
  "next_cursor_path": "pagination.next",       // JSON path to next cursor in response
  "page_size": 100,
  "limit_param": "limit"                       // optional
}
```

**Behavior**:
- First request: `GET /api/items?limit=100`
- Response: `{"data": [...], "pagination": {"next": "abc123"}}`
- Second request: `GET /api/items?cursor=abc123&limit=100`
- Response: `{"data": [...], "pagination": {"next": "def456"}}`
- Continues until `next_cursor_path` is null or missing

**Termination**: When `next_cursor_path` field is null, empty, or missing

**Note**: This is for true cursor pagination where the API returns an opaque token/cursor for the next page, not for filtering mechanisms like `starting_after` which are query filters rather than pagination.

#### 4. Link Header Pagination (RFC 5988)

**Configuration**:
```json
"$pagination": {
  "type": "link_header",
  "page_size": 30,
  "limit_param": "per_page"       // optional
}
```

**Behavior**:
- First request: `GET /api/repos?per_page=30`
- Response headers: `Link: <https://api.example.com/repos?page=2&per_page=30>; rel="next"`
- Second request: `GET https://api.example.com/repos?page=2&per_page=30`
- Continues until no `rel="next"` link in headers

**Termination**: When Link header has no `rel="next"` entry

#### 5. Next URL Pagination

**Configuration**:
```json
"$pagination": {
  "type": "next_url",
  "next_url_path": "pagination.next_url",      // JSON path to next URL
  "page_size": 100,
  "limit_param": "limit"                       // optional
}
```

**Behavior**:
- First request: `GET /api/items?limit=100`
- Response: `{"data": [...], "pagination": {"next_url": "https://api.example.com/api/items?page=2"}}`
- Second request: `GET https://api.example.com/api/items?page=2`
- Continues until `next_url_path` is null or missing

**Termination**: When `next_url_path` field is null, empty, or missing

## Implementation Architecture

### Class Structure

```
PaginationConfig
├── type: String (offset, page, cursor, link_header, next_url)
├── offsetParam: String (for offset/page types)
├── limitParam: String (for all types)
├── pageSize: int
├── initialOffset: int (for offset type)
├── initialPage: int (for page type)
├── cursorParam: String (for cursor type)
├── nextCursorPath: String (for cursor type)
└── nextUrlPath: String (for next_url type)

RestTableDefinition
├── path: String
├── dataPath: String
├── paginationConfig: PaginationConfig (new)
└── fields: List<RestFieldDefinition>

JsonToRecordStream (refactored)
├── Current state: single HTTP request
└── New state: multi-page iteration
    ├── fetchNextPage(): fetches next page when current exhausted
    ├── extractNextPageInfo(): extracts cursor/URL from response
    ├── buildNextPageUrl(): constructs URL for next page
    └── hasMorePages(): checks if pagination should continue
```

### JsonToRecordStream Refactoring

**Current Flow**:
1. `initialize()` - opens HTTP connection, positions parser at first array element
2. `advance()` - reads next JSON object from current stream
3. `hasNext()` - checks if more objects in current stream

**New Flow**:
1. `initialize()` - opens first page, positions parser at first array element
2. `advance()` - reads next JSON object, fetches next page if current exhausted
3. `fetchNextPage()` - closes current stream, opens next page
4. `extractNextPageInfo()` - extracts pagination info from response
5. `hasMorePages()` - determines if more pages exist

### Streaming Considerations

**Key Challenge**: Jackson's streaming parser reads forward-only, but we need to:
1. Read all data objects from the array
2. Also read pagination metadata (which may be after the array)

**Solution Approach**:

**For offset/page types**: 
- No metadata needed from response
- Just count records in current page
- If count < page_size, no more pages
- Otherwise, increment offset/page and fetch next

**For cursor/link_header/next_url types**:
- After reading data array, continue parsing to find pagination metadata
- Extract next cursor/URL
- Close current stream
- Open next page if metadata indicates more pages

**Implementation Strategy**:
```java
private Record advance() {
    // Try to read next object from current page
    Record record = readNextObjectFromCurrentPage();
    
    if (record != null) {
        recordsInCurrentPage++;
        return record;
    }
    
    // Current page exhausted, check if more pages exist
    if (hasMorePages()) {
        fetchNextPage();
        return advance(); // Recursive call to read from new page
    }
    
    // No more pages
    return null;
}

private boolean hasMorePages() {
    if (paginationConfig == null) {
        return false; // No pagination configured
    }
    
    switch (paginationConfig.getType()) {
        case "offset":
        case "page":
            // More pages if current page was full
            return recordsInCurrentPage >= paginationConfig.getPageSize();
        
        case "cursor":
        case "next_url":
            // More pages if we extracted next cursor/URL
            return nextPageInfo != null && !nextPageInfo.isEmpty();
        
        case "link_header":
            // More pages if Link header had rel="next"
            return nextPageUrl != null && !nextPageUrl.isEmpty();
        
        default:
            return false;
    }
}
```

### URL Building

**Offset-Based**:
```java
String nextUrl = baseUrl + path + "?" + offsetParam + "=" + currentOffset + "&" + limitParam + "=" + pageSize;
```

**Page-Based**:
```java
String nextUrl = baseUrl + path + "?" + pageParam + "=" + currentPage + "&" + limitParam + "=" + pageSize;
```

**Cursor-Based**:
```java
String nextUrl = baseUrl + path + "?" + cursorParam + "=" + nextCursor + "&" + limitParam + "=" + pageSize;
```

**Link Header**:
```java
String nextUrl = extractLinkHeader(response.headers(), "next");
```

**Next URL**:
```java
String nextUrl = extractFromJson(response, nextUrlPath);
```

## Configuration Examples

### GitHub API (Link Header)
```json
"REPOSITORIES": {
  "$path": ["/user/repos"],
  "$pagination": {
    "type": "link_header",
    "page_size": 100,
    "limit_param": "per_page"
  }
}
```

### ServiceNow API (Offset-Based)
```json
"INCIDENT": {
  "$path": ["/api/now/table/incident"],
  "$data_path": "result",
  "$pagination": {
    "type": "offset",
    "offset_param": "sysparm_offset",
    "limit_param": "sysparm_limit",
    "page_size": 1000
  }
}
```

### Twitter API (Cursor with Next Token)
```json
"TWEETS": {
  "$path": ["/2/tweets/search/recent"],
  "$data_path": "data",
  "$pagination": {
    "type": "cursor",
    "cursor_param": "next_token",
    "next_cursor_path": "meta.next_token",
    "page_size": 100,
    "limit_param": "max_results"
  }
}
```

### Generic API (Page-Based)
```json
"PRODUCTS": {
  "$path": ["/api/products"],
  "$pagination": {
    "type": "page",
    "page_param": "page",
    "limit_param": "per_page",
    "page_size": 50,
    "initial_page": 1
  }
}
```

### API with Next URL in Response
```json
"ORDERS": {
  "$path": ["/api/orders"],
  "$pagination": {
    "type": "next_url",
    "next_url_path": "links.next",
    "page_size": 100
  }
}
```

## Implementation Steps

1. **Create PaginationConfig class** - holds all pagination settings
2. **Update RestTableDefinition** - add paginationConfig field
3. **Update RestApiMappingLoader** - parse $pagination from JSON
4. **Refactor JsonToRecordStream** - support multi-page iteration:
   - Add page iteration state (currentOffset, currentPage, nextCursor, etc.)
   - Implement fetchNextPage()
   - Implement extractNextPageInfo()
   - Implement buildNextPageUrl()
   - Update advance() to fetch next page when needed
   - Update initialize() to handle first page with pagination params
5. **Add pagination examples** - update template.json and create test configs
6. **Test with real APIs** - verify with GitHub, ServiceNow, etc.

## Edge Cases and Error Handling

1. **Empty first page**: If first page returns 0 items, don't fetch more pages
2. **Malformed pagination metadata**: Log warning, stop pagination
3. **HTTP errors on subsequent pages**: Log error, stop pagination gracefully
4. **Infinite loops**: Track page count, stop after reasonable limit (e.g., 10,000 pages)
5. **Partial pages**: Handle pages with fewer items than page_size correctly
6. **Missing pagination metadata**: For cursor/next_url types, treat as last page

## Performance Considerations

1. **Memory**: Only one page in memory at a time (streaming)
2. **Network**: Fetch pages on-demand as records are consumed
3. **Latency**: Each page requires a new HTTP request
4. **Record counting**: Track records per page for offset/page termination

## Testing Strategy

1. **Unit tests**: Test PaginationConfig parsing and URL building
2. **Integration tests**: Test with mock paginated APIs
3. **Real API tests**: Test with actual APIs (GitHub, ServiceNow, etc.)
4. **Edge case tests**: Empty pages, single page, many pages, errors

## Future Enhancements

1. **Rate limiting**: Automatic retry with exponential backoff
2. **Parallel fetching**: Fetch multiple pages concurrently (if API supports)
3. **Progress reporting**: Report pagination progress to user
4. **Page limits**: Allow user to limit total pages fetched
5. **Custom termination**: Allow custom logic for detecting end of pagination
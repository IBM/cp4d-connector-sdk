# Pagination Support - Summary

## What We're Adding

Automatic pagination support for REST APIs, enabling the connector to fetch all pages of data transparently.

## Supported Pagination Types

1. **Offset-Based** - `?offset=0&limit=100`, `?offset=100&limit=100`, etc.
   - Example: ServiceNow API (`sysparm_offset`, `sysparm_limit`)

2. **Page-Based** - `?page=1&per_page=50`, `?page=2&per_page=50`, etc.
   - Example: Many generic REST APIs

3. **Cursor-Based** - `?cursor=abc123&limit=100`
   - API returns next cursor in response: `{"data": [...], "pagination": {"next": "def456"}}`
   - Example: Twitter API (`next_token`)

4. **Link Header** - Uses HTTP Link header (RFC 5988)
   - Response header: `Link: <https://api.example.com/repos?page=2>; rel="next"`
   - Example: GitHub API

5. **Next URL** - Full URL in response body
   - Response: `{"data": [...], "pagination": {"next_url": "https://..."}}`

## Configuration Example

```json
{
  "$connector_name": "Example API",
  "$hostname": "https://api.example.com",
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
    }
  }
}
```

## Key Features

- **Streaming**: Only one page in memory at a time
- **Automatic**: Fetches pages on-demand as records are consumed
- **Flexible**: Supports different parameter names for each API
- **Safe**: Prevents infinite loops with page limit
- **Transparent**: Works seamlessly with existing Arrow streaming

## Implementation Components

1. **PaginationConfig** - New class to hold pagination settings
2. **RestTableDefinition** - Updated to include pagination config
3. **RestApiMappingLoader** - Updated to parse `$pagination` from JSON
4. **JsonToRecordStream** - Refactored to support multi-page fetching

## How It Works

```
User queries table
    ↓
Connector fetches page 1
    ↓
Streams records from page 1
    ↓
Page 1 exhausted → Check if more pages
    ↓
Fetch page 2 (if exists)
    ↓
Continue streaming records
    ↓
Repeat until no more pages
```

## Termination Logic

- **Offset/Page**: Stop when page has fewer records than `page_size`
- **Cursor**: Stop when `next_cursor` is null/empty
- **Link Header**: Stop when no `rel="next"` link
- **Next URL**: Stop when `next_url` is null/empty

## Documentation

- **PAGINATION_DESIGN.md** - Detailed design and architecture
- **PAGINATION_IMPLEMENTATION_PLAN.md** - Step-by-step implementation guide with code examples

## Next Steps

Ready to implement! The plan includes:
1. Create PaginationConfig class
2. Update RestTableDefinition
3. Update RestApiMappingLoader
4. Refactor JsonToRecordStream
5. Create test configurations
6. Test with real APIs

All design decisions have been documented and approved.
# REST Connector Implementation Guide

## Overview

The REST Connector provides a flexible framework for connecting to REST APIs and exposing their data through Apache Arrow Flight. It supports hierarchical data structures, automatic field discovery, custom field mapping, and various authentication mechanisms.

## Table of Contents

1. [Configuration Properties](#configuration-properties)
2. [REST Configuration YAML Syntax](#rest-configuration-yaml-syntax)
3. [Supported Response Types](#supported-response-types)
4. [Field Discovery](#field-discovery)
5. [Field Value Extraction](#field-value-extraction)
6. [Authentication](#authentication)
7. [Pagination Support](#pagination-support)
8. [Known Limitations](#known-limitations)
9. [Examples](#examples)

---

## Configuration Properties

The REST connector requires the following connection properties:

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `url` | Base URL of the REST API | `https://api.github.com` |
| `auth_type` | Authentication type: `basic`, `oauth2`, or `none` | `basic` |
| `rest_config_yaml` | YAML configuration defining entity types and endpoints | See [YAML Syntax](#rest-configuration-yaml-syntax) |

### Authentication Properties

#### Basic Authentication
| Property | Description | Required |
|----------|-------------|----------|
| `username` | Username for basic auth | Yes (if auth_type=basic) |
| `password` | Password for basic auth | Yes (if auth_type=basic) |

#### OAuth 2.0 Authentication
| Property | Description | Required | Default |
|----------|-------------|----------|---------|
| `oauth2_token_url` | Token endpoint URL | Yes | - |
| `oauth2_client_id` | OAuth2 client ID | Yes | - |
| `oauth2_client_secret` | OAuth2 client secret | Yes | - |
| `oauth2_scope` | OAuth2 scope | No | - |
| `oauth2_grant_type` | OAuth2 grant type | No | `client_credentials` |

For detailed OAuth 2.0 configuration, see [oauth2-guide.md](oauth2-guide.md).

### Optional Properties

| Property | Description |
|----------|-------------|
| `ssl_certificate` | SSL certificate for HTTPS connections |

---

## REST Configuration YAML Syntax

The REST configuration YAML defines the structure of your REST API, including entity types, endpoints, and field mappings.

### Basic Structure

```yaml
entityTypes:
  - name: <entity-type-name>
    parentEntity: <parent-entity-name-or-null>
    endpoint: <api-endpoint-path>
    method: <HTTP-method>
    requestQueryParams:
      <param-name>: <param-value>
    headers:
      <header-name>: <header-value>
    pagination:
      type: <pagination-type>
      pageParam: <page-parameter-name>
      sizeParam: <size-parameter-name>
    uniqueIdField: <field-path-for-unique-id>
    labelField: <field-path-for-label>
    fields:
      - name: <field-name>
        jsonPath: <json-path-expression>
```

### Entity Type Properties

#### Core Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `name` | String | Yes | Unique name for the entity type |
| `parentEntity` | String | No | Name of parent entity (null for root entities) |
| `endpoint` | String | Yes | API endpoint path (supports variable substitution) |
| `method` | String | No | HTTP method (default: `GET`) |
| `uniqueIdField` | String | Yes | Field path used as unique identifier (e.g., `id`, `users[].id`, `login`) |
| `labelField` | String | Yes | Field path used as display label (e.g., `name`, `users[].name`, `title`) |

#### Request Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `requestQueryParams` | Map | No | Query parameters to include in requests (supports variable substitution) |
| `headers` | Map | No | Custom headers to include in requests |

#### Pagination Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `pagination.location` | String | No | Where pagination params are sent (default: `query`) |
| `pagination.type` | String | Yes* | Pagination type: `page`, `offset`, or `link_header` |
| `pagination.pageParam` | String | Conditional | Query parameter for page number (required for `page` type) |
| `pagination.sizeParam` | String | Conditional | Query parameter for page size (required for `page` type) |
| `pagination.offsetParam` | String | Conditional | Query parameter for offset (required for `offset` type) |
| `pagination.limitParam` | String | Conditional | Query parameter for limit (required for `offset` type) |
| `pagination.linkHeader` | String | Conditional | Response header name containing next page link (required for `link_header` type) |
| `pagination.supportedMaxLimit` | Integer | No | Maximum page size supported by the API (default: 1000) |

*Required only if pagination is configured

#### Field Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `fields` | Array | No | Field definitions for custom mapping (optional for auto-discovery) |
| `fields[].name` | String | Yes | Field name (prefix for nested fields) |
| `fields[].jsonPath` | String | Yes | JSONPath expression to extract data |

### Variable Substitution in Endpoints

Endpoints support variable substitution using the syntax `${entity-name}` for both path parameters and query parameters.

#### Path Parameter Substitution

```yaml
endpoint: "/orgs/${organization}/repos/${repository}/issues"
```

#### Query Parameter Substitution

```yaml
endpoint: "/api/data"
requestQueryParams:
  org_id: "${organization}"
  repo_id: "${repository}"
```

#### Important Rules

1. **Variable names must match entity names**: The variable name (e.g., `${organization}`) must exactly match the `name` property of a parent entity type.

2. **Parent entity must define uniqueIdField**: The parent entity referenced in the variable must have a `uniqueIdField` configured. The value from this field is used for substitution.

3. **Case sensitivity**: Variable names are case-insensitive during resolution (converted to lowercase).

#### Example

```yaml
entityTypes:
  - name: organization
    parentEntity: null
    endpoint: "/organizations"
    uniqueIdField: "orgs[].login"  # This value will be used for ${organization}
    labelField: "orgs[].login"
    fields:
      - name: "orgs"
        jsonPath: "$[*]"
        
  - name: repository
    parentEntity: organization
    endpoint: "/orgs/${organization}/repos"  # ${organization} resolved from parent's uniqueIdField
    uniqueIdField: "repos[].name"  # This value will be used for ${repository}
    labelField: "repos[].name"
    fields:
      - name: "repos"
        jsonPath: "$[*]"
        
  - name: issues
    parentEntity: repository
    endpoint: "/repos/${organization}/${repository}/issues"  # Both variables resolved
    uniqueIdField: "id"
    labelField: "title"
```

**Resolution Flow:**
1. User selects organization "ibm" (from `uniqueIdField: "orgs[].login"`)
2. `${organization}` is replaced with "ibm" → `/orgs/ibm/repos`
3. User selects repository "my-repo" (from `uniqueIdField: "repos[].name"`)
4. `${organization}` → "ibm", `${repository}` → "my-repo" → `/repos/ibm/my-repo/issues`

---

## Supported Response Types

The REST connector currently supports the following response types:

### JSON (application/json)

- **MIME Type**: `application/json`
- **Detection**: Automatic based on `Content-Type` header
- **Features**:
  - Automatic field discovery
  - Custom field mapping via JSONPath
  - Nested object flattening
  - Array handling (both primitive and object arrays)

### Response Type Detection

The connector automatically detects response types using the `ResponseType` enum:

```java
public enum ResponseType {
    JSON("application/json"),
    UNKNOWN("*/*");
}
```

Detection is case-insensitive and checks if the `Content-Type` header contains the response type identifier.

---

## Field Discovery

Field discovery is the process of identifying available fields in the API response. The connector supports two modes:

### 1. Automatic Field Discovery (Without Field Configuration)

When no `fields` configuration is provided in the YAML, the connector automatically discovers all fields by:

1. **Executing the REST API** for the leaf entity type
2. **Recursively traversing** the JSON response structure
3. **Collecting leaf nodes** (primitive values and primitive arrays)
4. **Generating field paths** using dot notation for nested objects and `[]` suffix for arrays

#### Discovery Algorithm

The `JsonResponseHandler.collectUniqueFieldsRecursive()` method implements the following logic:

```
For each node in JSON:
  - If OBJECT: 
      - Recurse into each field with prefix "parent.field"
      - Add primitive fields to discovered list
  - If ARRAY of primitives: 
      - Add as single field (values will be comma-separated)
  - If ARRAY of objects: 
      - Recurse with prefix "parent[]"
  - If PRIMITIVE: 
      - Add as field with current prefix
```

#### Example: Auto-Discovery

**JSON Response:**
```json
{
  "users": [
    {
      "id": 1,
      "name": "John",
      "address": {
        "city": "NYC",
        "zip": "10001"
      },
      "tags": ["developer", "admin"]
    }
  ]
}
```

**Discovered Fields:**
- `users[].id` (type: NUMBER)
- `users[].name` (type: STRING)
- `users[].address.city` (type: STRING)
- `users[].address.zip` (type: STRING)
- `users[].tags` (type: STRING - comma seperated items)

### 2. Configuration-Based Field Discovery (With Field Configuration)

When `fields` are specified in the YAML configuration, discovery is performed on the extracted nodes:

1. **Execute JSONPath** expression for each configured field
2. **Extract the target node** from the response
3. **Perform auto-discovery** on the extracted node
4. **Prefix field names** with the configured field name

#### Example: Configuration-Based Discovery

**YAML Configuration:**
```yaml
fields:
  - name: "repos"
    jsonPath: "$[*]"
  - name: "id"
    jsonPath: "$[*].id"
```

**JSON Response:**
```json
[
  {
    "id": 1,
    "name": "repo1",
    "owner": {
      "login": "user1"
    }
  }
]
```

**Discovered Fields:**
- `id` (from explicit JSONPath)
- `repos[].id` (from auto-discovery on `$[*]`)
- `repos[].name` (from auto-discovery on `$[*]`)
- `repos[].owner.login` (from auto-discovery on `$[*]`)

### Discovery Process Flow

```
1. Identify leaf entity type from YAML hierarchy
2. Execute REST API call for leaf entity
3. Parse JSON response
4. If fields configured:
     For each field config:
       - Apply JSONPath to extract node
       - Auto-discover fields within that node
   Else:
     - Auto-discover all fields from root
5. Return list of FieldDefinition objects
```

---

## Field Value Extraction

Field value extraction converts JSON responses into tabular data (rows and columns). The connector uses different strategies based on configuration.

### 1. Extraction Without Field Configuration (Auto-Discovery Mode)

**Implementation**: `JsonFieldValueExtractorByAutoDiscovery`

#### Process

1. Uses pre-calculated field definitions from discovery phase
2. For each field path (e.g., `users[].name`):
   - Traverses JSON following the path
   - Handles array notation (`[]`) by iterating elements
   - Collects all values at that path
3. Aligns values into rows (handles arrays of different lengths)
4. Creates one row per array element (Cartesian product for multiple arrays)

#### Example

**Discovered Fields**: `users[].id`, `users[].name`

**JSON Response:**
```json
{
  "users": [
    {"id": 1, "name": "Alice"},
    {"id": 2, "name": "Bob"}
  ]
}
```

**Extracted Rows:**
```
Row 1: {users[].id: 1, users[].name: "Alice"}
Row 2: {users[].id: 2, users[].name: "Bob"}
```

#### Path Traversal Algorithm

The `extractValuesForPath()` method:
1. Splits path by `.` (e.g., `users[].address.city`)
2. For each segment:
   - If ends with `[]`: iterate array elements
   - Otherwise: navigate to child node
3. Collect primitive values at leaf nodes

### 2. Extraction With Field Configuration

**Implementation**: `JsonFieldValueExtractorByFieldConfig`

#### Process

This is more complex and handles nested arrays and multiple JSONPath expressions:

1. **Group fields by array parent path**:
   - Fields with same array parent (e.g., `$.users[*]`) are grouped
   - Fields without array parent are processed independently

2. **For each group**:
   - If no array parent: Extract each field independently
   - If array parent exists:
     - Extract array elements
     - For each element, extract all fields relative to that element
     - Merge fields within each element (Cartesian product)

3. **Merge groups**: Cartesian product between different groups

#### Example: Simple Field Extraction

**YAML Configuration:**
```yaml
fields:
  - name: "user_id"
    jsonPath: "$.users[*].id"
  - name: "user_name"
    jsonPath: "$.users[*].name"
```

**JSON Response:**
```json
{
  "users": [
    {"id": 1, "name": "Alice"},
    {"id": 2, "name": "Bob"}
  ]
}
```

**Extracted Rows:**
```
Row 1: {user_id[].id: 1, user_id[].name: "Alice", user_name[].id: 1, user_name[].name: "Alice"}
Row 2: {user_id[].id: 2, user_id[].name: "Bob", user_name[].id: 2, user_name[].name: "Bob"}
```

#### Example: Nested Arrays

**YAML Configuration:**
```yaml
fields:
  - name: "user"
    jsonPath: "$.users[*]"
```

**JSON Response:**
```json
{
  "users": [
    {
      "id": 1,
      "name": "Alice",
      "addresses": [
        {"city": "NYC", "zip": "10001"},
        {"city": "LA", "zip": "90001"}
      ]
    }
  ]
}
```

**Extracted Rows** (flattened):
```
Row 1: {user[].id: 1, user[].name: "Alice", user[].addresses[].city: "NYC", user[].addresses[].zip: "10001"}
Row 2: {user[].id: 1, user[].name: "Alice", user[].addresses[].city: "LA", user[].addresses[].zip: "90001"}
```

### Handling Primitive Arrays

When an array contains only primitive values (strings, numbers, booleans):
- Values are joined with comma delimiter
- Treated as a single field (no row multiplication)

**Example:**
```json
{"tags": ["java", "rest", "api"]}
```
**Result:** `tags: "java, rest, api"`

### Field Naming Convention

- **Nested objects**: Use dot notation (e.g., `address.city`)
- **Arrays of objects**: Use `[]` suffix (e.g., `users[].name`)
- **Configured fields**: Use configured name as prefix (e.g., `repos[].owner.login`)

---

## Authentication

The REST connector supports multiple authentication mechanisms:

### 1. No Authentication

```yaml
auth_type: none
```

### 2. Basic Authentication

```yaml
auth_type: basic
username: your_username
password: your_password
```

The connector automatically adds the `Authorization: Basic <base64>` header to all requests.

### 3. OAuth 2.0 Authentication

```yaml
auth_type: oauth2
oauth2_token_url: https://auth.example.com/oauth/token
oauth2_client_id: your_client_id
oauth2_client_secret: your_client_secret
oauth2_scope: read:data  # optional
oauth2_grant_type: client_credentials  # default
```

**Features**:
- Automatic token acquisition and refresh
- Token caching to minimize auth requests
- Support for client credentials grant type
- Configurable scope

**Implementation**: `OAuth2TokenManager` handles token lifecycle.

For detailed OAuth 2.0 setup, refer to [oauth2-guide.md](oauth2-guide.md).

---

## Pagination Support

The connector supports three types of pagination for APIs that return data in pages. Pagination is automatically handled by the connector, iterating through all pages and combining results.

### Supported Pagination Types

#### 1. Page-Based Pagination (`page`)

Uses page number and page size parameters. The connector automatically calculates page numbers based on offset and limit values using 0-based indexing.

**Configuration:**
```yaml
pagination:
  type: page
  pageParam: page          # Required: Query parameter name for page number
  sizeParam: size          # Required: Query parameter name for page size
  supportedMaxLimit: 100   # Optional, default: 1000
```

**Properties:**
- `pageParam`: **(Required)** Query parameter name for page number (e.g., `page`, `pageNumber`). Must not be null or empty.
- `sizeParam`: **(Required)** Query parameter name for page size (e.g., `size`, `per_page`, `limit`). Must not be null or empty.
- `supportedMaxLimit`: **(Optional)** Maximum page size supported by the API (default: 1000)

**Page Calculation:**
The connector uses 0-based page indexing by default:
- `pageNumber = offset / limit`
- Example: `offset=0, limit=100` → `page=0`
- Example: `offset=100, limit=100` → `page=1`
- Example: `offset=200, limit=100` → `page=2`

**Example:**
```yaml
endpoint: "/api/users"
pagination:
  type: page
  pageParam: page
  sizeParam: per_page
  supportedMaxLimit: 100
```

**Generated Requests:**
```
GET /api/users?page=0&per_page=100
GET /api/users?page=1&per_page=100
GET /api/users?page=2&per_page=100
...
```

**Note:** The connector continues requesting pages until the API returns an empty response. Ensure your API supports 0-based page indexing, or adjust accordingly if your API uses 1-based indexing.

#### 2. Offset-Based Pagination (`offset`)

Uses offset and limit parameters.

**Configuration:**
```yaml
pagination:
  type: offset
  offsetParam: offset
  limitParam: limit
  supportedMaxLimit: 100  # optional, default: 1000
```

**Properties:**
- `offsetParam`: Query parameter name for offset (e.g., `offset`, `skip`)
- `limitParam`: Query parameter name for limit (e.g., `limit`, `count`, `take`)

**Example:**
```yaml
endpoint: "/api/users"
pagination:
  type: offset
  offsetParam: offset
  limitParam: limit
  supportedMaxLimit: 100
```

**Generated Requests:**
```
GET /api/users?offset=0&limit=100
GET /api/users?offset=100&limit=100
GET /api/users?offset=200&limit=100
...
```

#### 3. Link Header Pagination (`link_header`)

Uses HTTP Link header (RFC 5988) to navigate between pages. Common in GitHub API and other REST APIs.

**Configuration:**
```yaml
pagination:
  type: link_header
  linkHeader: Link
  supportedMaxLimit: 100  # optional, default: 1000
```

**Properties:**
- `linkHeader`: Name of the response header containing pagination links (typically `Link`)

**Example:**
```yaml
endpoint: "/organizations"
pagination:
  type: link_header
  linkHeader: Link
  supportedMaxLimit: 100
```

**Response Header Example:**
```
Link: <https://api.github.com/organizations?since=100>; rel="next",
      <https://api.github.com/organizations?since=500>; rel="last"
```

The connector automatically extracts the `next` link and uses it for subsequent requests.

### Common Properties

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `location` | String | No | `query` | Where pagination params are sent (currently only `query` is supported) |
| `type` | String | Yes | - | Pagination type: `page`, `offset`, or `link_header` |
| `supportedMaxLimit` | Integer | No | 1000 | Maximum page size supported by the API |

### Behavior

- **Automatic iteration**: Connector automatically fetches all pages
- **Stop conditions**: 
  - Response is empty
  - Response contains fewer items than requested page size
  - For `link_header`: No `next` link in response header
- **Result combination**: All pages are combined into a single result set
- **Sub-pagination**: If requested limit exceeds `supportedMaxLimit`, connector automatically splits into multiple requests

### Sub-Pagination Example

If you request 500 records but API only supports 100 per page:

```yaml
pagination:
  type: page
  pageParam: page
  sizeParam: size
  supportedMaxLimit: 100
```

**Connector behavior:**
- Automatically splits into 5 requests of 100 records each
- Combines all results transparently

---

## Known Limitations

### 1. First Page Field Discovery

**Issue**: Field discovery only examines the first page of paginated results.

**Impact**: If subsequent pages contain additional fields not present in the first page, those fields will not be discovered.

**Workaround**: 
- Explicitly configure fields in YAML using `fields` configuration
- Ensure first page is representative of all data

**Tracking**: [Issue #281955](https://github.ibm.com/wdp-gov/tracker/issues/281955)

### 2. Primitive Array Handling

**Issue**: Arrays of primitive values are converted to comma-separated strings.

**Impact**: 
- Cannot query individual array elements
- Array structure is lost in the output
- Data type becomes VARCHAR

**Example:**
```json
{"tags": ["java", "rest", "api"]}
```
**Result:** `tags: "java, rest, api"` (VARCHAR)

### 3. Nested Array Flattening

**Issue**: Nested arrays create multiple rows through Cartesian product.

**Impact**: 
- Can result in data explosion with deeply nested structures
- May produce unexpected row counts

**Example:**
```json
{
  "user": {
    "addresses": [{"city": "NYC"}, {"city": "LA"}],
    "phones": [{"number": "111"}, {"number": "222"}]
  }
}
```
**Result:** 4 rows (2 addresses × 2 phones)

### 4. Response Type Support

**Issue**: Currently only JSON responses are fully supported.

**Impact**: XML, CSV, or other formats require custom implementation.

### 5. HTTP Method Support

**Issue**: While configurable, only `GET` method is thoroughly tested.

**Impact**: POST, PUT, DELETE methods may have limited support.

---

## Examples

### Example 1: Simple Flat Structure (Auto-Discovery)

**YAML Configuration:**
```yaml
entityTypes:
  - name: users
    parentEntity: null
    endpoint: "/api/users"
    method: GET
    requestQueryParams:
      limit: "100"
    uniqueIdField: "id"
    labelField: "name"
    # No fields configuration - auto-discovery enabled
```

**JSON Response:**
```json
[
  {"id": 1, "name": "Alice", "email": "alice@example.com"},
  {"id": 2, "name": "Bob", "email": "bob@example.com"}
]
```

**Discovered Fields:**
- `[].id`
- `[].name`
- `[].email`

### Example 2: Nested Structure with Field Configuration

**YAML Configuration:**
```yaml
entityTypes:
  - name: organization
    parentEntity: null
    endpoint: "/organizations"
    method: GET
    uniqueIdField: "orgs[].login"
    labelField: "orgs[].login"
    fields:
      - name: "orgs"
        jsonPath: "$[*]"
```

**JSON Response:**
```json
[
  {
    "login": "ibm",
    "id": 1234,
    "url": "https://api.github.com/orgs/ibm",
    "repos_url": "https://api.github.com/orgs/ibm/repos"
  }
]
```

**Discovered Fields:**
- `orgs[].login`
- `orgs[].id`
- `orgs[].url`
- `orgs[].repos_url`

### Example 3: Hierarchical Structure (Parent-Child)

**YAML Configuration:**
```yaml
entityTypes:
  - name: organization
    parentEntity: null
    endpoint: "/organizations"
    method: GET
    requestQueryParams:
      per_page: "100"
    uniqueIdField: "orgs[].login"
    labelField: "orgs[].login"
    fields:
      - name: "orgs"
        jsonPath: "$[*]"
        
  - name: repository
    parentEntity: organization
    endpoint: "/orgs/${organization}/repos"
    method: GET
    requestQueryParams:
      per_page: "100"
    uniqueIdField: "repos[].name"
    labelField: "repos[].name"
    fields:
      - name: "repos"
        jsonPath: "$[*]"
```

**Usage Flow:**
1. User browses to root → Lists organizations
2. User selects "ibm" organization → Lists repositories under ibm
3. User selects "repo1" repository → Shows repository data

### Example 4: Complex Nested Arrays

**YAML Configuration:**
```yaml
entityTypes:
  - name: issues
    parentEntity: null
    endpoint: "/api/issues"
    method: GET
    uniqueIdField: "id"
    labelField: "title"
    fields:
      - name: "id"
        jsonPath: "$.results[*].id"
      - name: "title"
        jsonPath: "$.results[*].title"
      - name: "results"
        jsonPath: "$.results[*].*"
```

**JSON Response:**
```json
{
  "results": [
    {
      "id": 1,
      "title": "Bug fix",
      "labels": [
        {"name": "bug", "color": "red"},
        {"name": "urgent", "color": "orange"}
      ]
    }
  ]
}
```

**Discovered Fields:**
- `id`
- `title`
- `results[].id`
- `results[].title`
- `results[].labels[].name`
- `results[].labels[].color`

**Extracted Rows** (flattened):
```
Row 1: {id: 1, title: "Bug fix", results[].labels[].name: "bug", results[].labels[].color: "red"}
Row 2: {id: 1, title: "Bug fix", results[].labels[].name: "urgent", results[].labels[].color: "orange"}
```

### Example 5: Custom Headers and Query Parameters

**YAML Configuration:**
```yaml
entityTypes:
  - name: repository
    parentEntity: organization
    endpoint: "/orgs/${organization}/repos"
    method: GET
    requestQueryParams:
      type: "all"
      sort: "updated"
      per_page: "50"
    headers:
      X-Custom-Header: "custom-value"
      Accept: "application/vnd.github.v3+json"
    pagination:
      type: page
      pageParam: page
      sizeParam: per_page
    uniqueIdField: "id"
    labelField: "name"
    fields:
      - name: "id"
        jsonPath: "$.results[*].id"
      - name: "name"
        jsonPath: "$.results[*].name"
```

### Example 6: Pagination Types

#### Page-Based Pagination
```yaml
entityTypes:
  - name: users
    parentEntity: null
    endpoint: "/api/users"
    method: GET
    pagination:
      type: page
      pageParam: page
      sizeParam: size
      supportedMaxLimit: 100
    uniqueIdField: "id"
    labelField: "name"
```

#### Offset-Based Pagination
```yaml
entityTypes:
  - name: products
    parentEntity: null
    endpoint: "/api/products"
    method: GET
    pagination:
      type: offset
      offsetParam: offset
      limitParam: limit
      supportedMaxLimit: 50
    uniqueIdField: "id"
    labelField: "name"
```

#### Link Header Pagination (GitHub-style)
```yaml
entityTypes:
  - name: organization
    parentEntity: null
    endpoint: "/organizations"
    method: GET
    pagination:
      type: link_header
      linkHeader: Link
      supportedMaxLimit: 100
    uniqueIdField: "orgs[].login"
    labelField: "orgs[].login"
    fields:
      - name: "orgs"
        jsonPath: "$[*]"
```

---

## Best Practices

### 1. Field Configuration

- **Use explicit field configuration** for complex APIs to avoid discovery limitations
- **Configure only necessary fields** to improve performance
- **Use meaningful field names** that reflect the data structure

### 2. Pagination

- **Always configure pagination** for APIs that support it
- **Set appropriate page sizes** (typically 50-100 items)
- **Test with multiple pages** to ensure all data is retrieved

### 3. Authentication

- **Use OAuth 2.0** for production environments when available
- **Store credentials securely** (never in YAML configuration)
- **Test authentication** before deploying

### 4. Performance

- **Limit nesting depth** to avoid Cartesian product explosion
- **Use field configuration** to extract only needed data
- **Monitor API rate limits** and adjust pagination accordingly

### 5. Error Handling

- **Validate YAML configuration** before deployment
- **Test with sample data** to verify field extraction
- **Handle missing fields gracefully** in consuming applications

---

## Troubleshooting

### Issue: Fields Not Discovered

**Possible Causes:**
- Fields only appear in later pages (see [Limitation #1](#1-first-page-field-discovery))
- JSONPath expression is incorrect
- Response structure doesn't match expectations

**Solutions:**
- Add explicit field configuration with correct JSONPath
- Verify API response structure
- Check logs for parsing errors

### Issue: Too Many Rows Generated

**Possible Causes:**
- Nested arrays creating Cartesian product
- Multiple array fields at same level

**Solutions:**
- Restructure field configuration to avoid cross-multiplication
- Extract arrays separately using different entity types
- Use more specific JSONPath expressions

### Issue: Authentication Failures

**Possible Causes:**
- Incorrect credentials
- Token expiration (OAuth 2.0)
- Missing required scopes

**Solutions:**
- Verify credentials are correct
- Check OAuth 2.0 token URL and client configuration
- Review API documentation for required scopes
- Check logs for detailed error messages

---

## Additional Resources

- [OAuth 2.0 Configuration Guide](oauth2-guide.md)
- [JSONPath Syntax Reference](https://goessner.net/articles/JsonPath/)
- [Apache Arrow Flight Documentation](https://arrow.apache.org/docs/format/Flight.html)

---

## Support

For issues, questions, or contributions, please refer to the project's issue tracker and documentation.
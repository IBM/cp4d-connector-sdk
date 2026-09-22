# File Connector Testing Guide

This document explains how to configure, run, and extend the functional test suite
for file connectors in the CP4D Connector SDK.

---

## Table of contents

1. [Architecture overview](#architecture-overview)
2. [Quick start — run LocalFS tests](#quick-start--run-localfs-tests)
3. [Configuration (`tests.properties`)](#configuration-testsproperties)
4. [Writing a new Java test method](#writing-a-new-java-test-method)
5. [Scenario-based tests](#scenario-based-tests)
   - [Scenario file format](#scenario-file-format)
   - [Step types reference](#step-types-reference)
   - [Registering scenarios](#registering-scenarios)
   - [Enabling Write steps](#enabling-write-steps)
6. [Adding a new file connector test class](#adding-a-new-file-connector-test-class)
7. [Running PMD and the full check](#running-pmd-and-the-full-check)
8. [Troubleshooting](#troubleshooting)

---

## Architecture overview

```
FlightTestSuite          (generic Arrow Flight contract tests)
  └── ConnectorTestSuite (SDK action tests: health_check, validate, etc.)
        └── FileTestSuite (file-specific: discover, read, write, scenarios)
              ├── TestLocalFSFlightProducer
              ├── TestAWSS3FlightProducer
              └── TestGitHubFlightProducer
```

| Class | Location | Purpose |
|---|---|---|
| `FileTestSuite` | `test/src/main/java/…/test/file/FileTestSuite.java` | Abstract base — all inherited `@Test` methods live here |
| `TestScenario` | `test/src/main/java/…/test/file/TestScenario.java` | Parses and runs `*.scenario` files |
| `TestConfig` | `test/src/main/java/…/test/TestConfig.java` | Loads `tests.properties`, resolves `#ref#` tokens |
| `TestFlight` | `test/src/main/java/…/test/TestFlight.java` | Starts/stops a local Flight server for a test run |

---

## Quick start — run LocalFS tests

LocalFS needs no credentials. Just run:

```bash
cd sdk-gen
./gradlew :wdp-connect-sdk-gen-java-file-localfs:test
```

To run tests for all file connectors:

```bash
./gradlew :wdp-connect-sdk-gen-java-file-localfs:test \
          :wdp-connect-sdk-gen-java-file-s3:test \
          :wdp-connect-sdk-gen-java-file-github:test
```

Tests for S3 and GitHub are skipped automatically (not failed) when the
required credentials are absent from `tests.properties`.

---

## Configuration (`tests.properties`)

Connection properties and server settings are loaded from **`sdk-gen/tests.properties`**
(gitignored — never commit this file).

### Setup

```bash
cp sdk-gen/tests.properties.example sdk-gen/tests.properties
# then edit sdk-gen/tests.properties with your real values
```

### Property resolution order (highest wins)

1. Environment variable `SDK_TEST_<KEY>` — dots replaced by `_`, uppercased
2. JVM system property `-Dsdk.test.<key>=<value>`
3. `sdk-gen/tests.properties` in the working directory (developer overrides)
4. `tests.properties` bundled on the test classpath (safe defaults shipped with the project)

### `TestConfig` API

| Method | Description |
|---|---|
| `TestConfig.get(key)` | Returns the resolved string value, or `null` |
| `TestConfig.get(key, defaultValue)` | Returns the resolved value, or `defaultValue` |
| `TestConfig.getBoolean(key, defaultValue)` | Parses the value as `boolean` |
| `TestConfig.getBoolean(key)` | Same as above with `false` as default |
| `TestConfig.getPort(key)` | Parses integer, or picks a random free port if absent |

### `#ref#` interpolation

A property value can reference another key:

```properties
file_s3.s3.test_base=test-data/
file_s3.s3.test_csv_key=#file_s3.s3.test_base#cars.csv
# → resolves to: test-data/cars.csv
```

### Encrypting secrets

Wrap secrets with `encrypted.{{ }}` to avoid storing plaintext:

```properties
file_s3.s3.secret_access_key=encrypted.{{<base64-ciphertext>}}
```

Use `EncryptUtil -e <plaintext>` to produce the ciphertext, and set the
`decrypt.key` property so the suite can decrypt it at runtime.

### Key namespaces

| Connector | Key prefix |
|---|---|
| LocalFS | `file_localfs.*` |
| Amazon S3 | `file_s3.*` |
| GitHub | `file_github.*` |

---

## Writing a new Java test method

Add the method directly to the connector's test class (e.g. `TestLocalFSFlightProducer`).
The class extends `FileTestSuite`, so the full helper API is available.

```java
@Test
public void testReadJsonFile() throws Exception
{
    final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
    props.put("file_name", "/data/sample.json");

    final FlightInfo info = getFlightInfo(props);       // getInfo RPC
    final Table<Integer, Integer, Object> data          // getStream RPC
            = getTableData(info);

    assertFalse("Expected at least one row", data.isEmpty());
    assertEquals("Expected 3 rows", 3, data.rowKeySet().size());
    assertEquals("First cell", "value1", data.get(0, 0).toString());
}
```

### Available helpers in `FileTestSuite`

| Helper | Description |
|---|---|
| `getFlightInfo(props)` | Calls `getInfo` and returns `FlightInfo` |
| `getTableData(info)` | Reads all stream batches into a `Table<row, col, Object>` |
| `listAssets(path)` | Calls `listFlights` and returns `List<CustomFlightAssetDescriptor>` |
| `listAssets(path, offset, limit)` | Same with paging |
| `getClient()` | The raw `FlightClient` |
| `getDatasourceTypeName()` | The connector's datasource type string |
| `createConnectionProperties()` | The connection properties used by all tests |

### Skipping when credentials are absent

Use JUnit's `assumeTrue` / `assumeNotNull` instead of throwing an error:

```java
@Test
public void testPrivateRepo() throws Exception
{
    assumeNotNull("Set file_github.github.access_token to run this test",
            TestConfig.get("file_github.github.access_token"));
    // … test body
}
```

---

## Scenario-based tests

Scenario files let you express a sequence of connector interactions in a
plain-text format, without writing Java. They are especially useful for
quick regression scenarios and for onboarding contributors who are not
familiar with the Arrow Flight API.

Scenarios live in the connector's test resources directory:

```
file/<connector>/src/test/resources/scenarios/<connector>/
    readwrite_csv.scenario
    discover_root.scenario
    …
```

### Scenario file format

```properties
# Lines starting with # are comments and are ignored.
# Each [Step] block defines one operation.

[Step]
Type=Write
Interaction.file_name=/output/test.csv
Interaction.first_line_header=true
# Schema: semicolon-separated list of  name|type  pairs
# Supported types: varchar, integer, bigint, smallint, tinyint,
#                  boolean, real, float, double, decimal,
#                  date, timestamp, varbinary
Schema=id|integer; label|varchar; active|boolean
# Rows: pipe-separated values in schema order; use <NULL> for SQL NULL
Row.0=1|Alice|true
Row.1=2|Bob|false
Row.2=3|<NULL>|true

[Step]
Type=Read
Interaction.file_name=/output/test.csv
# Optional assertions:
ExpectedColumns=id|label|active       # pipe-separated column names in order
ExpectedRowCount=3                    # exact row count
ExpectedCell.0.0=1                    # cell[row][col] = value (0-based)
ExpectedCell.0.1=Alice
ExpectedCell.2.1=<NULL>               # <NULL> asserts the cell is SQL null

[Step]
Type=Discover
Path=/output
ExpectedNotEmpty=true                 # at least one descriptor returned
# ExpectedCount=5                     # exact descriptor count
# ExpectedContains=/output/test.csv   # a descriptor with this id/name/path exists

[Step]
Type=FileCompare
Interaction.file_name=/output/test.csv
ReferenceFile=scenarios/reference/test.ref   # classpath path to the reference bytes
ReferenceEncoding=UTF-8                      # default; omit for binary comparison
```

#### Key syntax rules

- Every step starts with `[Step]` (case-insensitive).
- Keys and values are trimmed of leading/trailing whitespace.
- `Interaction.*` keys: the `Interaction.` prefix is stripped and the
  remaining key/value is passed as an interaction property.
- Multiple rows use `Row.0=`, `Row.1=`, … A single-row step may use bare `Row=`.
- Long values can span multiple lines with a trailing `\`:
  ```
  Schema=col_a|varchar; \
         col_b|integer
  ```

### Step types reference

#### `Type=Write`

Writes rows to the connector. Requires:

| Key | Required | Description |
|---|---|---|
| `Interaction.*` | Yes | Interaction properties (at minimum `file_name`) |
| `Schema` | Yes | Semicolon-separated `name\|type` column definitions |
| `Row.N` | Yes (≥1) | Pipe-separated row values; `<NULL>` for SQL null |

Write steps are silently skipped when no write hook is registered (see
[Enabling Write steps](#enabling-write-steps)).

#### `Type=Read`

Reads from the connector and verifies the result. Requires:

| Key | Required | Description |
|---|---|---|
| `Interaction.*` | Yes | Interaction properties |
| `ExpectedColumns` | No | Pipe-separated column names in schema order |
| `ExpectedRowCount` | No | Exact number of rows; if absent, asserts ≥1 row |
| `ExpectedCell.R.C` | No | Asserts `data[R][C].toString() == value` (0-based) |

#### `Type=Discover`

Lists assets at a path. Requires:

| Key | Required | Description |
|---|---|---|
| `Path` | Yes | The discovery path (e.g. `/`, `/master`) |
| `ExpectedNotEmpty` | No | `true` asserts at least one descriptor is returned |
| `ExpectedCount` | No | Asserts exactly N descriptors are returned |
| `ExpectedContains` | No | Asserts a descriptor whose `id`, `name`, or `path` equals this value exists |

All returned descriptors are automatically validated for the mandatory
`id`, `name`, and `assetType` fields.

#### `Type=FileCompare`

Reads a file as raw bytes and compares to a reference file on the classpath.

| Key | Required | Description |
|---|---|---|
| `Interaction.*` | Yes | Interaction properties (at minimum `file_name`) |
| `ReferenceFile` | Yes | Classpath path to the `.ref` file |
| `ReferenceEncoding` | No | Charset name (default `UTF-8`); CRLF is normalised before comparison |

Place reference files under `src/test/resources/scenarios/reference/`:

```
file/<connector>/src/test/resources/
    scenarios/
        <connector>/
            my_scenario.scenario
        reference/
            expected_output.ref
```

### Registering scenarios

Override `getScenarioPaths()` in your connector test class to tell the suite
which files to run. The inherited `testScenarios()` `@Test` method picks them
up automatically.

```java
@Override
protected List<String> getScenarioPaths()
{
    return Arrays.asList(
            "scenarios/localfs/readwrite_csv.scenario",
            "scenarios/localfs/discover_root.scenario");
}
```

If `getScenarioPaths()` returns an empty list (the default), `testScenarios`
is skipped — not failed.

### Enabling Write steps

The `TestScenario` runner lives in the `test` subproject, which deliberately
does **not** depend on `ArrowConversions`. Write support is injected via the
`TestScenario.WriteHook` functional interface.

Override `createScenarioWriteHook()` in your connector test class (which
**does** have the full classpath):

```java
@Override
protected TestScenario.WriteHook createScenarioWriteHook()
{
    return (flightClient, modelMapper, dsTypeName, connProps, iprops,
            schemaSpec, rows, nullToken) -> {
        // Build CustomFlightAssetDescriptor from schemaSpec + connProps + iprops,
        // create an Arrow VectorSchemaRoot, populate it from rows/nullToken,
        // and call flightClient.startPut(…).
        // See TestLocalFSFlightProducer.createScenarioWriteHook() for a full example.
    };
}
```

`FileTestSuite.scenario()` calls `createScenarioWriteHook()` automatically
and registers the returned hook, so no manual `.withWriteHook(…)` call is
needed in `getScenarioPaths()` scenarios.

For one-off scenarios in a regular `@Test` method, you can chain manually:

```java
scenario().withWriteHook(myHook).run("scenarios/localfs/readwrite_csv.scenario");
```

---

## Adding a new file connector test class

1. **Create the test class** in `file/<connector>/src/test/java/…`:

   ```java
   public class TestMyConnectorFlightProducer extends FileTestSuite
   {
       private static final Logger LOGGER = getLogger(TestMyConnectorFlightProducer.class);
       private static final String DATASOURCE_TYPE_NAME = MyDatasourceType.DATASOURCE_TYPE_NAME;

       // --- Static fields first, then inner config class at the bottom ---
       private static TestFlight testFlight;
       private static FlightClient client;
       private static final MyConfig CONFIG = new MyConfig();

       @BeforeClass
       public static void setUpOnce() throws Exception
       {
           testFlight = TestFlight.createLocal(CONFIG.port, CONFIG.useSSL,
                   new MyFlightProducer(), null);
           client = testFlight.getClient();
       }

       @AfterClass
       public static void tearDownOnce() throws IOException
       {
           testFlight.close();
       }

       // --- FileTestSuite abstract hooks ---

       @Override public FlightClient getClient()           { return client; }
       @Override public String getDatasourceTypeName()     { return DATASOURCE_TYPE_NAME; }
       @Override public ConnectionProperties createConnectionProperties() { … }
       @Override public String getRootPath()               { return "/"; }
       @Override public String getContainerPath()          { return "/my-folder"; }
       @Override public DiscoveredAssetInteractionProperties createReadInteractionProperties() { … }
       @Override public String getKnownFilePath()          { return "/my-folder/known.csv"; }
       @Override public String getKnownFileName()          { return "known.csv"; }

       // --- Scenario support (optional) ---

       @Override
       protected List<String> getScenarioPaths()
       {
           return Arrays.asList("scenarios/myconnector/discover_root.scenario");
       }

       // --- Config inner class (must be LAST in the file — PMD requirement) ---
       private static final class MyConfig
       {
           final boolean createLocal = TestConfig.getBoolean("my.flight.createLocal", true);
           final boolean useSSL      = TestConfig.getBoolean("my.flight.ssl", true);
           final int     port        = TestConfig.getPort("my.flight.port");
           // … more keys
       }
   }
   ```

2. **Create at least one scenario file** under:
   ```
   file/<connector>/src/test/resources/scenarios/<connector>/discover_root.scenario
   ```

3. **Add the connector to `tests.properties.example`** with all required keys
   documented and commented out.

4. **Register the Gradle subproject** in `sdk-gen/settings.gradle` if the
   connector is a new module.

---

## Running PMD and the full check

```bash
# Run PMD only
./gradlew :wdp-connect-sdk-gen-java-file-localfs:pmdTest \
          :wdp-connect-sdk-gen-java-file-s3:pmdTest \
          :wdp-connect-sdk-gen-java-file-github:pmdTest

# Run tests + PMD for a single connector
./gradlew :wdp-connect-sdk-gen-java-file-localfs:check

# Run everything
./gradlew check
```

### PMD conventions to follow

| Rule | How to satisfy it |
|---|---|
| `FieldDeclarationsShouldBeAtStartOfClass` | Declare all fields **before** any inner class. Move inner classes to the **bottom** of the outer class. |
| `UseLocaleWithCaseConversions` | Use `toLowerCase(Locale.ENGLISH)` / `toUpperCase(Locale.ENGLISH)` |
| `IfStmtsMustUseBraces` | Always wrap `if`/`else` bodies in `{ }` |
| `UnnecessaryFullyQualifiedName` | Add an `import` and use the simple name |
| `UseStringBufferForStringAppends` | Use `StringBuilder` instead of `+` in loops |

---

## Troubleshooting

### Tests are skipped, not run

S3 and GitHub tests skip when the required credential key is `null`.
Check that `sdk-gen/tests.properties` exists and contains the key
printed in the skip message (e.g. `file_s3.s3.access_key_id`).

### `testScenarios` fails: "step N failed: …"

The failure message includes the scenario file path and step number.
Open the scenario file, find that step, and check:

- For `Read` steps after a `Write` — confirm `createScenarioWriteHook()` is
  overridden in the test class (a missing hook silently skips the write, so the
  file will not exist when the read runs).
- For `Discover` steps — confirm the path exists and the connector returns at
  least one descriptor.
- For `ExpectedCell` failures — values are compared as strings via `.toString()`;
  ensure the expected string exactly matches what the connector serialises.

### `Cannot find symbol: variable Collections` / `StringUtils`

These indicate a missing import or a dependency that was not added.
Prefer plain Java APIs over external utilities:

| Utility | Plain Java replacement |
|---|---|
| `StringUtils.isBlank(s)` | `s == null \|\| s.chars().allMatch(Character::isWhitespace)` |
| `StringUtils.isEmpty(s)` | `s == null \|\| s.isEmpty()` |
| `Objects.toString(o, "")` | Available in `java.util.Objects` (no import needed beyond `java.util`) |

### Flight server fails to start on a fixed port

Leave the `*.flight.port` property blank. `TestConfig.getPort(key)` will pick
a random free port automatically.

### Encrypted property is not decrypted

Ensure `decrypt.key` is set in `tests.properties` (or via the `SDK_TEST_DECRYPT_KEY`
environment variable) and that the ciphertext was produced with the same key.

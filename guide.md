# Introduction
With IBM® Cloud Pak for Data, you can connect to the different data sources in your enterprise so that everyone can find the data that they need quickly and easily. Cloud Pak for Data Services share the common layer of connections and connectors. There are several [data source types supported out of the box](https://www.ibm.com/docs/en/cloud-paks/cp-data/4.5.x?topic=data-supported-sources). In addition for data source types not supported out-of-the-box there is a [Generic JDBC connection](https://www.ibm.com/docs/en/cloud-paks/cp-data/4.5.x?topic=2-importing-jdbc-drivers) which can be used with an arbitrary JDBC driver.
Yet another option to extend supported data source types is to develop custom connectors with this SDK. The benefits of such a custom connector are:
- no JDBC driver is required,
- custom performance optimization can be implemented,
- custom metadata discovery can be implemented,
- the connector is automatically built to talk via gRPC and Apache Flight/Arrow,
- the connector can be used with IBM Cloud services,
- the connector can be co-located with the data source,
- the connector suits microservices architecture,
- it can be scaled up/down with replicas.

Custom connectors can provide three types of interactions with data sources:
- writing to a data source,
- reading from a data source,
- discovering metadata of a data source.

## Definitions
### Data source
A data source is structured or unstructured data storage that provides the customer access to data assets (database, data warehouse, object storage, application). This type of data can be read, previewed, or written back by connectors.
### Connection
[comment]: <TODO MB> ()
A connection is a set of connection properties, including data source credentials, endpoint details (port, host, etc.), and certificates. A connection can be created, updated, and deleted either in the UI or by REST API of IBM Cloud Pak for Data.
### Connector
[comment]: <TODO MB> ()
A connector is a library that can interact (read, write, stream, etc.) with a given data source using a connection definition.

### Flight
[comment]: <TODO MB> ()
A Flight Server is a data connection service that enables applications/services to interact with various data sources without calling the APIs for the data sources. By default, the Flight Server is only available to the IBM® Cloud Pak for Data instance where the Flight Server is running. However, a Red Hat® OpenShift® Container Platform project administrator can create an external route to the Flight Server to enable other applications to interact with it.

Flight defines a gPRC service API in the Apache Arrow project designed for high performance transfer of Arrow data to and from a data source.

https://arrow.apache.org/docs/format/Flight.html

## Flight-based connector service
A Flight-based connector service must implement the methods of the Arrow Flight RPC.  The connector model for requests and responses of those methods defines the message protocol that the connector must speak in order for the IBM Watson Data Platform Connections and Flight services to be able to communicate with the connector.  The model representation is JSON in UTF-8 encoding.  The SDK provides implementation examples that demonstrate the use of those models.  One example implements a connector for Apache Derby.

### ListActions
The `ListActions` method returns a list of the action types that the connector service supports. The method must return at least the following action types:

- health_check
- list_datasource_types
- put_setup
- put_wrapup
- test
- validate

A connector can also choose to define its own action types in addition to the ones required.

### DoAction

The `DoAction` method must support the actions returned by `ListActions`.  The request model is received in the body of the `Action` object.  In the SDK, that model is described by the `CustomFlightActionRequest` class.  The response model is returned in the body of the `Result` object.  In the SDK, that model is described by the `CustomFlightActionResponse` class.

#### health_check

The `health_check` action must return a model that describes the health of the service.  In the response model, only the `response_properties` attribute needs to be returned by the `health_check` action.  Here is a sample `Result` body returned by the `health_check` action in the Derby example:

```
{
    "response_properties": {
        "version": "unknown",
        "status": "OK"
    }
}
```


#### list_datasource_types

The `list_datasource_types` action must return a model that describes the data source types supported by the service.  In the response model, only the `datasource_types` attribute needs to be returned by the `list_datasource_types` action.  Here is a sample `Result` body returned by the `list_datasource_types` action in the Derby example:

```
{
    "datasource_types": {
        "datasource_types": [
            {
                "name": "custom_derby",
                "label": "Apache Derby (custom)",
                "description": "A custom connection type for Apache Derby",
                "allowed_as_source": true,
                "allowed_as_target": true,
                "properties": {
                    "connection": [
                        {
                            "name": "host",
                            "type": "string",
                            "label": "Host",
                            "description": "Name of server",
                            "required": true,
                            "group": "domain"
                        },
                        {
                            "name": "port",
                            "type": "integer",
                            "label": "Port",
                            "description": "Port number",
                            "required": true,
                            "group": "domain"
                        },
                        {
                            "name": "database",
                            "type": "string",
                            "label": "Database",
                            "description": "Name of database",
                            "required": true
                        },
                        {
                            "name": "username",
                            "type": "string",
                            "label": "User name",
                            "description": "User name",
                            "required": true,
                            "group": "credentials"
                        },
                        {
                            "name": "password",
                            "type": "string",
                            "label": "Password",
                            "description": "Password",
                            "required": true,
                            "masked": true,
                            "group": "credentials"
                        },
                        {
                            "name": "create_database",
                            "type": "boolean",
                            "label": "Create database",
                            "description": "Whether database should be created",
                            "required": false,
                            "default_value": "false"
                        }
                    ],
                    "source": [
                        {
                            "name": "schema_name",
                            "type": "string",
                            "label": "Schema name",
                            "description": "The name of the schema that contains the table to read from",
                            "required": false
                        },
                        {
                            "name": "table_name",
                            "type": "string",
                            "label": "Table name",
                            "description": "The name of the table to read from",
                            "required": true
                        },
                        {
                            "name": "row_limit",
                            "type": "integer",
                            "label": "Row limit",
                            "description": "The maximum number of rows to return",
                            "required": false
                        }
                    ],
                    "target": [
                        {
                            "name": "schema_name",
                            "type": "string",
                            "label": "Schema name",
                            "description": "The name of the schema that contains the table to write to",
                            "required": false
                        },
                        {
                            "name": "table_name",
                            "type": "string",
                            "label": "Table name",
                            "description": "The name of the table to write to",
                            "required": true
                        }
                    ],
                    "filter": [
                        {
                            "name": "include_system",
                            "type": "boolean",
                            "label": "Include system",
                            "description": "Whether to include system objects",
                            "required": false
                        },
                        {
                            "name": "include_table",
                            "type": "boolean",
                            "label": "Include tables",
                            "description": "Whether to include tables",
                            "required": false
                        },
                        {
                            "name": "include_view",
                            "type": "boolean",
                            "label": "Include views",
                            "description": "Whether to include views",
                            "required": false
                        },
                        {
                            "name": "name_pattern",
                            "type": "string",
                            "label": "Name pattern",
                            "description": "A name pattern to filter on",
                            "required": false
                        },
                        {
                            "name": "primary_key",
                            "type": "boolean",
                            "label": "Include primary key list",
                            "description": "Whether to include a list of primary keys",
                            "required": false
                        },
                        {
                            "name": "schema_name_pattern",
                            "type": "string",
                            "label": "Schema name pattern",
                            "description": "A name pattern for schema filtering",
                            "required": false
                        }
                    ]
                },
                "status": "active",
                "tags": []
            }
        ]
    }
}
```

In the connection UI, most connection properties will be displayed in the "Connection details" section. However special rules apply to certain property groups:

- credentials - Any connection property that represents a credential such as a user name, password, access key, secret key, or API key must have its `group` attribute set to "credentials". Any sensitive credential property must have its `masked` attribute set to "true" so that its value is encrypted when stored.  Credential properties are displayed in the "Credentials" section of the connection UI and when "Personal" credentials is selected, a separate set of credentials is stored for each user of the connection.

- domain - Any connection property that represents a network location such as a host name, port number, or URL must have its `group` attribute set to "domain". If a connection collaborator changes the value of a domain property, any personal credentials currently stored with the connection are invalidated. This is a security mechanism to prevent a malicious user from redirecting connection traffic to a spoofed host in an attempt to steal the personal credentials of other users.

- ssl - Any connection property related to SSL or TLS network protocol should have its `group` attribute set to "ssl" and will be displayed in the "Certificates" section of the connection UI.

- other - Any other connection property that has its `group` attribute set to "other" will be displayed in the "Other properties" section of the connection UI instead of the "Connection details" section.

#### put_setup

The `put_setup` action must perform any setup operations required before doing a partitioned write such as creating the target table.  The request model will contain the `asset` attribute which is described by the `CustomFlightAssetDescriptor` class in the SDK.  It will contain `datasource_type_name`, `connection_properties`, `interaction_properties`, `partition_count` and `fields` attributes.  For example:

```
{
    "asset": {
        "datasource_type_name": "custom_derby",
        "connection_properties": {
            "database": "testdb",
            "password": "2a439e36-0445-45af-87b2-e723d1fde9f5",
            "port": 59582,
            "host": "127.0.0.1",
            "create_database": true,
            "username": "testuser"
        },
        "interaction_properties": {
            "schema_name": "SCHEMA1",
            "table_name": "T2"
        },
        "partition_count": 4,
        "fields": [
            {
                "name": "C1",
                "type": "bigint",
                "length": 19,
                "scale": 0,
                "nullable": true,
                "signed": true
            },
            {
                "name": "C2",
                "type": "decimal",
                "length": 31,
                "scale": 2,
                "nullable": true,
                "signed": true
            }
        ]
    }
}
```

The response model must also set the `asset` attribute.  It can suggest an alternative value for `partition_count` if the data source does not support the requested number of partitions.  For example:

```
{
    "asset": {
        "datasource_type_name": "custom_derby",
        "connection_properties": {
            "database": "testdb",
            "password": "2a439e36-0445-45af-87b2-e723d1fde9f5",
            "port": 59582,
            "host": "127.0.0.1",
            "create_database": true,
            "username": "testuser"
        },
        "interaction_properties": {
            "schema_name": "SCHEMA1",
            "table_name": "T2"
        },
        "partition_count": 1,
        "fields": [
            {
                "name": "C1",
                "type": "bigint",
                "length": 19,
                "scale": 0,
                "nullable": true,
                "signed": true
            },
            {
                "name": "C2",
                "type": "decimal",
                "length": 31,
                "scale": 2,
                "nullable": true,
                "signed": true
            }
        ]
    }
}
```

#### put_wrapup

The `put_wrapup` action must perform any wrap-up operations required after doing a partitioned write such as updating table statistics.  The request model will contain the `asset` attribute which is described by the `CustomFlightAssetDescriptor` class in the SDK.  It will contain `datasource_type_name`, `connection_properties`, `interaction_properties`, `partition_count` and `fields` attributes.  For example:

```
{
    "asset": {
        "datasource_type_name": "custom_derby",
        "connection_properties": {
            "database": "testdb",
            "password": "2a439e36-0445-45af-87b2-e723d1fde9f5",
            "port": 59582,
            "host": "127.0.0.1",
            "create_database": true,
            "username": "testuser"
        },
        "interaction_properties": {
            "schema_name": "SCHEMA1",
            "table_name": "T2"
        },
        "partition_count": 4,
        "fields": [
            {
                "name": "C1",
                "type": "bigint",
                "length": 19,
                "scale": 0,
                "nullable": true,
                "signed": true
            },
            {
                "name": "C2",
                "type": "decimal",
                "length": 31,
                "scale": 2,
                "nullable": true,
                "signed": true
            }
        ]
    }
}
```

No attributes need to be set in the response model.

#### test

The `test` action must test for a successful connection to the data source and return an error if it fails.  The request model will contain `datasource_type_name` and `connection_properties` attributes.  No attributes need to be set in the response model.  An empty response model verifies that connection was successful.  Here is a sample `Action` body supplied to the `test` action in the Derby example:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "18e235ba-a42a-4b23-a959-c91f5ab46503",
        "port": 61719,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    }
}
```

And here is a sample `Result` body indicating successful connection:

`{}`

#### validate

The `validate` action must validate the connection properties for a data source and return an error if it fails.  The request model will contain `datasource_type_name` and `connection_properties` attributes.  No attributes need to be set in the response model.  An empty response model verifies that validation was successful.  The IBM Watson Data Platform Connections service will do basic validation to ensure that connection properties adhere to the data source type definition returned by the `list_datasource_types` action, but the `validate` action should perform any additional validation that may be required.  For example if two connection properties are mutually exclusive, that logic should be enforced by this action.  Here is a sample `Action` body supplied to the `validate` action in the Derby example:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "18e235ba-a42a-4b23-a959-c91f5ab46503",
        "port": 61719,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    }
}
```

And here is a sample `Result` body indicating successful validation:

`{}`

#### Custom actions

A custom action can receive a JSON object in the `request_properties` attribute of the request model, and it can return a JSON object in the `response_properties` of the response model.  Custom actions can be invoked from the IBM Watson Data Platform Connections service via the following REST endpoint:

`PUT /v2/connections/{connection_id}/actions/{action_name}`

### ListFlights

The `ListFlights` method returns a list of assets discovered on the data source given certain criteria.  The request model is received in the expression of the `Criteria` object.  In the SDK, that model is described by the `CustomFlightAssetsCriteria` class.  The response model is returned in the `FlightDescriptor` command of the `FlightInfo` object.  In the SDK, that model is described by the `CustomFlightAssetDescriptor` class.

The request model will contain `datasource_type_name`, `connection_properties`, and `path` attributes.  The `path` attribute indicates what portion of the data source should be searched.  A path of "/" indicates that assets from the root of the data source should be returned.  For a database connector, typically it means returning a list of schemas in the database.  For a file-based data source, it could mean returning a list of folders and files at the root of the file system.  For an object storage data source, it could mean returning a list of buckets or containers.  Here is a sample `Criteria` expression from the Derby example that returns the schemas in the database:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "78a0c6cb-1f52-49fc-9f8b-16b94aaa4a53",
        "port": 55236,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "path": "/"
}
```

For each discovered asset, the response model in the `FlightDescriptor` command must contain `id`, `asset_type`, `name`, and `path` attributes.  The `asset_type` must contain `type`, `dataset`, and `dataset_container` attributes that indicate the type of asset and whether the asset is a data asset or a container of data assets.  For asset containers, the `Schema` of the `FlightInfo` should contain no fields.  The `path` attribute in the response model should indicate the path that the caller would use if they want to perform further discovery of that asset.  Here is a sample `FlightDescriptor` command returned for a discovered schema in the Derby example:

```
{
    "id": "SCHEMA1",
    "asset_type": {
        "type": "schema",
        "dataset": false,
        "dataset_container": true
    },
    "name": "SCHEMA1",
    "path": "/SCHEMA1"
}
```

If the caller wanted to perform further discovery of the discovered asset, they would call `ListFlights` again and supply the path of the asset in the request model.  Here is a sample `Criteria` expression from the Derby example that discovers the tables in the given schema:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "78a0c6cb-1f52-49fc-9f8b-16b94aaa4a53",
        "port": 55236,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "path": "/SCHEMA1"
}
```

And here is a sample `FlightDescriptor` command returned for a discovered table in that schema of the Derby example:

```
{
    "id": "T1",
    "asset_type": {
        "type": "table",
        "dataset": true,
        "dataset_container": false
    },
    "name": "T1",
    "path": "/SCHEMA1/T1"
}
```

Optionally, additional filter properties can be specified in the `filters` attribute of the request.  The data source type returned by the `list_datasource_types` action defines what `filter` properties the connector supports.  Here is a sample `Criteria` expression from the Derby example that excludes system schemas from the discovered schemas:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "25bd9799-54cb-45d2-92fd-e5db07d5e736",
        "port": 56790,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "path": "/",
    "filters": {
        "include_system": "false"
    }
}
```

To discover the columns of a data asset, you would call `ListFlights` again and specify the path of that data asset.  For example:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "c0df580d-8437-46dd-ab9d-b2cd30af470f",
        "port": 56898,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "path": "/SCHEMA1/T1",
    "context": "source"
}
```

The response model for the data asset must contain an `interaction_properties` attribute that contains the `source` or `target` interaction property values required to read from or write to that data asset.  For example:

```
{
    "id": "T1",
    "asset_type": {
        "type": "table",
        "dataset": true,
        "dataset_container": false
    },
    "name": "T1",
    "path": "/SCHEMA1/T1",
    "interaction_properties": {
        "schema_name": "SCHEMA1",
        "table_name": "T1"
    }
}
```

The `Schema` in a returned `FlightInfo` for an asset container has no `Field` objects.  But when performing discovery on a data asset, the returned `Schema` will contain a `Field` object for each column of the data asset.  Each `Field` object will include metadata.  In the SDK, the metadata model is described by the `CustomFlightAssetField` class.  For example:

```
{
    "name": "C1",
    "type": "bigint",
    "length": 19,
    "scale": 0,
    "nullable": true,
    "signed": true
}
```

Although the `CustomFlightAssetField` model in the SDK contains a `name` attribute, it is not necessary to include that attribute in the `Field` metadata model, because the `Field` object itself already has a name attribute.

### GetFlightInfo

The `GetFlightInfo` method returns information about a data asset to be consumed.  The request model is received in the `FlightDescriptor` command and is described by the `CustomFlightAssetDescriptor` class in the SDK.  The request model must contain `datasource_type_name`, `connection_properties`, and `interaction_properties` attributes.  Optionally it can also contain a `batch_size` attribute to control the number of rows in each Arrow batch and a `partition_count` attribute to request that the data be partitioned.  For example:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "10f713c7-4214-46c5-b1bb-7e245c8a52bb",
        "port": 51614,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "interaction_properties": {
        "schema_name": "SCHEMA1",
        "table_name": "T1"
    },
    "batch_size": 1000,
    "partition_count": 4
}
```

The returned `FlightInfo` must contain a `FlightEndpoint` for each partition.  Each `FlightEndpoint` must contain a `Ticket` that the caller can submit to the `DoGet` method to read the data for that partition.

No specific model is defined for the contents of a `Ticket`.  The connector implementer is free to put whatever information the connector needs to read the partition for `DoGet`.

### DoGet

The `DoGet` method accepts a Ticket for a partition of data and returns a stream of Arrow batches.

### DoPut

The `DoPut` method accepts a stream of Arrow batches to write to a data asset.  The request model is received in the `FlightDescriptor` command of the `FlightStream` and is described by the `CustomFlightAssetDescriptor` class in the SDK.  The request model must contain `datasource_type_name`, `connection_properties`, and `interaction_properties` attributes.  For partitioned writes, it must also contain `partition_count` and `partition_index` attributes.  For example:

```
{
    "datasource_type_name": "custom_derby",
    "connection_properties": {
        "database": "testdb",
        "password": "e287cf1a-9d15-48ca-8d41-198f1a5ba945",
        "port": 58115,
        "host": "127.0.0.1",
        "create_database": true,
        "username": "testuser"
    },
    "interaction_properties": {
        "schema_name": "SCHEMA1",
        "table_name": "T2"
    },
    "partition_count": 4,
    "partition_index": 0
}
```

Each `Field` of the `Schema` in the `FlightStream` should also contain metadata which is described by the `CustomFlightAssetField` model in the SDK.  For example:

```
{
    "type": "varchar",
    "length": 20,
    "scale": 0,
    "nullable": true,
    "signed": false
}
```

The metadata is necessary because the `ArrowType` on the `Field` may not have enough detail to determine the proper target data type.  For example, the field types "char" and "varchar" both use `ArrowType.Utf8` vectors.

If the stream is partitioned, the `put_setup` action will be called first.  That action should perform any setup operation required before receiving the streams such as creating the target table.  If `partition_count` was not specified or has a value of 1, then setup, writing, and wrap-up actions can all be performed by `DoPut`.  For partitioned streams, after calls to the `DoPut` method have been completed for each partition, the `put_wrapup` action will be called to perform any wrap-up actions that are required.

## Security
Communication between the platform services and a custom Flight service is secured through a combination of Transport Layer Security (TLS) and authentication. The following steps must be followed during deployment and registration of a custom Flight service to establish secure communication.
### 1. Get the platform public keys.
Get the platform public key for each cluster in which you intend to register your custom Flight service. The key can be retrieved with an HTTP request. For example, for an on-premise installation of Cloud Pak for Data:

`GET https://host/auth/jwtpublic`

For Cloud Pak for Data on IBM Cloud:

`GET https://iam.cloud.ibm.com/oidc/jwks`

### 2. Install the public keys in your custom Flight service image.
The default location where you should place the public keys is in the following directory of your container image:

`/config/etc/wdp_public_keys`

You can specify an alternate directory by setting the following environment variable in your container:

`WDP_PUBLIC_KEYS_PATH`

The public keys on IBM Cloud are changed weekly, so it can be more convenient to retrieve them dynamically. For any keys to be retrieved dynamically, put the URLs in the following file of the container image:

`/config/etc/wdp_public_key_urls.txt`

You can specify an alternate location of the file by setting the following environment variable in your container:

`WDP_PUBLIC_KEY_URLS`

In case of a template project you can place the keys in `/src/dist/resources/payload/etc/wdp_public_keys` and they will moved to `/config/etc/wdp_public_keys` during build. You can read more on templates in [Connector Development](#Connector Development) section.

The keys will be retrieved when the container is started.

### 3. Deploy your custom Flight service.

Deploy your custom Flight service. If the service will be registered with any external clusters, be sure to create an external route.  For example:

`oc create route passthrough <name> --hostname <host-name> --service <service-name>`

### 4. Retrieve the SSL certificate of the deployed custom Flight service.

Retrieve the SSL certificate of the deployed custom Flight service.  For example:

`keytool -printcert -sslserver host:port -rfc`

### 5. Register the custom Flight service in each platform.

To register a custom Flight service in a platform, you must be an administrator or editor of the platform asset catalog.

Registering your custom Flight service in a platform requires that the platform supports custom data source types.  You can determine whether the platform supports custom data source types by issuing an HTTP request to the IBM Watson Data Platform Connections service:

`GET https://host/v2/connections/version`

If the platform has not enabled custom data source types, you can enable it by changing an environment variable in the `wdp-connect-connection` and `wdp-connect-flight` pods:

```
oc set env deploy wdp-connect-connection ENABLE_CUSTOM_DATASOURCE_TYPES=true
oc set env deploy wdp-connect-flight ENABLE_CUSTOM_DATASOURCE_TYPES=true
```

Alternatively, the setting in the `wdp-connect-connection` pod can be configured by editing its config map:

`oc edit configmap config-wdp-connect-connection`

After changing the service configuration, poll the status of the pods until the new services have started and the old ones have terminated:

```
oc get pods | grep wdp-connect-connection
oc get pods | grep wdp-connect-flight
```

If custom data source types are enabled and the pods are running, issue a POST request to the Connections service to register your custom Flight service:

`POST https://host/v2/datasource_types`

```
{
    "flight_info": {
        "flight_uri": "grpc+tls://host:port",
        "ssl_certificate": "-----BEGIN CERTIFICATE-----\n<The-content-of-the-cert>\n-----END CERTIFICATE-----\n"
    },
    "origin_country": "us"
}
```

If the certificate cannot be verified such as when routing through a proxy, you can disable certificate validation for testing purposes. But this would not be recommended for production.

`POST https://host/v2/datasource_types`

```
{
    "flight_info": {
        "flight_uri": "grpc+tls://host:port",
        "ssl_certificate_validation": "false"
    },
    "origin_country": "us"
}
```

# <a name="quickstart"></a> Build your first connector
Building your first connector will involve the following steps. More details on each step can be found in later sections.

## 1. Build the SDK.

Verify that you can build the SDK.

```
cd <SDK-directory>/sdk-gen
./gradlew clean
./gradlew build
```

If the build fails because the Gradle scripts are not in the proper line endings for your platform, simply run the `spotlessApply` task first:

`./gradlew spotlessApply`

To build without running tests:

`./gradlew build -x test`

## 2. Generate connector and flight server
### Generate connector and server separately
#### 1. Generate a basic connector skeleton.

Generate a basic skeleton for a row-based connector.

`./gradlew generateJavaBasic`

Provide the following information in response to the prompts:

* Connector label - The connector display label as it should appear on the connection types page of Cloud Pak for Data.
* Connector name - The unique identifier name in snake case in accordance to [IBM Cloud API Handbook naming standards](https://cloud.ibm.com/docs/api-handbook?topic=api-handbook-terminology)
* Connector class name prefix - Class name prefix in camel case in accordance to Java class naming standards
* Java package name
* Connector description

Verify that the connector builds successfully:

`./gradlew build -x test`

#### 2. Generate a Flight server.

Generate a Flight server for your connector:

`./gradlew generateFlightApp`

In response to the prompts, provide a project name for the Flight server and choose which connectors to include in your Flight server. Since you have not yet provided an implementation for your connector, consider adding the sample bundle connector to your Flight server in addition to your custom connector. The bundle automatically includes both the sample Derby and generic JDBC connectors.

Verify that the Flight server builds successfully:

`./gradlew build -x test`

### Generate connector and server using quickstart task

This task only prompts for Connector's label and infers the rest from it. It creates a single Connector project and a single Flight Server project that includes the former.

`./gradlew quickstart`

Verify that the project builds successfully:

`./gradlew build -x test`

## 3. Build a Docker image.

Before you can build a Docker image, the Docker service must be running. Consider starting it in a separate terminal so that its log messages do not clutter your development terminal:

`systemctl start docker`

Or for Windows Subsystem for Linux with Ubuntu:

`sudo dockerd`

Once the Docker service is running, you can build a Docker image:

`./gradlew dockerBuild`

## 4. Start your Docker container.

By default your Docker container will expose your Flight service on port 443. To use a custom port, add an entry to your `gradle.properties` and change the first port number to your custom port:

`docker.publish.https=443:9443`

Start your Docker container:

`./gradlew dockerStart`

Or instead of specifying your custom port in `gradle.properties`, you can specify it on the command line:

`./gradlew dockerStart -Pdocker.publish.https=443:9443`

If you have created more than one Flight server project, specify the project of which server you wish to start, for example:

`./gradlew :wdp-connect-sdk-gen-flight:dockerStart -Pdocker.publish.https=443:9443`

## 5. Register your Flight service in Cloud Pak for Data.

Get the SSL certificate of the deployed Flight service, for example:

`keytool -printcert -sslserver localhost:443 -rfc > subprojects/flight/flight.pem`

Create a configuration file that points sdk to a specific environment by duplicating `src/dist/resources/payload/envs/template.properties` filling it with desired properties and giving it a name of your choosing e.g. `myEnv.properties`

Register the Flight service in an environment for which you have created a configuration, for example:

`./gradlew register -Penv=<myEnv>`

For more details on registration, refer to the following sections:

[Register the custom Flight service in each platform](#5-register-the-custom-flight-service-in-each-platform)

[Registration](#registration)

## 6. Test your connector's integration with cloud services.
Now, your connector should be visible on connection type selection screen and returned by `/v2/datasource_types` API.

If you included bundle connector in your flight service then you can execute its tests to validate it. You are encouraged to create similar tests for your own connector. In the main resources of the `java-test` project, copy the `tests.properties.template` file to `test.properties`. Configure the file to enable access to a cloud environment. For example, let's say you configured a `cloud.type` named `private`.

Run `testConnection` test:

`./gradlew :wdp-connect-sdk-gen-java-bundle:test --tests com.ibm.connect.sdk.bundle.TestBundleFlightProducerDerbyCloud.testConnection -Dsdk.test.cloud.type=private --no-daemon`

By default the test will start a local Flight server. If you have deployed the bundle connector in a container and have configured `bundle.flight.uri` to contain the location where the Flight server can be reached. This URI will be used by both connection service and flight client used in tests. If you need to use separate URIs, e.g. when executing tests behind a firewall against public cloud accessing your environment with Satellite Link, then you can also specify `bundle.flight.uri.internal` for your flight client. Then you can specify not to use a local Flight server:

`./gradlew :wdp-connect-sdk-gen-java-bundle:test --tests com.ibm.connect.sdk.bundle.TestBundleFlightProducerDerbyCloud.testConnection -Dsdk.test.cloud.type=private -Dsdk.test.bundle.flight.createLocal=false --no-daemon`

Or to run all of the tests in that class:

`./gradlew :wdp-connect-sdk-gen-java-bundle:test --tests com.ibm.connect.sdk.bundle.TestBundleFlightProducerDerbyCloud -Dsdk.test.cloud.type=private -Dsdk.test.bundle.flight.createLocal=false --no-daemon`

## 7. Push your Docker image to a container registry.

Push your image to a container registry. For an on-premise installation of Cloud Pak for Data, you can use the OpenShift Container Registry. Before pushing to the OpenShift Container Registry, you should build the `flightservice` operator to create the `flightservice-operator-system` namespace. For example:

```
cd <SDK-directory>/sdk-operator/flightservice
./gradlew addCredentials --key "openshift.username" --value <CPD-admin-username>
./gradlew addCredentials --key "openshift.password" --value <CPD-admin-password>
./gradlew deploy-operator -Popenshift.console.url=<CPD-console-url>
```

But before you can build the operator, additional installation and configuration is required. Details can be found in the section on the [Flight Operator](#flight-operator).

After the namespace has been created, specify the docker login credentials of the target repository using the following properties in the `.gradle/gradle.properties` file of your home directory:

```
systemProp.repos.dockerTarget.url
systemProp.repos.dockerTarget.username
systemProp.repos.dockerTarget.password
```

Return to the `sdk-gen` directory and push your image to the registry, for example:

`cd <SDK-directory>/sdk-gen`

`./gradlew dockerPush -Ptarget.image.registry=default-route-openshift-image-registry.apps.testing.com -Ptarget.image.project=flightservice-operator-system -Ptarget.image.tag=1.0.0`

If you do not have a registry, you can create one on IBM Cloud. For more information, refer to [Getting started with IBM Cloud Container Registry](https://cloud.ibm.com/docs/Registry?topic=Registry-getting-started).

Login to your container registry. For example, if you have created a registry on IBM Cloud:

```
ibmcloud login -a https://cloud.ibm.com
ibmcloud cr region-set global
ibmcloud cr login
```

Tag your image with your registry namespace and an image version number, for example:

`docker tag wdp-connect-sdk-gen-flight:latest icr.io/<my_namespace>/wdp-connect-sdk-gen-flight:1.0.0`

Then push your image to the registry, for example:

`docker push icr.io/<my_namespace>/wdp-connect-sdk-gen-flight:1.0.0`

## 8. Deploy your container in Cloud Pak for Data.

For an on-premise installation of Cloud Pak for Data, deploy your container. For example:

`oc apply -f subprojects/flight/src/dist/resources/payload/wdp-connect-sdk-gen-flight.yaml`

Refer to the section on [Using the flightservice operator to deploy a Flight Service](#using-the-flightservice-operator-to-deploy-a-flight-service) for information on the format of the deployment file.

Verify that the service is running, for example:

`oc get pods | grep wdp-connect-sdk-gen-flight-flightservice`

Re-register your Flight service at its new Flight URI, for example:

`grpc+tls://wdp-connect-sdk-gen-flight-flightservice.flightservice-operator-system.svc:443`

With your service now registered in its new location, you can stop your local container:

`./gradlew dockerStop`

# Connector development
IBM Connector SDK is a set of Gradle script plugins and a skeleton root project that allows you to generate connectors and microservices based on Apache Arrow Flight framework from provided templates as its subprojects. You can then fill in the templates with your logic and deploy one or many microservices to IBM Cloud or IBM Cloud Pak for Data (CPD).
## Download SDK
Use this link to download SDK zip. You can also clone the GitHub repository directly. Note that the SDK is provided as a convenience to assist with connector development, but there is no dependency on the SDK to develop connectors. You can develop and deploy your own service as long as it conforms to the [specification](#flight-based-connector-service).
## Setup
The Connector SDK relies on Gradle. It is bundled, so you do not need to install it.  However a Java Development Kit (JDK) needs to be present on your environment. SDK was developed and tested with JDK 11. Please refer to [Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html) to verify supported platforms. Docker must also be present if you wish to build a container image.
All you need to do to get started is to unpack the previously downloaded zip in a chosen directory.
## Choose a connector type
Decide on what type of connector that you want to implement. Your choice will determine which interfaces or abstract classes you should implement or extend:
* Arrow-based connector - The `Connector`, `SourceInteraction` and `TargetInteraction` interfaces allow you to interact directly with Arrow data structures such as `Schema` and `VectorSchemaRoot`. Interacting directly in Arrow format would provide the best performance, but you would need to provide the full implementation for your connector.
* Row-based connector - The `RowBasedConnector`, `RowBasedSourceInteraction` and `RowBasedTargetInteraction` abstract classes provide some implementation for you and allow you to interact with data as a `Record` of Java objects such as `java.lang.String`, `java.lang.Integer` and `java.sql.Timestamp`. Interacting with data as a row of those types may be more convenient for your implementation.  However there may be some performance overhead for converting to and from Arrow format.
* JDBC-based connector - The `JdbcConnector`, `JdbcSourceInteraction` and `JdbcTargetInteraction` abstract classes are convenient for implementing any JDBC-based connector and provide much of the implementation for you.
## Generate a connector skeleton
### Row-based connectors
Connector skeletons are provided as a convenience to help develop a connector rapidly, but there is no requirement that a connector implementation must follow that skeleton structure. A connector skeleton can be generated by calling a Gradle task that corresponds to the chosen template. Each task is interactive and will allow for a certain degree of customization. The newly generated connector will appear in the **subprojects** directory. What exactly needs to be implemented is explained in [Implementation](#Implementation) section. Currently the available tasks for generating connector templates are:
* `generateJavaBasic` - Generates a basic row-based connector that assumes a single read partition and defines no actions. This task expects, at a minimum, a **label** for the new connector. It can also set its **description**, **id**, **java package** and **class name prefix** but defaults will be suggested based on the **label** so you can keep pressing enter to accept them. The task responsible for generation is located in build.gradle of root project. `/sdk-gen/subprojects/java/basic` subproject is used as a template. If you want to add anything that is present in all your newly generated connectors you can modify that project. All templates are flagged as such by `ext.isTemplate` property.
### JDBC-based and Arrow-based connectors
These connector types are not templatized at the time. You need to create a new subproject that, in case of JDBC-based, will implement abstract classes from `sdk-gen/subprojects/java/jdbc`(you can follow `sdk-gen/subprojects/java/jdbc/derby` sample connector structure and contents) and in case of Arrow-based connector you can copy `sdk-gen/subprojects/java/noop` and modify it to your needs. All connector projects need to place a provider-configuration file in the resource directory `META-INF/services` in accordance to [ServiceLoader specification](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) where service provider is the implementation of FlightProducer.
## Generate a connector(s) service
### Flight service template
Once you have at least a single connector generated you can proceed with creating a flight service project. The task that creates it is called `generateFlightApp`. This task is also interactive and will ask you to select which of the available connectors to include. It will display a **numbered list of detected connector subprojects**. To select a given connector, input a number that is displayed to its left. The given project is treated as a connector project if it contains a resources file, `META-INF/services/org.apache.arrow.flight.FlightProducer`, which identifies the connector's `FlightProducer` class as well as if it doesn't specify `ext.isTemplate` or `ext.isServer` properties. To include a connector manually after a service project is generated, you simply need to define a dependency on a given subproject within the service's `build.gradle` file. Service subproject is generated from template located in `sdk-gen/subprojects/java/flight-app`.
## Implementation
### Flight Service
A basic flight service project generated from our template is capable of producing a docker image with Open Libery application server hosting flight. A FlightProducer implementation in this project is `DelegatingFlightProducer` that loads all other implementations of `FlightProducer` from its classpath via `ServiceLoader`. It can then delegate to a specific producer based on the `datasourceTypeName`. It is important to note here that the `datasourceTypeName` is not a part of the a `Ticket` by default. The present implementation utilizes caching to keep track of relationship between a ticket and its FlightProducer and the  Source Interaction properties. This caching is executed by a DelegatingFlightProducer and respective connector's FlightProducer. If you want to use multiple replicas it is important to switch to external cache.

Generated subproject will contain several important resources:
* `src/dist/resources/payload/Dockerfile` - Dockerfile template that can be customized
* `src/dist/resources/payload/envs` - properties files that point `register` and `unregister` tasks to specific environments
* `src/dist/resources/payload/initscripts` - scripts that will run on startup, executed in alphanumerical order
* `src/dist/resources/payload/etc/wdp_public_keys` - public keys placed here will be moved to docker image's `/etc/wdp_public_keys` during build.
* `src/main/resources/wlp/usr/servers/defaultServer/server.xml` - Open Liberty server configuration, can be modified to change things like ssl configuration or trace specification.
### Row-based connectors
The basic java template defines a number of classes that can be used to build a row-based connector. At a minimum every connector needs to implement at least the `ConnectorFactory` interface and extend the `RowBasedConnector` and `CustomFlightDatasourceType` classes. Connectors must also extend `RowBasedSourceInteraction` for reading data and `RowBasedTargetInteraction` for writing data. They don't need to have actual implementations but they still need to be defined. The template project defines stubs for all of these in the `basic.impl` package.
#### ConnectorFactory
`ConnectorFactory` is responsible for instantiation of connectors and allows the Flight server to respond to the `list_datasource_types` action. The [CustomFlightDatasourceType](#datasource-type-overview) object is what describes a particular data source. For example:
  ```JAVA
  public class MyConnectorFactory implements ConnectorFactory
{
    @Override
    public MyConnector createConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if("myconnector".equals(datasourceTypeName)){
            return new MyConnector(properties);
        } else {
            throw new UnsupportedOperationException("ID not supported);
        }
    }

    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().datasourceTypes(Arrays.asList(new MyConnectorDatasourceType()));
    }
}
  ```
  This allows handling of requests addressed to `myconnector` id and is able to present the capabilities of that connector by returning its implementation of `CustomFlightDatasourceType`.
#### RowBasedConnector
The `RowBasedConnector` class ties it all together. This interface is parameterized, and the implementing class needs to specialize it with your `RowBasedSourceInteraction` and `RowBasedTargetInteraction` implementations. The class definition expects the names of the implementing classes. This allows for dynamic instantiation in the framework. The `ConnectionProperties` parameter contains the connection property values.

 Your connector class is also used to execute custom actions via the `performAction` method. An action is a custom operation that you can implement for your connector. It accepts an **action** name and a set of input **properties** in `ConnectionActionConfiguration`. The result is a **Map** of output property names to values in `ConnectionActionResponse`. An example action that returns a record count might look like this:
```JAVA
@Override
public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties)
{
    if ("get_record_count".equalsIgnoreCase(action)) {
        final Properties inputProperties = ModelMapper.toProperties(properties);
        final String schemaName = inputProperties.getProperty("schema_name");
        final String tableName = inputProperties.getProperty("table_name");
        final String statementText = "SELECT COUNT(*) FROM "+schemaName+"."+tableName;
        long rowCount = -1;
        Statement statement = getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(statementText);
        if (resultSet.next()) {
            rowCount = resultSet.getLong(1);
        }
        final ConnectionActionResponse response = new ConnectionActionResponse();
        response.put("record_count", rowCount);
        return response;
    }
}
```
#### Record
Before delving into the remaining implementable interfaces, it is important to familiarize oneself with how data is represented within this template. Information about a row of data is encapsulated in a list of `Serializable` Java objects. `CustomFlightAssetField` encapsulates metadata information about a data field used in data transfer. The following table lists the mappings between the supported `CustomFlightAssetField` type names and Java types of values transfered for those fields.

Connectors must conform to this mapping when retrieving field values via `RowBasedSourceInteraction` and inserting field values via `RowBasedTargetInteraction`.

 </p>
 <table border="1" style="width:50%">
 <caption></caption>
 <tr>
 <th>CustomFlightAssetField Type</th>
 <th>CustomFlightAssetField Signed</th>
 <th>Field value Java type</th>
 <th>ArrowType</th>
 </tr>
 <tr>
 <td>array</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>bigint</td>
 <td>false</td>
 <td>java.math.BigInteger</td>
 <td>ArrowType.Decimal(38, 0, 128)</td>
 </tr>
 <tr>
 <td>bigint</td>
 <td>true</td>
 <td>java.lang.Long</td>
 <td>ArrowType.Int(64, true)</td>
 </tr>
 <tr>
 <td>binary</td>
 <td></td>
 <td>byte[]</td>
 <td>ArrowType.Binary</td>
 </tr>
 <tr>
 <td>bit</td>
 <td></td>
 <td>java.lang.Boolean</td>
 <td>ArrowType.Bool</td>
 </tr>
 <tr>
 <td>blob</td>
 <td></td>
 <td>byte[]</td>
 <td>ArrowType.Binary</td>
 </tr>
 <tr>
 <td>boolean</td>
 <td></td>
 <td>java.lang.Boolean</td>
 <td>ArrowType.Bool</td>
 </tr>
 <tr>
 <td>char</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>clob</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>datalink</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>date</td>
 <td></td>
 <td>java.sql.Date</td>
 <td>ArrowType.Date(DateUnit.DAY)</td>
 </tr>
 <tr>
 <td>decimal</td>
 <td></td>
 <td>java.math.BigDecimal</td>
 <td>ArrowType.Decimal(length, scale, 128) or ArrowLargeDecimalType(length, scale)</td>
 </tr>
 <tr>
 <td>distinct</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>double</td>
 <td></td>
 <td>java.lang.Double</td>
 <td>ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)</td>
 </tr>
 <tr>
 <td>float</td>
 <td></td>
 <td>java.lang.Double</td>
 <td>ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)</td>
 </tr>
 <tr>
 <td>integer</td>
 <td>false</td>
 <td>java.lang.Long</td>
 <td>ArrowType.Int(32, false)</td>
 </tr>
 <tr>
 <td>integer</td>
 <td>true</td>
 <td>java.lang.Integer</td>
 <td>ArrowType.Int(32, true)</td>
 </tr>
 <tr>
 <td>java_object</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>longnvarchar</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>longvarbinary</td>
 <td></td>
 <td>byte[]</td>
 <td>ArrowType.Binary</td>
 </tr>
 <tr>
 <td>longvarchar</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>nchar</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>nclob</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>null</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Null</td>
 </tr>
 <tr>
 <td>numeric</td>
 <td></td>
 <td>java.math.BigDecimal</td>
 <td>ArrowType.Decimal(length, scale, 128) or ArrowLargeDecimalType(length, scale)</td>
 </tr>
 <tr>
 <td>nvarchar</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>other</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>real</td>
 <td></td>
 <td>java.lang.Float</td>
 <td>ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)</td>
 </tr>
 <tr>
 <td>ref</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>rowid</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>smallint</td>
 <td>false</td>
 <td>java.lang.Integer</td>
 <td>ArrowType.Int(16, false)</td>
 </tr>
 <tr>
 <td>smallint</td>
 <td>true</td>
 <td>java.lang.Short</td>
 <td>ArrowType.Int(16, true)</td>
 </tr>
 <tr>
 <td>sqlxml</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>struct</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 <tr>
 <td>time</td>
 <td></td>
 <td>java.sql.Time</td>
 <td>ArrowType.Time(TimeUnit.MILLISECOND, 32)</td>
 </tr>
 <tr>
 <td>timestamp</td>
 <td></td>
 <td>java.sql.Timestamp</td>
 <td>ArrowType.Timestamp(TimeUnit.MILLISECOND, null)</td>
 </tr>
 <tr>
 <td>tinyint</td>
 <td>false</td>
 <td>java.lang.Short</td>
 <td>ArrowType.Int(8, false)</td>
 </tr>
 <tr>
 <td>tinyint</td>
 <td>true</td>
 <td>java.lang.Byte</td>
 <td>ArrowType.Int(8, true)</td>
 </tr>
 <tr>
 <td>varbinary</td>
 <td></td>
 <td>byte[]</td>
 <td>ArrowType.Binary</td>
 </tr>
 <tr>
 <td>varchar</td>
 <td></td>
 <td>java.lang.String</td>
 <td>ArrowType.Utf8</td>
 </tr>
 </table>

#### RowBasedSourceInteraction
A `RowBasedSourceInteraction` is retrieved from a connector by calling the `getSourceInteraction` method on the `RowBasedConnector` object. A `CustomFlightAssetDescriptor` object is passed as a parameter. The `CustomFlightAssetDescriptor` specifies **interaction_properties** and **fields**. A typical read flow should first save the interaction properties and record definition, open a transaction upon first call to `getRecord` and then continue to create `Record` objects based on read data for every call to `getRecord`. If end of data is reached this method should return `null`. The class that extends `RowBasedConnector` should encapsulate a **connection**. Any extra information required to read the data, like the name of the table or schema, should be passed via interaction properties. An example basic implementation of `getRecord` might look like this assuming we have a `resultSet` field in which we keep the result of our query:
  ```JAVA
  @Override
  public Record getRecord()
  {
      if (resultSet == null) {
          executeQuery();
      }
      if (resultSet.next()) {
          final List<CustomFlightAssetField> fields = getFields();
          final Record record = new Record(fields.size());
          readRow(fields, (index, value) -> record.appendValue(value));
          return record
      }
      return null;
  }
  ```

#### RowBasedTargetInteraction
A `RowBasedTargetInteraction` is used to transfer data to a data source. Similar to `RowBasedSourceInteraction`, it receives a `CustomFlightAssetDescriptor` object which contains **interaction_properties** and **fields**. The `putRecord` method must be implemented. Its purpose is to save the passed `Record` in the data source.

#### Discovery
The `discoverAssets` method in `RowBasedConnector` discovers metadata. It operates on paths that identify assets within a data source. The user should implement the `discoverAssets` method that, for the given criteria, will describe available assets by creating an instance of `CustomFlightAssetDescriptor` for each.
  ```java
  public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception {
    //TODO implement this
  }
  ```
An asset is identified by its **path** in `CustomFlightAssetsCriteria`. The purpose of the `discoverAssets` method is to describe every asset that can be found under that path. Users typically call discovery with a path of "/" and then repeatedly call discovery for each path they are interested in discovering. This process can be influenced by **filters** specified in the `CustomFlightAssetsCriteria`. The **extendedMetadata** attribute determines whether the connector should provide extended metadata. The **detail** flag determines whether additional details should be included. The **context** specifies whether we're discovering assets for the purpose of reading as a source or writing as a target. Allowed values are **source** and **target**. This determines what kind of properties should be provided in the asset definition for the sake of future interaction. This allows various tools to interact with assets via paths rather than force their users to provide properties for each interaction. Let's take a look on an example of discovery. We assume here that our sample database has a schema named `GOSALES` which contains a table called `PRODUCTS`. Asset discovery starts with an empty path and our code could look like this:
```java
  final CustomFlightAssetDescriptor assetDescriptor = new CustomFlightAssetDescriptor();
  assetDescriptor.setId("GOSALES");
  final DiscoveredAssetType assetType = new DiscoveredAssetType();
  // List the schemas.
  assetType.setType("schema");
  assetType.setDataset(false);
  assetType.setDatasetContainer(true);
  assetDescriptor.setType(assetType);
```
We instantiate `CustomFlightAssetDescriptor` and set its id. Here it is hard-coded but normally we would execute a query against our data source to actually retrieve the assets we want to describe. We set the asset id and define its type by providing a `DiscoveredAssetType` object. The asset type needs to have an id set via **setType**. Call **setDataset(true)** if the asset that you're describing holds data (e.g. it's a table). In this case we're working with a schema so we set it to false. We call **setDatasetContainer(true)** instead. That means this type is something abstract that doesn't hold data itself. There can be multiple levels of containers. We don't need to set the path as it will be built automatically from asset id. In case of our `GOSALES` schema the path will be `/GOSALES`. Now once we return this asset, this method will be called again with a path of `/GOSALES`:
```java
  assetType.setType("table");
  assetType.setDataset(true);
  assetType.setDatasetContainer(false);
  assetDescriptor.setId("PRODUCT");
  assetDescriptor.setType(assetType);
```
The procedure is pretty straightforward and similar to the previous example. This time we mark our asset as a dataset since it is a table. Lastly, in another call we will need to describe this table.
```java
  assetType.setType("table");
  assetType.setDataset(true);
  assetType.setDatasetContainer(false);
  assetDescriptor.setId("PRODUCT");
  assetDescriptor.setType(assetType);
  final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
  interactionProperties.put("schema_name", pathElements[0]);
  interactionProperties.put("table_name", pathElements[1]);
  assetDescriptor.setInteractionProperties(interactionProperties);
  final List<CustomFlightAssetField> fields = new ArrayList<>();
  final FieldDefinition assetField = new FieldDefinition();
  assetField.setName("PRODUCT_NUMBER");
  assetField.setType(FieldType.INTEGER);
  assetField.setLength(10);
  assetField.setScale(0);
  assetField.setNullable(false);
  assetField.setSigned(true);
  assetField.setDescription("Product number");
  fields.add(assetField);
  assetDescriptor.setFields(fields)
```
We provide interaction properties that are to be used when interacting with this asset. In this example, we provide `schema_name` and `table_name` so that our connector can generate a SELECT statement. We also describe the fields that correspond to the table columns.

#### Defining a datasource type
##### Datasource Type Overview
A `CustomFlightDatasourceType` is an object that describes the capabilities of a connector and how to interact with it. It defines properties for the following contexts:

* connection - properties used for establishing a connection to the data source
* source - properties for read operations
* target - properties for write operations
* filter - properties for the purpose of discovering assets that the data source contains
##### Top-level descriptor fields
Top-level fields contain some basic information about the data source. Use setters described below to provide them.
|Setter|Type of argument|Purpose of the field|
|---|---|---|
|setName|String|A unique identifier name such as "my_custom_type" that should follow [IBM Cloud API Handbook naming standards](https://cloud.ibm.com/docs/api-handbook?topic=api-handbook-terminology)|
|setLabel|String|A localized display label such as "My Custom Type"|
|setDescription|String|Connector description|
|setStatus|StatusEnum|Status of datasource type. One of ACTIVE, INACTIVE, DEPRECATED, PENDING. Only ACTIVE ones will be visible in production environments.|
|setAllowedAsSource|boolean|Whether connector supports read operation|
|setAllowedAsTarget|boolean|Whether connector support write operation|
|[setProperties](#property-descriptors)|object|Describes properties a connector uses in various contexts|
##### Property descriptors
Property descriptor defines a property and its attributes. It needs to be defined in a proper **context**. Some attributes may have an effect on a generated UI.
|Setter|Argument Type|Purpose|Effect on UI|
| --- | --- | --- | --- |
|setName|String|The identifier name of the property such as "host_name" that should follow [IBM Cloud API Handbook naming standards](https://cloud.ibm.com/docs/api-handbook?topic=api-handbook-terminology)| - |
|setLabel|String|A localized display label such as "Host name"|Will be displayed as a property label|
|setDescription|String| Description of a property | Will be displayed as a tooltip|
|setDefaultValue|String|default value of a property| A property control will contain this value if enabled |
|setPlaceholder|String|placeholder value| A property control will display this value in gray. The value itself will not be saved. |
|setMasked|boolean| Whether property value contains sensitive information and needs to be encrypted e.g. if it is a password | Property control wil display dots instead of characters |
|setRequired|boolean| Whether this property is required. Required but not provided properties will cause validation errors | UI will complain if this property is not provided and will not allow to save properties.|
|setType|TypeEnum|type of a property. One of STRING, BOOLEAN, INTEGER or ENUM| UI uses different controls for different types. String uses default control, boolean will be displayed as a checkbox, integer uses a number control and enum uses drop-down list. |
|setValues|List|defines allowed enum values of this property. See enums section for details on how to define your own enum values|UI will display defined options as a list|
|setGroup|String|defines the group of this property. If a property belongs to a **credentials** group an end-user will be able to use it with personal credentials and it will support vault references|

Let's take a look at a complete example of defining a datasource type. Firstly we need to instantiate **CustomFlightDatasourceType** and set some basic information like its id and whether we can use it as a source or target.
```java
final String customTypeName = "my_custom_type";
        final CustomFlightDatasourceType datasourceType = new CustomFlightDatasourceType();
        datasourceType.setName(customTypeName);
        datasourceType.setLabel(customTypeName + " label");
        datasourceType.setDescription(customTypeName + " description");
        datasourceType.setAllowedAsSource(true);
        datasourceType.setAllowedAsTarget(true);
        datasourceType.setStatus(StatusEnum.ACTIVE);
```

Now we can instantiate a **CustomFlightDatasourceTypeProperties** object and add it to our **datasourceType**.

```java
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        datasourceType.setProperties(properties);
```
Now we can fill it with properties. **buildCustomPropertyDef** is a helper factory method that accepts all the previously described fields in that order.

```java
        // Connection properties
        properties.addConnectionItem(buildCustomPropertyDef("host", "Host", "Name of server", TypeEnum.STRING, true, false, "domain"));
        properties.addConnectionItem(buildCustomPropertyDef("port", "Port", "Port number", TypeEnum.INTEGER, true, false, "domain"));
        properties.addConnectionItem(buildCustomPropertyDef("database", "Database", "Name of database"));
        properties.addConnectionItem(
                buildCustomPropertyDef("username", "User name", "User name", TypeEnum.STRING, true, false, "credentials"));
        properties
                .addConnectionItem(buildCustomPropertyDef("password", "Password", "Password", TypeEnum.STRING, true, true, "credentials"));

        // Source interaction properties
        properties.addSourceItem(
                buildCustomPropertyDef("schema_name", "Schema name", "The name of the schema that contains the table to read from"));
        properties.addSourceItem(buildCustomPropertyDef("table_name", "Table name", "The name of the table to read from"));
        properties.addSourceItem(buildCustomPropertyDef("row_limit", "Row limit", "The maximum number of rows to return", TypeEnum.INTEGER,
                false, false, null));

        // Target interaction properties
        properties.addTargetItem(
                buildCustomPropertyDef("schema_name", "Schema name", "The name of the schema that contains the table to write to"));
        properties.addTargetItem(buildCustomPropertyDef("table_name", "Table name", "The name of the table to write to"));
```
### JDBC-based connectors
As with other classes that implement the `Connector` interface, a class that extends the abstract `JdbcConnector` class needs to implement the `getSourceInteraction` and `getTargetInteraction` methods.  Additionally, it needs to implement the `getDriver` method to return the JDBC `Driver` class used by the connector.  It also must implement the `getConnectionURL` method to generate the connection URL required by the driver.

A class that extends the abstract `JdbcSourceInteraction` class needs to implement the `generateRowLimitPrefix` and `generateRowLimitSuffix` methods to indicate what syntax should be used to push row limits to the database.  Additionally it must implement the `getPartitioningPredicate` method to indicate what predicate should be used to partition rows.

A class that extends the abstract `JdbcTargetInteraction` class generally does not need to implement any methods besides its constructor, but it can choose to override methods to perform custom behavior.

### Arrow-based connectors
An arrow-based connector that uses the `Connector` interface must implement the `connect`, `discoverAssets`, `getSchema`, `getSourceInteraction`, `getTargetInteraction`, `performAction` and `close` methods.

An arrow-based source interaction must implement the methods of the `SourceInteraction` interface which includes `beginStream`, `getSchema`, `getTickets`, `hasNextBatch`, `nextBatch`, and `close`.

An arrow-based target interaction must implement the methods of the `TargetInteraction` interface which includes `putSetup`, `putStream`, `putWrapup` and `close`.

### Custom FlightProducer
A fully custom implementation could provide its own implementation of the `FlightProducer` interface rather than extend `ConnectorFlightProducer`, but the implementation must still conform to the [specification](#flight-based-connector-service).
```java
/**
 * Flight producer for custom data source types.
 */
public class CustomFlightProducer implements FlightProducer
{
    /**
     * Action type to list data source types supported by this flight producer.
     */
    public static final String ACTION_LIST_DATASOURCE_TYPES = "list_datasource_types";

    /**
     * Action type to test a connection to a custom data source type.
     */
    public static final String ACTION_TEST = "test";

    /**
     * Constructs a flight producer for custom data source types.
     *
     * @param allocator
     */
    public CustomFlightProducer(BufferAllocator allocator)
    {
        this.allocator = allocator;

        //TODO Define the data source types supported by this flight producer.

        //TODO Connection properties

        //TODO Source interaction properties

        //TODO Target interaction properties
    }

    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener)
    {
    	//TODO create FlightInfo
        listener.onNext(flightInfo);
        listener.onCompleted();
    }

    @Override
    public void getStream(CallContext context, Ticket flightTicket, ServerStreamListener listener)
    {
        // Read data
    }

    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream)
    {
        // Write data
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener)
    {
        listener.onNext(new ActionType(ACTION_LIST_DATASOURCE_TYPES, ACTION_LIST_DATASOURCE_TYPES));
        listener.onNext(new ActionType(ACTION_TEST, ACTION_TEST));
        listener.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener)
    {
        try {
            final String requestJson = new String(action.getBody(), StandardCharsets.UTF_8);
            final String responseJson = "";
            if (ACTION_LIST_DATASOURCE_TYPES.equals(action.getType())) {
                // TODO prepare JSON with DatasourceType
            } else if (ACTION_TEST.equals(action.getType())) {
                // TODO test connection
            } else {
                throw new UnsupportedOperationException("doAction " + action.getType() + "is not supported");
            }
            final Result result = new Result(responseJson.getBytes(StandardCharsets.UTF_8));
            listener.onNext(result);
            listener.onCompleted();
        }
        catch (Exception e) {
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

}
```
## Build
The entire project can be built by calling the `build` task in the **root** project. You can also build a certain subproject and its dependencies by calling its build task. The `projects` task can list the names of defined projects.

By default, open source libraries will be retrieved from Maven Central. To use an alternate repository, configure the following properties in the `.gradle/gradle.properties` file of your home directory:

```
systemProp.repos.maven.url
systemProp.repos.maven.username
systemProp.repos.maven.password
```

## Testing
The `java-basic` template contains some unit test stubs. You can use them to test connector instantiation and verify your data source and asset descriptors.

The `java-test` project contains abstract classes that your tests can extend.  The `ConnectorTestSuite` class contains a base set of tests for testing your connector in isolation independent of any cloud environment.  The `CloudTestSuite` class contains a base set of tests for testing your connector through cloud services such as the WDP Connections service and WDP Flight service. The `java-bundle` project contains examples of tests that extend those test suites.
## Building a Docker Image
You can create a docker image by executing the `dockerBuild` task in any project that defines and sets the extension property `isServer=true`. The task utilizes the `Dockerfile` template located at `src/dist/resources` and can be customized with a number of properties. You can define these properties in `gradle.properties` or pass them with the `-P<prop_name>=<prop_value>` command line option. You can then run `dockerStart` to create and start a container which exposes the default HTTP and HTTPS ports. The default base image is based on WebSphere Liberty. You can configure WebSphere by editing `server.xml` in `src/main/resources`. The default configuration creates a self-signed certificate. If you want to use a CA-signed certificate, edit the `Dockerfile` so that your certificate is added to configured keystore. Any public keys required to validate user tokens need to be placed under the `/config/etc/wdp_public_keys` directory.

The set of properties available for `Dockerfile` customization are:
- `image.registry` - where to pull the base image from
- `image.name` - name of the base image
- `image.tag` - tag of the base image
- `target.image.tag` - target image tag
- `target.image.description` - target image description
- `target.image.release`
- `target.image.summary`
- `maintainer.name` - maintainer label of the target image
- `vendor.name` - vendor label of the target image
- `service.user` - user for the WebSphere Liberty instance

The default name and tag of the target image are governed by the `project.name` and `project.version` built-in properties respectively.
`image.registry` is used during login performed by the `dockerLogin` task as well as when checking and pulling the base image.

By default, the base image will be retrieved from Docker Hub. To use an alternate repository, configure the following properties in the `.gradle/gradle.properties` file of your home directory:

```
systemProp.repos.docker.url
systemProp.repos.docker.username
systemProp.repos.docker.password
```
## Registration
Registration of newly deployed connectors can be done via the connections service API which first queries your Flight server for connectors and then creates a custom datasource type for each. Your Flight server provides the list of available connectors by responding to the `list_datasource_types` action. There is a dedicated Gradle task which can be used to trigger registration.

`./gradlew :wdp-connect-sdk-gen-<flight-srv-name>:register -Penv=<environment_code>`

It expects an `<environment_code>.properties` file to be placed under `build/resources/dist/payload/envs/`. There is a template of this file located in `src/dist/resources/payload/envs/template.properties`. The `assembleRegistrationConfig` task located in build.gradle of your Flight service project will automatically copy the properties files from the source directory and place them in the expected directory mentioned above. The properties file defines several properties:
- `apikey` - your API key (IBM Cloud only)
- `username` - user name (Cloud Pak for Data on-premise only)
- `password` - password (Cloud Pak for Data on-premise only)
- `auth_uri` - authentication API endpoint, varies by platform. The value in the template contains the IBM Cloud endpoint. If you are using Cloud Pak for Data on-premise use `https://{cpd_cluster_host}{:port}/icp4d-api/v1/authorize` instead.
- `datasource_types_uri` - /v2/datasource_types API endpoint. The template specifies the IBM Cloud endpoint. Replace the host and port if using Cloud Pak for Data on-premise.
- `flight_uri=grpc+tls://host:port` - address of your Flight service
- `ssl_certificate_path` - path to the file containing your Flight service SSL certificate in PEM format
- `ssl_certificate_validation` - whether to validate the certificate, accepts true/false values.
- `auth_token` - this one is optional and can be used instead of the `apikey` property. If you don't want to use `apikey`, you can perform a login yourself or in your own task and place the retrieved bearer token here.

**Note:** If you change the datasource type definition of a connector and deploy a new version of your Flight service, you can update the datasource type definitions in Cloud Pak for Data by performing the registration step again. **Do not unregister** the datasource type or you can permanently break any assets that were depending on it. Never change the name identifier of a datasource type unless you intend on registering an entirely new datasource type instead of updating the existing one.

### Unregistration of data source types
`./gradlew :wdp-connect-sdk-gen-<flight-srv-name>:unregister`

This task uses the same set of properties as the `register` task. Additionally it expects a `-PdatasourceType` argument that denotes which datasource type should be removed. If it is not provided the user will be prompted for it. The task makes a `DELETE` HTTP request to `/v2/datasource_types/<datasourceType>`.

**WARNING: Unregistering a datasource type can permanently break any connection assets or other assets that were depending on that datasource type. Only unregister a datasource type if you are certain that no assets depend on it.**

Note that to update an existing datasource type definition with a new version, **do not unregister** the datasource type before registering the new version. Simply perform the registration step and it will update the existing definitions.

# Example connectors

The source code for the sample connectors is contained in the `sdk-gen` directory. One sample connector connects to Apache Derby using JDBC.

The main files in the directory are:

```
build.gradle                     Gradle build script of the sdk-gen project
gradle.properties                Version numbers of the open source packages on which the examples depend
config/pmd/pmd.xml               PMD quality check rule set
```

The sub-directories in the directory are:

```
subprojects/java                 Folder for the subprojects of all Java examples
subprojects/java/api             Interfaces and abstract classes for implementing any Java-based connector
subprojects/java/basic           Templates for generating a basic row-based Java connector
subprojects/java/bundle          Subproject that bundles multiple connectors
subprojects/java/flight-app      Subproject that builds a Flight app server
subprojects/java/jdbc            Abstract classes for implementing any JDBC-based connector
subprojects/java/jdbc/derby      Subproject that builds the Apache Derby sample connector
subprojects/java/jdbc/generic    Subproject that builds a generic JDBC sample connector
subprojects/java/noop            Subproject that builds a no-op Flight producer that has no real implementation
subprojects/java/test            Utilities that are useful for testing connectors
subprojects/java/util            Utilities that are useful for implementing any Java-based connector
```

## subprojects/java/api

This directory contains interfaces and abstract classes for implementing any Java-based connector.

The contents of the main source directory are:

```
ConnectorFlightProducer.java     An abstract Flight producer for connectors.
ActionProvider.java              An interface for performing custom actions.
Connector.java                   An interface that all connectors should implement.
ConnectorFactory.java            An interface for a connector factory.
ConnectorPoolKey.java            A connector pool key.
FlightDescriptorCache.java       A Flight descriptor cache for getting a descriptor for a Flight Ticket
PooledConnector.java             A connector that returns to the pool when closed.
PooledConnectorFactory.java      An abstract factory for creating connectors or borrowing one from a pool.
SourceInteraction.java           An interface for an interaction with a connector asset as a source.
TargetInteraction.java           An interface for an interaction with a connector asset as a target.
TicketInfo.java                  A model for information contained in a Flight ticket.
```

## subprojects/java/bundle

This directory contains a subproject that bundles multiple connectors.

The contents of the main source directory are:

```
BundleConnectorFactory.java      A factory for creating connectors from a bundle of multiple connectors.
BundleFlightProducer.java        A Flight producer for a bundle of multiple connectors.
```

The contents of the test source directory are:

```
BundleTestEnvironment.java                A test environment shared by bundle tests
TestBundleFlightProducerDerby.java        Tests the Apache Derby connector in a bundle of multiple connectors
TestBundleFlightProducerDerbyCloud.java   Tests a bundled Apache Derby connector via Cloud Pak for Data
TestBundleFlightProducerGenericJdbc.java  Tests the generic JDBC connector in a bundle of multiple connectors
```

Note however that the build will skip any cloud tests unless a test configuration has been defined for connecting to a cloud cluster. Refer to the later section on the [test subproject](#subprojectsjavatest).

## subprojects/java/jdbc

This directory contains abstract classes for implementing any JDBC-based connector.

The contents of the main source directory are:

```
AssetFieldType.java              Utility methods for converting between JDBC field types and asset model field types
JdbcConnector.java               An abstract JDBC connector.
JdbcSourceInteraction.java       An interaction with a JDBC asset as a source.
JdbcTargetInteraction.java       An interaction with a JDBC asset as a target.
```

## subprojects/java/jdbc/derby

This directory contains a subproject that builds the Apache Derby sample connector.

The contents of the main source directory are:

```
DerbyConnector.java              A connector for connecting to Apache Derby.
DerbyConnectorFactory.java       A factory for creating Apache Derby connectors.
DerbyDatasourceType.java         The definition of a custom Apache Derby data source type.
DerbyFlightProducer.java         A Flight producer for Apache Derby.
DerbySourceInteraction.java      An interaction with an Apache Derby asset as a source.
DerbyTargetInteraction.java      An interaction with an Apache Derby asset as a target.
```

The contents of the test source directory are:

```
TestDerbyFlightProducer.java     JUnit tests that exercise the sample Apache Derby connector
```

## subprojects/java/jdbc/generic

This directory contains a subproject that builds a generic JDBC sample connector.

The contents of the main source directory are:

```
GenericJdbcConnector.java           A connector for connecting to a generic JDBC data source.
GenericJdbcConnectorFactory.java    A factory for creating generic JDBC connectors.
GenericJdbcDatasourceType.java      The definition of a custom generic JDBC data source type.
GenericJdbcFlightProducer.java      A Flight producer for generic JDBC.
GenericJdbcSourceInteraction.java   An interaction with a generic JDBC asset as a source.
GenericJdbcTargetInteraction.java   An interaction with a generic JDBC asset as a target.
```

The contents of the test source directory are:

```
TestGenericJdbcFlightProducer.java  JUnit tests that exercise the sample generic JDBC connector
```

## subprojects/java/noop

This directory contains a subproject that builds a no-op Flight producer that has no real implementation.

The contents of the main source directory are:

```
NoOpFlightProducer.java          A no-op Flight producer with no real implementation.
```

The contents of the test source directory are:

```
TestNoOpFlightProducer.java      JUnit tests that exercise each Flight method.
```

## subprojects/java/test

This directory contains utilities that are useful for testing connectors.  Tests will dynamically assign ports for any Derby and Flight servers that they use.  To configure tests to use statically assigned ports, the `tests.properties.template` file should be copied and renamed to `test.properties` in which the desired configuration settings should be specified.  You can also configure cloud settings for cloud integration tests.

You can encrypt property values using the `EncryptUtil` utility, for example:

`java EncryptUtil -e <value>`

The `EncryptUtil` utility requires that the encryption/decryption key be defined in the following environment variable:

`SDK_TEST_DECRYPT_KEY`

Alternatively, the encryption/decryption key can be supplied via a JVM system property, for example:

`java -Dsdk.test.decrypt.key=<key> EncryptUtil -e <value>`

But using a JVM system property would mean that the decryption key would need to be supplied on every invocation of the JVM that needs to decrypt the property values.

The packages of the main source directory are:

```
com.ibm.connect.sdk.test               Abstract classes and utilities for testing any connector
com.ibm.connect.sdk.test.jdbc.derby    Abstract classes and utilities for testing the Apache Derby connector
com.ibm.connect.sdk.test.jdbc.generic  Abstract classes and utilities for testing the generic JDBC connector
```

The contents of the `com.ibm.connect.sdk.test` package are:

```
CloudClient.java                 A client for interacting with Cloud Pak for Data
CloudTestSuite.java              An abstract class for a cloud test suite
ConnectorTestSuite.java          An abstract class for a connector test suite
EncryptUtil.java                 Utility for encrypting and decrypting test configuration properties
FlightTestSuite.java             An abstract class for a Flight test suite
TestConfig.java                  Test configuration properties
TestFlight.java                  A wrapper for managing a Flight server and client for testing
```

The contents of the `com.ibm.connect.sdk.test.jdbc.derby` package are:

```
DerbyCloudTestSuite.java         An abstract class for testing Apache Derby connector via Cloud Pak for Data
DerbyTestSuite.java              An abstract class for an Apache Derby test suite
DerbyUtils.java                  Utility methods for starting and connecting to an Apache Derby server
```

The contents of the `com.ibm.connect.sdk.test.jdbc.generic` package are:

```
GenericJdbcTestSuite.java        An abstract class for a generic JDBC test suite
```

The contents of the main resources directory are:

```
tests.properties.template        Template for test configuration properties
```

## subprojects/java/util

This directory contains utilities that are useful for implementing any Java-based connector.

The contents of the main source directory are:

```
AuthUtils.java                   Utility methods for validating authorization tokens
ClientTokenAuthHandler.java      An authentication handler for a Flight client
ModelMapper.java                 A mapper for converting between model objects and JSON bytes
ServerTokenAuthHandler.java      An authentication handler for a Flight server
SSLUtils.java                    Utility methods for handling SSL certificates
Utils.java                       Miscellaneous utility methods
```

# Flight Operator

## Setup
The flightservice operator is used to manage flight services. However, before the flightservice operator can be used, it must be deployed and before deploying you will need to do some setup.
>### Required applications
- `oc` (Openshift Client)
- `kubectl` (Kubernetes Client)
- `make` (Linux command)
- `podman` or `docker`
### Save TLS Certificate
If your Openshift cluster uses a TLS certificate that is not signed by a well known certificate authority, you will need to obtain a pem encoded copy of the certificate to store on your computer. Depending on whether you use `docker` or `podman` you will store the certificate in a different location.

#### Podman
You will need to create a directory under `~/.config/containers/certs.d` with name as your image registry in Openshift. The name of the image registry can be found by running the following openshift command
  ```
  $ oc get routes -n openshift-image-registry
  NAME            HOST/PORT                                                        PATH   SERVICES         PORT    TERMINATION   WILDCARD
  default-route   default-route-openshift-image-registry.apps.testing.com          image-registry   <all>   reencrypt     None
  ```
  The name of the directory to create is the value in the HOST/PORT column. In this example it is `default-route-openshift-image-registry.apps.testing.com`, and then you will save the certificate authority certificate to that directory. Here is one way to do that:
  ```
  $ mkdir -p ~/.config/containers/certs.d/default-route-openshift-image-registry.apps.testing.com
  $ oc extract secret/router-ca --keys=tls.crt -n openshift-ingress-operator --to=~/.config/containers/certs.d/default-route-openshift-image-registry.apps.testing.com
  ```
#### Docker

There are a few ways in which docker may be installed on your system. For purposes of the flight operator sdk, we assume that docker is configured for rootless access.

You will need to create a directory under `/etc/docker/certs.d` with name as your image registry in Openshift. You will need to use sudo to create the directory. The name of the image registry can be found by running the following openshift command
  ```
  $ oc get routes -n openshift-image-registry
  NAME            HOST/PORT                                                        PATH   SERVICES         PORT    TERMINATION   WILDCARD
  default-route   default-route-openshift-image-registry.apps.testing.com          image-registry   <all>   reencrypt     None
  ```
  The name of the directory to create is the value in the HOST/PORT column. In this example it is `default-route-openshift-image-registry.apps.testing.com`, and you will need to save the certificate authority certificate to that directory. Here is one way to do that:
  ```
  $ sudo mkdir -p /etc/docker/certs.d/default-route-openshift-image-registry.apps.testing.com
  $ oc extract secret/router-ca --keys=tls.crt -n openshift-ingress-operator --to=/tmp
  $ sudo cp /tmp/tls.crt /etc/docker/certs.d/default-route-openshift-image-registry.apps.testing.com
  ```
### Configure Openshift login
Before running the installation you will need to either log into the Openshift cluster with appropriate administrator credentials, or provide those credentials to
the deployment script by either:

- Logging onto the Openshift cluster by using the `oc login` command.<br>

\- or -

- Editing the <sdk-operator/flightservice>/gradle.properties file and adding the url of your Openshift cluster. For example:
    ```
    openshift.console.url=https://console-openshift-console.apps.testing.com:6443
    ```
- Obtaining the Openshift administrator username and password and adding them to your local `~/.gradle/encrypted.gradle.properties` by running the following commands:
    ```
    $ ./gradlew addCredentials --key "openshift.username" --value <your openshift user>
    $ ./gradlew addCredentials --key "openshift.password" --value <your openshift password>
    ```

After doing one of the above, you can then deploy the flightservice operator by running these commands:
```
$ cd <location of sdk-operator/flightservice>
$ ./gradlew deploy-operator
```
## Using the flightservice operator to deploy a Flight Service

Create a flightservice deployment file containing the following:
```
apiVersion: flight.wdp.ibm.com/v1alpha1
kind: FlightService
metadata:
  name: noop
spec:
  replicas: 1
  dockerImage: default-route-openshift-image-registry.apps.my-openshift.com/myproject/noop-flight-service:0.0.2
```
Save the file as `<flight service name>.yaml` for example, `noop-flight-service.yaml`

You can then deploy the flight service using
```
$ oc apply -f noop-flight-service.yaml
```
Afterwards you can see the deployment of the service
```
$ oc get deployment
NAME                                    READY   UP-TO-DATE   AVAILABLE   AGE
noop-flight-service                     1/1     1            1           1m
```
And the flight service pod will be running
```
$ oc get pods
NAME                                  READY     STATUS    RESTARTS   AGE
noop-flight-service-6fd7c98d8-7dqdr   1/1       Running   0          1m
```
## Adding environment variables to your flight service
You can make your flight service more dynamic by using environment variables to define what it does. To accomplish this you will need to make changes to the flightservice operator and redeploy it.

Add an `env` section to the `roles/flightservice/templates/deployment.yaml.j2` file:
```
...
        resources:
          limits:
            cpu: {{ resources.cpu.limits }}
            memory: {{ resources.memory.limits }}
          requests:
            cpu: {{ resources.cpu.requests }}
            memory: {{ resources.memory.requests }}
        env:
          MY_ENV_VARIABLE: "my env value"
```
Instead of a string, you can also refer to a value in the `roles/flightservice/defaults/main.yml` file:
```
        env:
          MY_ENV_VARIABLE: "{{ env.myEnvVar }}"
```
and then the `roles/flightservice/defaults/main.yml` can be changed:
```
resources:
  cpu:
    limits: 1
    requests: 500m
  memory:
    limits: 4Gi
    requests: 2Gi
env:
  myEnvVar: "my env value"
```
After making the changes you can run the build to undeploy and deploy the flightservice operator:
```
$ ./gradlew undeploy-operator
$ ./gradlew deploy-operator
```

/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.Location;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ibm.connect.sdk.util.AuthUtils;
import com.ibm.connect.sdk.util.ClientTokenAuthHandler;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.connect.sdk.util.Utils;

/**
 * A client for interacting with Cloud Pak for Data.
 */
public class CloudClient implements Closeable
{
    private static final Logger LOGGER = getLogger(CloudClient.class);

    private static final int FLIGHT_PORT = 443;

    private static final String CONFIG_PROP_CLOUD_PREFIX = "cloud.";
    private static final String CONFIG_PROP_CLOUD_TYPE = "cloud.type";

    private static final String DEFAULT_CONTAINER_TYPE = "project";
    private static final String ORIGIN_COUNTRY = "us";

    private static final int HTTP_CONNECTION_SOCKET_TIMEOUT = 50 * 1000;
    private static final int HTTP_CONNECTION_READ_TIMEOUT = 50 * 1000;
    private static final String HTTPS_PREFIX = "https://";
    private static final String CHARSET_UTF8 = ";charset=utf8";
    private static final String FORM_URL_ENCODED = URLEncodedUtils.CONTENT_TYPE + CHARSET_UTF8;

    private final CloseableHttpClient httpClient;
    private final BufferAllocator allocator;
    private final BufferAllocator clientAllocator;
    private final FlightClient flightClient;
    private final String authToken;

    /**
     * Constructs a client for interacting with Cloud Pak for Data.
     *
     * @throws Exception
     */
    public CloudClient() throws Exception
    {
        httpClient = createHttpClient();
        allocator = new RootAllocator(Long.MAX_VALUE);
        clientAllocator = allocator.newChildAllocator("client", 0, Long.MAX_VALUE);
        final Location location = Utils.createLocation(getFlightHost(), FLIGHT_PORT, true);
        LOGGER.info("Connecting to Flight server at " + location.getUri());
        final String sslCert = getSSLCertificate();
        final String verifyCert = getSSLCertificateValidation();
        final FlightClient.Builder clientBuilder = FlightClient.builder(clientAllocator, location).useTls();
        if (!Boolean.parseBoolean(verifyCert)) {
            clientBuilder.verifyServer(false);
        } else if (sslCert != null) {
            clientBuilder.trustedCertificates(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)));
        }
        flightClient = clientBuilder.build();
        authToken = getAuthToken();
        flightClient.authenticate(new ClientTokenAuthHandler(getFlightAuthToken()));
    }

    /**
     * Returns the Flight client.
     *
     * @return the Flight client
     */
    public FlightClient getFlightClient()
    {
        return flightClient;
    }

    private CloseableHttpClient createHttpClient() throws Exception
    {
        final HttpClientBuilder builder = HttpClientBuilder.create().useSystemProperties();
        builder.setDefaultRequestConfig(RequestConfig.custom().setNormalizeUri(false).setConnectTimeout(HTTP_CONNECTION_SOCKET_TIMEOUT)
                .setSocketTimeout(HTTP_CONNECTION_READ_TIMEOUT).build());

        // If a certificate was supplied, verify the certificate but skip host name
        // verification if requested. Otherwise trust all server certificates if
        // requested.
        final String sslCert = getSSLCertificate();
        final boolean verifyCert = Boolean.parseBoolean(getSSLCertificateValidation());
        builder.setSSLContext(sslCert != null ? SSLUtils.buildSSLContext(sslCert) : SSLUtils.buildSSLContext(!verifyCert));
        if (!verifyCert) {
            builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }
        return builder.build();
    }

    private String getAuthToken() throws Exception
    {
        final String iamUrl = getCloudConfigProperty("iam_url");
        final String apiKey = getCloudConfigProperty("api_key");
        final String username = getCloudConfigProperty("user_name");
        final String password = getCloudConfigProperty("user_pass");
        final String accessToken;
        if (iamUrl != null && apiKey != null) {
            accessToken = getPublicCloudToken(iamUrl, apiKey);
        } else if (username != null && password != null) {
            accessToken = getPrivateCloudToken(username, password);
        } else {
            throw new IllegalArgumentException("Missing cloud credentials in test configuration");
        }
        return accessToken;
    }

    private String getFlightAuthToken()
    {
        final JsonObject json = new JsonObject();
        json.addProperty("authorization", getAuthHeader());
        return json.toString();
    }

    /**
     * Returns a cloud authorization header.
     *
     * @return a cloud authorization header
     * @throws Exception
     */
    public String getAuthHeader()
    {
        return AuthUtils.AUTHORIZATION_BEARER + authToken;
    }

    private String getPublicCloudToken(String iamUrl, String apiKey) throws Exception
    {
        final String endpoint = iamUrl + "/identity/token";
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter("grant_type", "urn:ibm:params:oauth:grant-type:apikey").setParameter("apikey", apiKey);
        final HttpPost request = new HttpPost(builder.build());
        request.addHeader(HttpHeaders.CONTENT_TYPE, FORM_URL_ENCODED);
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        LOGGER.info("Executing HTTP request POST " + endpoint);
        final JsonObject response = executeHttpRequest(request);
        return response.get("access_token").getAsString();
    }

    private String getPrivateCloudToken(String username, String password) throws Exception
    {
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v1/preauth/validateAuth";
        final HttpGet request = new HttpGet(endpoint);
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        request.addHeader("username", username);
        request.addHeader("password", password);
        LOGGER.info("Executing HTTP request " + request);
        final JsonObject response = executeHttpRequest(request);
        return response.get("accessToken").getAsString();
    }

    private JsonObject executeHttpRequest(HttpUriRequest request) throws Exception
    {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }
            final String responseString = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
                throw new Exception(responseString != null ? responseString : response.toString());
            }
            return new Gson().fromJson(responseString, JsonObject.class);
        }
    }

    /**
     * Registers a custom flight server.
     *
     * @param uri
     *            the Flight server URI
     * @param sslCert
     *            the SSL certificate of the server or null
     * @param verifyCert
     *            true if the SSL certificate should be validated
     * @throws Exception
     */
    public void registerFlightServer(String uri, String sslCert, boolean verifyCert) throws Exception
    {
        final JsonObject flightInfo = new JsonObject();
        flightInfo.addProperty("flight_uri", uri);
        if (sslCert != null) {
            flightInfo.addProperty("ssl_certificate", sslCert);
        }
        flightInfo.addProperty("ssl_certificate_validation", verifyCert);
        final JsonObject requestJson = new JsonObject();
        requestJson.add("flight_info", flightInfo);
        requestJson.addProperty("origin_country", ORIGIN_COUNTRY);
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/datasource_types";
        final HttpPost request = new HttpPost(endpoint);
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.setEntity(new StringEntity(requestJson.toString(), ContentType.APPLICATION_JSON));
        LOGGER.info("Executing HTTP request " + request);
        executeHttpRequest(request);
    }

    /**
     * Creates a connection in the cloud.
     *
     * @param name
     *            the connection name
     * @param dsTypeName
     *            the data source type name
     * @param connectionProperties
     *            the connection properties
     * @return the connection ID
     * @throws Exception
     */
    public String createConnection(String name, String dsTypeName, JsonObject connectionProperties) throws Exception
    {
        final JsonObject requestJson = new JsonObject();
        requestJson.addProperty("name", name);
        requestJson.addProperty("origin_country", ORIGIN_COUNTRY);
        requestJson.addProperty("datasource_type", dsTypeName);
        requestJson.add("properties", connectionProperties);
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections";
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter(containerParam, containerId);
        final HttpPost request = new HttpPost(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.setEntity(new StringEntity(requestJson.toString(), ContentType.APPLICATION_JSON));
        LOGGER.info("Executing HTTP request " + request);
        final JsonObject response = executeHttpRequest(request);
        final JsonObject metadata = response.getAsJsonObject("metadata");
        return metadata.get("asset_id").getAsString();
    }

    /**
     * Returns the name of the container parameter such as "project_id" or
     * "catalog_id".
     *
     * @return the name of the container parameter such as "project_id" or
     *         "catalog_id"
     */
    public String getContainerParameter()
    {
        final String containerType = getCloudConfigProperty("container_type", DEFAULT_CONTAINER_TYPE);
        return containerType + "_id";
    }

    /**
     * Returns the container ID.
     *
     * @return the container ID
     */
    public String getContainerId()
    {
        return getCloudConfigProperty("container_id");
    }

    /**
     * Deletes a connection from the cloud.
     *
     * @param id
     *            the connection ID
     * @throws Exception
     */
    public void deleteConnection(String id) throws Exception
    {
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections/" + id;
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter(containerParam, containerId);
        final HttpDelete request = new HttpDelete(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        LOGGER.info("Executing HTTP request " + request);
        executeHttpRequest(request);
    }

    /**
     * Discovers assets in a connection on the cloud.
     *
     * @param id
     *            the connection ID
     * @param path
     *            the asset path
     * @return the discovered assets
     * @throws Exception
     */
    public JsonObject discoverConnection(String id, String path) throws Exception
    {
        return discoverConnection(id, path, null, null);
    }

    /**
     * Discovers assets with filters in a connection on the cloud.
     *
     * @param id
     *            the connection ID
     * @param path
     *            the asset path
     * @param filters
     *            discovery filters
     * @return the discovered assets
     * @throws Exception
     */
    public JsonObject discoverConnection(String id, String path, JsonObject filters) throws Exception
    {
        return discoverConnection(id, path, filters, null);
    }

    /**
     * Discovers assets with fetch parameters in a connection on the cloud.
     *
     * @param id
     *            the connection ID
     * @param path
     *            the asset path
     * @param fetch
     *            what to fetch such as
     *            "data,metadata,interaction,connection,datasource_type"
     * @return the discovered assets
     * @throws Exception
     */
    public JsonObject discoverConnection(String id, String path, String fetch) throws Exception
    {
        return discoverConnection(id, path, null, fetch);
    }

    /**
     * Discovers assets with filters and fetch parameters in a connection on the
     * cloud.
     *
     * @param id
     *            the connection ID
     * @param path
     *            the asset path
     * @param filters
     *            discovery filters
     * @param fetch
     *            what to fetch such as
     *            "data,metadata,interaction,connection,datasource_type"
     * @return the discovered assets
     * @throws Exception
     */
    public JsonObject discoverConnection(String id, String path, JsonObject filters, String fetch) throws Exception
    {
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections/" + id + "/assets";
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter("path", path);
        builder.setParameter(containerParam, containerId);
        if (filters != null) {
            builder.setParameter("filters", filters.toString());
        }
        if (fetch != null) {
            builder.setParameter("fetch", fetch);
        }
        final HttpGet request = new HttpGet(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        LOGGER.info("Executing HTTP request " + request);
        return executeHttpRequest(request);
    }

    /**
     * Discovers assets with filters and paging in a connection on the cloud.
     *
     * @param id
     *            the connection ID
     * @param path
     *            the asset path
     * @param filters
     *            discovery filters
     * @param offset
     *            the number of assets to skip
     * @param limit
     *            the maximum number of assets to return
     * @return the discovered assets
     * @throws Exception
     */
    public JsonObject discoverConnection(String id, String path, JsonObject filters, int offset, int limit) throws Exception
    {
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections/" + id + "/assets";
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter("path", path);
        builder.setParameter(containerParam, containerId);
        builder.setParameter("offset", Integer.toString(offset));
        builder.setParameter("limit", Integer.toString(limit));
        if (filters != null) {
            builder.setParameter("filters", filters.toString());
        }
        final HttpGet request = new HttpGet(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        LOGGER.info("Executing HTTP request " + request);
        return executeHttpRequest(request);
    }

    /**
     * Lists all supported actions for the connection.
     *
     * @param id
     *            the connection ID
     * @return the actions supported for the connection
     * @throws Exception
     */
    public JsonObject listActions(String id) throws Exception
    {
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections/" + id + "/actions";
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter(containerParam, containerId);
        final HttpGet request = new HttpGet(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        LOGGER.info("Executing HTTP request " + request);
        return executeHttpRequest(request);
    }

    /**
     * Performs an action on the data source using a connection.
     *
     * @param id
     *            the connection ID
     * @param actionName
     *            the name of the action to be performed
     * @param inputProperties
     *            input properties
     * @return the output properties
     * @throws Exception
     */
    public JsonObject performAction(String id, String actionName, JsonObject inputProperties) throws Exception
    {
        final String containerParam = getContainerParameter();
        final String containerId = getContainerId();
        final String endpoint = HTTPS_PREFIX + getAPIHost() + "/v2/connections/" + id + "/actions/" + actionName;
        final URIBuilder builder = new URIBuilder(endpoint);
        builder.setParameter(containerParam, containerId);
        final HttpPut request = new HttpPut(builder.build());
        request.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        request.setEntity(new StringEntity(inputProperties.toString(), ContentType.APPLICATION_JSON));
        LOGGER.info("Executing HTTP request " + request);
        return executeHttpRequest(request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        try {
            httpClient.close();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            flightClient.close();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        clientAllocator.close();
        allocator.close();
    }

    /**
     * Returns the API host name.
     *
     * @return the API host name
     */
    public static String getAPIHost()
    {
        return getCloudConfigProperty("host");
    }

    private static String getFlightHost()
    {
        return getCloudConfigProperty("flight.host", getAPIHost());
    }

    /**
     * Returns the cloud SSL certificate.
     *
     * @return the cloud SSL certificate
     */
    private static String getSSLCertificate()
    {
        return getCloudConfigProperty("ssl_certificate");
    }

    private static String getSSLCertificateValidation()
    {
        return getCloudConfigProperty("ssl_certificate_validation", "true");
    }

    private static String getCloudConfigProperty(String keySuffix)
    {
        return getCloudConfigProperty(keySuffix, null);
    }

    private static String getCloudConfigProperty(String keySuffix, String defaultValue)
    {
        final String cloudType = TestConfig.get(CONFIG_PROP_CLOUD_TYPE);
        if (cloudType == null) {
            return null;
        }
        final String key = CONFIG_PROP_CLOUD_PREFIX + cloudType + "." + keySuffix;
        return TestConfig.get(key, defaultValue);
    }

}

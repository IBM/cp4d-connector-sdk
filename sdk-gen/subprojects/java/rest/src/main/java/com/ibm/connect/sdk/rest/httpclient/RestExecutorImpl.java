/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.httpclient;

import static com.ibm.connect.sdk.rest.RestMsgs.SERVER_NOT_REACHABLE;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic REST API Connector using Apache HttpClient 5.
 * Supports all HTTP methods, headers, query parameters, payloads.
 */
@SuppressWarnings("PMD")
public class RestExecutorImpl {
    private static final Logger logger = LoggerFactory.getLogger(RestExecutorImpl.class);

    private final CloseableHttpClient httpClient;

    public RestExecutorImpl(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Executes HEAD request to the specified URL to ensure server reachable
     *
     * @param url Base URL
     * @return
     * @throws IOException
     */
    public boolean checkConnection(String url) throws IOException {
        HttpClientResponseHandler<Boolean> responseHandler = response -> {
            int status = response.getCode();
            // Consider 2xx and 3xx as reachable
            if (status >= 200 && status < 400) {
                return true;
            } else {
                throw new HttpResponseException(status, SERVER_NOT_REACHABLE.format(url));
            }
        };

        return this.executeRequest("HEAD", url, Collections.emptyMap(), Collections.emptyMap(), null, responseHandler);
    }

    /**
     * Executes a generic REST API request, using responseHandler to process the response and return the result.
     *
     * @param method       HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param url          Base URL
     * @param headers      Request headers
     * @param queryParams  Query parameters
     * @param bodyPayload  Request body (JSON, XML, etc.)
     * @param responseHandler Handler to handle response object
     * @return
     * @param <T>
     * @throws IOException in case of network or I/O errors
     */
    public <T> T executeRequest(
            String method,
            String url,
            Map<String, String> headers,
            Map<String, String> queryParams,
            String bodyPayload,
            HttpClientResponseHandler<T> responseHandler
    ) throws IOException {
        String finalUrl = buildUrlWithParams(url, queryParams);
        ClassicHttpRequest request = createRequest(method, finalUrl, bodyPayload);

        if (headers != null) {
            headers.forEach(request::addHeader);
        }

        return httpClient.execute(request, responseHandler);
    }

    private ClassicHttpRequest createRequest(String method, String url, String body) {
        switch (method == null ? "" : method.toUpperCase()) {
            case "GET":
                return new HttpGet(url);
            case "POST":
                HttpPost post = new HttpPost(url);
                if (body != null) {
                    post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                }
                return post;
            case "PUT":
                HttpPut put = new HttpPut(url);
                if (body != null) {
                    put.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                }
                return put;
            case "DELETE":
                return new HttpDelete(url);
            case "PATCH":
                HttpPatch patch = new HttpPatch(url);
                if (body != null) {
                    patch.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                }
                return patch;
            case "HEAD":
                return new HttpHead(url);
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }

    private String buildUrlWithParams(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        String paramString = queryParams.entrySet()
                .stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return url + "?" + paramString;
    }

    public void close() throws IOException {
        if(httpClient != null) {
            httpClient.close();
        }
    }
}


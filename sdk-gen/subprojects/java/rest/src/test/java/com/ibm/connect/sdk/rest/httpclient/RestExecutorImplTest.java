/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.httpclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

public class RestExecutorImplTest {
    @Test
    public void testGetHttpCall() throws IOException {
        final HttpClientFactory.Builder httpBuilder = new HttpClientFactory.Builder();
        final RestExecutorImpl connector = new RestExecutorImpl(httpBuilder.buildFactory().build());
        final HttpClientResponseHandler<String> responseHandler = response -> {
            HttpEntity entity = response.getEntity();
            return entity != null
                    ? EntityUtils.toString(entity, StandardCharsets.UTF_8)
                    : null;
        };
        final String response = connector.executeRequest(
                "GET",
                "https://jsonplaceholder.typicode.com/posts",
                Map.of("Accept", "application/json"),
                Map.of("userId", "1"),
                null, responseHandler
        );
        Assert.assertNotNull(response);
        connector.close();
    }

    @Test
    public void testHeadHttpCall() throws IOException {
        final HttpClientFactory.Builder httpBuilder = new HttpClientFactory.Builder();
        final RestExecutorImpl connector = new RestExecutorImpl(httpBuilder.buildFactory().build());
        final boolean response = connector.checkConnection("https://jsonplaceholder.typicode.com/posts");
        Assert.assertTrue(response);
        connector.close();
    }
}

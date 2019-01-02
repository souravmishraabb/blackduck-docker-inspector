/**
 * blackduck-docker-inspector
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.dockerinspector.restclient.connection;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.rest.connection.RestConnection;
import com.synopsys.integration.rest.proxy.ProxyInfo;
import com.synopsys.integration.rest.request.Response;
import org.apache.http.impl.client.CloseableHttpClient;

public class NonRedirectingUnauthenticatedRestConnection extends RestConnection {

    public NonRedirectingUnauthenticatedRestConnection(final IntLogger logger, final int timeout, final boolean trustCert, final ProxyInfo proxyInfo) {
        super(logger, timeout, trustCert, proxyInfo);
        logger.debug("Disabling redirect handling on this rest connection");
        getClientBuilder().disableRedirectHandling();
    }

    @Override
    public Response execute(final HttpUriRequest request) throws IntegrationException {
        final long start = System.currentTimeMillis();
        logger.trace("starting request: " + request.getURI().toString());
        try {
            return handleClientExecution(request);
        } finally {
            final long end = System.currentTimeMillis();
            logger.trace(String.format("completed request: %s (%d ms)", request.getURI().toString(), end - start));
        }
    }

    private Response handleClientExecution(final HttpUriRequest request) throws IntegrationException {
        finalizeRequest(request);

        try {
            final CloseableHttpClient client = getClientBuilder().build();
            final URI uri = request.getURI();
            final String urlString = request.getURI().toString();
            if (isAlwaysTrustServerCertificate() && uri.getScheme().equalsIgnoreCase("https")) {
                logger.debug("Automatically trusting the certificate for " + urlString);
            }
            logRequestHeaders(request);
            final CloseableHttpResponse closeableHttpResponse = client.execute(request);
            final Response response = new Response(request, client, closeableHttpResponse);
            logResponseHeaders(closeableHttpResponse);
            // TODO need something here for redirects; see commented code below
            if (response.isStatusCodeError()) {
                handleErrorResponse(request, response);
            }
            return response;
        } catch (final IOException e) {
            throw new IntegrationException(e.getMessage(), e);
        }
    }

    // TODO delete:
//    public Response executeORIG(final HttpUriRequest request) throws IntegrationException {
//        final long start = System.currentTimeMillis();
//        logger.trace("starting request: " + request.getURI().toString());
//        try {
//            return handleClientExecution(request, 0);
//        } finally {
//            final long end = System.currentTimeMillis();
//            logger.trace(String.format("completed request: %s (%d ms)", request.getURI().toString(), end - start));
//        }
//    }

    // TODO delete:
//    private Response handleClientExecutionORIG(final HttpUriRequest request, final int retryCount) throws IntegrationException {
//        logger.debug(String.format("NonRedirectingUnauthenticatedRestConnection.handleClientExecution() called: %s", request.getURI().toString()));
//        if (getClient() != null) {
//            try {
//                final URI uri = request.getURI();
//                final String urlString = request.getURI().toString();
//                if (isAlwaysTrustServerCertificate() && uri.getScheme().equalsIgnoreCase("https") && logger != null) {
//                    logger.debug("Automatically trusting the certificate for " + urlString);
//                }
//                logRequestHeaders(request);
//                final CloseableHttpResponse response = getClient().execute(request);
//                final int statusCode = response.getStatusLine().getStatusCode();
//                logger.debug(String.format("statusCode: %s", statusCode));
//                final String statusMessage = response.getStatusLine().getReasonPhrase();
//                if (statusCode < RestConstants.OK_200 || statusCode >= RestConstants.BAD_REQUEST_400) {
//                    try {
//                        if (statusCode == RestConstants.UNAUTHORIZED_401 && retryCount < 2) {
//                            connect();
//                            final HttpUriRequest newRequest = copyHttpRequest(request);
//                            return handleClientExecution(newRequest, retryCount + 1);
//                        } else {
//                            String warningHeaderValue;
//                            String responseBody;
//                            try (Response resp = new Response(response)) {
//                                warningHeaderValue = resp.getHeaderValue("Warning");
//                                responseBody = resp.getContentString();
//                            }
//                            throw new IntegrationRestException(statusCode, statusMessage, responseBody,
//                                    String.format("There was a problem trying to %s this item: %s. Error: %s %s; Warning header: '%s'; Body: '%s'", request.getMethod(), urlString, statusCode, statusMessage, warningHeaderValue,
//                                            responseBody));
//                        }
//                    } finally {
//                        response.close();
//                    }
//                }
//                logResponseHeaders(response);
//                return new Response(response);
//            } catch (final IOException e) {
//                throw new IntegrationException(e.getMessage(), e);
//            }
//        } else {
//            connect();
//            final HttpUriRequest newRequest = copyHttpRequest(request);
//            return handleClientExecution(newRequest, retryCount);
//        }
//    }
}

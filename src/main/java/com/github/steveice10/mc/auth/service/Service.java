package com.github.steveice10.mc.auth.service;

import lombok.Getter;

import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for auth-related services.
 */
@Getter
public abstract class Service {
    /**
     * Endpoint names will be appended to the base URI when making requests.
     */
    private URI baseUri;
    private Proxy proxy = Proxy.NO_PROXY;

    public Service(URI initialBaseUri) {
        if (initialBaseUri == null) throw new IllegalArgumentException("Initial Base URI cannot be null.");
        this.baseUri = initialBaseUri;
    }

    /**
     * Sets the base URI of this service.
     * Endpoint names will be appended to the base URI when making requests.
     *
     * @param baseUri The base URI to use.
     * @throws IllegalArgumentException If the provided base URI is null or malformed.
     */
    @SuppressWarnings("unused")
    public void setBaseUri(String baseUri) {
        if (baseUri == null) throw new IllegalArgumentException("Base URI cannot be null.");
        try {
            this.setBaseUri(new URI(baseUri));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid base URI.", e);
        }
    }

    /**
     * Sets the base URI of this service.
     * Endpoint names will be appended to the base URI when making requests.
     *
     * @param baseUri The base URI to use.
     * @throws IllegalArgumentException If the provided base URI is null.
     */
    public void setBaseUri(URI baseUri) {
        if (baseUri == null) throw new IllegalArgumentException("Base URI cannot be null.");
        this.baseUri = baseUri;
    }

    /**
     * Gets the URI of a specific endpoint of this service.
     *
     * @param endpoint Endpoint to get the URI of.
     * @return The URI for the given endpoint.
     */
    public URI getEndpointUri(String endpoint) {
        return this.baseUri.resolve(endpoint);
    }

    /**
     * Gets the URI of a specific endpoint of this service.
     *
     * @param endpoint    Endpoint to get the URI of.
     * @param queryParams Query parameters to append to the URI.
     * @return The URI for the given endpoint.
     */
    public URI getEndpointUri(String endpoint, Map<String, String> queryParams) {
        var base = this.getEndpointUri(endpoint);
        try {
            var queryString = new StringBuilder();
            for (var queryParam : queryParams.entrySet()) {
                if (queryString.length() > 0)
                    queryString.append("&");
                queryString.append(queryParam.getKey()).append('=').append(URLEncoder.encode(queryParam.getValue(), StandardCharsets.UTF_8));
            }

            return new URI(base.getScheme(), base.getAuthority(), base.getPath(), queryString.toString(), base.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Arguments resulted in invalid endpoint URI.", e);
        }
    }

    /**
     * Sets the proxy used by this service.
     *
     * @param proxy Proxy to use. Null will be converted to NO_PROXY.
     */
    @SuppressWarnings("unused")
    public void setProxy(Proxy proxy) {
        this.proxy = Objects.requireNonNullElse(proxy, Proxy.NO_PROXY);
    }
}

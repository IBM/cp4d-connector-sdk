/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import static com.ibm.connect.sdk.rest.pagination.PaginationHelper.DEFAULT_MAX_LIMIT;
import static com.ibm.connect.sdk.rest.pagination.PaginationHelper.DEFAULT_OFFSET;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.RowBasedConnector;
import com.ibm.connect.sdk.rest.discovery.RestDiscoveryHandler;
import com.ibm.connect.sdk.rest.httpclient.HttpClientFactory;
import com.ibm.connect.sdk.rest.httpclient.OAuth2TokenManager;
import com.ibm.connect.sdk.rest.httpclient.RestExecutorImpl;
import com.ibm.connect.sdk.rest.utils.EntityTypeNodeHelper;
import com.ibm.connect.sdk.rest.utils.RestConnectionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.EntityTypeNode;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.rest.validator.RestApiConnectorValidator;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestConnector
        extends RowBasedConnector<RestSourceInteraction, RestTargetInteraction>
{
    private static final Logger LOGGER = getLogger(RestConnector.class);

    private final RestConnectionProperties restConnectionProperties;
    private final Map<String, EntityType> entityTypeMap;
    private final EntityTypeNode entityTypeRootNode;
    private final RestExecutorImpl restApiExecutor;



    /**
     * Creates a row-based connector.
     *
     * @param properties
     *            connection properties
     */
    public RestConnector(ConnectionProperties properties)
    {
        super(properties);

        this.restConnectionProperties = new RestConnectionProperties(this.getConnectionProperties());
        RestApiConnectorValidator.validate(this.restConnectionProperties);
        this.entityTypeRootNode = EntityTypeNodeHelper.buildEntityChain(this.restConnectionProperties.getRestConfiguration().getEntityTypes());
        this.entityTypeMap = this.restConnectionProperties.getRestConfiguration().getEntityTypes().stream().collect(Collectors.toMap(EntityType::getName, Function.identity()));

        final HttpClientFactory.Builder httpBuilder = new HttpClientFactory.Builder();

        if(!RestUtils.isNullOrEmpty(restConnectionProperties.getSslCertificate())) {
            httpBuilder.withSslCertificate(restConnectionProperties.getSslCertificate());
        }

        if(this.restConnectionProperties.isAuthenticationBasic()) {
            httpBuilder.withBasicAuth(this.restConnectionProperties.getUsername(), this.restConnectionProperties.getPassword());
        } else if(this.restConnectionProperties.isAuthenticationOAuth2()) {
            // Create a temporary HTTP client for OAuth token requests
            final HttpClientFactory.Builder tokenClientBuilder = new HttpClientFactory.Builder();
            if(!RestUtils.isNullOrEmpty(restConnectionProperties.getSslCertificate())) {
                tokenClientBuilder.withSslCertificate(restConnectionProperties.getSslCertificate());
            }
            final OAuth2TokenManager tokenManager = new OAuth2TokenManager(
                this.restConnectionProperties.getOAuth2TokenUrl(),
                this.restConnectionProperties.getOAuth2ClientId(),
                this.restConnectionProperties.getOAuth2ClientSecret(),
                this.restConnectionProperties.getOAuth2Scope(),
                this.restConnectionProperties.getOAuth2GrantType(),
                this.restConnectionProperties.getOAuth2RefreshToken(),
                tokenClientBuilder.buildFactory().build()
            );

            httpBuilder.withOAuth2(tokenManager);
        }

        this.restApiExecutor = new RestExecutorImpl(httpBuilder.buildFactory().build());
    }

    @Override
    public void close() throws Exception
    {
        try {
            this.restApiExecutor.close();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void connect() throws Exception {
       this.restApiExecutor.checkConnection(this.restConnectionProperties.getBasicUrl());
    }

    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final RestDiscoveryHandler discoveryHandler = new RestDiscoveryHandler(this.restApiExecutor, this.restConnectionProperties);
        final String path = Optional.ofNullable(criteria).map(CustomFlightAssetsCriteria::getPath).orElse("/");
        final int levelToDiscover = RestUtils.determineNextLevelToDiscover(path);
        final int totalLevels = this.entityTypeMap.size();
        final EntityTypeNode currentNode = EntityTypeNodeHelper.getNthLevel(this.entityTypeRootNode, levelToDiscover);
        final EntityType entityType = this.entityTypeMap.get(currentNode.getName());
        final List<CustomFlightAssetDescriptor> assets;

        // Extract offset and limit from criteria to pass to the handler
        final Integer offset = Optional.ofNullable(criteria).map(CustomFlightAssetsCriteria::getOffset).orElse(DEFAULT_OFFSET);
        final Integer maxPageLimitAllowsByAPI = Optional.ofNullable(entityType.getPagination())
                .map(Pagination::getSupportedMaxLimit)
                .orElse(DEFAULT_MAX_LIMIT);
        final Integer defaultLimit = Math.min(DEFAULT_MAX_LIMIT, maxPageLimitAllowsByAPI);
        final Integer limit = Optional.ofNullable(criteria).map(CustomFlightAssetsCriteria::getLimit).orElse(defaultLimit);


        if(levelToDiscover < totalLevels) {
            // Its container asset
            assets = discoveryHandler.listContainerAssets(path, entityType,
                    getEntityTypeNodeByLevel(), offset, limit);
        } else {
            // Its leaf asset, hence add only the response field information. (CustomFlightAssetDescriptor.Fields)
            final CustomFlightAssetDescriptor asset = discoveryHandler.listLeafAsset(path, entityType, getEntityTypeNodeByLevel());
            assets = Collections.singletonList(asset);
        }
        return assets;
    }

    @Override
    public RestSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception {
        return new RestSourceInteraction(this, asset, ticket);
    }

    @Override
    public RestTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset)
    {
        return new RestTargetInteraction(this, asset);
    }

    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration conf)
    {
        throw new UnsupportedOperationException(RestMsgs.UNEXPECTED_RESPONSE_EXECUTING_REST_API.format(action));
    }

    @Override
    public void commit()
    {
        // Nothing to do

    }

    public RestExecutorImpl getRestApiExecutor() {
        return restApiExecutor;
    }

    public RestConnectionProperties getRestConnectionProperties() {
        return restConnectionProperties;
    }

    /**
     * Returns a map containing all entity types, where the key represents the entity name.
     */
    public Map<String, EntityType> getEntityTypeMap() {
        return entityTypeMap;
    }

    /**
     * Returns the root node of the entity type hierarchy.
     */
    public EntityTypeNode getEntityTypeRootNode() {
        return entityTypeRootNode;
    }

    public IntFunction<EntityTypeNode> getEntityTypeNodeByLevel() {
        return level -> EntityTypeNodeHelper.getNthLevel(this.entityTypeRootNode, level);
    }

}

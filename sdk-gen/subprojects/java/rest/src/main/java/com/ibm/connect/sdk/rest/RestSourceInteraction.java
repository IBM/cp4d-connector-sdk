/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import static com.ibm.connect.sdk.rest.utils.RestUtils.mapToFlightAssetFields;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.apache.arrow.flight.Ticket;
import org.apache.hc.core5.http.Header;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.RowBasedSourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.rest.request.EntityTypeRequestHandler;
import com.ibm.connect.sdk.rest.response.EntityTypeResponseHandler;
import com.ibm.connect.sdk.rest.response.PaginatedRecordProvider;
import com.ibm.connect.sdk.rest.utils.EntityTypeNodeHelper;
import com.ibm.connect.sdk.rest.utils.RestSourceInteractionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.EntityTypeNode;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestSourceInteraction extends RowBasedSourceInteraction<RestConnector>
{
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private final ModelMapper modelMapper = new ModelMapper();
    private final RestSourceInteractionProperties sourceInteractionProperties;
    private final EntityTypeRequestHandler entityTypeRequestHandler;
    private final PaginatedRecordProvider  paginatedRecordProvider;
    private final Map<String, String> entityNameAndSelectedAssetIdMap;
    private final EntityType currentEntityType;
    private List<CustomFlightAssetField>  fields;
    private List<Header> prevPageHeaders = Collections.emptyList();

    @SuppressWarnings("PMD.UnusedFormalParameter")
    protected RestSourceInteraction(RestConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) {
        super();
        setConnector(connector);
        if (asset.getBatchSize() == null) {
            asset.setBatchSize(DEFAULT_BATCH_SIZE);
        }
        setAsset(asset);

        this.sourceInteractionProperties = new RestSourceInteractionProperties(ModelMapper.toProperties(this.getAsset().getInteractionProperties()));
        this.entityNameAndSelectedAssetIdMap = this.sourceInteractionProperties.getSelectedParentEntityValues();
        final int lastLevel = this.getConnector().getEntityTypeMap().size();
        final EntityTypeNode currentEntityTypeNode = EntityTypeNodeHelper.getNthLevel(this.getConnector().getEntityTypeRootNode(), lastLevel);
        this.currentEntityType = this.getConnector().getEntityTypeMap().get(currentEntityTypeNode.getName());
        this.entityTypeRequestHandler = new EntityTypeRequestHandler(this.getConnector().getRestApiExecutor(), this.getConnector().getRestConnectionProperties());
        this.paginatedRecordProvider = new PaginatedRecordProvider(fetchDataByPageNumber());
    }

    @Override
    public Record getRecord()
    {
        return this.paginatedRecordProvider.getRecord();
    }

    @Override
    public void close() throws Exception
    {
        super.close();
    }

    @Override
    public List<Ticket> getTickets() throws Exception
    {
        final String requestId = UUID.randomUUID().toString();
        return Collections.singletonList(new Ticket(modelMapper.toBytes(new TicketInfo().requestId(requestId).partitionIndex(0))));
    }

    @Override
    public List<CustomFlightAssetField> getFields()
    {
        if(this.fields == null || this.fields.isEmpty()) {
            try {
                final EntityTypeResponseHandler responseHandler = this.entityTypeRequestHandler.executeRequest(currentEntityType, entityNameAndSelectedAssetIdMap);
                this.fields = mapToFlightAssetFields(responseHandler.getFieldDefinitions());
            } catch (IOException e) {
                throw new RuntimeException("Unable to fetch the fields. Ensure you provided interaction property `selected_parent_entity_values`", e);
            }
        }
        return this.fields;
    }

	private Function<Integer, List<Map<String, Object>>> fetchDataByPageNumber() {
		return (pageNum) -> {
			try {
                //Here pagination index always start with 1. Refer: PaginatedRecordProvider class
                if(pageNum == 1 || RestUtils.isPaginationSupported(currentEntityType.getPagination())) { //To navigate to next pages, pagination configuration is must
                    final int configuredBatch = getAsset().getBatchSize();  //Already have default value to DEFAULT_BATCH_SIZE
                    int limit = (configuredBatch > 0) ? configuredBatch : DEFAULT_BATCH_SIZE;
                    final Integer maxPageLimitAllowsByAPI = Optional.ofNullable(currentEntityType.getPagination())
                                                                    .map(Pagination::getSupportedMaxLimit)
                                                                    .orElse(limit);
                    limit = limit > maxPageLimitAllowsByAPI? maxPageLimitAllowsByAPI : limit;

                    // Calculate offset based on pageNum & batch size
                    final int offset = (pageNum - 1) * limit;
                    final List<EntityTypeResponseHandler> responseHandlers = this.entityTypeRequestHandler
                            .executeRequest(currentEntityType, entityNameAndSelectedAssetIdMap, offset, limit, prevPageHeaders);
                    final List<Map<String, Object>> fieldsValues = new ArrayList<>();
                    for(final EntityTypeResponseHandler responseHandler : responseHandlers)  {
                        fieldsValues.addAll(responseHandler.getFieldValueMap());
                    }
                    this.prevPageHeaders = !responseHandlers.isEmpty() ? responseHandlers.get(responseHandlers.size() - 1).getHeaders() : Collections.emptyList(); //Add last page response header.
                    return fieldsValues;
                }
                return Collections.emptyList();
			} catch (IOException e) {
				throw new RuntimeException("Failed to execute the API", e);
			}
		};
	}
}

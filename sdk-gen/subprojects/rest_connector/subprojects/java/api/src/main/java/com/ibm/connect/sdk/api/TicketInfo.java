/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

/**
 * A model for information contained in a Flight ticket.
 */
public class TicketInfo
{
    @JsonProperty("request_id")
    @SerializedName("request id")
    private String requestId;

    @JsonProperty("partition_index")
    @SerializedName("partition_index")
    private Integer partitionIndex;

    /**
     * @param requestId
     *            the requestId to set
     * @return the TicketInfo
     */
    @SuppressWarnings("hiding")
    public TicketInfo requestId(String requestId)
    {
        this.requestId = requestId;
        return this;
    }

    /**
     * @return the requestId
     */
    public String getRequestId()
    {
        return requestId;
    }

    /**
     * @param requestId
     *            the requestId to set
     */
    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    /**
     * @param partitionIndex
     *            the partitionIndex to set
     * @return the TicketInfo
     */
    @SuppressWarnings("hiding")
    public TicketInfo partitionIndex(Integer partitionIndex)
    {
        this.partitionIndex = partitionIndex;
        return this;
    }

    /**
     * @return the partitionIndex
     */
    public Integer getPartitionIndex()
    {
        return partitionIndex;
    }

    /**
     * @param partitionIndex
     *            the partitionIndex to set
     */
    public void setPartitionIndex(Integer partitionIndex)
    {
        this.partitionIndex = partitionIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TicketInfo ticketInfo = (TicketInfo) o;
        return Objects.equals(this.requestId, ticketInfo.requestId) && Objects.equals(this.partitionIndex, ticketInfo.partitionIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(requestId, partitionIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(100);
        sb.append("class TicketInfo {\n");

        sb.append("    requestId: ").append(toIndentedString(requestId)).append('\n');
        sb.append("    partitionIndex: ").append(toIndentedString(partitionIndex)).append('\n');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o)
    {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

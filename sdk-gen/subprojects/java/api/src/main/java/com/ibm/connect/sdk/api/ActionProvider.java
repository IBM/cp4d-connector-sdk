/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;

/**
 * An object that allows execution of specific actions in a generic way.
 */
public interface ActionProvider
{
    /**
     * Performs a custom action.
     *
     * @param action
     *            the name of the action to perform
     * @param properties
     *            action input properties
     * @return action output properties
     * @throws Exception
     */
    ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception;
}

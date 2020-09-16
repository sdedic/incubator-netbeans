/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.netbeans.modules.java.lsp.server.debugging.protocol;

import java.util.concurrent.CompletableFuture;

public interface IProtocolServer {
    /**
     * Send a request to the DA.
     *
     * @param request
     *            the request message.
     * @return a CompletableFuture.
     */
    CompletableFuture<Messages.Response> sendRequest(Messages.Request request);

    /**
     * Send a request to the DA. The future will complete exceptionally if no response is received at the give time.
     *
     * @param request
     *            the request message.
     * @param timeout
     *            the maximum time (in millis) to wait.
     * @return a CompletableFuture.
     */
    CompletableFuture<Messages.Response> sendRequest(Messages.Request request, long timeout);

    /**
     * Send an event to the DA.
     * @param event
     *              the event message.
     */
    void sendEvent(Events.DebugEvent event);

    /**
     * Send a response to the DA.
     * @param response
     *                  the response message.
     */
    void sendResponse(Messages.Response response);
}

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

package org.netbeans.modules.java.lsp.server.debugging;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.modules.java.lsp.server.debugging.protocol.IProtocolServer;
import org.netbeans.modules.java.lsp.server.debugging.protocol.JsonUtils;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;
import org.netbeans.modules.java.lsp.server.debugging.requests.HandlersCollection;

public class NbDebugAdapter implements IDebugAdapter {

    private static final Logger LOGGER = Logger.getLogger(NbDebugAdapter.class.getName());

    private final IDebugAdapterContext debugContext;
    private final HandlersCollection handlers;

    /**
     * Constructor.
     */
    public NbDebugAdapter(IProtocolServer server, IProviderContext providerContext) {
        providerContext.registerProvider(IShutdownProvider.class, new ShutdownHandler());
        this.debugContext = new DebugAdapterContext(server, providerContext);
        this.handlers = new HandlersCollection();
    }

    @Override
    public CompletableFuture<Messages.Response> dispatchRequest(Messages.Request request) {
        System.out.println("REQUEST: " + request.command + " " + request.arguments);
        Messages.Response response = new Messages.Response();
        response.request_seq = request.seq;
        response.command = request.command;
        response.success = true;

        Command command = Command.parse(request.command);
        Arguments cmdArgs = JsonUtils.fromJson(request.arguments, command.getArgumentType());

        if (debugContext.isVmTerminated() && command != Command.DISCONNECT) {
            return CompletableFuture.completedFuture(response);
        }
        DebuggerRequestHandler handler = handlers.getHandler(command, debugContext.getLaunchMode());
        if (handler != null) {
            return handler.handle(command, cmdArgs, response, debugContext);
        } else {
            final String errorMessage = String.format("Unrecognized request: { _request: %s }", request.command);
            LOGGER.log(Level.SEVERE, errorMessage);
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE, errorMessage);
        }
    }

    private class ShutdownHandler implements IShutdownProvider {

        @Override
        public void shutDown() {
            handlers.shutDown(debugContext);
        }
        
    }
}

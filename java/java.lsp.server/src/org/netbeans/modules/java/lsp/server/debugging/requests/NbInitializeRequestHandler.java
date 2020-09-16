/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.java.lsp.server.debugging.requests;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.IThreadsProvider;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
final class NbInitializeRequestHandler implements DebuggerRequestHandler {

    NbInitializeRequestHandler() {
    }

    private boolean initialized = false;

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Collections.singletonList(Requests.Command.INITIALIZE);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        if (!initialized) {
            initialized = true;
            // Called from postLaunch: 
            context.getProvider(IThreadsProvider.class).initialize(context, Collections.emptyMap());
        }
        Requests.InitializeArguments initializeArguments = (Requests.InitializeArguments) arguments;
        context.setClientLinesStartAt1(initializeArguments.linesStartAt1);
        context.setClientColumnsStartAt1(initializeArguments.columnsStartAt1);
        String pathFormat = initializeArguments.pathFormat;
        if (pathFormat != null) {
            switch (pathFormat) {
                case "uri":
                    context.setClientPathsAreUri(true);
                    break;
                default:
                    context.setClientPathsAreUri(false);
            }
        }
        context.setSupportsRunInTerminalRequest(initializeArguments.supportsRunInTerminalRequest);

        Types.Capabilities caps = new Types.Capabilities();
        caps.supportsConfigurationDoneRequest = true;
        caps.supportsHitConditionalBreakpoints = true;
        caps.supportsConditionalBreakpoints = true;
        caps.supportsSetVariable = true;
        caps.supportTerminateDebuggee = true;
        caps.supportsCompletionsRequest = true;
        caps.supportsRestartFrame = true;
        caps.supportsLogPoints = true;
        caps.supportsEvaluateForHovers = true;
        Types.ExceptionBreakpointFilter[] exceptionFilters = {
            Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER,
            Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER,
        };
        caps.exceptionBreakpointFilters = exceptionFilters;
        caps.supportsExceptionInfoRequest = true;
        response.body = caps;
        return CompletableFuture.completedFuture(response);
    }

}

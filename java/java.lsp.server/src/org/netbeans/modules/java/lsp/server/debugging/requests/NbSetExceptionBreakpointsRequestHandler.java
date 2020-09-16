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
import org.apache.commons.lang3.ArrayUtils;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

final class NbSetExceptionBreakpointsRequestHandler implements DebuggerRequestHandler {

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Collections.singletonList(Requests.Command.SETEXCEPTIONBREAKPOINTS);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        String[] filters = ((Requests.SetExceptionBreakpointsArguments) arguments).filters;
        try {
            boolean notifyCaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER_NAME);
            boolean notifyUncaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER_NAME);

            //TODO: context.getDebugSession().setExceptionBreakpoints(notifyCaught, notifyUncaught);
            return CompletableFuture.completedFuture(response);
        } catch (Exception ex) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to setExceptionBreakpoints. Reason: '%s'", ex.toString()),
                ErrorCode.SET_EXCEPTIONBREAKPOINT_FAILURE,
                ex);
        }
    }

}

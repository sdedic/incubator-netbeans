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
import org.netbeans.modules.java.lsp.server.debugging.IConfigurationSemaphore;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.IDebugSession;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
final class NbConfigurationDoneRequestHandler implements DebuggerRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Collections.singletonList(Command.CONFIGURATIONDONE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            // Breakpoints were submitted, we can resume the debugger
            context.getProvider(IConfigurationSemaphore.class).notifyCongigurationDone();;
            return CompletableFuture.completedFuture(response);
        } else {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Failed to launch debug session, the debugger will exit.");
        }
    }

}

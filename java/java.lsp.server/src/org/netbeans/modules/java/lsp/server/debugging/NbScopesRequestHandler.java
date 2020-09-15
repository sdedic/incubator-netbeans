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
package org.netbeans.modules.java.lsp.server.debugging;

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.variables.StackFrameReference;
import com.microsoft.java.debug.core.adapter.variables.VariableProxy;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ScopesArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ThreadReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.netbeans.modules.debugger.jpda.models.JPDAThreadImpl;
import org.netbeans.modules.debugger.jpda.util.WeakCacheMap;
import org.netbeans.spi.debugger.ui.DebuggingView.DVFrame;
import org.netbeans.spi.debugger.ui.DebuggingView.DVThread;

/**
 *
 * @author martin
 */
public class NbScopesRequestHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Collections.singletonList(Command.SCOPES);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        ScopesArguments scopesArgs = (ScopesArguments) arguments;
        NbFrame stackFrame = (NbFrame) context.getRecyclableIdPool().getObjectById(scopesArgs.frameId);
        if (stackFrame == null) {
            response.body = new Responses.ScopesResponseBody(Collections.emptyList());
            return CompletableFuture.completedFuture(response);
        }
        stackFrame.getDVFrame().makeCurrent(); // The scopes and variables are always provided with respect to the current frame
        // TODO: Provide Truffle scopes.
        NbScope localScope = new NbScope(stackFrame, "Local");
        int localScopeId = context.getRecyclableIdPool().addObject(stackFrame.getThreadId(), localScope);
        List<Types.Scope> scopes = Collections.singletonList(new Types.Scope(localScope.getName(), localScopeId, false));

        response.body = new Responses.ScopesResponseBody(scopes);
        return CompletableFuture.completedFuture(response);
    }

}

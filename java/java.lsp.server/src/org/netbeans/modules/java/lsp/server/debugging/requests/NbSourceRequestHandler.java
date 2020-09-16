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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.ISourceLookUpProvider;

import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.SourceArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Responses;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
public class NbSourceRequestHandler implements DebuggerRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SOURCE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        int sourceReference = ((SourceArguments) arguments).sourceReference;
        if (sourceReference <= 0) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                    "SourceRequest: property 'sourceReference' is missing, null, or empty");
        } else {
            String uri = context.getSourceUri(sourceReference);
            ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
            response.body = new Responses.SourceResponseBody(sourceProvider.getSourceContents(uri));
            return CompletableFuture.completedFuture(response);
        }
    }

}

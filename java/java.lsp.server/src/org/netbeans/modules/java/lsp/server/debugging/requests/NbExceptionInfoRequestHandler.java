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
import java.util.logging.Logger;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.IExceptionManager.ExceptionInfo;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.ExceptionInfoArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Responses;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types.ExceptionBreakMode;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
public class NbExceptionInfoRequestHandler implements DebuggerRequestHandler {

    private static final Logger LOGGER = Logger.getLogger(NbExceptionInfoRequestHandler.class.getName());

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.EXCEPTIONINFO);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        ExceptionInfoArguments exceptionInfoArgs = (ExceptionInfoArguments) arguments;
        ExceptionInfo exceptionInfo = context.getExceptionManager().getException(exceptionInfoArgs.threadId);
        if (exceptionInfo == null) {
            throw AdapterUtils.createCompletionException("No exception exists in thread " + exceptionInfoArgs.threadId, ErrorCode.EXCEPTION_INFO_FAILURE);
        }

        String typeName = exceptionInfo.getException().getLocalizedMessage(); // TODO
        String exceptionToString = exceptionInfo.getException().toString();

        response.body = new Responses.ExceptionInfoResponse(typeName, exceptionToString,
                exceptionInfo.isCaught() ? ExceptionBreakMode.ALWAYS : ExceptionBreakMode.USERUNHANDLED);
        return CompletableFuture.completedFuture(response);
    }
    
}

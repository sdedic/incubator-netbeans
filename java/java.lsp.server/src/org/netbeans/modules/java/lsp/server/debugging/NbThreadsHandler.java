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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ContinueArguments;
import com.microsoft.java.debug.core.protocol.Requests.PauseArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.modules.debugger.jpda.JPDADebuggerImpl;
import org.netbeans.spi.debugger.ui.DebuggingView.DVThread;
import org.netbeans.spi.viewmodel.Models;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.openide.util.Exceptions;

public class NbThreadsHandler implements IDebugRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.THREADS, Command.PAUSE, Command.CONTINUE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Debug Session doesn't exist.");
        }

        JPDADebuggerImpl jpdaDebugger = (JPDADebuggerImpl) Debugger.findJPDADebugger(context.getDebugSession());

        switch (command) {
            case THREADS: {
                Models.CompoundModel threadsModel = new ViewModel("DebuggingView").createModel();
                Object[] threads;
                try {
                    threads = threadsModel.getChildren(threadsModel.getRoot(), 0, Integer.MAX_VALUE);
                } catch (UnknownTypeException ex) {
                    Exceptions.printStackTrace(ex);
                    threads = new Object[0];
                }
                List<Types.Thread> arr = new ArrayList<>();
                for (int i = 0; i < threads.length; i++) {
                    Object t = threads[i];
                    if (t instanceof DVThread) {
                        DVThread dvt = (DVThread) t;
                        arr.add(new Types.Thread(dvt.getId(), dvt.getName()));
                    }
                }
                response.body = new Responses.ThreadsResponseBody(arr);
                return CompletableFuture.completedFuture(response);
            }
            case PAUSE: {
                PauseArguments args = (PauseArguments) arguments;
                final Events.StoppedEvent ev;
                final JPDAThread currentThread = jpdaDebugger.getCurrentThread();
                if (args.threadId != 0 && currentThread != null && currentThread.getID() == args.threadId) {
                    currentThread.suspend();
                    ev = new Events.StoppedEvent("pause", args.threadId, false);
                } else {
                    jpdaDebugger.suspend();
                    ev = new Events.StoppedEvent("pause", 0, true);
                }
                context.getProtocolServer().sendEvent(ev);
                return CompletableFuture.completedFuture(response);
            }

            case CONTINUE:
                ContinueArguments args = (ContinueArguments) arguments;
                if (args.threadId != 0) {
                    jpdaDebugger.resumeCurrentThread();
                    response.body = new Responses.ContinueResponseBody(false);
                    return CompletableFuture.completedFuture(response);
                } else {
                    jpdaDebugger.resume();
                    response.body = new Responses.ContinueResponseBody(true);
                    return CompletableFuture.completedFuture(response);
                }
            default:
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                        String.format("Unrecognized request: { _request: %s }", command.toString()));
        }
    }
}

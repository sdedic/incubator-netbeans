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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.IThreadsProvider;
import org.netbeans.modules.java.lsp.server.debugging.NbFrame;
import org.netbeans.modules.java.lsp.server.debugging.launch.NbDebugSession;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Events;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages.Response;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Arguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.ContinueArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.PauseArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.StackTraceArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.ThreadsArguments;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Responses;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;
import org.netbeans.spi.debugger.ui.DebuggingView.DVFrame;
import org.netbeans.spi.debugger.ui.DebuggingView.DVThread;
import org.netbeans.modules.java.lsp.server.debugging.requests.DebuggerRequestHandler;

/**
 *
 * @author martin
 */
public class NbThreadsAndStacksRequestHandler implements DebuggerRequestHandler {

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.THREADS, Command.PAUSE, Command.CONTINUE, Command.STACKTRACE);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Debug Session doesn't exist.");
        }

        switch (command) {
            case THREADS:
                return this.threads((ThreadsArguments) arguments, response, context);
            case STACKTRACE:
                return this.stack((StackTraceArguments) arguments, response, context);
            case PAUSE:
                return this.pause((PauseArguments) arguments, response, context);
            case CONTINUE:
                return this.resume((ContinueArguments) arguments, response, context);
            default:
                return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                        String.format("Unrecognized request: { _request: %s }", command.toString()));
        }
    }

    private CompletableFuture<Response> threads(Requests.ThreadsArguments arguments, Response response, IDebugAdapterContext context) {
        List<Types.Thread> arr = new ArrayList<>();
        context.getProvider(IThreadsProvider.class).visitThreads((id, dvThread) -> {
            arr.add(new Types.Thread(id, dvThread.getName()));
        });
        response.body = new Responses.ThreadsResponseBody(arr);
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> stack(Requests.StackTraceArguments arguments, Response response, IDebugAdapterContext context) {
        List<Types.StackFrame> result = new ArrayList<>();
        if (arguments.startFrame < 0 || arguments.levels < 0) {
            response.body = new Responses.StackTraceResponseBody(result, 0);
            return CompletableFuture.completedFuture(response);
        }

        DVThread dvThread = context.getProvider(IThreadsProvider.class).getThread(arguments.threadId);
        if (dvThread == null) {
            response.body = new Responses.StackTraceResponseBody(result, 0);
            return CompletableFuture.completedFuture(response);
        }
        int from, to;
        if (arguments.levels > 0) {
            from = arguments.startFrame;
            to = arguments.startFrame + arguments.levels;
        } else {
            from = 0;
            to = Integer.MAX_VALUE;
        }
        List<DVFrame> stackFrames = dvThread.getFrames(from, to);
        for (DVFrame frame : stackFrames) {
            int frameId = context.getRecyclableIdPool().addObject(arguments.threadId, new NbFrame(arguments.threadId, frame));
            int line = frame.getLine();
            if (line < 0) { // unknown
                line = 0;
            }
            int column = frame.getColumn();
            if (column < 0) { // unknown
                column = 0;
            }
            result.add(new Types.StackFrame(frameId, frame.getName(), getSource(frame.getSourceURI()), line, column));
        }

        response.body = new Responses.StackTraceResponseBody(result, dvThread.getFrameCount());
        return CompletableFuture.completedFuture(response);
    }

    private Types.Source getSource(URI sourceURI) {
        if (sourceURI == null || sourceURI.getPath() == null) {
            return null;
        }
        return new Types.Source(sourceURI.getPath(), 0);
    }

    private CompletableFuture<Response> pause(Requests.PauseArguments arguments, Response response, IDebugAdapterContext context) {
        final Events.StoppedEvent ev;
        if (arguments.threadId > 0) {
            DVThread dvThread = context.getProvider(IThreadsProvider.class).getThread(arguments.threadId);
            if (dvThread != null) {
                dvThread.suspend();
                ev = new Events.StoppedEvent("pause", arguments.threadId, false);
            } else {
                ev = null;
            }
        } else {
            JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
            debugger.getSession().getCurrentEngine().getActionsManager().doAction("pause");
            ev = new Events.StoppedEvent("pause", 0, true);
        }
        if (ev != null) {
            context.getProtocolServer().sendEvent(ev);
        }
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
        if (arguments.threadId != 0) {
            DVThread dvThread = context.getProvider(IThreadsProvider.class).getThread(arguments.threadId);
            if (dvThread != null) {
                dvThread.resume();
                context.getRecyclableIdPool().removeObjectsByOwner(arguments.threadId);
            }
            response.body = new Responses.ContinueResponseBody(false);
            return CompletableFuture.completedFuture(response);
        } else {
            JPDADebugger debugger = ((NbDebugSession) context.getDebugSession()).getDebugger();
            debugger.getSession().getCurrentEngine().getActionsManager().doAction("continue");
            context.getRecyclableIdPool().removeAllObjects();
            response.body = new Responses.ContinueResponseBody(true);
            return CompletableFuture.completedFuture(response);
        }
    }

}

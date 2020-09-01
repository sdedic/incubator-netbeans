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

import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.ContinueArguments;
import com.microsoft.java.debug.core.protocol.Requests.PauseArguments;
import com.microsoft.java.debug.core.protocol.Requests.StackTraceArguments;
import com.microsoft.java.debug.core.protocol.Requests.ThreadsArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.DebuggerEngine;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.spi.debugger.ui.DebuggingView;

/**
 *
 * @author martin
 */
public class NbThreadsAndStacksRequestHandler implements IDebugRequestHandler {

    private static final String THREADS_VIEW_NAME = "DebuggingView";

    private final ViewModel threadsView = new ViewModel(THREADS_VIEW_NAME);
    private final Map<Long, DebuggingView.DVThread> threadsById = new HashMap<>();

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
        ArrayList<Types.Thread> threads = new ArrayList<>();
        DebuggerEngine currentEngine = DebuggerManager.getDebuggerManager().getCurrentEngine();
        DebuggingView.DVSupport dvSupport;
        if (currentEngine != null) {
            dvSupport = currentEngine.lookupFirst(null, DebuggingView.DVSupport.class);
        } else {
            return CompletableFuture.completedFuture(response);
        }
        threadsById.clear();
        long tid = 1L;
        List<DebuggingView.DVThread> allThreads = dvSupport.getAllThreads();
        for (DebuggingView.DVThread dvThread : allThreads) {
            long id = dvThread.getId();//tid++;
            Types.Thread clientThread = new Types.Thread(id, dvThread.getName());
            threads.add(clientThread);
            threadsById.put(id, dvThread);
        }
        response.body = new Responses.ThreadsResponseBody(threads);
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> stack(Requests.StackTraceArguments arguments, Response response, IDebugAdapterContext context) {
        List<Types.StackFrame> result = new ArrayList<>();
        if (arguments.startFrame < 0 || arguments.levels < 0) {
            response.body = new Responses.StackTraceResponseBody(result, 0);
            return CompletableFuture.completedFuture(response);
        }

        // XXX Stepping workaround:
            ActionsManager am = DebuggerManager.getDebuggerManager().getCurrentEngine().getActionsManager();
            System.err.println("am: " + am);
            am.doAction("pauseInGraalScript");

        DebuggingView.DVThread dvThread = threadsById.get(arguments.threadId);
        int from, to;
        if (arguments.levels > 0) {
            from = arguments.startFrame;
            to = arguments.startFrame + arguments.levels;
        } else {
            from = 0;
            to = Integer.MAX_VALUE;
        }
        List<DebuggingView.DVFrame> stackFrames = dvThread.getFrames(from, to);
        for (DebuggingView.DVFrame frame : stackFrames) {
            int frameId = context.getRecyclableIdPool().addObject(arguments.threadId, frame);
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
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        if (thread != null) {
            thread.suspend();
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", arguments.threadId));
        } else {
            context.getDebugSession().suspend();
            context.getProtocolServer().sendEvent(new Events.StoppedEvent("pause", arguments.threadId, true));
        }
        return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<Response> resume(Requests.ContinueArguments arguments, Response response, IDebugAdapterContext context) {
        boolean allThreadsContinued = true;
        ThreadReference thread = DebugUtility.getThread(context.getDebugSession(), arguments.threadId);
        /**
         * See the jdi doc https://docs.oracle.com/javase/7/docs/jdk/api/jpda/jdi/com/sun/jdi/ThreadReference.html#resume(),
         * suspends of both the virtual machine and individual threads are counted. Before a thread will run again, it must
         * be resumed (through ThreadReference#resume() or VirtualMachine#resume()) the same number of times it has been suspended.
         */
        if (thread != null) {
            context.getExceptionManager().removeException(arguments.threadId);
            allThreadsContinued = false;
            DebugUtility.resumeThread(thread);
            checkThreadRunningAndRecycleIds(thread, context);
        } else {
            context.getExceptionManager().removeAllExceptions();
            context.getDebugSession().resume();
            context.getRecyclableIdPool().removeAllObjects();
        }
        response.body = new Responses.ContinueResponseBody(allThreadsContinued);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Recycle the related ids owned by the specified thread.
     */
    public static void checkThreadRunningAndRecycleIds(ThreadReference thread, IDebugAdapterContext context) {
        try {
            IEvaluationProvider engine = context.getProvider(IEvaluationProvider.class);
            engine.clearState(thread);
            boolean allThreadsRunning = !DebugUtility.getAllThreadsSafely(context.getDebugSession()).stream()
                    .anyMatch(ThreadReference::isSuspended);
            if (allThreadsRunning) {
                context.getRecyclableIdPool().removeAllObjects();
            } else {
                context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
            }
        } catch (VMDisconnectedException ex) {
            // isSuspended may throw VMDisconnectedException when the VM terminates
            context.getRecyclableIdPool().removeAllObjects();
        } catch (ObjectCollectedException collectedEx) {
            // isSuspended may throw ObjectCollectedException when the thread terminates
            context.getRecyclableIdPool().removeObjectsByOwner(thread.uniqueID());
        }
    }
}

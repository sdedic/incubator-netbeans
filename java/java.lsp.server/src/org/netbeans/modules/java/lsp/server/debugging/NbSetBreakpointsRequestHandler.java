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

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.BreakpointManager;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent.EventType;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.SetBreakpointArguments;
import com.microsoft.java.debug.core.protocol.Responses;
import com.microsoft.java.debug.core.protocol.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.modules.java.lsp.server.debugging.breakpoints.NbBreakpoint;

/**
 *
 * @author martin
 */
final class NbSetBreakpointsRequestHandler implements IDebugRequestHandler {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private final BreakpointManager manager = new BreakpointManager();

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.SETBREAKPOINTS);
    }

    @Override
    public void initialize(IDebugAdapterContext context) {
        IDebugRequestHandler.super.initialize(context);
        IHotCodeReplaceProvider provider = context.getProvider(IHotCodeReplaceProvider.class);
        provider.getEventHub()
            .filter(event -> event.getEventType() == EventType.END)
            .subscribe(event -> {
                try {
                    List<String> classNames = (List<String>) event.getData();
                    reinstallBreakpoints(context, classNames);
                } catch (Exception e) {
                    logger.severe(e.toString());
                }
            });
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        if (context.getDebugSession() == null) {
            return AdapterUtils.createAsyncErrorResponse(response, ErrorCode.EMPTY_DEBUG_SESSION, "Empty debug session.");
        }

        SetBreakpointArguments bpArguments = (SetBreakpointArguments) arguments;
        String clientPath = bpArguments.source.path;
        if (AdapterUtils.isWindows()) {
            // VSCode may send drive letters with inconsistent casing which will mess up the key
            // in the BreakpointManager. See https://github.com/Microsoft/vscode/issues/6268
            // Normalize the drive letter casing. Note that drive letters
            // are not localized so invariant is safe here.
            String drivePrefix = FilenameUtils.getPrefix(clientPath);
            if (drivePrefix != null && drivePrefix.length() >= 2
                    && Character.isLowerCase(drivePrefix.charAt(0)) && drivePrefix.charAt(1) == ':') {
                drivePrefix = drivePrefix.substring(0, 2); // d:\ is an illegal regex string, convert it to d:
                clientPath = clientPath.replaceFirst(drivePrefix, drivePrefix.toUpperCase());
            }
        }
        String sourcePath = clientPath;
        if (StringUtils.isNotBlank(clientPath)) {
            // See the bug https://github.com/Microsoft/vscode/issues/30996
            // Source.path in the SetBreakpointArguments could be a file system path or uri.
            sourcePath = AdapterUtils.convertPath(clientPath, AdapterUtils.isUri(clientPath), context.isDebuggerPathsAreUri());
        }
        if (StringUtils.isBlank(sourcePath)) {
            throw AdapterUtils.createCompletionException(
                String.format("Failed to setBreakpoint. Reason: '%s' is an invalid path.", bpArguments.source.path),
                ErrorCode.SET_BREAKPOINT_FAILURE);
        }

        List<Types.Breakpoint> res = new ArrayList<>();
        IBreakpoint[] toAdds = this.convertClientBreakpointsToDebugger(sourcePath, bpArguments.breakpoints, context);
        // See the VSCode bug https://github.com/Microsoft/vscode/issues/36471.
        // The source uri sometimes is encoded by VSCode, the debugger will decode it to keep the uri consistent.
        IBreakpoint[] added = manager.setBreakpoints(AdapterUtils.decodeURIComponent(sourcePath), toAdds, bpArguments.sourceModified);
        for (int i = 0; i < bpArguments.breakpoints.length; i++) {
            // For newly added breakpoint, should install it to debuggee first.
            if (toAdds[i] == added[i] && added[i].className() != null) {
                added[i].install().thenAccept(bp -> {
                    Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("new", this.convertDebuggerBreakpointToClient(bp, context));
                    context.getProtocolServer().sendEvent(bpEvent);
                });
            } else if (added[i].className() != null) {
                if (toAdds[i].getHitCount() != added[i].getHitCount()) {
                    // Update hitCount condition.
                    added[i].setHitCount(toAdds[i].getHitCount());
                }

                if (!StringUtils.equals(toAdds[i].getLogMessage(), added[i].getLogMessage())) {
                    added[i].setLogMessage(toAdds[i].getLogMessage());
                }

                if (!StringUtils.equals(toAdds[i].getCondition(), added[i].getCondition())) {
                    added[i].setCondition(toAdds[i].getCondition());
                }

            }
            res.add(this.convertDebuggerBreakpointToClient(added[i], context));
        }
        response.body = new Responses.SetBreakpointsResponseBody(res);
        return CompletableFuture.completedFuture(response);
    }

    private Types.Breakpoint convertDebuggerBreakpointToClient(IBreakpoint breakpoint, IDebugAdapterContext context) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null && (boolean) breakpoint.getProperty("verified");
        int lineNumber = AdapterUtils.convertLineNumber(breakpoint.getLineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        return new Types.Breakpoint(id, verified, lineNumber, "");
    }

    private IBreakpoint[] convertClientBreakpointsToDebugger(String sourceFile, Types.SourceBreakpoint[] sourceBreakpoints, IDebugAdapterContext context) {
            //throws DebugException {
        int[] lines = Arrays.asList(sourceBreakpoints).stream().map(sourceBreakpoint -> {
            return AdapterUtils.convertLineNumber(sourceBreakpoint.line, context.isClientLinesStartAt1(), context.isDebuggerLinesStartAt1());
        }).mapToInt(line -> line).toArray();
        IBreakpoint[] breakpoints = new IBreakpoint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            int hitCount = 0;
            try {
                hitCount = Integer.parseInt(sourceBreakpoints[i].hitCondition);
            } catch (NumberFormatException e) {
                hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
            }
            breakpoints[i] = new NbBreakpoint(sourceFile, lines[i], hitCount, sourceBreakpoints[i].condition, sourceBreakpoints[i].logMessage);
        }
        return breakpoints;
    }

    private void reinstallBreakpoints(IDebugAdapterContext context, List<String> typenames) {
        if (typenames == null || typenames.isEmpty()) {
            return;
        }
        IBreakpoint[] breakpoints = manager.getBreakpoints();

        for (IBreakpoint breakpoint : breakpoints) {
            if (typenames.contains(breakpoint.className())) {
                try {
                    breakpoint.close();
                    breakpoint.install().thenAccept(bp -> {
                        Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("new", this.convertDebuggerBreakpointToClient(bp, context));
                        context.getProtocolServer().sendEvent(bpEvent);
                    });
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Remove breakpoint exception: %s", e.toString()), e);
                }
            }
        }
    }
}

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
package org.netbeans.modules.java.lsp.server.debugging.breakpoints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.netbeans.modules.java.lsp.server.debugging.DebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.netbeans.modules.java.lsp.server.debugging.utils.ErrorCode;

/**
 *
 * @author martin
 */
public final class NbBreakpointsRequestHandler {

    public static final String CAUGHT_EXCEPTION_FILTER_NAME = "caught";
    public static final String UNCAUGHT_EXCEPTION_FILTER_NAME = "uncaught";

    public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments arguments, DebugAdapterContext context) {
        CompletableFuture<SetBreakpointsResponse> resultFuture = new CompletableFuture<>();
        if (context.getDebugSession() == null) {
            resultFuture.completeExceptionally(AdapterUtils.createResponseErrorException("Empty debug session.", ErrorCode.EMPTY_DEBUG_SESSION));
            return resultFuture;
        }
        String clientPath = arguments.getSource().getPath();
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
            resultFuture.completeExceptionally(AdapterUtils.createResponseErrorException(
                String.format("Failed to setBreakpoint. Reason: '%s' is an invalid path.", arguments.getSource().getPath()),
                ErrorCode.SET_BREAKPOINT_FAILURE));
            return resultFuture;
        }
        List<Breakpoint> res = new ArrayList<>();
        NbBreakpoint[] toAdds = this.convertClientBreakpointsToDebugger(sourcePath, arguments.getBreakpoints(), context);
        // See the VSCode bug https://github.com/Microsoft/vscode/issues/36471.
        // The source uri sometimes is encoded by VSCode, the debugger will decode it to keep the uri consistent.
        NbBreakpoint[] added = context.getBreakpointManager().setBreakpoints(AdapterUtils.decodeURIComponent(sourcePath), toAdds, arguments.getSourceModified());
        for (int i = 0; i < arguments.getBreakpoints().length; i++) {
            // For newly added breakpoint, should install it to debuggee first.
            if (toAdds[i] == added[i] && added[i].className() != null) {
                added[i].install().thenAccept(bp -> {
                    BreakpointEventArguments bpEvent = new BreakpointEventArguments();
                    bpEvent.setReason("new");
                    bpEvent.setBreakpoint(this.convertDebuggerBreakpointToClient(bp, context));
                    context.getClient().breakpoint(bpEvent);
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
        SetBreakpointsResponse response = new SetBreakpointsResponse();
        response.setBreakpoints(res.toArray(new Breakpoint[res.size()]));
        resultFuture.complete(response);
        return resultFuture;
    }

    public CompletableFuture<Void> setExceptionBreakpoints(SetExceptionBreakpointsArguments arguments, DebugAdapterContext context) {
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        if (context.getDebugSession() == null) {
            resultFuture.completeExceptionally(AdapterUtils.createResponseErrorException("Empty debug session.", ErrorCode.EMPTY_DEBUG_SESSION));
            return resultFuture;
        }
        String[] filters = arguments.getFilters();
        boolean notifyCaught = ArrayUtils.contains(filters, CAUGHT_EXCEPTION_FILTER_NAME);
        boolean notifyUncaught = ArrayUtils.contains(filters, UNCAUGHT_EXCEPTION_FILTER_NAME);
        //TODO: context.getDebugSession().setExceptionBreakpoints(notifyCaught, notifyUncaught);
        return CompletableFuture.completedFuture(null);
    }

    private Breakpoint convertDebuggerBreakpointToClient(NbBreakpoint breakpoint, DebugAdapterContext context) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null && (boolean) breakpoint.getProperty("verified");
        int lineNumber = AdapterUtils.convertLineNumber(breakpoint.getLineNumber(), context.isDebuggerLinesStartAt1(), context.isClientLinesStartAt1());
        Breakpoint bp = new Breakpoint();
        bp.setId(id);
        bp.setVerified(verified);
        bp.setLine(lineNumber);
        bp.setMessage("");
        return bp;
    }

    private NbBreakpoint[] convertClientBreakpointsToDebugger(String sourceFile, SourceBreakpoint[] sourceBreakpoints, DebugAdapterContext context) {
            //throws DebugException {
        int[] lines = Arrays.asList(sourceBreakpoints).stream().map(sourceBreakpoint -> {
            return AdapterUtils.convertLineNumber(sourceBreakpoint.getLine(), context.isClientLinesStartAt1(), context.isDebuggerLinesStartAt1());
        }).mapToInt(line -> line).toArray();
        NbBreakpoint[] breakpoints = new NbBreakpoint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            int hitCount = 0;
            try {
                hitCount = Integer.parseInt(sourceBreakpoints[i].getHitCondition());
            } catch (NumberFormatException e) {
                hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
            }
            breakpoints[i] = new NbBreakpoint(sourceFile, lines[i], hitCount, sourceBreakpoints[i].getCondition(), sourceBreakpoints[i].getLogMessage());
        }
        return breakpoints;
    }
}

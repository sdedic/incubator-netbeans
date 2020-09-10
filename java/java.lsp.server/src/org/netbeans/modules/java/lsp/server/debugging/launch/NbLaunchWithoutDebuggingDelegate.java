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
package org.netbeans.modules.java.lsp.server.debugging.launch;

import com.google.gson.JsonObject;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.handler.LaunchRequestHandler;
import com.microsoft.java.debug.core.protocol.Events;
import com.microsoft.java.debug.core.protocol.JsonUtils;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 *
 * @author martin
 */
public class NbLaunchWithoutDebuggingDelegate extends NbLaunchDelegate {
    protected static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    protected static final String TERMINAL_TITLE = "Java Process Console";
    protected static final long RUNINTERMINAL_TIMEOUT = 10 * 1000;
    private Consumer<IDebugAdapterContext> terminateHandler;

    public NbLaunchWithoutDebuggingDelegate(Consumer<IDebugAdapterContext> terminateHandler) {
        this.terminateHandler = terminateHandler;
    }

    @Override
    public Process launch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        throw new UnsupportedOperationException();
    }

    protected static String[] constructEnvironmentVariables(Requests.LaunchArguments launchArguments) {
        String[] envVars = null;
        if (launchArguments.env != null && !launchArguments.env.isEmpty()) {
            Map<String, String> environment = new HashMap<>(System.getenv());
            List<String> duplicated = new ArrayList<>();
            for (Map.Entry<String, String> entry : launchArguments.env.entrySet()) {
                if (environment.containsKey(entry.getKey())) {
                    duplicated.add(entry.getKey());
                }
                environment.put(entry.getKey(), entry.getValue());
            }
            // For duplicated variables, show a warning message.
            if (!duplicated.isEmpty()) {
                logger.warning(String.format("There are duplicated environment variables. The values specified in launch.json will be used. "
                        + "Here are the duplicated entries: %s.", String.join(",", duplicated)));
            }

            envVars = new String[environment.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                envVars[i++] = entry.getKey() + "=" + entry.getValue();
            }
        }
        return envVars;
    }

    @Override
    public void postLaunch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context) {
        // For NO_DEBUG launch mode, the debugger does not respond to requests like
        // SetBreakpointsRequest,
        // but the front end keeps sending them according to the Debug Adapter Protocol.
        // To avoid receiving them, a workaround is not to send InitializedEvent back to
        // the front end.
        // See https://github.com/Microsoft/vscode/issues/55850#issuecomment-412819676
        return;
    }

    @Override
    // XXX TODO:
    public CompletableFuture<Messages.Response> launchInTerminal(Requests.LaunchArguments launchArguments, Messages.Response response,
            IDebugAdapterContext context) {
        CompletableFuture<Messages.Response> resultFuture = new CompletableFuture<>();

        final String launchInTerminalErrorFormat = "Failed to launch debuggee in terminal. Reason: %s";

        String[] cmds = LaunchRequestHandler.constructLaunchCommands(launchArguments, false, null);
        Requests.RunInTerminalRequestArguments requestArgs = null;
        if (launchArguments.console == Requests.CONSOLE.integratedTerminal) {
            requestArgs = Requests.RunInTerminalRequestArguments.createIntegratedTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, TERMINAL_TITLE);
        } else {
            requestArgs = Requests.RunInTerminalRequestArguments.createExternalTerminal(cmds, launchArguments.cwd,
                    launchArguments.env, TERMINAL_TITLE);
        }
        Messages.Request request = new Messages.Request(Requests.Command.RUNINTERMINAL.getName(),
                (JsonObject) JsonUtils.toJsonTree(requestArgs, Requests.RunInTerminalRequestArguments.class));

        // Notes: In windows (reference to
        // https://support.microsoft.com/en-us/help/830473/command-prompt-cmd--exe-command-line-string-limitation),
        // when launching the program in cmd.exe, if the command line length exceed the
        // threshold value (8191 characters),
        // it will be automatically truncated so that launching in terminal failed.
        // Especially, for maven project, the class path contains
        // the local .m2 repository path, it may exceed the limit.
        context.getProtocolServer().sendRequest(request, RUNINTERMINAL_TIMEOUT).whenComplete((runResponse, ex) -> {
            if (runResponse != null) {
                if (runResponse.success) {
                    // Without knowing the pid, debugger has lost control of the process.
                    // So simply send `terminated` event to end the session.
                    context.getProtocolServer().sendEvent(new Events.TerminatedEvent());
                    resultFuture.complete(response);
                } else {
                    resultFuture.completeExceptionally(
                            new DebugException(String.format(launchInTerminalErrorFormat, runResponse.message),
                                    ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()));
                }
            } else {
                if (ex instanceof CompletionException && ex.getCause() != null) {
                    ex = ex.getCause();
                }
                String errorMessage = String.format(launchInTerminalErrorFormat,
                        ex != null ? ex.toString() : "Null response");
                resultFuture.completeExceptionally(
                        new DebugException(String.format(launchInTerminalErrorFormat, errorMessage),
                                ErrorCode.LAUNCH_IN_TERMINAL_FAILURE.getId()));
            }
        });
        return resultFuture;
    }

    @Override
    public void preLaunch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context) {
        // TODO Auto-generated method stub
    }
    
}

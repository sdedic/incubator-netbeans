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

import java.util.HashMap;
import java.util.Map;

import org.netbeans.modules.java.lsp.server.debugging.LaunchMode;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests.Command;

/**
 *
 * @author martin
 */
public final class HandlersCollection {

    private Map<Command, DebuggerRequestHandler> debugHandlers = new HashMap<>();
    private Map<Command, DebuggerRequestHandler> noDebugHandlers = new HashMap<>();

    public HandlersCollection() {
        register(new NbInitializeRequestHandler(), debugHandlers, noDebugHandlers);
        register(new NbLaunchRequestHandler(), debugHandlers, noDebugHandlers);

        //register(new AttachRequestHandler(), debugHandlers); // TODO
        register(new NbConfigurationDoneRequestHandler(), debugHandlers);
        register(new NbSetBreakpointsRequestHandler(), debugHandlers);
        register(new NbSetExceptionBreakpointsRequestHandler(), debugHandlers);
        register(new NbThreadsAndStacksRequestHandler(), debugHandlers);
        register(new NbSourceRequestHandler(), debugHandlers);
        register(new NbStepRequestHandler(), debugHandlers);
        register(new NbScopesRequestHandler(), debugHandlers);
        register(new NbVariablesRequestHandler(), debugHandlers);
        register(new NbSetVariableRequestHandler(), debugHandlers);
        register(new NbEvaluateRequestHandler(), debugHandlers);
        register(new NbExceptionInfoRequestHandler(), debugHandlers);
        register(new NbDisconnectRequestHandler(), debugHandlers);
        // TODO: register(new HotCodeReplaceHandler(), debugHandlers);
        // TODO: register(new RestartFrameHandler(), debugHandlers);
        // TODO: register(new CompletionsHandler(), debugHandlers);

        // TODO: register(new DisconnectRequestWithoutDebuggingHandler(), noDebugHandlers);
    }

    private void register(DebuggerRequestHandler handler, Map<Command, DebuggerRequestHandler>... maps) {
        if (maps.length == 0) {
            throw new IllegalArgumentException("No maps to register the commands to.");
        }
        for (Map<Command, DebuggerRequestHandler> map : maps) {
            for (Command command : handler.getTargetCommands()) {
                if (map.containsKey(command)) {
                    throw new IllegalStateException("Handler for command " + command.getName() + " is registered already: " + map.get(command) + " and " + handler);
                }
                map.put(command, handler);
            }
        }
    }

    public DebuggerRequestHandler getHandler(Command command, LaunchMode mode) {
        switch (mode) {
            case DEBUG:
                return debugHandlers.get(command);
            case NO_DEBUG:
                return noDebugHandlers.get(command);
            default:
                throw new IllegalStateException("Unhandled mode: " + mode);
        }
    }
}

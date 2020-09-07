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

import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.handler.AbstractDisconnectRequestHandler;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;
import com.microsoft.java.debug.core.protocol.Requests.DisconnectArguments;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.jpda.JPDADebugger;

/**
 *
 * @author martin
 */
final class NbDisconnectRequestHandler extends AbstractDisconnectRequestHandler {

    @Override
    public void destroyDebugSession(Command command, Arguments arguments, Response response, IDebugAdapterContext context) {
        DisconnectArguments disconnectArguments = (DisconnectArguments) arguments;
        IDebugSession debugSession = context.getDebugSession();
        if (debugSession != null) {
            JPDADebugger dbg = Debugger.findJPDADebugger(context.getDebugSession());
            dbg.finish();
            cleanBreakpoints();
        }
    }

    /**
     * Breakpoints are always being set from the client. We must clean them so that
     * they are not duplicated on the next start.
     */
    private static void cleanBreakpoints() {
        DebuggerManager debuggerManager = DebuggerManager.getDebuggerManager();
        for (Breakpoint breakpoint : debuggerManager.getBreakpoints()) {
            debuggerManager.removeBreakpoint(breakpoint);
        }
        debuggerManager.removeAllWatches();
    }
}

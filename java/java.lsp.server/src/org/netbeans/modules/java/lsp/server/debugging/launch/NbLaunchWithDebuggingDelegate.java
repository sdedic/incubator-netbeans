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

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.netbeans.modules.java.lsp.server.debugging.IDebugAdapterContext;
import org.netbeans.modules.java.lsp.server.debugging.IThreadsProvider;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Events;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Messages;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Requests;

/**
 *
 * @author martin
 */
public class NbLaunchWithDebuggingDelegate extends NbLaunchDelegate {

    @Override
    // XXX TODO:
    public CompletableFuture<Messages.Response> launchInTerminal(Requests.LaunchArguments launchArguments, Messages.Response response, IDebugAdapterContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Process launch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void postLaunch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context) {
        //context.getProvider(IThreadsProvider.class).initialize(context, Collections.emptyMap());
        // send an InitializedEvent to indicate that the debugger is ready to accept
        // configuration requests (e.g. SetBreakpointsRequest, SetExceptionBreakpointsRequest).
        context.getProtocolServer().sendEvent(new Events.InitializedEvent());
    }

    @Override
    public void preLaunch(Requests.LaunchArguments launchArguments, IDebugAdapterContext context) {
        // debug only
        context.setAttached(false);
        context.setSourcePaths(launchArguments.sourcePaths);
        context.setVmStopOnEntry(launchArguments.stopOnEntry);
        //context.setMainClass(LaunchRequestHandler.parseMainClassWithoutModuleName(launchArguments.mainClass));
        context.setStepFilters(launchArguments.stepFilters);
    }
}

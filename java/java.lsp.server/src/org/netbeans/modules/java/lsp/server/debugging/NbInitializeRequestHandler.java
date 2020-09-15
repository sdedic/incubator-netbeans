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

import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.protocol.Messages;
import com.microsoft.java.debug.core.protocol.Requests;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author martin
 */
final class NbInitializeRequestHandler implements IDebugRequestHandler {

    NbInitializeRequestHandler() {
    }

    private boolean initialized = false;

    @Override
    public List<Requests.Command> getTargetCommands() {
        return Collections.singletonList(Requests.Command.INITIALIZE);
    }

    @Override
    public CompletableFuture<Messages.Response> handle(Requests.Command command, Requests.Arguments arguments, Messages.Response response, IDebugAdapterContext context) {
        if (!initialized) {
            initialized = true;
            // Called from postLaunch: 
            context.getProvider(IThreadsProvider.class).initialize(context, Collections.emptyMap());
        }
        return CompletableFuture.completedFuture(response);
    }

}

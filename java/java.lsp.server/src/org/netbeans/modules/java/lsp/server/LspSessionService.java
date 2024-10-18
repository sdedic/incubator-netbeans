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
package org.netbeans.modules.java.lsp.server;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.netbeans.api.annotations.common.CheckForNull;
import org.openide.util.Lookup;

/**
 * A SPI that allows to plug into LSP server per-client. Upon initialization, the LSP server service looks up
 * {@link LspSessionService.Factory} from the default Lookup and asks it to create {@link LspSessionService} instance.
 * The returned instance is then included into the session Lookup, which is available from {@link LspServerState#getLookup()}
 * and from Lookup.getDefault() when the client request is being processed.
 * <p>
 * The LspSessionService is informed about {@link #shutdown(org.netbeans.modules.java.lsp.server.LspServerState) client's shutdown}.
 * @author sdedic
 */
public interface LspSessionService {
    /**
     * May provide a Lookup that will merge with the server session's Lookup. The method is called once only,
     * after {@link #initialize} completes (or returns {@code null}).
     * @return session Lookup.
     */
    public default Lookup createLookup() {
        return Lookup.EMPTY;
    }
    
    /**
     * Called to initialize the service. Note that the order of LspSessionService
     * initialization is not defined; the services are already present in the session's
     * Lookup, but may not be initialized.
     * <p>
     * The returned CompletableFuture will be waited on before server's initialization completes.
     * The returned Consumer may adjust {@link ServerCapabilities} reported to the client.
     * <p>
     * The implementation can return {@code null} to indicate no special setup of the server's
     * initialization response is not required.
     * 
     * @param state the server 
     * @param initParams initialization parameters from the client.
     */
    @CheckForNull
    public CompletableFuture<Consumer<ServerCapabilities>> initialize(LspServerState state, InitializeParams initParams);
    
    /**
     * Informs that the server is shutting down.
     * @param state server state
     */
    public void shutdown(LspServerState state);
    
    /**
     * Factory that creates {@link LspSessionService} instances. Factory must be registered in 
     * the system Lookup.
     */
    public interface Factory {
        /**
         * Creates a service for the {@link LspServerState} client. The service need not initialize
         * fully, its {@link LspSessionService#initialize} will be called after LspSessionServices are registered
         * in the Lookup
         */
        public @CheckForNull LspSessionService create(LspServerState state);
    }
}

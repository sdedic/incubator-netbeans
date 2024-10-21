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
package org.netbeans.modules.java.lsp.server.protocol.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.text.Document;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.LspSessionService;

/**
 *
 * @author sdedic
 */
class LspClientBridge implements LspSessionService {
    
    private final ServerDocumentRegistry registry;
    private final LspServerState server;
    private final Consumer<String> openConsumer = this::documentOpened;
    private final Consumer<String> closeConsumer = this::documentClosed;
    private CompletableFuture<Void> currentOperation;
    private List<PendingEdit> waitingEdits = new ArrayList<>();

    public LspClientBridge(ServerDocumentRegistry registry, LspServerState server) {
        this.registry = registry;
        this.server = server;
    }

    @Override
    public CompletableFuture<Consumer<ServerCapabilities>> initialize(LspServerState state, InitializeParams initParams) {
        state.getOpenedDocuments().addOpenedConsumer(openConsumer);
        state.getOpenedDocuments().addClosedConsumer(closeConsumer);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown(LspServerState state) {
        server.getOpenedDocuments().removeOpenedConsumer(openConsumer);
        server.getOpenedDocuments().removeClosedConsumer(closeConsumer);
        registry.serverShutdown(server);
    }

    private void documentOpened(String uri) {
        Document doc = server.getOpenedDocuments().getDocument(uri);
        registry.registerOpenedDocument(uri, doc, server);
    }

    private void documentClosed(String uri) {
        registry.removeClosedDocument(uri, null, server);
    }
    
}

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.text.Document;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.protocol.NbCodeLanguageClient;

/**
 * Tracks what documents are opened by a LspServer and what Servers opened
 * a particular document.
 */
public class ServerDocumentRegistry {

    private final Map<String, Set<LspServerState>> openedURIs = new HashMap<>();
    private final Map<String, Document> openedDocuments = new HashMap<>();
    private final Map<LspServerState, CompletableFuture<?>> operationInProgress = new HashMap<>();
    private final Map<LspServerState, LspClientBridge> clientBridges = new HashMap<>();

    public synchronized void registerBridge(LspServerState state, LspClientBridge bridge) {
        clientBridges.put(state, bridge);
    }

    public synchronized LspClientBridge toBridge(LspServerState state) {
        return clientBridges.get(state);
    }

    public synchronized <T> CompletableFuture<T> whenClientReady(LspServerState server, Function<NbCodeLanguageClient, CompletableFuture<T>> workFactory) {
        CompletableFuture<T> next;
        CompletableFuture<?> cf = operationInProgress.get(server);
        if (cf != null) {
            next = cf.thenCompose(v -> workFactory.apply(server.getClient()));
            operationInProgress.put(server, next);
        } else {
            next = workFactory.apply(server.getClient());
            operationInProgress.put(server, next);
        }
        return next.thenApply(x -> {
            synchronized (this) {
                operationInProgress.remove(server, next);
            }
            return x;
        });
    }

    public void registerOpenedDocument(String uri, Document document, LspServerState state) {
        synchronized (this) {
            openedURIs.computeIfAbsent(uri, u -> new HashSet<>()).add(state);
            openedDocuments.put(uri, document);
        }
    }

    public void serverShutdown(LspServerState srv) {
        List<String> uris = new ArrayList<>();
        synchronized (this) {
            for (String uri : openedURIs.keySet()) {
                if (openedURIs.get(uri).contains(srv)) {
                    uris.add(uri);
                }
            }
        }
        uris.forEach(u -> removeClosedDocument(u, null, srv));
    }

    public void removeClosedDocument(String uri, Document document, LspServerState state) {
        synchronized (this) {
            if (document == null) {
                document = openedDocuments.get(uri);
            }
            if (openedDocuments.remove(uri, document)) {
                Set<LspServerState> servers = openedURIs.get(uri);
                if (servers != null) {
                    servers.remove(state);
                    if (servers.isEmpty()) {
                        openedURIs.remove(uri);
                    }
                }
            }
        }
    }

    public Set<String> getOpenedURIs() {
        synchronized (this) {
            return new HashSet<>(openedURIs.keySet());
        }
    }

    public Set<Document> getOpenedDocuments() {
        synchronized (this) {
            return new HashSet<>(openedDocuments.values());
        }
    }

    public Set<LspServerState> getAttachedServers(String uri) {
        synchronized (this) {
            return new HashSet<>(openedURIs.getOrDefault(uri, Collections.emptySet()));
        }
    }
    
}

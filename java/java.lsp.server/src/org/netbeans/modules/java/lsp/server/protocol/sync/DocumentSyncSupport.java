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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.LspSessionService;
import org.netbeans.modules.java.lsp.server.Utils;
import org.netbeans.modules.java.lsp.server.protocol.NbCodeLanguageClient;
import org.openide.filesystems.FileObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.CloneableEditorSupportRedirector;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 *
 * @author sdedic
 */
@ServiceProviders({
    @ServiceProvider(service = CloneableEditorSupportRedirector.class),
    @ServiceProvider(service = DocumentSyncFilter.class),
    @ServiceProvider(service = ServerFileRegistry.class),
    @ServiceProvider(service = LspSessionService.Factory.class)
})
public class DocumentSyncSupport extends CloneableEditorSupportRedirector  implements DocumentSyncFilter, ServerFileRegistry, LspSessionService.Factory {
    static final Logger LOG = Logger.getLogger(DocumentSyncSupport.class.getName());
           
    // @GuardedBy(this)
    private final Map<FileObject, EditorLspSupport>    redirectors = new HashMap<>();
    
    /**
     * Holds the current client for non-LSP code executing on behalf of some client. Managed by {@link #runClientDocumentChange}
     */
    static ThreadLocal<LspServerState> currentClient = new ThreadLocal<>();
    
    class ServerAdapter implements LspSessionService {
        private final LspServerState server;
        private final Consumer<String> openConsumer = this::documentOpened;
        private final Consumer<String> closeConsumer = this::documentClosed;
        private CompletableFuture<?> lastOperation;
        
        public ServerAdapter(LspServerState server) {
            this.server = server;
        }
        
        private void documentOpened(String uri) {
            Document doc = server.getOpenedDocuments().getDocument(uri);
            try {
                FileObject fo = Utils.fromUri(uri);
                synchronized(DocumentSyncSupport.this) {
                    openedURIs.computeIfAbsent(fo, u -> new HashSet<>()).add(server);
                }
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        private void documentClosed(String uri) {
            try {
                FileObject fo = Utils.fromUri(uri);
                synchronized (DocumentSyncSupport.this) {
                    Set<LspServerState> servers = openedURIs.get(fo);
                    if (servers != null) {
                        servers.remove(server);
                        if (servers.isEmpty()) {
                            openedURIs.remove(fo);
                        }
                    }
                }
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        public CompletableFuture<Consumer<ServerCapabilities>> initialize(LspServerState state, InitializeParams initParams) {
            state.getOpenedDocuments().addOpenedConsumer(openConsumer);
            state.getOpenedDocuments().addClosedConsumer(closeConsumer);
            return null;
        }

        @Override
        public void shutdown(LspServerState state) {
            server.getOpenedDocuments().removeOpenedConsumer(openConsumer);
            server.getOpenedDocuments().removeClosedConsumer(closeConsumer);
            LOG.log(Level.FINE, "LSP Server {0} shutting down", server);
            serverShutdown(server);
        }

        /**
         * Chains requests to the single client. Composes & sends a request only after the previous one has completed.
         * 
         * @param <T> type of the response
         * @param workFactory callback that executes the request and returns a future response.
         * @return the future response
         */
        public <T> CompletableFuture<T> whenClientReady(Object desc, Function<NbCodeLanguageClient, CompletableFuture<T>> workFactory) {
            CompletableFuture<?> chain;
            CompletableFuture<T> last;

            // atomically replace "lastOperation" with a temporary Future.
            synchronized (this) {
                chain = lastOperation;
                last = new CompletableFuture<>();
                lastOperation = last;
            } 

            CompletableFuture<T> next;
            if (chain != null && !chain.isDone()) {
                LOG.log(Level.FINE, "Server {0}: chaining work {1} with factory {3} after {2}", new Object[] { server, desc, workFactory, chain });
                next = chain.thenCompose(v -> workFactory.apply(server.getClient()));
            } else {
                LOG.log(Level.FINE, "Server {0}: running work {1} with factory {2} immediately", new Object[] { server, desc,workFactory });
                next = workFactory.apply(server.getClient());
            }
            return next.whenComplete((r, t) -> {
                synchronized (this) {
                    if (lastOperation == last) {
                        lastOperation = null;
                    } 
                }
                // completes the Future, potentially executing a chained workFactory
                last.complete(r);
            });
        }
    }
    
    @Override
    public LspSessionService create(LspServerState state) {
        ServerAdapter bridge = new ServerAdapter(state);
        synchronized (this) {
            serverAdapters.put(state, bridge);
        }
        return bridge;
    }
    
    @Override
    protected CloneableEditorSupport redirect(Lookup env) {
        FileObject fo = env.lookup(FileObject.class);
        if (fo == null) {
            return null;
        }
        EditorLspSupport redir;
        synchronized (this) {
            redir = redirectors.get(fo);
            if (redir != null) {
                return Boolean.TRUE.equals(redir.nestedRedirect.get()) ? null : redir;
            }
            CloneableEditorSupport original = (CloneableEditorSupport)fo.getLookup().lookup(org.openide.cookies.EditorCookie.class);
            redir = new EditorLspSupport(original, fo, this);
            LOG.log(Level.FINE, "{0}: Creating redirector: {1}", new Object[] { fo, redir });
            redirectors.put(fo, redir);
        }
        return redir;
    }

    @Override
    public boolean checkLocalChangesPending(LspServerState server, String uri, Document doc) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return false;
            }
            EditorLspSupport redir;
            synchronized (this) {
                redir = redirectors.get(fo);
            }
            if (redir != null && redir.getDocument() == doc) {
                return redir.isPendingChanges(server);
            }
            LOG.log(Level.FINE, "{0}: No pending local change", fo);
            return false;
        } catch (MalformedURLException ex) {
            return false;
        }
    }
    
    @Override
    public boolean notifyDidOpenDocument(LspServerState server, String uri, String content) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return true;
            }
            EditorLspSupport redir;
            synchronized(this) {
                redir = redirectors.get(fo);
            }
            if (redir == null) {
                return true;
            }
            redir.openedAtClient();
            return !redir.isOpenDocumentPending(server, uri, content);
        } catch (MalformedURLException ex) {
            return true;
        }
    }
    
    @Override
    public List<TextDocumentContentChangeEvent> adjustDocumentChanges(LspServerState server, String uri, Document doc, List<TextDocumentContentChangeEvent> edits) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return edits;
            }
            EditorLspSupport redir;
            synchronized(this) {
                redir = redirectors.get(fo);
            }
            if (redir != null && redir.getDocument() == doc) {
                return redir.adjustDocumentChanges(server, uri, edits);
            }
            LOG.log(Level.FINE, "{0}: No changes for client edit events", fo);
            return edits;
        } catch (MalformedURLException ex) {
            return edits;
        }
    }
    
    /**
     * Executes a text change originated at the client. All document manipulations made
     * during Runnable execution will NOT be treated as NBLS-originated
     * @param ch 
     */
    @Override
    public void runClientDocumentChange(ClientDocumentAction ch) throws BadLocationException {
        LspServerState old = currentClient.get();
        LspServerState now = Lookup.getDefault().lookup(LspServerState.class);
        try {
            currentClient.set(now);
            LOG.log(Level.FINE, "Start client change from {0}", now);
            ch.run();
        } finally {
            LOG.log(Level.FINE, "End client change from {0}", now);
            if (old == null) {
                currentClient.remove();
            } else {
                currentClient.set(old);
            }
        }
    }
    
    static boolean isClientChange() {
        return currentClient.get() != null;
    }
    
    /**
     * Determines, if the call originates in open/close/reload operation of the
     * CloneableEditorSupport.
     * @return true, if from reload/open/close.
     */
    static boolean isFromOpenClose() {
        StackTraceElement[] els = Thread.currentThread().getStackTrace();
        for (int i = els.length - 1; i >= Math.max(0, els.length - 10); i--) {
            StackTraceElement e = els[i];
            if (e.getClassName().contains("org.openide.text.DocumentOpenClose")) { // NOI18N
                return true;
            }
        }
        return false;
    }

    private final Map<FileObject, Set<LspServerState>> openedURIs = new HashMap<>();
    private final Map<LspServerState, ServerAdapter> serverAdapters = new HashMap<>();

    public synchronized <T> CompletableFuture<T> whenClientReady(LspServerState server, Object desc, Function<NbCodeLanguageClient, CompletableFuture<T>> workFactory) {
        ServerAdapter bridge = serverAdapters.get(server);
        if (bridge == null) {
            CompletableFuture<T> cf = new CompletableFuture<>();
            cf.completeExceptionally(new IOException("Unknown or detached client"));
            return cf;
        }
        return bridge.whenClientReady(desc, workFactory);
    }

    public void registerOpenedDocument(FileObject uri, Document document, LspServerState state) {
        LOG.log(Level.FINE, "Server {0} opening document {1}", new Object[] { state, uri });
        synchronized (this) {
            openedURIs.computeIfAbsent(uri, u -> new HashSet<>()).add(state);
        }
    }

    public void serverShutdown(LspServerState srv) {
        List<FileObject> files = new ArrayList<>();
        synchronized (this) {
            for (FileObject f : openedURIs.keySet()) {
                if (openedURIs.get(f).contains(srv)) {
                    files.add(f);
                }
            }
            serverAdapters.remove(srv);
        }
        files.forEach(u -> removeClosedDocument(u, null, srv));
    }

    public void removeClosedDocument(FileObject uri, Document document, LspServerState state) {
        LOG.log(Level.FINE, "Server {0} closed document {1}", new Object[] { state, uri });
        synchronized (this) {
            Set<LspServerState> servers = openedURIs.get(uri);
            if (servers != null) {
                servers.remove(state);
                if (servers.isEmpty()) {
                    openedURIs.remove(uri);
                }
            }
        }
    }

    public Set<FileObject> getOpenedURIs() {
        synchronized (this) {
            return new HashSet<>(openedURIs.keySet());
        }
    }

    public Set<LspServerState> getAttachedServers(FileObject f) {
        synchronized (this) {
            return new HashSet<>(openedURIs.getOrDefault(f, Collections.emptySet()));
        }
    }
}

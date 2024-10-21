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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.editor.document.AtomicLockDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.LspServerUtils;
import org.netbeans.modules.java.lsp.server.Utils;
import org.openide.NotifyDescriptor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.Union2;

/**
 *
 * @author sdedic
 */
public class EditorLspSupport extends CloneableEditorSupport implements Runnable {
    private static final RequestProcessor RP = new RequestProcessor(DocumentSyncSupport.class);
    
    private final FileObject file;
    private final CloneableEditorSupport original;
    private final ServerDocumentRegistry registry;
    private LspChangesConverter converter;
    private RequestProcessor.Task changesCollector;
    ThreadLocal<Boolean> nestedRedirect = new ThreadLocal<>();
    private Map<LspServerState, List<PendingEdit>> applyingEdits = new HashMap<>();
    private boolean syncBroken;

    public EditorLspSupport(CloneableEditorSupport original, FileObject file, ServerDocumentRegistry registry) {
        super(new DocumentSyncSupport.RedirEnv(file, original));
        this.file = file;
        this.original = original;
        this.registry = registry;
        addPropertyChangeListener(pe -> {
            if (EditorCookie.Observable.PROP_DOCUMENT.equals(pe.getPropertyName())) {
                clearPendingChanges();
            }
        });
    }
    
    class LspDocumentFilter extends DocumentFilter {

        @Override
        public void insertString(DocumentFilter.FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            super.insertString(fb, offset, string, attr);
        }

        @Override
        public void remove(DocumentFilter.FilterBypass fb, int offset, int length) throws BadLocationException {
            super.remove(fb, offset, length);
        }
        
    }

    public boolean ignoreReloadAttempt() {
        EditorKit ek = createEditorKit();
        StyledDocument doc = (StyledDocument) ek.createDefaultDocument();
        String readContent;
        try (InputStream is = file.getInputStream()) {
            loadFromStreamToKit(doc, is, ek);
            readContent = doc.getText(0, doc.getLength());
        } catch (IOException | BadLocationException ex) {
            // conservative result
            return false;
        }
        return true;
    }

    public synchronized boolean isPendingChanges(LspServerState server) {
        return !applyingEdits.getOrDefault(server, Collections.emptyList()).isEmpty();
    }

    public synchronized boolean isOpenDocumentPending(LspServerState server, String uri, String content) {
        List<PendingEdit> pending = applyingEdits.getOrDefault(server, Collections.emptyList());
        if (pending.isEmpty()) {
            // the Converter may have recorded some changes, taking an initial snapshot
            if (converter != null) {
                String initialSnaphot = converter.getContentCopy();
                if (content.equals(initialSnaphot)) {
                    return true;
                }
            }
            return false;
        }
        // let's be conservative: the 'document open' is pending, if:
        // - there are changes queued for the server
        // - the server has not opened the document yet
        // - the content the server THINKS the document has is the same as
        //   the content snapshot of the unprocessed change
        if (registry.getAttachedServers(uri).contains(server)) {
            return false;
        }
        return pending.get(0).initialContent.equals(content);
    }

    void clearPendingChanges() {
        synchronized (this) {
            if (changesCollector != null) {
                changesCollector.cancel();
                changesCollector = null;
            }
            applyingEdits.clear();
            flushConverter(false);
        }
    }

    void scheduleChangeSync(Document d) {
        synchronized (this) {
            if (changesCollector == null) {
                changesCollector = RP.create(this);
                DocumentSyncSupport.LOG.log(Level.FINE, "{0}: starting modifications.", file);
            }
            changesCollector.schedule(20000);
        }
    }

    List<TextDocumentContentChangeEvent> adjustDocumentChanges(LspServerState server, String uri, List<TextDocumentContentChangeEvent> events) {
        List<PendingEdit> pending = applyingEdits.get(server);
        if (pending == null || pending.isEmpty()) {
            return events;
        }
        PendingEdit e = pending.get(0);
        WorkspaceEditMatcher matcher = new WorkspaceEditMatcher(e.initialContent, e.edits, e.removedContent, events);
        if (matcher.matches()) {
            pending.remove(e);
            return Collections.emptyList();
        }
        boolean overlaps = false;
        for (TextDocumentContentChangeEvent te : events) {
            for (PendingEdit p : pending) {
                if (p.overlaps(te)) {
                    overlaps = true;
                }
            }
        }
        if (overlaps) {
            if (!syncBroken) {
                String s;
                try {
                    FileObject f = Utils.fromUri(uri);
                    s = f.getPath();
                } catch (MalformedURLException ex) {
                    s = uri.toString();
                }
                NotifyDescriptor desc = new NotifyDescriptor("The same parts of document " + s + " are being modified from both the editor and the Apache NetBeans Language Server. " + " You should save the content now to ensure the document consistency", "Concurrent modification", NotifyDescriptor.DEFAULT_OPTION, NotifyDescriptor.WARNING_MESSAGE, null, null);
            }
            syncBroken = true;
        } else {
            // apply the edits, supposing they will happen:
            for (PendingEdit edit : pending) {
                edit.apply(events);
            }
        }
        return events;
    }

    private PendingEdit flushConverter(boolean save) {
        LspChangesConverter conv;
        synchronized (this) {
            conv = this.converter;
            if (conv == null || conv.isEmpty()) {
                return null;
            }
        }
        PendingEdit[] result = new PendingEdit[1];
        conv.getDocument().render(() -> {
            synchronized (this) {
                if (this.converter != conv) {
                    return;
                }
                this.converter = new LspChangesConverter(original, conv.getDocument(), this::scheduleChangeSync);
                if (changesCollector != null) {
                    changesCollector.cancel();
                }
                changesCollector = null;
            }
            AtomicLockDocument ald = LineDocumentUtils.asRequired(conv.getDocument(), AtomicLockDocument.class);
            ald.removeAtomicLockListener(conv);
            conv.getDocument().removeDocumentListener(conv);
            conv.disable = true;
            conv.getDocument().addDocumentListener(converter);
            ald.addAtomicLockListener(converter);
            result[0] = new PendingEdit(conv.getClients(), conv.contentCopy, conv.makeTextEdits(), conv.getRemovedParts(), save);
        });
        return result[0];
    }

    @Override
    public void run() {
        // XXX FIXME run was called during debugging unexpectedly after events have been sent to the client ??
        PendingEdit pending;
        pending = flushConverter(false);
        if (pending == null) {
            return;
        }
        String uri = Utils.toUri(file);
        WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(Union2.createFirst(new TextDocumentEdit(uri, pending.edits))));
        DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Applying edits: {1}", new Object[]{file, pending.edits});
        Set<LspServerState> recipients = new HashSet<>(registry.getAttachedServers(uri));
        recipients.addAll(pending.getRecipients());
        recipients.forEach(srv -> {
            DocumentSyncSupport.LOG.log(Level.FINER, "{0}: sending to client", srv);
            applyingEdits.computeIfAbsent(srv, c -> new ArrayList<>()).add(pending);
            ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams(Utils.workspaceEditFromApi(we, uri, srv.getClient()));
            registry.whenClientReady(srv, client -> client.applyEdit(params).handle((r, t) -> {
                recordEditsApplied(pending, srv, r, t);
                return null;
            }));
        });
    }

    void recordEditsApplied(PendingEdit pending, LspServerState server, ApplyWorkspaceEditResponse resp, Throwable t) {
        synchronized (this) {
            applyingEdits.getOrDefault(server, new ArrayList<>()).remove(pending);
            // TODO: handle possible error condition if t != null;
        }
    }

    @Override
    public void saveDocument() throws IOException {
        LspServerState server = Lookup.getDefault().lookup(LspServerState.class);
        boolean clientThread = LspServerUtils.isClientResponseThread(null);
        if (server == null) {
            Boolean r = nestedRedirect.get();
            try {
                nestedRedirect.set(true);
                original.saveDocument();
            } finally {
                if (r == null) {
                    nestedRedirect.remove();
                } else {
                    nestedRedirect.set(r);
                }
            }
            return;
        }
        IOException[] fail = new IOException[1];
        Runnable r = () -> {
            RP.post(() -> {
                // flush must be postponed as much as possible, so Positions track document insertions.
                PendingEdit nblsEdits = flushConverter(true);
                DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Capturing saveDocument() with edits {1}", new Object[]{file, nblsEdits});
                List<TextEdit> edits = nblsEdits == null ? Collections.emptyList() : nblsEdits.edits;
                TextDocumentEdit te = new TextDocumentEdit(Utils.toUri(file), edits);
                WorkspaceEdit edit = new WorkspaceEdit(Collections.singletonList(Union2.createFirst(te)));
                if (nblsEdits != null) {
                    applyingEdits.computeIfAbsent(server, x -> new ArrayList<>()).add(nblsEdits);
                }
                CompletableFuture<List<String>> f = registry.whenClientReady(server, client -> WorkspaceEdit.applyEdits(Collections.singletonList(edit), true));
                try {
                    if (f.get().isEmpty()) {
                        fail[0] = new IOException("Client failed to save file.");
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    fail[0] = new IOException(ex);
                }
                recordEditsApplied(nblsEdits, server, new ApplyWorkspaceEditResponse(true), fail[0]);
            }).waitFinished();
        };
        if (clientThread) {
            RequestProcessor.getDefault().post(r, 20000);
        } else {
            r.run();
        }
        if (fail[0] != null) {
            throw fail[0];
        }
    }

    @Override
    public boolean isModified() {
        Boolean r = nestedRedirect.get();
        try {
            nestedRedirect.set(true);
            return original.isModified();
        } finally {
            if (r == null) {
                nestedRedirect.remove();
            } else {
                nestedRedirect.set(r);
            }
        }
    }

    @Override
    public StyledDocument openDocument() throws IOException {
        Boolean r = nestedRedirect.get();
        try {
            nestedRedirect.set(true);
            DocumentSyncSupport.LOG.log(Level.FINE, "Opening document: {0}", file);
            StyledDocument sd = original.openDocument();
            synchronized (this) {
                if (converter != null) {
                    return sd;
                }
                converter = new LspChangesConverter(original, sd, this::scheduleChangeSync);
                AtomicLockDocument ald = LineDocumentUtils.asRequired(sd, AtomicLockDocument.class);
                ald.addAtomicLockListener(converter);
                sd.addDocumentListener(converter);
                ((AbstractDocument)sd).setDocumentFilter(new LspDocumentFilter());
            }
            return sd;
        } finally {
            if (r == null) {
                nestedRedirect.remove();
            } else {
                nestedRedirect.set(r);
            }
        }
    }

    public StyledDocument getDocument() {
        Boolean r = nestedRedirect.get();
        try {
            nestedRedirect.set(true);
            return original.getDocument();
        } finally {
            if (r == null) {
                nestedRedirect.remove();
            } else {
                nestedRedirect.set(r);
            }
        }
    }

    @Override
    public Task prepareDocument() {
        Boolean r = nestedRedirect.get();
        try {
            nestedRedirect.set(true);
            return original.prepareDocument();
        } finally {
            if (r == null) {
                nestedRedirect.remove();
            } else {
                nestedRedirect.set(r);
            }
        }
    }

    @Override
    protected String messageOpening() {
        return null;
    }

    @Override
    protected String messageOpened() {
        return null;
    }

    @Override
    protected String messageSave() {
        return null;
    }

    @Override
    protected String messageName() {
        return null;
    }

    @Override
    protected String messageToolTip() {
        return null;
    }
    
}

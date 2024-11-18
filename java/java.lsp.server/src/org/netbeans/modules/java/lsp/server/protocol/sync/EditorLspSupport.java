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

import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.StyledDocument;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.editor.document.AtomicLockDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.LspServerUtils;
import org.netbeans.modules.java.lsp.server.Utils;
import org.netbeans.modules.java.lsp.server.protocol.SaveDocumentRequestParams;
import static org.netbeans.modules.java.lsp.server.protocol.sync.DocumentSyncSupport.LOG;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.Union2;
import org.openide.util.UserQuestionException;
import org.openide.windows.CloneableOpenSupport;

/**
 * Implementation of CloneableEditorSupport that delegates to the original one, but 
 * handles some requests differently communicating with LSP client.
 * 
 * @author sdedic
 */
public class EditorLspSupport extends CloneableEditorSupport implements Runnable {
    private final int CHANGE_COLLECTING_DELAY = Integer.getInteger(DocumentSyncSupport.class.getName() + ".collectDelay", 50);
    private final int CHANGE_SAVE_DELAY = Integer.getInteger(DocumentSyncSupport.class.getName() + ".saveDelay", 0);
    
    /**
     * Work queue should be processed in this RP.
     */
    private static final RequestProcessor RP = new RequestProcessor(DocumentSyncSupport.class);
    
    private final FileObject file;
    private final CloneableEditorSupport original;
    private final ServerFileRegistry registry;
    
    /**
     * The converter watching the document. Will be replaced after flush.
     */
    private LspChangesConverter converter;
    
    /**
     * Task scheduled for flushing changes from the converter.
     */
    private RequestProcessor.Task changesCollector;
    
    /**
     * True, if the document was opened before any of the clients has requested it.
     */
    private volatile boolean openedAtServer;
    
    /**
     * True means the redirection from CloneableEditorSupport should be skipped.
     * call. Allows the EditorLspSupport itself to call the original implementation.
     */
    final ThreadLocal<Boolean> nestedRedirect = new ThreadLocal<>();
    
    /**
     * The edit being sent to LSP clients.
     */
    /**
     * Edits being processed at the client. 
     */
    // @GuardedBy(this)
    private final List<PendingEdit> pendingEdits = new ArrayList<>();
    
    /**
     * Synchronization is broken, local edits are not allowed until document reloads. 
     */
    private volatile boolean syncBroken;
    
    /**
     * True, if reload because of didSave() was blocked and postponed.
     */
    private LspServerState saveBlocked;
    
    /**
     * Document reference. Document should not be held directly to prevent it to GC eventually.
     */
    private Reference<Document> documentRef;

    public EditorLspSupport(CloneableEditorSupport original, FileObject file, ServerFileRegistry registry) {
        super(new RedirEnv(file, original));
        this.file = file;
        this.original = original;
        this.registry = registry;
        addPropertyChangeListener(pe -> {
            if (EditorCookie.Observable.PROP_DOCUMENT.equals(pe.getPropertyName())) {
                // potentially null ref if document expired.
                documentRef = new WeakReference<>(original.getDocument());
                clearPendingChanges();
            }
        });
    }
    
    public static class RedirEnv implements CloneableEditorSupport.Env {
        private final FileObject file;
        private final CloneableEditorSupport original;

        public RedirEnv(FileObject file, CloneableEditorSupport original) {
            this.file = file;
            this.original = original;
        }
        
        @Override
        public InputStream inputStream() throws IOException {
            return file.getInputStream();
        }

        @Override
        public OutputStream outputStream() throws IOException {
            throw new IOException("");
        }

        @Override
        public Date getTime() {
            return file.lastModified();
        }

        @Override
        public String getMimeType() {
            return file.getMIMEType();
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
        }

        @Override
        public void addVetoableChangeListener(VetoableChangeListener l) {
        }

        @Override
        public void removeVetoableChangeListener(VetoableChangeListener l) {
        }

        @Override
        public boolean isValid() {
            try {
                return DataObject.find(file).isValid();
            } catch (IOException ex) {
                return false;
            }
        }

        @Override
        public boolean isModified() {
            return original.isModified();
        }

        @Override
        public void markModified() throws IOException {
        }

        @Override
        public void unmarkModified() {
        }

        @Override
        public CloneableOpenSupport findCloneableOpenSupport() {
            return original;
        }
    }
    
    /**
     * Special exception that will abort the reload. The application must be branded
     * to treat this type as "reject" always.
     */
    class CancelReloadException extends UserQuestionException {
        public CancelReloadException(String s) {
            super(s);
        }

        @Override
        public void confirmed() throws IOException {
            LOG.log(Level.FINE, "{0}: skipping reload", file);
        }
    }

    /**
     * DocumentFilter that can block document reloads. A reload is defined as "replace entire document with a new content"
     * and starts with {@code remove(0, doc.getLength())}. We must NOT allow reload, if there are still pending changes
     * that are not delivered to the client. In that case, AFTER the sync queue is flushed, the client will be asked
     * to save the result and the save will be unblocked, since at that time the client and server can synchronize
     * of the jointly modified content.
     * <p>
     * The Blocker also blocks local changes if the document synchronization is broken: any change operation done locally
     * fails and throws a {@link BadLocationException}, aborting whatever modification / refactoring is running.
     * 
     */
    @NbBundle.Messages({
        "# {0} - document path",
        "ERR_SyncBrokenUntilSave=Synchronization is broken for document {0}. Local changes are disabled until client saves the document.",
        "# {0} - document path",
        "ERR_SyncReloadPostponed={0} save will be postponed until after client synchronizes edits"
    })
    final class DocumentChangesBlocker extends DocumentFilter {
        private final DocumentFilter origFilter;

        public DocumentChangesBlocker(DocumentFilter origFilter) {
            this.origFilter = origFilter;
        }
        
        private void checkModificationsAllowed(int offset) throws BadLocationException {
            if (offset == 0 && DocumentSyncSupport.isFromOpenClose()) {
                if (postponeReloadEvent()) {
                    BadLocationException ble = new BadLocationException(Bundle.ERR_SyncReloadPostponed(file.getPath()), offset);
                    ble.initCause(new CancelReloadException("Blocked reload operation"));
                    throw ble;
                }
            } else {
                if (!DocumentSyncSupport.isClientChange()) {
                    if (syncBroken) {
                        throw new BadLocationException(Bundle.ERR_SyncBrokenUntilSave(file.getPath()), offset);
                    }
                }
            }
        }
        
        @Override
        public void insertString(DocumentFilter.FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            checkModificationsAllowed(offset);
            if (origFilter != null) {
                origFilter.insertString(fb, offset, string, attr);
            } else {
                fb.insertString(offset, string, attr);
            }
        }

        @Override
        public void remove(DocumentFilter.FilterBypass fb, int offset, int length) throws BadLocationException {
            checkModificationsAllowed(offset);
            if (origFilter != null) {
                origFilter.remove(fb, offset, length);
            } else {
                fb.remove(offset, length);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            checkModificationsAllowed(offset);
            if (origFilter != null) {
                origFilter.replace(fb, offset, length, text, attrs);
            } else {
                fb.replace(offset, length, text, attrs);
            }
        }
    }
    
    /**
     * Checks if the reload should be blocked and postponed after server sends all
     * edits to the client.
     * 
     * @return true, if reload should be postponed.
     */
    private boolean postponeReloadEvent() {
        synchronized (this) {
            if (saveBlocked != null) {
                LOG.log(Level.FINE, "Reload {0}: save operation is blocked in favour of {1}", new Object[] { file, saveBlocked });
                return true;
            }
            if (pendingEdits.isEmpty()) {
                if (converter == null || converter.isEmpty()) {
                    if (syncBroken) {
                        LOG.log(Level.FINE, "Reload {0}: sync queue is empty, CLEAR broken flag", file);
                    }
                    // permit reload, clear broken flag as the document becomes consistent with the client again.
                    syncBroken = false;
                    return false;
                }
            } else {
                PendingEdit e = pendingEdits.get(0);
                if (e.saveAfterEdit) {
                    LOG.log(Level.FINE, "Reload {0}: save operation during client-requested save()", file);
                    return false;
                }
            }
            LspServerState srv = DocumentSyncSupport.currentClient.get();
            if (srv == null) {
                Collection<LspServerState> srvs = registry.getAttachedServers(file);
                if (srvs.isEmpty()) {
                    saveBlocked = null;
                    LOG.log(Level.FINE, "Reload {0}: document has no clients, reloading.", file);
                    return false;
                }
                srv = srvs.iterator().next();
            }
            LOG.log(Level.FINE, "Reload {0}: save POSTPONED, will be handled by {1}", new Object[] { file, srv });
            saveBlocked = srv;
            return true;
        }
    }
    
    public synchronized boolean isPendingChanges(LspServerState server) {
        return !((converter == null || converter.isEmpty()) && pendingEdits.isEmpty());
    }

    public synchronized boolean isOpenDocumentPending(LspServerState server, String uri, String content) {
        List<PendingEdit> pending = pendingEdits.stream().filter(e -> e.contains(server)).toList();
        if (pending.isEmpty()) {
            // the Converter may have recorded some changes, taking an initial snapshot
            if (converter != null && !converter.isEmpty()) {
                ContentSnapshot initialSnaphot = converter.getContentCopy();
                if (initialSnaphot != null && content.equals(initialSnaphot.getText())) {
                    LOG.log(Level.FINE, "{0}: locally-only modified file independently opened at client {1}", new Object[] { file, server });
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
        if (registry.getAttachedServers(file).contains(server)) {
            LOG.log(Level.FINE, "{0}: ERROR ? document open reported for a file modified by server", new Object[] { file, server });
            return false;
        }
        PendingEdit pe = pending.get(0);
        boolean b = !pe.edits.isEmpty() && pe.initialContent.getText().equals(content);
        LOG.log(Level.FINE, "{0}: document with PendingEdit {1} reported open from {2}, result: {3}", new Object[] { file, pe, server, b });
        return b;
    }

    void clearPendingChanges() {
        List<PendingEdit> edits;
        synchronized (this) {
            if (changesCollector != null) {
                changesCollector.cancel();
                changesCollector = null;
            }
            edits = new ArrayList<>(pendingEdits);
            pendingEdits.clear();
            flushConverter();
            saveBlocked = null;
            syncBroken = false;
            // TBD: what to do with `syncBroken` flag ? The loaded content may be different from that on the client (?).
            // In theory a modified Document will never GC so this should not happen.
        }
        Throwable t = new IOException();
        edits.forEach(e -> e.getCompletion().completeExceptionally(t));
    }

    void scheduleChangeSync(Document d) {
        synchronized (this) {
            if (changesCollector == null) {
                changesCollector = RP.create(this);
                DocumentSyncSupport.LOG.log(Level.FINE, "{0}: starting modifications.", file);
            }
            changesCollector.schedule(CHANGE_COLLECTING_DELAY);
        }
    }
    
    synchronized void openedAtClient() {
        if (openedAtServer) {
            LOG.log(Level.FINE, "{0}: Client opened a document which was previously local", file);
            openedAtServer = false;
        }
    }
    
    synchronized List<TextDocumentContentChangeEvent> adjustDocumentChanges(LspServerState server, String uri, List<TextDocumentContentChangeEvent> events) {
        DocumentChangesConverter c;
        if (pendingEdits.isEmpty()) {
            if (converter == null || converter.isEmpty()) {
                return events;
            }
        } else {
            PendingEdit e = pendingEdits.get(0);
            WorkspaceEditMatcher matcher = new WorkspaceEditMatcher(e.initialContent.getText(), e.edits, e.removedContent, events);
            if (matcher.matches()) {
                // note: the edit event will be removed after it has been fully processed.
                LOG.log(Level.FINE, "{0}: MATCHED received changes to pending edit {1}", new Object[] { file, e });
                return Collections.emptyList();
            } else {
                LOG.log(Level.FINE, "{0}: Received MISMATCHES changes with pending edit {1}, marking BROKEN", new Object[] { file, e });
                // irrecoverable: the edit has been sent to the client already, but received other client change afterwards.
                markSyncBroken();
                // allow changes to happen
                return events;
            }
        }
        
        c = this.converter;
        // this does doc.render(), but runAtomic is already in effect, so OK. This is important
        // as no new events can reach the Converter. Also no new message is accepted as this is the LSP reception thread
        PendingEdit pe = flushConverter();
        ContentSnapshot live;
        LOG.log(Level.FINE, "{0}: merging local changes with client", file);
        LOG.log(Level.FINER, "{0}: local changes: ", new Object[] { file, pe });
        LOG.log(Level.FINER, "{0}: client changes: ", new Object[] { file, events });
        live = new ContentSnapshot(c.getDocument());
        TwoWayMerger merger = new TwoWayMerger(pe.initialContent, live, pe.edits, pe.removedContent);
        try {
            merger.withClientEvents(events).merge();
        } catch (BadLocationException ex) {
            LOG.log(Level.FINE, "{0}: FAILED merge, mark broken", file);
            markSyncBroken();
            return events;
        }
        LOG.log(Level.FINER, "{0}: merged local changes: ", new Object[] { file, merger.getUpdatedLocalEdits() });
        LOG.log(Level.FINER, "{0}: merged client changes: ", new Object[] { file, merger.getUpdatedClientEvents() });
        if (converter != null) {
            converter.useInitialEdits(merger.getUpdatedSnapshot(), merger.getUpdatedLocalEdits(), merger.getUpdatedRemovedText());
        }
        return merger.getUpdatedClientEvents();
    }
    
    private PendingEdit flushConverter() {
        return flushConverter(false, null);
    }
    
    private PendingEdit flushConverter(boolean save, LspServerState toClient) {
        PendingEdit[] result = new PendingEdit[1];
        Document d;
        LspChangesConverter conv;
        synchronized (this) {
            d = documentRef.get();
            if (d == null) {
                return null;
            }
            conv = this.converter;
            if (conv == null || conv.isEmpty()) {
                return null;
            }
        }
        // first render, then lock. The render ensures that no further events come to the Converter.
        d.render(() -> {
            synchronized (this) {
                if (this.converter != conv) {
                    return;
                }
                this.converter = new LspChangesConverter(original, conv.getDocument(), this::scheduleChangeSync);
                conv.getDocument().putProperty(LspChangesConverter.class, converter);
                if (changesCollector != null) {
                    changesCollector.cancel();
                }
                changesCollector = null;
                AtomicLockDocument ald = LineDocumentUtils.asRequired(conv.getDocument(), AtomicLockDocument.class);
                ald.removeAtomicLockListener(conv);
                conv.getDocument().removeDocumentListener(conv);
                conv.disable();
                
                result[0] = new PendingEdit(toClient, conv.getContentCopy(), conv.makeTextEdits(), conv.getRemovedParts(), save, conv.getClients());
                result[0].setServerOpen(openedAtServer);
                conv.getDocument().addDocumentListener(converter);
                ald.addAtomicLockListener(converter);
                
                LOG.log(Level.FINER, "{0}: flushed changes: {1} ", new Object[] { file, result[0] });
            }
        });
        return result[0];
    }

    @Override
    public void run() {
        // XXX FIXME run was called during debugging unexpectedly after events have been sent to the client ??
        PendingEdit pending;
        synchronized (this) {
            // try again later
            if (!this.pendingEdits.isEmpty()) {
                DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Postponing collection, client synchronizing", file);
                scheduleChangeSync(null);
                return;
            }
        }
        pending = flushConverter();
        if (pending == null) {
            return;
        }
        DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Collected edits: {1}", new Object[]{file, pending });
        schedulePendingEvent(pending);
    }
    
    synchronized CompletableFuture<Void> schedulePendingEvent(PendingEdit pe) {
        boolean wasEmpty = pendingEdits.isEmpty();
        pendingEdits.add(pe);
        if (wasEmpty) {
            processEditQueue(null);
        } else {
            LOG.log(Level.FINE, "{0}: scheduling edit {1} after {2}: ", new Object[] { file, pe, pendingEdits.get(0).getId() });
        }
        return pe.getCompletion();
    }
    
    private void processEditQueue(PendingEdit toFinish) {
        PendingEdit pe = null;
        PendingEdit save = null;
        boolean localReload = false;
        X: synchronized (this) {
            // only allow processing of events in the dedicated thread.
            if (!RP.isRequestProcessorThread()) {
                RP.post(() -> processEditQueue(toFinish));
                return;
            }
            
            if (toFinish != null) {
                if (!pendingEdits.remove(toFinish)) {
                    LOG.log(Level.FINE, "{0}: unknown edit finishing: ", new Object[] { file, toFinish });
                    return;
                }
                LOG.log(Level.FINER, "{0}: finished edit: {1}", new Object[] { file, toFinish });
            }
            if (pendingEdits.isEmpty()) {
                if (saveBlocked != null) {
                    LspServerState sendTo = saveBlocked;

                    Collection<LspServerState> clients = registry.getAttachedServers(file);
                    if (!clients.contains(saveBlocked)) {
                        if (!clients.isEmpty()) {
                            sendTo = clients.iterator().next();
                        } else {
                            localReload = true;
                            break X;
                        }
                    }
                    LOG.log(Level.FINE, "{0}: Sending delayed save to client {1}", new Object[] { file, sendTo });
                    save = new PendingEdit(sendTo, 
                            null, Collections.emptyList(), Collections.emptyMap(), true);
                    pendingEdits.add(save);
                    pe = save;
                }
            } else {
                pe = pendingEdits.get(0);
            }
        }
        
        // no synchronized block here.
        if (pe != null) {
            processPendingEvent(pe);
        } else if (localReload) {
            LOG.log(Level.FINE, "{0}: Trying to reload locally", file);
            try {
                Method reload = CloneableEditorSupport.class.getDeclaredMethod("reloadDocument");
                reload.setAccessible(true);
                org.openide.util.Task t = (org.openide.util.Task)reload.invoke(original);
                // wait for a limited time, this could be enough for the reload to complete, blocking LSP queue. We do not want to block LSP queue indefinitely:
                // in case of an error, the server could become unresponsive.
                if (!t.waitFinished(300)) {
                    LOG.log(Level.WARNING, "{0}: document reload did not finish in 300ms", file);
                }
            } catch (ReflectiveOperationException | InterruptedException | SecurityException ex) {
                // nop 
            }
        }
    }

    synchronized void processPendingEvent(PendingEdit pe) {
        Set<LspServerState> clients = new HashSet<>(registry.getAttachedServers(file));
        // there may be clients that have not opened the document yet, but issued commands that edited files:
        clients.addAll(pe.getActiveClients());
        LspServerState point = pe.getTarget();
        if (point != null && !clients.contains(point)) {
            clients.add(point);
        }
        String uri = Utils.toUri(file);
        CompletableFuture<Void> editsDone;
        AtomicBoolean broken = new AtomicBoolean();
        LspServerState fPoint = point;
       
        // for non-empty edit part, collect edits to all clients that eiter opened the document or have originated the edit, and make Future
        // that completes after all those edits are complete.
        if (!pe.edits.isEmpty()) {
            DocumentSyncSupport.LOG.log(Level.FINER, "{0}: Applying edits: {1}, to clients {2}", new Object[]{file, pe, clients});
            List<CompletableFuture<Void>> completions = new ArrayList<>();
            WorkspaceEdit we = new WorkspaceEdit(Collections.singletonList(Union2.createFirst(new TextDocumentEdit(uri, pe.edits))));
            clients.forEach(srv -> {
                DocumentSyncSupport.LOG.log(Level.FINER, "{0}: sending to client", srv);
                ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams(Utils.workspaceEditFromApi(we, uri, srv.getClient()));
                completions.add(registry.whenClientReady(srv, params, client -> client.applyEdit(params).handle((r, t) -> {
                    try {
                        if (!r.isApplied() || (r.getFailedChange() != null)) {
                            DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Client: {1} reports failure", new Object[]{file, srv});
                            broken.set(true);
                            markSyncBroken();
                        }
                    } catch (Exception | Error e) {
                        Exceptions.printStackTrace(e);
                    }
                    return null;
                })));
            });
            editsDone = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
        } else {
            editsDone = CompletableFuture.completedFuture(null);
        }
        
        editsDone.thenCompose((x) -> {
            LspServerState saver = fPoint;
            LspServerState blocked;
            
            synchronized (this) {
                blocked = saveBlocked;
                // do not request save for broken documents, leave it to the user.
                if (broken.get() || syncBroken) {
                    return CompletableFuture.completedFuture(null);
                }
                if (pe.initialContent == null && pendingEdits.size() > 1) {
                    // ignore event that originated from a blocking save
                    return CompletableFuture.completedFuture(null);
                }
                if (!pe.saveAfterEdit || fPoint == null) {
                    return CompletableFuture.completedFuture(null);
                }
                if (pe.initialContent == null) {
                    // will process save event when it arrives.
                    if (saveBlocked != null) {
                        DocumentSyncSupport.LOG.log(Level.FINER, "{0}: Unblocking save", file);
                        saveBlocked = null;
                    }
                }
            }
            Collection<LspServerState> saveClients = registry.getAttachedServers(file);
            if (saver == null) {
                saver = blocked;
            }
            if (!saveClients.contains(saver)) {
                // do not send to client that have closed the document, pick from the remaining
                // or save locally if no client is willing to handle.
                if (saveClients.isEmpty()) {
                    CompletableFuture<Boolean> fLocalSave = new CompletableFuture<>();
                    try {
                        saveDocumentLocally();
                        fLocalSave.complete(true);
                    } catch (IOException ex) {
                        // the same future as requestDocumentSave
                        fLocalSave.completeExceptionally(ex);
                    }
                    return fLocalSave;
                } else {
                    saver = saveClients.iterator().next();
                }
            }
            DocumentSyncSupport.LOG.log(Level.FINER, "{0}: Requesting save from client", saver);
            SaveDocumentRequestParams params = new SaveDocumentRequestParams(Collections.singletonList(uri));
            return registry.whenClientReady(fPoint, params, client -> 
                client.requestDocumentSave(params)
            );
        }).handle((s, t) -> {
            if (Boolean.FALSE.equals(s) || broken.get() || t != null || syncBroken) {
                pe.getCompletion().completeExceptionally(new IOException());
            } else {
                pe.getCompletion().complete(null);
            }
            processEditQueue(pe);
            return null;
        });
    }
    
    @NbBundle.Messages({
        "# {0} - filename",
        "ERR_SynchronizationBroken=The Apache NetBeans LSP server attempted to modify the document {0} at the same time it was modified in the editor (LSP client). " +
            "The document now may contain conflicting or inappropriately placed changes or may miss some changes. Please correct the contents, and save the document to " +
            "resume synchronization between the editor and the Apache NetBeans LSP server."
    })
    void markSyncBroken() {
        if (syncBroken) {
            return;
        }
        clearPendingChanges();
        syncBroken = true;
        NotifyDescriptor.Message desc = new NotifyDescriptor.Message(Bundle.ERR_SynchronizationBroken(file), NotifyDescriptor.Message.ERROR_MESSAGE);
        DialogDisplayer.getDefault().notifyLater(desc);
    }
    
    void saveDocumentLocally() throws IOException {
        Boolean r = nestedRedirect.get();
        try {
            nestedRedirect.set(true);
            LOG.log(Level.FINE, "{0}: Saving locally bypassing LSP", file);
            original.saveDocument();
        } finally {
            if (r == null) {
                nestedRedirect.remove();
            } else {
                nestedRedirect.set(r);
            }
        }
    }

    @Override
    public void saveDocument() throws IOException {
        LspServerState server = Lookup.getDefault().lookup(LspServerState.class);
        if (server == null) {
            saveDocumentLocally();
            return;
        }
        IOException[] fail = new IOException[1];
        // wrapped in Runnable to allow easy fork to RequestProcessor, so that server receive thread is not blocked.
        Runnable r = () -> {
            AtomicReference<CompletableFuture<Void>> f = new AtomicReference<>();
            // ensure that flushConverter happens in the RP thread. The 'save' event must be scheduled into outgoing queue
            // before any potential already scheduled/delayed flush in the RP.
            RP.post(() -> {
                // flush must be postponed as much as possible, so Positions track document insertions.
                PendingEdit nblsEdits = flushConverter(true, server);
                if (nblsEdits == null) {
                    nblsEdits = new PendingEdit(server, new ContentSnapshot(""), Collections.emptyList(), Collections.emptyMap(), true);
                }
                DocumentSyncSupport.LOG.log(Level.FINE, "{0}: Capturing saveDocument() with edits {1}", new Object[]{file, nblsEdits});

                f.set(schedulePendingEvent(nblsEdits));
            }).waitFinished();
            // but wait on the client in the generic RP or the caller's thread:
            try {
                f.get().get();
            } catch (InterruptedException | ExecutionException ex) {
                fail[0] = new IOException(ex);
            }
        };
        
        // avoid save() blocking in the client thread; that would block reception of responses.
        if (LspServerUtils.isClientResponseThread(null)) {
            LOG.log(Level.INFO, "{0}: saveDocument() called in LSP request thread.", file);
            LOG.log(Level.INFO, "Stacktrace:", new Throwable());
            RequestProcessor.getDefault().post(r, CHANGE_SAVE_DELAY);
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
            StyledDocument sd = original.openDocument();
            synchronized (this) {
                Object c = sd.getProperty(LspChangesConverter.class);
                // Use the converter instance as an "init done" flag.s
                if (converter != null && c == converter) {
                    return sd;
                }
                
                sd.putProperty(LspChangesConverter.class, converter);
                if (!DocumentSyncSupport.isClientChange()) {
                    // this request did not originate from the LSP protocol messages.
                    openedAtServer = true;
                }
                DocumentSyncSupport.LOG.log(Level.FINE, "Opening document: {0}", file);
                registry.getAttachedServers(file);
                converter = new LspChangesConverter(original, sd, this::scheduleChangeSync);
                
                AtomicLockDocument ald = LineDocumentUtils.asRequired(sd, AtomicLockDocument.class);
                ald.addAtomicLockListener(converter);
                sd.addDocumentListener(converter);
                
                AbstractDocument ad = (AbstractDocument)sd;
                DocumentFilter existing = ad.getDocumentFilter();
                ad.setDocumentFilter(new DocumentChangesBlocker(existing));
                documentRef = new WeakReference<>(ad);
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

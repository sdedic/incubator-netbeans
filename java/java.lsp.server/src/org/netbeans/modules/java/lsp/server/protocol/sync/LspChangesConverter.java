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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.document.AtomicLockEvent;
import org.netbeans.api.editor.document.AtomicLockListener;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;
import static org.netbeans.modules.java.lsp.server.protocol.sync.DocumentSyncSupport.LOG;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Lookup;

/**
 * An extension to DocumentChangesConverter. Makes snapshot of document's content upon 1st modification.
 * Collects clients that are responsible for recorded edits - this is for case that the client did not open the document
 * but executed an action that opened the document server-side.
 * 
 * @author sdedic
 */
public class LspChangesConverter extends DocumentChangesConverter implements AtomicLockListener {
    private final CloneableEditorSupport editSupport;
    private final Consumer<Document> changeConsumer;
    private final Set<LspServerState> clients = new HashSet<>();
    /**
     * Disables processing the changes. The convertor may be replaced 
     */
    private volatile boolean disable;
    private ContentSnapshot contentCopy;

    public LspChangesConverter(CloneableEditorSupport editSupport, Document document, Consumer<Document> changeConsumer) {
        super(document);
        this.editSupport = editSupport;
        this.changeConsumer = changeConsumer;
    }
    
    public void disable() {
        disable = true;
    }

    public ContentSnapshot getContentCopy() {
        return contentCopy;
    }

    public DocumentChangesConverter useInitialEdits(ContentSnapshot base, List<TextEdit> initialEdits, Map<TextEdit, String> removedText) {
        this.contentCopy = base;
        return super.useInitialEdits(initialEdits, removedText);
    }
    
    public Set<LspServerState> getClients() {
        // OK, called after the converter detaches from events.
        return clients;
    }

    @Override
    protected boolean isEnabled() {
        return !disable && DocumentSyncSupport.currentClient.get() == null;
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        if (isEnabled()) {
            if (DocumentSyncSupport.isFromOpenClose()) {
                return;
            }
            registerOriginatingClient();
            super.removeUpdate(e);
            notifyChanged(false);
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        if (isEnabled()) {
            if (DocumentSyncSupport.isFromOpenClose()) {
                return;
            }
            registerOriginatingClient();
            super.insertUpdate(e);
            notifyChanged(false);
        }
    }

    synchronized void registerOriginatingClient() {
        LspServerState state = Lookup.getDefault().lookup(LspServerState.class);
        if (state != null) {
            clients.add(state);
        } else {
            Document d = editSupport.getDocument();
            LOG.log(Level.INFO, "Something is editing document {0} with no LSP client identity", d);
            LOG.log(Level.INFO, "Stacktrace:", new Throwable());
        }
    }

    @Override
    protected void notifyChanged(boolean firstChange) {
        Document d = getDocument();
        if (!disable && d != null) {
            changeConsumer.accept(d);
        }
    }

    @Override
    public void atomicLock(AtomicLockEvent evt) {
        Document d = getDocument();
        if (isEnabled() && contentCopy == null && d != null) {
            try {
                LOG.log(Level.INFO, "Created a copy of document {0}", d);
                contentCopy = new ContentSnapshot(d.getText(0, d.getLength()));
            } catch (BadLocationException ex) {
                // should never happen, the document was already atomic-locked before entering the method.eibccbdvrbeijlfgrlvgtkcjgtlujglrvccdivkivlft
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void atomicUnlock(AtomicLockEvent evt) {
    }
    
}

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
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.editor.document.AtomicLockEvent;
import org.netbeans.api.editor.document.AtomicLockListener;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Lookup;

/**
 *
 * @author sdedic
 */
public class LspChangesConverter extends DocumentChangesConverter implements AtomicLockListener {
    
    private final CloneableEditorSupport editSupport;
    private final Document document;
    private final Consumer<Document> changeConsumer;
    private final Set<LspServerState> clients = new HashSet<>();
    boolean disable;
    String contentCopy;

    public LspChangesConverter(CloneableEditorSupport editSupport, Document document, Consumer<Document> changeConsumer) {
        super(document);
        this.editSupport = editSupport;
        this.document = document;
        this.changeConsumer = changeConsumer;
    }

    public String getContentCopy() {
        return contentCopy;
    }

    public Set<LspServerState> getClients() {
        return clients;
    }

    @Override
    protected boolean isEnabled() {
        return !disable && DocumentSyncSupport.currentClient.get() == null;
    }

    @Override
    protected javax.swing.text.Position createPosition(int offset, javax.swing.text.Position.Bias bias) throws BadLocationException {
        return editSupport.createPositionRef(offset, bias);
    }
    
    public void adjustEdits(List<TextDocumentContentChangeEvent> events) {
        class Mod {
            int offset = 0;
            int eoffset = 0;
            int evenIndex = 0;
            TextDocumentContentChangeEvent current;

            public Mod() {
                current = events.get(0);
            }
            
            public Integer computeOffset(TextEdit edit) {
                /*
                int so = current.getStartOffset() + offset;
                int eo = current.getEndOffset() + offset;
                int tl = current.getNewText().length();
                int del = eo - so;
                
                int eso = edit.getStartOffset();
                int eeo = edit.getEndOffset();
                int etl = edit.getNewText() == null ? 0 : edit.getNewText().length();
                int edel = eeo - eso;
                
                */
                return null;
            }
        }
        
        if (events.isEmpty()) {
            return;
        }
        //DocumentChangesConverter.adjustEdits();
    }

    protected static boolean isFromOpenClose() {
        StackTraceElement[] els = Thread.currentThread().getStackTrace();
        for (int i = els.length - 1; i >= Math.max(0, els.length - 6); i--) {
            StackTraceElement e = els[i];
            if (e.getClassName().contains("org.openide.text.DocumentOpenClose")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        if (isEnabled()) {
            if (isFromOpenClose()) {
                return;
            }
            registerOriginatingClient();
            super.removeUpdate(e);
            notifyChanged(false);
        } else {
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        if (isEnabled()) {
            if (isFromOpenClose()) {
                return;
            }
            registerOriginatingClient();
            super.insertUpdate(e);
            notifyChanged(false);
        } else {
        }
    }

    void registerOriginatingClient() {
        LspServerState state = Lookup.getDefault().lookup(LspServerState.class);
        if (state != null) {
            clients.add(state);
        }
    }

    @Override
    protected void notifyChanged(boolean firstChange) {
        if (!disable) {
            changeConsumer.accept(document);
        }
    }

    @Override
    public void atomicLock(AtomicLockEvent evt) {
        if (isEnabled() && contentCopy == null && document != null) {
            try {
                contentCopy = document.getText(0, document.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void atomicUnlock(AtomicLockEvent evt) {
    }
    
}

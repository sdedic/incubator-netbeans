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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.Position;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;

/**
 * Records a single pending WorkspaceEdit operation fired to clients. The change applied to a single Document
 */
class PendingEdit {

    /**
     * Initial document content, on which the TextEdits should be applied by the client.
     */
    final ContentSnapshot initialContent;
    
    /**
     * Time of creation
     */
    private final long timestamp;
    
    /**
     * Individual document edits.
     */
    final List<TextEdit> edits;
    
    /**
     * Recipient of the save edit, or applyEdit of unopened document.
     */
    private final LspServerState target;
    
    /**
     * Clients that have provoked changes in this edit.
     */
    private Set<LspServerState> activeClients;
    
    /**
     * Scheduled recipients that did not responded to the edit yet.
     */
    private final List<LspServerState> pendingRecipients = new ArrayList<>();
    
    /**
     * Text removed by specific TextEdit operations.
     */
    final Map<TextEdit, String> removedContent;

    /**
     * True, if the document should be saved after applying edits
     */
    final boolean saveAfterEdit;
    
    /**
     * True, if only opened at server.
     */
    boolean serverOpen;
    
    private final CompletableFuture<Void> completed = new CompletableFuture<>();

    /**
     * Description of pending edit sent to the client
     * @param target the single target client. Use null if postponed save.
     * @param initialContent initial document content 
     * @param edits collected edits, maybe empty
     * @param removedContent content removed by the edits
     * @param saveAfterEdit flag to save after the edits are processed.
     */
    public PendingEdit(LspServerState target, ContentSnapshot initialContent, List<TextEdit> edits, Map<TextEdit, String> removedContent, boolean saveAfterEdit) {
        this(target, Collections.emptySet(), initialContent, edits, removedContent, saveAfterEdit);
    }

    public PendingEdit(LspServerState target, ContentSnapshot initialContent, List<TextEdit> edits, Map<TextEdit, String> removedContent, boolean saveAfterEdit, Set<LspServerState> clients) {
        this(target, clients, initialContent, edits, removedContent, saveAfterEdit);
    }

    private PendingEdit(LspServerState target, Set<LspServerState> clients, ContentSnapshot initialContent, List<TextEdit> edits, Map<TextEdit, String> removedContent, boolean saveAfterEdit) {
        this.activeClients = clients == null ? Collections.emptySet() : clients;
        this.saveAfterEdit = saveAfterEdit;
        this.initialContent = initialContent;
        this.edits = edits;
        this.removedContent = removedContent;
        this.timestamp = System.currentTimeMillis();
        this.target = target;
    }

    public boolean isServerOpen() {
        return serverOpen;
    }

    public void setServerOpen(boolean serverOpen) {
        this.serverOpen = serverOpen;
    }

    public Set<LspServerState> getActiveClients() {
        return activeClients;
    }
    
    public CompletableFuture<Void> getCompletion() {
        return completed;
    }
    
    public LspServerState getTarget() {
        return target;
    }
    
    public boolean contains(LspServerState srv) {
        return pendingRecipients.contains(srv) || target == srv;
    }
    
    public void setRecipients(List<LspServerState> recipients) {
        this.pendingRecipients.addAll(recipients);
    }

    public Collection<LspServerState> getPendingRecipients() {
        return pendingRecipients;
    }
    
    public boolean isFinished() {
        return pendingRecipients.isEmpty();
    }
    
    public boolean finishedAtClient(LspServerState st) {
        return pendingRecipients.remove(st);
    }

    private int position2Offset(Position pos) {
        return initialContent.position2Offset(pos);
    }
    
    public String getId() {
        return "Edit@" + Integer.toHexString(System.identityHashCode(this));
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Edit@");
        sb.append(Integer.toHexString(System.identityHashCode(this))).append("[edits=");
        boolean first = true;
        for (TextEdit te : edits) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("[").append(te.getStartOffset()).append("-").append(te.getEndOffset()).append("]");
            if (te.getNewText() != null && !te.getNewText().isEmpty()) {
                sb.append("+").append(te.getNewText());
            }
        }
        sb.append(edits.toString());
        sb.append(", save: ").append(saveAfterEdit);
        if (saveAfterEdit) {
            sb.append(", toClient: ").append(target);
        }
        sb.append(", serverOnly: ").append(serverOpen);
        sb.append("]");
        return sb.toString();
    }
}

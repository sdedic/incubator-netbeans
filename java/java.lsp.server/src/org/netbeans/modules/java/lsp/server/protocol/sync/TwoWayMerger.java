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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.BadLocationException;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.lsp.TextEdit;

/**
 * This Merger takes TextDocumentContentChangeEvent and TextEdit streams as an input.
 * Since the TextDocumentContentChangeEvent happened on the client (the ultimate source of the truth),
 * the Merger assumes they happened first, and merges them with local TextEdit. If the edits
 * overlap, the Merger raises a BadLocationException to indicate an irrecoverable clash has occurred.
 * <p>
 * Otherwise, it modifies the ContentSnapshot's contents to contain all TextDocumentContentChangeEvent changes
 * modified to match the <b>local document state</b>, which contains the changes described by TextEdits. This
 * ContentSnapshot will serve as a new base of {@link PendingEdit} or {@link DocumentChangesConverter} accumulating
 * further changes.
 * <p>
 * The TextEdits will be also updated, offset by changes made by TextDocumentContentChangeEvents on the client, which are
 * about to be incorporated into the document. The updated TextEdits will be sent to the client, which already has the changes
 * reported by TextDocumentContentChangeEvents.
 * <p>
 * Sanity checks are implemented, which can be used for diagnosis / early error detection, and should be
 * removed from the mainline processing after stabilization. The following should be exactly the same:
 * <ul>
 * <li> the {@link #updatedSnapshot} + updated TextEdit stream 
 * <li> the {@link #snapshot} + updated TextDocumentContentChangeEvents stream
 * </ul>
 * 
 * @author sdedic
 */
public class TwoWayMerger {
    /**
     * The initial snapshot, before local changes started.
     */
    private final ContentSnapshot snapshot;
    
    /**
     * The contents of the current local document, including local changes.
     */
    private final ContentSnapshot localDocument;
    
    /**
     * Local edits, made from snapshot to liveDocument.
     */
    private final List<TextEdit>  localEdits;
    
    /**
     * Client edits, in form of events.
     */
    private List<TextDocumentContentChangeEvent> events;
    
    /**
     * Client edits, which should be incorporated.
     */
    private final List<TextEdit>  clientEdits = new ArrayList<>();
    
    /**
     * Adjusted client edits, applicable to liveDocument
     */
    private final List<TextEdit>  updatedClient = new ArrayList<>();
    
    /**
     * Adjusted local edits, applicable to updatedContent.
     */
    private final List<TextEdit>  updatedLocal = new ArrayList<>();
    
    /**
     * clientEdits converted into events.
     */
    private final List<TextDocumentContentChangeEvent> updatedEvents = new ArrayList<>();
    
    /**
     * Content of snapshot, being updated with client edits
     */
    private final StringBuilder   content;
    
    /**
     * The updated snapshot that contain client edits, but not local changes.
     */
    private ContentSnapshot updatedSnapshot;
    
    private final Map<TextEdit, String> removedText;
    
    private final Map<TextEdit, String> updatedRemovedText = new HashMap<>();

    /**
     * Creates a merger that processes baseline `snapshot` and the current local document contents
     * @param snapshot
     * @param localDocument
     * @param localEdits
     * @param removedText 
     */
    public TwoWayMerger(ContentSnapshot snapshot, ContentSnapshot localDocument, List<TextEdit> localEdits, Map<TextEdit, String> removedText) {
        this.localDocument = localDocument;
        this.localEdits = localEdits;
        this.snapshot = snapshot;
        this.content = new StringBuilder(snapshot.getText());
        this.removedText = removedText;
    }
    
    /**
     * Includes client edits from events. Internally converts them to TextEdits.
     * @param events client events
     * @return this instance
     */
    public TwoWayMerger withClientEvents(List<TextDocumentContentChangeEvent> events) {
        this.events = events;
        events2Edits();
        return this;
    }
    
    /**
     * Includes TextEdits representing client changes.
     * @param edits the edits to include
     * @return this instance
     */
    public TwoWayMerger addClientEdits(List<TextEdit> edits) {
        this.clientEdits.addAll(edits);
        return this;
    }
    
    private void events2Edits() {
        for (TextDocumentContentChangeEvent ev : events) {
            Position s = ev.getRange().getStart();
            Position e = ev.getRange().getEnd();
            int so = snapshot.position2Offset(s);
            int eo = snapshot.position2Offset(e);
            clientEdits.add(new TextEdit(so, eo, ev.getText()));
        }
    }
    
    private void textEdits2Events() {
        for (TextEdit edit : updatedClient) {
            Position start = localDocument.offset2Position(edit.getStartOffset());
            Position end = edit.getEndOffset() > edit.getStartOffset() ? localDocument.offset2Position(edit.getEndOffset()) : start;
            updatedEvents.add(new TextDocumentContentChangeEvent(new Range(start, end), edit.getNewText()));
        }
    }
    
    /**
     * Index into converted events - client edits.
     */
    private int clientIndex = 0;
    
    /**
     * Index into localEdits
     */
    private int localIndex = 0;
    
    /**
     * The offset to add for client positions, adjusts with each edit
     * merged in.
     */
    private int clientOffset = 0;
    
    /**
     * The offset to add for local positions, adjusts with client
     * edits incorporated into 'contents'.
     */
    private int localOffset = 0;
    
    /**
     * The current client edit or `null` if exhausted
     */
    private TextEdit clientEdit;

    /**
     * The current local edit or `null` if exhausted
     */
    private TextEdit localEdit;
    
    private void nextClient() {
        if (clientEdit == null) {
            return;
        }
        clientIndex++;
        if (clientIndex >= clientEdits.size()) {
            clientEdit = null;
        } else {
            clientEdit = clientEdits.get(clientIndex);
        }
    }
    
    private void nextLocal() {
        if (localEdit == null) {
            return;
        }
        localIndex++;
        if (localIndex >= localEdits.size()) {
            localEdit = null;
        } else {
            localEdit = localEdits.get(localIndex);
        }
    }
    
    private int textLen(TextEdit t) {
        return t.getNewText() == null ? 0 : t.getNewText().length();
    }
    
    private int editDiff(TextEdit t) {
        return textLen(t) - (t.getEndOffset() - t.getStartOffset());
    }
    
    /**
     * Integrates a local edit in the result. Assumes the 'content' that started from the current local document 
     * already contains local edits, so it only adjusts positions of local TextEdit and adjusts offset for
     * next client edits.
     * @param local local edit to integrate
     */
    private void addLocal(TextEdit local) {
        TextEdit t;
        if (localOffset == 0) {
            t = local;
        } else {
            t = new TextEdit(local.getStartOffset() + localOffset, local.getEndOffset() + localOffset, local.getNewText());
        }
        updatedLocal.add(t);
        String rt = removedText.get(local);
        if (rt != null) {
            updatedRemovedText.put(t, rt);
        }
        clientOffset += editDiff(local);
        nextLocal();
    }
    
    /**
     * Integrates a client edit in the result. Since the 'content' does not contain client changes, 
     * it merges the edit into 'content'.
     * 
     * @param local client edit to integrate
     */
    private void addClient(TextEdit client) {
        TextEdit t;
        
        if (localOffset == 0) {
            updatedClient.add(client);
            t = client;
        } else {
             t = new TextEdit(client.getStartOffset() + clientOffset, client.getEndOffset() + clientOffset, client.getNewText());
            updatedClient.add(t);
        }
        content.replace(client.getStartOffset() + localOffset, client.getEndOffset() + localOffset, client.getNewText() == null ? "" : client.getNewText());
        localOffset += editDiff(client);
        nextClient();
    }

    public void merge() throws BadLocationException {
        List<TextEdit> ordered = new ArrayList<>(clientEdits);
        clientEdits.sort(DocumentChangesConverter.textEditComparator(ordered));
        
        if (clientEdits.isEmpty()) {
            clientEdit = null;
        } else {
            clientEdit = clientEdits.get(0);
        }
        if (localEdits.isEmpty()) {
            localEdit = null;
        } else {
            localEdit = localEdits.get(0);
        }
        
        while (true) {
            if (localEdit == null && clientEdit == null) {
                break;
            }
            if (clientEdit == null) {
                // no more client edits
                addLocal(localEdit);
                continue;
            }
            if (localEdit == null) {
                addClient(clientEdit);
                continue;
            }
            int cs = clientEdit.getStartOffset() + clientOffset;
            int ce = clientEdit.getEndOffset() + clientOffset;
            int ci = textLen(clientEdit);
            int cd = ce - cs;

            int ls = localEdit.getStartOffset();
            int le = localEdit.getEndOffset();
            int li = textLen(localEdit);
            int ld = le - ls;

            // if the local edit is fully before the client edit, skip it:
            if (le == cs) {
                // possible overlap: the client wrote immediately after our local edit perhaps.
                throw new BadLocationException(null, le);
            }
            if (le < cs) {
                addLocal(localEdit);
                continue;
            }

            if (cd > 0) {
                if (ce >= ls && ce < le + li && cs <= ls) {
                    // the client edit overlaps and removes surroundings or part of the client edit
                    throw new BadLocationException(null, cs);
                }
            } else if (ce == ls) {
                // the client has inserted right at/before the position the local edit happened. Probably destroys context.
                throw new BadLocationException(null, cs);
            }
            
            if (ld > 0) {
                if (le >= cs && le < ce + ci && ls <= cs) {
                    // the local edit damaged the client change - apparently edited something that was not locally seen at the
                    // time the document was modified. BAD.
                    throw new BadLocationException(null, le);
                }
            } else if (le == cs) {
                throw new BadLocationException(null, le);
            }
            
            if (ls > ce) {
                addClient(clientEdit);
            } else {
                throw new IllegalStateException();
            }
        }
        updatedSnapshot = new ContentSnapshot(content.toString());
        textEdits2Events();
    }

    public List<TextEdit> getUpdatedClientEdits() {
        return updatedClient;
    }

    public List<TextDocumentContentChangeEvent> getUpdatedClientEvents() {
        return updatedEvents;
    }

    public Map<TextEdit, String> getUpdatedRemovedText() {
        return updatedRemovedText;
    }

    public List<TextEdit> getUpdatedLocalEdits() {
        return updatedLocal;
    }
    
    public String getContent() {
        return content.toString();
    }
    
    public ContentSnapshot getSnapshot() {
        return snapshot;
    }

    public ContentSnapshot getUpdatedSnapshot() {
        return updatedSnapshot;
    }
    
    /**
     * Check that the both baselines + the text edits from the other side will yield the
     * same text content.
     */
    public void checkConsistency() {
        StringBuilder sbClient = new StringBuilder(updatedSnapshot.getText());
        for (int i = updatedLocal.size() - 1; i >= 0; i--) {
            TextEdit edit = updatedLocal.get(i);
            sbClient.replace(edit.getStartOffset(), edit.getEndOffset(), edit.getNewText());
        }
        
        StringBuilder sbLocal = new StringBuilder(localDocument.getText());
        for (int i = updatedClient.size() - 1; i >= 0; i--) {
            TextEdit edit = updatedClient.get(i);
            sbLocal.replace(edit.getStartOffset(), edit.getEndOffset(), edit.getNewText());
        }
        
        if (!sbLocal.toString().equals(sbClient.toString())) {
            throw new IllegalStateException();
        }
    }
}

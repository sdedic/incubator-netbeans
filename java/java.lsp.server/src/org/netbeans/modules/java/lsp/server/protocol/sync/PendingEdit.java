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
import java.util.Map;
import java.util.Set;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.modules.java.lsp.server.LspServerState;

/**
 * Records a single pending WorkspaceEdit operation fired to clients. The change applied to a single Document
 */
class PendingEdit {

    /**
     * Initial document content, on which the TextEdits should be applied by the client.
     */
    String initialContent;
    /**
     * Time of creation
     */
    private final long timestamp;
    /**
     * Individual document edits.
     */
    final List<TextEdit> edits;
    private final Set<LspServerState> recipients;
    /**
     * Text removed by specific TextEdit operations.
     */
    final Map<TextEdit, String> removedContent;
    private int[] lineOffsets;
    private boolean saveAfterEdit;

    public PendingEdit(Set<LspServerState> recipients, String initialContent, List<TextEdit> edits, Map<TextEdit, String> removedContent, boolean saveAfterEdit) {
        this.saveAfterEdit = saveAfterEdit;
        this.initialContent = initialContent;
        this.edits = edits;
        this.removedContent = removedContent;
        this.timestamp = System.currentTimeMillis();
        this.recipients = recipients;
        computeLineOffsets();
    }

    public Set<LspServerState> getRecipients() {
        return recipients;
    }

    private void computeLineOffsets() {
        List<Integer> offs = new ArrayList<>();
        offs.add(0);
        for (int n = 0; n < initialContent.length(); n++) {
            char c = initialContent.charAt(n);
            if (c == '\n') {
                offs.add(n + 1);
            }
        }
        lineOffsets = offs.stream().mapToInt(Integer::intValue).toArray();
    }

    private int position2Offset(Position pos) {
        int r = pos.getLine();
        if (r >= lineOffsets.length) {
            return initialContent.length();
        }
        return lineOffsets[r] + pos.getCharacter();
    }

    public boolean overlaps(TextDocumentContentChangeEvent ev) {
        int so = position2Offset(ev.getRange().getStart());
        int eo = position2Offset(ev.getRange().getStart());
        for (TextEdit te : edits) {
            int e = te.getEndOffset();
            if (te.getNewText() != null) {
                e += te.getNewText().length();
            }
            if (so < e && eo >= te.getStartOffset()) {
                return true;
            }
        }
        return false;
    }

    private boolean assertNonOverlapping(List<TextDocumentContentChangeEvent> events) {
        for (TextDocumentContentChangeEvent e : events) {
            if (overlaps(e)) {
                return false;
            }
        }
        return true;
    }

    public void apply(List<TextDocumentContentChangeEvent> events) {
        assert assertNonOverlapping(events) : "The events must be checked for overlap first !";
        int editIndex = 0;
        int offset = 0;
        StringBuilder sb = new StringBuilder(initialContent);
        TextEdit edit = edits.get(editIndex);
        for (int i = events.size() - 1; i >= 0; i--) {
            TextDocumentContentChangeEvent event = events.get(i);
            int s = position2Offset(event.getRange().getStart());
            int e = position2Offset(event.getRange().getEnd());
            int l = e - s;
            int diff = e - s + event.getText().length();
            offset += diff;
            while (edit != null && edit.getEndOffset() < s) {
                adjustTextEdit(editIndex, edit, offset);
                editIndex++;
                if (editIndex >= edits.size()) {
                    edit = null;
                } else {
                    edit = edits.get(editIndex);
                }
            }
            if (edit.getStartOffset() >= s && edit.getEndOffset() < e) {
                throw new IllegalStateException("Events should not overlap with edits, should be checked before");
            }
            sb.replace(s, e, event.getText());
        }
        adjustTextEdit(editIndex, edit, offset);
        this.initialContent = sb.toString();
    }

    private void adjustTextEdit(int editIndex, TextEdit edit, int offset) {
        if (edit == null || offset == 0 || editIndex >= edits.size()) {
            return;
        }
        TextEdit newEdit = new TextEdit(edit.getStartOffset() + offset, edit.getEndOffset() + offset, edit.getNewText());
        edits.set(editIndex, newEdit);
        removedContent.put(newEdit, removedContent.remove(edit));
    }
    
}

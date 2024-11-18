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
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.api.lsp.Range;
import org.netbeans.api.lsp.TextEdit;

/**
 * Matches series of TextEdits against a particular document state against incoming 
 * TextDocumentContentChangeEvents. Decides if the list of events exactly matches the set of edits.
 * 
 * @author sdedic
 */
class WorkspaceEditMatcher {
    
    private final List<TextEdit> edits;
    private final List<TextDocumentContentChangeEvent> events;
    private final Map<TextEdit, String> removedContent;
    private final String textContent;
    
    private int[] lineOffsets;
    private List<TextEdit> orderedEdits;
    private int editIndex;
    private int eventIndex;
    private Map<TextDocumentContentChangeEvent, Range> ranges;

    public WorkspaceEditMatcher(String textContent, List<TextEdit> edits, Map<TextEdit, String> removedContent, List<TextDocumentContentChangeEvent> events) {
        this.removedContent = removedContent;
        this.textContent = textContent;
        this.edits = edits;
        this.events = events;
        // XXX FIXME -- line offsets are already computed!
        computeLineOffsets();
    }
    
    private void computeLineOffsets() {
        List<Integer> offs = new ArrayList<>();
        offs.add(0);
        for (int n = 0; n < textContent.length(); n++) {
            char c = textContent.charAt(n);
            if (c == '\n') {
                offs.add(n + 1);
            }
        }
        lineOffsets = offs.stream().mapToInt(Integer::intValue).toArray();
        ranges = new HashMap<>();
        for (TextDocumentContentChangeEvent e : events) {
            int so = position2Offset(e.getRange().getStart());
            int eo = position2Offset(e.getRange().getEnd());
            ranges.put(e, new Range(so, eo));
        }
    }
    
    private int position2Offset(Position pos) {
        int r = pos.getLine();
        if (r >= lineOffsets.length) {
            return textContent.length();
        }
        return lineOffsets[r] + pos.getCharacter();
    }
    
    private boolean overlaps(TextDocumentContentChangeEvent event, TextEdit edit) {
        int so = position2Offset(event.getRange().getStart());
        int eo = position2Offset(event.getRange().getEnd());
        return so < edit.getEndOffset() && eo >= edit.getStartOffset();
    }
    
    List<TextEdit> reshapedEdits = Collections.emptyList();
    
    private String applyChange(String content, TextEdit edit, int posDiff) {
        String t = edit.getNewText();
        if (t == null) {
            t = "";
        }
        return content.substring(0, edit.getStartOffset()) + t + content.substring(edit.getEndOffset());
    }

    private String applyChange(String content, TextDocumentContentChangeEvent edit) {
        Range r = ranges.get(edit);
        String t = edit.getText();
        if (t == null) {
            t = "";
        }
        return content.substring(0, r.getStartOffset()) + t + content.substring(r.getEndOffset());
    }
    
    private boolean checkFromStart0(TextDocumentContentChangeEvent e) {
        reshapedEdits = new ArrayList<>(edits);
        
        int eventSo = position2Offset(e.getRange().getStart());
        int eventEo = position2Offset(e.getRange().getEnd());
        int eventL = eventEo - eventSo;
        
        int eIndex = 0;
        while (true) {
            TextEdit edit = edits.get(eIndex);
            String removedText = removedContent.get(edit);
            String newText = edit.getNewText();

            int editSo = edit.getStartOffset();
            int editEo = edit.getEndOffset();
            int editL = edit.getEndOffset() - edit.getStartOffset();

            if (eventL > 0) {
                int offs = eventSo - editSo;
                if (offs > 0) {
                    // initial part of the edit is not deleted, but that part may be in fact placed back by this edit's insert
                    
                    /*              <-------> editL
                                    v editSo
                        edit:       |--------XXXXXX|  
                        event:           |-----YYYY|
                                         ^ eventSo
                                    <----> offs
                                    <----> mx, shorter/equal XXXXX
                    */
                    int delL = Math.min(editL, offs);
                    String delT = removedText.substring(0, delL);
                    int mx = Math.min(newText.length(), delT.length());
                    if (mx > 0) {
                        if (!newText.substring(mx).equals(delT.substring(mx))) {
                            return false;
                        }
                        // mx characters are the same - adjust the start offset by the characters which are deleted and then replaced back 
                        TextEdit ne = new TextEdit(editSo + mx, editEo, newText.substring(mx));
                        eventL -= mx;
                        eventSo += mx;
                        reshapedEdits.set(eIndex, ne);
                        if (mx > removedText.length()) {
                            removedContent.put(ne, removedText.substring(mx));
                        }
                        // do not move eIndex, re-process again
                        continue;
                    }
                    if (delL > 0) {
                        /*              
                            newText is exhausted:
                                        <--------> editL
                                        v editSo
                            edit:       |--------|  |----XXXXX|
                            event:           |-----YYYY|
                                             ^ eventSo
                                        <----> offs
                                        <----> mx, shorter/equal XXXXX
                        */

                        /*              
                            editL is exhausted:
                                        <-------> editL
                                        v editSo
                            edit:       |----XXXX| |----zzzzz|
                            event:           |-----YYYY|
                                             ^ eventSo
                                        <----> offs
                                        <----> mx, shorter/equal XXXXX
                        */

                        /*              
                            offs is exhausted:
                                        <-------> editL
                                        v editSo
                            edit:       |----XXXX| |----zzzzz|
                            event:      |----YYYY|
                            ** never reaches here.
                        */
                        // reach to the subsequent edits
                        if (!newText.isEmpty()) {
                            // not all inserted text is consumed
                            return false;
                        }
                    }
                }
                if  (editL >= eventL) {
                    editSo += eventL;
                    removedText = removedText.substring(eventL);
                } else {
                    // the delete spans to the next edit. Check if this edit 
                }
            }
        }
    }
    
    public boolean matches() {
        orderedEdits = new ArrayList<>(edits);
        Collections.sort(orderedEdits, DocumentChangesConverter.textEditComparator(edits));
        String myChange = this.textContent;
        for (int i = orderedEdits.size() - 1; i >= 0; i--) {
            TextEdit edit = orderedEdits.get(i);
            myChange = applyChange(myChange, edit, 0);
        }
        
        String eventChange = this.textContent;
        for (int i = 0; i < events.size(); i++) {
            eventChange = applyChange(eventChange, events.get(i));
        }
        
        return myChange.equals(eventChange);
    }

    public boolean matches0() {
        orderedEdits = new ArrayList<>(edits);
        Collections.sort(orderedEdits, DocumentChangesConverter.textEditComparator(edits));
        for (TextDocumentContentChangeEvent e : events) {
            int so = position2Offset(e.getRange().getStart());
            int eo = position2Offset(e.getRange().getEnd());
            
            if (edits.isEmpty()) {
                return false;
            }
            
            if (overlaps(e, edits.get(0))) {
            }
        }
        return false;
    }
    
}

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
package org.netbeans.modules.java.lsp.server.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.openide.util.Exceptions;

/**
 * Converts a series of edits of a Document to a series of TextEdits.
 * The class acts as a Listener on a document, watching changes. At the end,
 * it computes list of TextEdits from those changes. It supports even overlapping edits:
 * it merges the overlapping changes into a single TextEdit (or several non-overlapping TextEdits)
 * since LSP specification does not like overlaps.
 * <p>
 * The code is originally written as a DocumentListener, so it accepts document edits as they are generated
 * and captures them as TextEdits, but still uses the original offsets, which shift from the original
 * state as the document is modified.
 * <p>
 * It could be also used to normalize a sequence of overlapping TextEdits.
 * @author sdedic
 */
public class DocumentChangesConverter implements DocumentListener {
    private final Document document;
    private boolean adjustPositions;

    public DocumentChangesConverter(Document document) {
        this.document = document;
    }
    
    public Document getDocument() {
        return this.document;
    }
    
    /**
     * Edits captured from the document.
     */
    private List<TextEdit> recordedEdits = new ArrayList<>();
    
    /**
     * The text removed by individual edits. This Map is kept for reference
     * after condensing edits.
     */
    private Map<TextEdit, String> removedStrings = new HashMap<>();
    
    protected boolean isEnabled() {
        return true;
    }
    
    protected void notifyChanged() {}
    
    public Map<TextEdit, String> getRemovedParts() {
        return removedEditStrings;
    }
    
    @Override
    public void insertUpdate(DocumentEvent e) {
        if (!isEnabled()) {
            return;
        }
        try {
            String text = document.getText(e.getOffset(), e.getLength());
            TextEdit edit = new TextEdit(e.getOffset(), e.getOffset(), text);
            recordedEdits.add(edit);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        notifyChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        if (!isEnabled()) {
            return;
        }
        String removedText = (String)DocumentUtilities.getEventProperty(e, String.class);
        assert removedText != null : "Every BaseDocument should offer this functionality!";
        TextEdit edit = new TextEdit(e.getOffset(), e.getOffset() + e.getLength(), null);
        recordedEdits.add(edit);
        removedStrings.put(edit, removedText);
        notifyChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // no op
    }
    
    /**
     * Order of edits in the sequence they have been applied to the document.
     * When an edit is modified or coalesced (a new instance is created), the edit (or all coalesced edits) must be replaced by
     * that new instance.
     */
    private List<TextEdit> ordered = new ArrayList<>();
    
    // must process events in the edit queue. Must insert into 'ordered' first !
    private final TreeSet<TextEdit> condensed = new TreeSet<TextEdit>((TextEdit t1, TextEdit t2) -> {
        int d = t1.getStartOffset() - t2.getStartOffset();
        if (d != 0) {
            return d;
        }
        int n1 = ordered.indexOf(t1);
        int n2 = ordered.indexOf(t2);
        if (n1 == -1) {
            return 1;
        } else if (n2 == -1) {
            return 1;
        }
        return n1 - n2;
    });
    
    /**
     * Maps start offsets of condensed edits that remove text to the removed Strings.
     */
    private Map<TextEdit, String> removedEditStrings = new HashMap<>();
    
    /**
     * Replaces one or more edits in the collection that defines order with the new one.
     * The order will be computed as the position of first (in therms of the original order) 
     * of the edits, that inserted new text to the document; or any of the remove edits.
     * @param remove edits to remove
     * @param replaceWith the replacement.
     */
    void replaceOrderedEdits(Collection<TextEdit> remove, TextEdit replaceWith) {
        int pos = -1;
        
        for (TextEdit candidate : remove) {
            int p = ordered.indexOf(candidate);
            if (pos == -1) {
                pos = p;
            } else {
                if ((candidate.getNewText() != null) && (p < pos)) {
                    pos = p;
                }
            }
        }
        
        ordered.set(pos, replaceWith);
        ordered.removeAll(remove);
    }
    
    private static int textLen(TextEdit p) {
        return p.getNewText() != null ? p.getNewText().length() : 0;
    }
    
    /**
     * Turns the recorded edits into a series of non-overlapping TextEdits that
     * make the same modification to the document. The edits may be coalesced, though
     * not completely (some adjacent edits that may be further collapsed may be present).
     * 
     * @return series of non-overlapping edits.
     */
    public List<TextEdit> makeTextEdits() {
        ordered.clear();
        ordered.addAll(recordedEdits);
        removedEditStrings.clear();
        removedEditStrings.putAll(removedStrings);
        condensed.clear();
        
        T: for (int i = 0; i < recordedEdits.size(); i++) {
            TextEdit t = recordedEdits.get(i);
            
            int adjustedStart = t.getStartOffset();
            int adjustedEnd = t.getEndOffset();
            int l = t.getEndOffset() - t.getStartOffset();

            NavigableSet<TextEdit> priorEdits = condensed.headSet(t, false).descendingSet();
            
            for (Iterator<TextEdit> it = priorEdits.iterator(); it.hasNext(); ) {
                TextEdit p = it.next();
                int added = textLen(p);
                int editLength = added - (p.getEndOffset() - p.getStartOffset());
                if (p.getStartOffset() + added >= adjustedStart) {
                    // next edit partially overlaps with the preceding one.
                    mergePrecedingOverlap(t, p, adjustedStart, it);
                    continue T;
                }
                adjustedStart -= editLength;
                adjustedEnd = adjustedStart + l;
            }
            
            SortedSet<TextEdit> nextEdits = condensed.tailSet(t);
            
            if (!nextEdits.isEmpty()) {
                // toMerge will contain (already processed) edits that follow the current one, 
                // and the current one either deletes (at least) part of the next edit (or whole),
                // or the current edit's change is adjacent to the next edit.
                List<TextEdit> toMerge = new ArrayList<>();
                for (Iterator<TextEdit> it = nextEdits.iterator(); it.hasNext(); ) {
                    TextEdit n = it.next();
                      if (n.getStartOffset() <= adjustedStart + l) {
                        toMerge.add(n);
                        it.remove();
                    } else {
                        break;
                    }
                }
                
                if (!toMerge.isEmpty()) {
                    mergeOverlappingEdits(toMerge, t, adjustedStart, adjustedEnd);
                    continue T;
                }
            }
            if (adjustedStart != t.getStartOffset()) {
                TextEdit edit = new TextEdit(adjustedStart, adjustedEnd, t.getNewText());
                replaceOrderedEdits(Collections.singletonList(t), edit);
                adjustRemovedText(t, edit);
                condensed.add(edit);
            } else {
                // already is in the ordered list
                condensed.add(t);
            }
        }
        
        return new ArrayList<>(condensed);
    }
    
    private void adjustRemovedText(TextEdit old, TextEdit next) {
        if (old.getStartOffset() > next.getStartOffset()) {
            String s = removedEditStrings.remove(old);
            if (s != null && !s.isEmpty()) {
                assert s.length() == next.getEndOffset() - next.getStartOffset();
                removedEditStrings.put(next, s);
            }
        }
    }
    
    private String removeRemovedText(TextEdit t, String prevDeleted) {
        if (t.getEndOffset() > t.getStartOffset()) {
            String r = removedEditStrings.remove(t);
            assert r != null && r.length() == t.getEndOffset() - t.getStartOffset();
            if (prevDeleted == null) {
                return r;
            } else {
                return prevDeleted + r;
            }
        } else {
            return prevDeleted;
        }
    }
    
    private static boolean isNonEmpty(String s) {
        return s != null && !s.isEmpty();
    }
    
    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
    
    private void mergeRemovedTextFromFollowing(TextEdit result, TextEdit following, int overlap, String firstDeleted) {
        String r2 = removedEditStrings.remove(following);
        if (overlap > 0) {
            firstDeleted = firstDeleted.substring(0, firstDeleted.length() - overlap);
        }
        if (r2 != null) {
            firstDeleted = firstDeleted + r2;
        }
        assert firstDeleted.length() == result.getEndOffset() - result.getStartOffset();
        removedEditStrings.put(result, firstDeleted);
    }
    
    /**
     * Merges the new edit with edits that follow this one. Note that the only way how 
     * we could overlap with following edits is to delete part of text.
     * @param toMerge
     * @param newEdit 
     */
    private void mergeOverlappingEdits(List<TextEdit> toMerge, final TextEdit newEdit, int adjustedStart, int adjustedEnd) {
        TextEdit remainder = null;
        String deletedText = null;
        String pastInsertedContent = "";
        
        for (int i = 0; i < toMerge.size(); i++) {
            TextEdit t = toMerge.get(i);
            int l = textLen(t);
            int nl = textLen(newEdit);
            if (adjustedEnd >= t.getStartOffset() + l) {
                // if this is the 1st skipped edit, get the part of deleted text unique to 'newEdit'.
                if (deletedText == null) {
                    String del = removedEditStrings.get(newEdit);
                    assert del != null && del.length() > 0;
                    int prefixLen = t.getStartOffset() - adjustedStart;
                    deletedText = del.substring(0, prefixLen);
                    pastInsertedContent = del.substring(prefixLen);
                }
                // whole text edit was deleted by newEdit
                adjustedEnd += (t.getEndOffset() - t.getStartOffset());
                // compensate for the inserted text
                adjustedEnd -= l;
                // accumulate the deleted text: append the relevant part by now-obsoleted 't'.
                deletedText = removeRemovedText(t, deletedText);
                // skip this token's length
                pastInsertedContent = pastInsertedContent.substring(l);
            } else {
                if (adjustedEnd > t.getStartOffset()) {
                    // newEdit partially overlaps with  't': let's merge it.
                    // part of the t's text was deleted by newEdit, rest of text is appended to newEdit's text
                    int delPart = adjustedEnd - t.getStartOffset();
                    String nt = t.getNewText().substring(delPart);
                    if (nl > 0) {
                        // this is probably unreachable from plain document events, since we have remove (so no insert can be done)
                        nt = newEdit.getNewText() + t.getNewText().substring(delPart);
                    } else {
                        nt = t.getNewText().substring(delPart);
                    }
                    if (nt.isEmpty()) {
                        nt = null;
                    }
                    TextEdit edit = new TextEdit(adjustedStart, t.getEndOffset(), nt);
                    replaceOrderedEdits(toMerge, edit);
                    
                    if (deletedText == null) {
                        deletedText = removedEditStrings.remove(newEdit);
                    } else {
                        // there was already a token consumed, and deletedText now contains a full prefix
                        delPart = 0;
                    }
                    
                    // "delPart" characters deleted by 'newEdit' were inserted by the now-merged edit 't'
                    // the deleted part from newEdit ust be shortened, and relevant part deleted by "t" must be appended.
                    mergeRemovedTextFromFollowing(edit, t, delPart, deletedText);
                    condensed.add(edit);
                    return;
                } 
                if (adjustedEnd + nl == t.getStartOffset()) {
                    // t immediately follows newEdit, will be merged unless there are two deletes separeted by newEdit's text
                    if (nl == 0 || (t.getEndOffset() == t.getStartOffset() && nl > 0)) {
                        TextEdit edit = new TextEdit(adjustedStart, adjustedEnd, 
                            appendStringsOrNull(newEdit.getNewText(), t.getNewText())
                        );
                        
                        replaceOrderedEdits(toMerge, edit);
                        if (deletedText == null) {
                            deletedText = removedEditStrings.remove(newEdit);
                        }
                        mergeRemovedTextFromFollowing(edit, t, 0, deletedText);
                        condensed.add(edit);
                        return;
                    }
                }
                // cannot happen, most probably.
                remainder = t;
                break;
            }
        }
        
        if (deletedText == null) {
            deletedText = removedEditStrings.remove(newEdit);
            assert deletedText != null && !deletedText.isEmpty();
        }
        deletedText += pastInsertedContent;
        
        TextEdit edit = new TextEdit(adjustedStart, adjustedEnd, newEdit.getNewText());
        if (remainder != null) {
            // unchanged, is in the ordered list
            condensed.add(remainder);
            toMerge.remove(remainder);
        }
        replaceOrderedEdits(toMerge, edit);
        removedEditStrings.put(edit, deletedText);
        condensed.add(edit);
    }
    
    private static String appendStringsOrNull(String s1, String s2) {
        if (s1 != null) {
            return s2 == null ? s1 : s1 + s2;
        } else {
            return s2;
        }
    }

    /**
     * Merges a subsequent `newEdit` with preceding `conflicting` one. Note that 'newEdit'
     * is always a delete-only, or insert-only instruction as Swing Document reports only those.
     * 
     * @param newEdit
     * @param conflicting 
     */
    private void mergePrecedingOverlap(TextEdit newEdit, TextEdit conflicting, int adjustedStart, Iterator<TextEdit> it) {
        /*
            |---------XXXXXXXX|
                         cL
                      ^
                      conf.endOffset

                      __ <- off    
            |---------XXXXXXXX|
                        |-------YYY|
                          del    nL
                        ^
                      adjStart
        */
        // the overlapping edit just inserts, mold the insert into the previous one.
        int del = newEdit.getEndOffset() - newEdit.getStartOffset();
        String deletedByNew = removedEditStrings.remove(newEdit);
        String deletedByConflicting = removedEditStrings.remove(conflicting);
        String confText = conflicting.getNewText();
        String newText = newEdit.getNewText();
        int confLen = textLen(conflicting);
        int off = adjustedStart - conflicting.getStartOffset(); //conflicting.getEndOffset();
        
        assert del == 0 || deletedByNew.length() == del;
        
        /*
            |------XXXXXX|
                     |YYY|
                    |---YYY|
        
            Potential deletion by `newEdit` is fully contained in conflicting's appended text: only that text is affected.
        */
        // The delete must be fully contained, 
        // The delete can also immediately follow or begin inside and extend past end, but only if conflicting edit is not complex
        boolean deleteContained = conflicting.getStartOffset()+ confLen >= adjustedStart;
        if (deleteContained) {
            String deletedText = deletedByConflicting;
            
            String text;
            int delStart = conflicting.getStartOffset();
            int delEnd = conflicting.getEndOffset();
            if (off == confLen) {
                // just append
                // newText should not contain ANY deletion.
                delEnd += del;
                deletedText = appendStringsOrNull(deletedText, deletedByNew);
                text = appendStringsOrNull(confText, newText);
                // the deleted text is the same, as 'conflicting' came first.
            } else {
                if (newText != null) {
                    if (confText != null) {
                        text = confText.substring(0, off) + newText + confText.substring(off + del);
                    } else {
                        // should never happen, as confContext == null => confLen = 0 => off == confLenn
                        text = newText + confText.substring(Math.min(confLen, off + del));
                    }
                }  else {
                    text = confText.substring(0, off) + confText.substring(Math.min(confLen, off + del));
                }
                int overlap = Math.min(del, confLen - off);
                delEnd += del - overlap;
                if (deletedByNew != null) {
                    deletedText = appendStringsOrNull(deletedText, deletedByNew.substring(overlap));
                }
            }
            if (text != null && text.isEmpty()) {
                text = null;
            }
            it.remove();
            if (delStart != delEnd || text != null) {
                TextEdit edit = new TextEdit(delStart, delEnd, text);
                replaceOrderedEdits(Collections.singleton(conflicting), edit);
                removedEditStrings.put(edit, deletedText);
                condensed.add(edit);
            }
            return;
        }
        
        // TODO: with refactorings, the following part is most probably obsolete, as "deleteContained" expression
        // is the same as the expression guarding this method's calling branch in makeTextEdits().
        
        if (off == 0) {
            /*
                |------XXXXXX|
                |------|
                       |-----------YYY|
            */
            int mergeDel = conflicting.getEndOffset() - conflicting.getStartOffset() + (del - confLen);
            assert deletedByConflicting.length() >= mergeDel;
            
            int uniqueNewDel = mergeDel - (conflicting.getEndOffset() - conflicting.getStartOffset());
            assert deletedByNew.length() >= uniqueNewDel;
            String deleteText = deletedByConflicting + deletedByNew.substring(uniqueNewDel);
            
            it.remove();
            TextEdit edit = new TextEdit(conflicting.getStartOffset(), 
                    conflicting.getStartOffset() + mergeDel, newText);
            replaceOrderedEdits(Collections.singleton(conflicting), edit);
            removedEditStrings.put(conflicting, deleteText);
            condensed.add(edit);
            return;
        }
        /*
            |------XXXXXX|
                     |-------YYY|
        */
        it.remove();
        TextEdit edit = new TextEdit(conflicting.getStartOffset(), conflicting.getEndOffset(), 
                confText.substring(0, off));
        replaceOrderedEdits(Collections.singleton(conflicting), edit);
        removedEditStrings.put(edit, deletedByConflicting);
        condensed.add(edit);
        int del2 = del - (confLen - off);
        
        TextEdit edit2 = new TextEdit(adjustedStart, adjustedStart + del2, newText);
        ordered.add(edit2);
        condensed.add(edit2);
        
        String deletedText = deletedByNew.substring(confLen - off);
        if (!deletedText.isEmpty()) {
            removedEditStrings.put(edit2, deletedText);
        }
    }
}

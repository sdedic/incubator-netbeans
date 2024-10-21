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
import java.util.Arrays;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.eclipse.lsp4j.Position;

/**
 *
 * @author sdedic
 */
public class ContentSnapshot {
    private final String text;
    private int[] lineOffsets;
    
    public ContentSnapshot(Document doc) {
        String[] t = new String[1];
        doc.render(() -> {
            try {
                t[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                // should never happen, we're under lock !
            }
        });
        text = t[0];
        computeLineOffsets();
    }

    public ContentSnapshot(String text) {
        this.text = text;
        computeLineOffsets();
    }
    
    private void computeLineOffsets() {
        List<Integer> offs = new ArrayList<>();
        offs.add(0);
        for (int n = 0; n < text.length(); n++) {
            char c = text.charAt(n);
            if (c == '\n') {
                offs.add(n + 1);
            }
        }
        lineOffsets = offs.stream().mapToInt(Integer::intValue).toArray();
    }

    public String getText() {
        return text;
    }
    
    public Position offset2Position(int offset) {
        int index = Arrays.binarySearch(lineOffsets, offset);
        if (index >= 0) {
            return new Position(index, 0);
        }
         // (-(insertion point) - 1)
        index = -(index + 1) - 1;
        assert index >= 0 && index < lineOffsets.length;
        return new Position(index, offset - lineOffsets[index]);
    }

    public int position2Offset(Position pos) {
        int r = pos.getLine();
        if (r >= lineOffsets.length) {
            return text.length();
        }
        return lineOffsets[r] + pos.getCharacter();
    }
}

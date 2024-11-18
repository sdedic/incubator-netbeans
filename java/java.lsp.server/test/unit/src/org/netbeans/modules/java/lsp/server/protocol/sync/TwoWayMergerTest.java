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

import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.editor.BaseDocument;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author sdedic
 */
public class TwoWayMergerTest extends NbTestCase {

    public TwoWayMergerTest(String name) {
        super(name);
    }
    
    private Document clientDocument;
    private Document document;
    private DocumentChangesConverter converter;
    private DocumentChangesConverter clientConverter;
    private ContentSnapshot initialSnapshot;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        document = new BaseDocument(false, "text/plain");
        clientDocument = new BaseDocument(false, "text/plain");
        
        converter = new DocumentChangesConverter(document);
        clientConverter = new DocumentChangesConverter(clientDocument);
    }
    
    private static final String CONTENT = 
                "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Integer tempor. Ut tempus purus at lorem. "
                + "Nulla pulvinar eleifend sem. Proin pede metus, vulputate nec, fermentum fringilla, vehicula vitae, "
                + "justo. Nullam faucibus mi quis velit. Nullam justo enim, consectetuer nec, ullamcorper ac, vestibulum "
                + "in, elit. Curabitur sagittis hendrerit ante. Sed vel lectus. Donec odio tempus molestie, porttitor "
                + "ut, iaculis quis, sem. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur "
                + "ridiculus mus. Aenean vel massa quis mauris vehicula lacinia. Morbi leo mi, nonummy eget tristique non, "
                + "rhoncus non leo. Duis bibendum, lectus ut viverra rhoncus, dolor nunc faucibus libero, eget facilisis "
                + "enim ipsum id lacus. Maecenas aliquet accumsan leo. Cum sociis natoque penatibus et magnis dis parturient "
                + "montes, nascetur ridiculus mus. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per "
                + "inceptos hymenaeos.\n";
    
    void insertInitialText() throws BadLocationException {
        document.removeDocumentListener(converter);
        document.insertString(0, CONTENT, null);
        document.addDocumentListener(converter);
        
        initialSnapshot = new ContentSnapshot(document);
        
        clientDocument.removeDocumentListener(clientConverter);
        clientDocument.insertString(0, CONTENT, null);
        clientDocument.addDocumentListener(clientConverter);
    }
    
    void assertMergeConsistent() throws Exception {
        TwoWayMerger merger = performMerge();
        assertEquals(clientDocument.getText(0, clientDocument.getLength()), merger.getUpdatedSnapshot().getText());
        merger.checkConsistency();
    }
    
    TwoWayMerger performMerge() throws Exception {
        List<TextEdit> local = converter.makeTextEdits();
        List<TextEdit> client = clientConverter.makeTextEdits();
        
        TwoWayMerger merger = new TwoWayMerger(initialSnapshot, new ContentSnapshot(document), local, converter.getRemovedParts());
        merger.addClientEdits(client);
        merger.merge();
        return merger;
    }
    
    public void testTwoSimpleChanges() throws Exception {
        insertInitialText();
        int i = CONTENT.indexOf("Nullam justo");
        document.insertString(i, "Blah blah bleh. ", null);
        clientDocument.insertString(0, "Changed. ", null);
        assertMergeConsistent();
    }
    
    public void testLocalChangeSurroundedByClient() throws Exception {
        insertInitialText();
        int i = CONTENT.indexOf("Nullam justo");
        document.insertString(i, "Blah blah bleh. ", null);
        clientDocument.insertString(0, "Changed. ", null);
        clientDocument.insertString(clientDocument.getLength(), "Changed, too", null);
        assertMergeConsistent();
    }
    
    public void testInterleavedChanges() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        int c1 = CONTENT.indexOf("Curabitur sagittis");
        int l2 = CONTENT.indexOf("Aenean vel");
        int c2 = CONTENT.indexOf("Duis bibendum");
        int l3 = CONTENT.indexOf("Maecenas alique");
        document.insertString(l1, "Blah blah bleh. ", null);
        document.insertString(l2, "Blah blah bleh. ", null);
        document.insertString(l3, "Blah blah bleh. ", null);
        clientDocument.insertString(0, "Changed. ", null);
        clientDocument.insertString(c1, "Client change. ", null);
        clientDocument.insertString(c2, "Client change 2. ", null);
        clientDocument.insertString(clientDocument.getLength(), "Changed, too", null);
        assertMergeConsistent();
    }
    
    public void testInsertJustBeforeThrows() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        document.insertString(l1, "Blah blah bleh. ", null);
        clientDocument.insertString(l1, "Client change. ", null);
        
        try {
            performMerge();
            fail("Expected failure");
        } catch (BadLocationException ex) {
        }
    }
    
    public void testDeleteToStartThrows() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        document.insertString(l1, "Blah blah bleh. ", null);
        clientDocument.remove(l1 -1 , 1);
        
        try {
            performMerge();
            fail("Expected failure");
        } catch (BadLocationException ex) {
        }
    }
    
    /**
     * The local edit deletes characters around client's insertion.
     * @throws Exception 
     */
    public void testClientInsertIntoDeletedThrows() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        document.remove(l1, 6);
        clientDocument.insertString(l1 + 1, "Blah blah bleh. ", null);
        
        try {
            performMerge();
            fail("Expected failure");
        } catch (BadLocationException ex) {
        }
    }
    
    public void testClientDeletesAroundInsertThrows() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        document.insertString(l1 + 1, "Blah blah bleh. ", null);
        clientDocument.remove(l1, 6);
        try {
            performMerge();
            fail("Expected failure");
        } catch (BadLocationException ex) {
        }
    }
    
    public void testInsertBeforeSeparated() throws Exception {
        insertInitialText();
        int l1 = CONTENT.indexOf("Nullam justo");
        document.insertString(l1, "Blah blah bleh. ", null);
        clientDocument.insertString(l1 - 1, "Client change. ", null);
        
        performMerge();
    }
}

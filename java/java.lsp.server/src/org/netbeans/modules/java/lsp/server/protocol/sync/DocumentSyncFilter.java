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
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.modules.java.lsp.server.LspServerState;

/**
 * Private API between TextDocumentServiceImpl and this sync package.
 * 
 * @author sdedic
 */
public interface DocumentSyncFilter {
    /**
     * Modification action done on behalf of the client. Modifications done by {@link #run} implementation
     * are considered client-side and will not be reflected to the client.
     */
    public interface ClientDocumentAction {
        /**
         * Should perform document changes which will not be tracked as server-local changes.
         * @throws BadLocationException 
         */
        public void run() throws BadLocationException;
    }
    
    /**
     * Runs a code that applies client-originated document changes.
     * @param action text-changing action
     */
    public void runClientDocumentChange(ClientDocumentAction action) throws BadLocationException;
    
    /**
     * Checks if the document has local changes pending. The document may have been modified inside the 
     * NBLS, the changes didn't reach the client yet.
     * @param client The client
     * @param uri document URI
     * @param doc the document instance
     * @return true, if the document has changes pending
     */
    public boolean checkLocalChangesPending(LspServerState state, String uri, Document doc);
    
    /**
     * Notifies that the client has opened a document. 
     * @param server the LSP server
     * @param uri the document URI
     * @param content the content of the document, reported by the client
     * @return true, if the document was not opened at the server side before.
     */
    public boolean notifyDidOpenDocument(LspServerState server, String uri, String content);
    /**
     * 
     * @param server the LspServer state.
     * @param uri document URI
     * @param doc document instance
     * @param edits the edits reported by the client
     * @return the events to apply
     */
    public List<TextDocumentContentChangeEvent> adjustDocumentChanges(LspServerState server, String uri, Document doc, List<TextDocumentContentChangeEvent> edits);
}

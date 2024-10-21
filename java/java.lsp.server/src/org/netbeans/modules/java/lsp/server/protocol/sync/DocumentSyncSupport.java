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

import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.netbeans.modules.java.lsp.server.LspServerState;
import org.netbeans.modules.java.lsp.server.LspSessionService;
import org.netbeans.modules.java.lsp.server.Utils;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.CloneableEditorSupportRedirector;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.openide.windows.CloneableOpenSupport;

/**
 *
 * @author sdedic
 */
@ServiceProviders({
    @ServiceProvider(service = CloneableEditorSupportRedirector.class),
    @ServiceProvider(service = DocumentSyncFilter.class),
    @ServiceProvider(service = LspSessionService.Factory.class)
})
public class DocumentSyncSupport extends CloneableEditorSupportRedirector  implements DocumentSyncFilter, LspSessionService.Factory {
    static final Logger LOG = Logger.getLogger(DocumentSyncSupport.class.getName());
           
    private Map<FileObject, EditorLspSupport>    redirectors = new HashMap<>();
    
    private final ServerDocumentRegistry docRegistry = new ServerDocumentRegistry();

    @Override
    public LspSessionService create(LspServerState state) {
        return new LspClientBridge(docRegistry, state);
    }
    
    @Override
    protected CloneableEditorSupport redirect(Lookup env) {
        FileObject fo = env.lookup(FileObject.class);
        if (fo == null) {
            return null;
        }
        EditorLspSupport redir = redirectors.get(fo);
        if (redir != null) {
            if (redir.nestedRedirect.get() == Boolean.TRUE) {
                return null;
            } else {
                return redir;
            }
        }
        CloneableEditorSupport original = (CloneableEditorSupport)fo.getLookup().lookup(org.openide.cookies.EditorCookie.class);
        redir = new EditorLspSupport(original, fo, docRegistry);
        redirectors.put(fo, redir);
        return redir;
    }

    @Override
    public boolean checkLocalChangesPending(LspServerState server, String uri, Document doc) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return false;
            }
            EditorLspSupport redir = redirectors.get(fo);
            if (redir != null && redir.getDocument() == doc) {
                return redir.isPendingChanges(server);
            }
            return false;
        } catch (MalformedURLException ex) {
            return false;
        }
    }
    
    @Override
    public boolean notifyDidOpenDocument(LspServerState server, String uri, String content) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return true;
            }
            EditorLspSupport redir = redirectors.get(fo);
            if (redir == null) {
                return true;
            }
            return !redir.isOpenDocumentPending(server, uri, content);
        } catch (MalformedURLException ex) {
            return true;
        }
    }
    
    @Override
    public List<TextDocumentContentChangeEvent> adjustDocumentChanges(LspServerState server, String uri, Document doc, List<TextDocumentContentChangeEvent> edits) {
        try {
            FileObject fo = Utils.fromUri(uri);
            if (fo == null) {
                return edits;
            }
            EditorLspSupport redir = redirectors.get(fo);
            if (redir != null && redir.getDocument() == doc) {
                return redir.adjustDocumentChanges(server, uri, edits);
            }
            return edits;
        } catch (MalformedURLException ex) {
            return edits;
        }
    }
    
    static ThreadLocal<LspServerState> currentClient = new ThreadLocal<>();
    
    /**
     * Executes a text change originated at the client. All document manipulations made
     * during Runnable execution will NOT be treated as NBLS-originated
     * @param ch 
     */
    @Override
    public void runClientDocumentChange(ClientDocumentAction ch) throws BadLocationException {
        LspServerState old = currentClient.get();
        try {
            LspServerState now = Lookup.getDefault().lookup(LspServerState.class);
            currentClient.set(now);
            ch.run();
        } finally {
            if (old == null) {
                currentClient.remove();
            } else {
                currentClient.set(old);
            }
        }
    }
    
    public static class RedirEnv implements CloneableEditorSupport.Env {
        private final FileObject file;
        private final CloneableEditorSupport original;

        public RedirEnv(FileObject file, CloneableEditorSupport original) {
            this.file = file;
            this.original = original;
        }
        
        @Override
        public InputStream inputStream() throws IOException {
            return file.getInputStream();
        }

        @Override
        public OutputStream outputStream() throws IOException {
            throw new IOException("");
        }

        @Override
        public Date getTime() {
            return file.lastModified();
        }

        @Override
        public String getMimeType() {
            return file.getMIMEType();
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
        }

        @Override
        public void addVetoableChangeListener(VetoableChangeListener l) {
        }

        @Override
        public void removeVetoableChangeListener(VetoableChangeListener l) {
        }

        @Override
        public boolean isValid() {
            try {
                return DataObject.find(file).isValid();
            } catch (IOException ex) {
                return false;
            }
        }

        @Override
        public boolean isModified() {
            return original.isModified();
        }

        @Override
        public void markModified() throws IOException {
        }

        @Override
        public void unmarkModified() {
        }

        @Override
        public CloneableOpenSupport findCloneableOpenSupport() {
            return original;
        }
    }
    
}

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

import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.java.lsp.server.Utils;
import org.netbeans.spi.lsp.CommandProvider;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.util.Union2;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author sdedic
 */
@ServiceProvider(service = CommandProvider.class)
public class TestCommand implements CommandProvider {

    @Override
    public Set<String> getCommands() {
        return Collections.singleton("nbls.test.workspace.edit");
    }

    @Override
    public CompletableFuture<Object> runCommand(String command, List<Object> arguments) {
        CompletableFuture<Object> cf = new CompletableFuture<>();
        FileObject file;
        try {
            String uri = ((JsonPrimitive) arguments.get(0)).getAsString();
            file = Utils.fromUri(uri);
            EditorCookie cake = file.getLookup().lookup(EditorCookie.class);
            StyledDocument docu = cake.openDocument();
            DocumentChangesConverter2 converter = new DocumentChangesConverter2(docu);
            docu.addDocumentListener(converter);
            AbstractDocument abs = (AbstractDocument)docu;
            abs.replace(10, 5, "newText1", null);
            abs.replace(20, 5, "secondText2", null);
            docu.removeDocumentListener(converter);
            List<TextEdit> edits = converter.makeTextEdits();
            WorkspaceEdit.applyEdits(Collections.singletonList(
                new WorkspaceEdit(Collections.singletonList(
                    Union2.createFirst(
                            new TextDocumentEdit(uri, edits)
                    )
                ))), true
            );
        } catch (IOException | BadLocationException ex) {
            cf.completeExceptionally(ex);
        }
        return cf;
    }
    
}

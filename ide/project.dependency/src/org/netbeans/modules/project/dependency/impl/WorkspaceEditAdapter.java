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
package org.netbeans.modules.project.dependency.impl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Set;
import java.util.stream.Collectors;
import org.netbeans.api.lsp.ResourceOperation;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.NbBundle;
import org.openide.util.Union2;

/**
 * Wraps a LSP modification result (WorkspaceEdit) in ProjectModificationResultImpl into a refactoring API's ModificationResult.
 * @author sdedic
 */
public final class WorkspaceEditAdapter implements ModificationResult {
    
    private final ProjectModificationResultImpl impl;

    public WorkspaceEditAdapter(ProjectModificationResultImpl impl) {
        this.impl = impl;
    }
    
    public Collection<FileObject> getFilesToSave() {
        List<FileObject> processed = new ArrayList<>();
        for (FileObject f : impl.getFilesToSave()) {
            if (f.isVirtual()) {
                FileObject changed = URLMapper.findFileObject(f.toURL());
                if (changed == null) {
                    continue;
                }
                f = changed;
            }
            if (f.isValid()) {
                processed.add(f);
            }
        }
        return processed;
    }

    @Override
    public String getResultingSource(FileObject file) throws IOException, IllegalArgumentException {
        TextDocumentEdit e = impl.getFileEdit(file);
        if (e == null) {
            throw new IllegalArgumentException();
        }
        TextDocumentEditProcessor proc = new TextDocumentEditProcessor(e).setForkDocument(true).execute();
        return proc.getText();
    }

    @Override
    public Collection<FileObject> getModifiedFileObjects() {
        return impl.fileModifications.keySet().stream().map(u -> {
            try {
                return URLMapper.findFileObject(u.toURL());
            } catch (MalformedURLException ex) {
                // ignore ?
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public Collection<File> getNewFiles() {
        return impl.createFiles.keySet().stream().map(File::new).collect(Collectors.toList());
    }

    @NbBundle.Messages({
        "# {0} - filename",
        "ERR_CreatedFileAlreadyExists=The file that should be created already exists: {0}"
    })
    @Override
    public void commit() throws IOException {
        // PENDING: the implementation could attach to undoable edits for each of the documents,
        // trying to revert if something goes wrong in the middle.
        
        WorkspaceEdit edit = impl.getWorkspaceEdit();
        
        for (Union2<TextDocumentEdit, ResourceOperation> ch : edit.getDocumentChanges()) {
            if (ch.hasSecond()) {
                ResourceOperation op = ch.second();
                if (op instanceof ResourceOperation.CreateFile) {
                    ResourceOperation.CreateFile cf = (ResourceOperation.CreateFile)op;
                    URL u = URI.create(cf.getNewFile()).toURL();
                    FileObject f = URLMapper.findFileObject(u);
                    if (f != null && f.isValid()) {
                        throw new IOException(Bundle.ERR_CreatedFileAlreadyExists(f.getPath()));
                    }
                    FileObject parent = f.getParent();
                    while (parent != null && parent.isVirtual()) {
                        parent = parent.getParent();
                    }
                    String relative = FileUtil.getRelativePath(parent, f);
                    // PENDING: how CreateFile denotes creation of a folder (alone) ??
                    FileUtil.createData(f, relative);
                    continue;
                }
                
                throw new IllegalStateException("Unknown resource operation");
            } else if (ch.hasFirst()) {
                TextDocumentEdit e = ch.first();
                TextDocumentEditProcessor proc = new TextDocumentEditProcessor(e).setSaveAfterEdit(true);
                proc.execute();
            }
        }
    }
}

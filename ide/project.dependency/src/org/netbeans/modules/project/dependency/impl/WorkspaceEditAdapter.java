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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.NbBundle;

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
    public Collection<? extends FileObject> getModifiedFileObjects() {
        Set<FileObject> fos = new LinkedHashSet<>();
        fos.addAll(impl.createFiles.keySet());
        fos.addAll(impl.fileModifications.keySet());
        return fos;
    }

    @Override
    public Collection<? extends File> getNewFiles() {
        return impl.createFiles.keySet().stream().sequential().map(f -> FileUtil.toFile(f)).collect(Collectors.toList());
    }

    @NbBundle.Messages({
        "# {0} - filename",
        "ERR_CreatedFileAlreadyExists=The file that should be created already exists: {0}"
    })
    @Override
    public void commit() throws IOException {
        WorkspaceEdit edit = impl.getWorkspaceEdit();
        WorkspaceEdit.applyEdits(Collections.singletonList(edit), true);
    }
}

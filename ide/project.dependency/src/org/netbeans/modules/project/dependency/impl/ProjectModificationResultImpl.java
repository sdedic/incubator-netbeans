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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.netbeans.api.lsp.ResourceOperation;
import org.netbeans.api.lsp.TextDocumentEdit;
import org.netbeans.api.lsp.TextEdit;
import org.netbeans.api.lsp.WorkspaceEdit;
import org.netbeans.api.project.Project;
import org.netbeans.modules.project.dependency.ProjectOperationException;
import org.netbeans.modules.project.dependency.spi.ProjectDependencyModifier;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.NbBundle;
import org.openide.util.Union2;

/**
 *
 * @author sdedic
 */
public class ProjectModificationResultImpl {
    private final Project project;
    
    private Set<FileObject> toSave = new LinkedHashSet<>();
    private List<ModificationResult>    customModifications = new ArrayList<>();
    private List<Union2<TextDocumentEdit, ResourceOperation>> edits;
    private ModificationResult combinedResult;
    Map<URI, ResourceOperation.CreateFile> createFiles = new HashMap<>();
    Map<URI, TextDocumentEdit> fileModifications = new LinkedHashMap<>();

    public ProjectModificationResultImpl(Project project) {
        this.project = project;
    }
    
    public TextDocumentEdit getFileEdit(FileObject file) {
        return fileModifications.get(file);
    }
    
    List<Union2<TextDocumentEdit, ResourceOperation>> edits() {
        if (edits != null) {
            return edits;
        }
        List<Union2<TextDocumentEdit, ResourceOperation>> r = new ArrayList<>();
        
        for (URI u : fileModifications.keySet()) {
            URL u2;
            
            try {
                u2 = u.toURL();
            } catch (MalformedURLException ex) {
                continue;
            }
            FileObject mapped = URLMapper.findFileObject(u2);
            if (mapped == null) {
                r.add(Union2.createSecond(createFiles.get(u)));
            } else {
                TextDocumentEdit te = fileModifications.get(u);
                r.add(Union2.createFirst(te));
            }
        }
        edits = r;
        return r;
    }
    
    public WorkspaceEdit getWorkspaceEdit() {
        return new WorkspaceEdit(edits());
    }
    
    public WorkspaceEdit getWorkspaceEdit(URI file) {
        List<Union2<TextDocumentEdit, ResourceOperation>> r = new ArrayList<>();
        
        ResourceOperation.CreateFile cf = createFiles.get(file);
        TextDocumentEdit te = fileModifications.get(file);
        if (cf != null) {
            r.add(Union2.createSecond(cf));
        }
        if (te != null) {
            r.add(Union2.createFirst(te));
        }
        return r.isEmpty() ? null : new WorkspaceEdit(r);
    }
    
    public void add(ProjectDependencyModifier.Result r) {
        if (r.getWorkspaceEdit() == null) {
            return;
        }
        Collection<FileObject> save = r.requiresSave();
        boolean saveAll = save == ProjectDependencyModifier.Result.SAVE_ALL;
        if (save != null && !saveAll) {
            toSave.addAll(save);
        }
        for (Union2<TextDocumentEdit, ResourceOperation> op : r.getWorkspaceEdit().getDocumentChanges()) {
            if (op.hasSecond()) {
                addResourceOperation(op.second());
            } else if (op.hasFirst()) {
                addTextOperation(op.first(), saveAll);
            }
        }
    }
    
    @NbBundle.Messages({
        "# {0} - file specification",
        "ERR_UnsupportedFileSpec=Unsupported file specification: {0}",
        "# {0} - file specification",
        "ERR_WritingToMissingFile=Writing to a file that has not been created: {0}",
        "# {0} - file with overlapping edits",
        "ERR_EditsOverlap=Inconsistent edits for file {0}"
    })
    private void addResourceOperation(ResourceOperation op) {
        if (op instanceof ResourceOperation.CreateFile) {
            ResourceOperation.CreateFile cf = (ResourceOperation.CreateFile)op;
            URI u;
            try {
                u = URI.create(cf.getNewFile());
            } catch (IllegalArgumentException ex) {
                throw new ProjectOperationException(project, ProjectOperationException.State.ERROR, 
                        Bundle.ERR_UnsupportedFileSpec(cf.getNewFile()), Collections.emptySet());
            }
            if (createFiles.containsKey(u)) {
                // file re-created, clear out all changes
                fileModifications.remove(u);
            } else {
                createFiles.put(u, cf);
            }
            return;
        }
        throw new IllegalStateException("Unknown resource operation");
    }
    
    static Comparator<TextEdit> textEditComparator(List<TextEdit> edits) {
        return new Comparator<TextEdit>() {
            @Override
            public int compare(TextEdit o1, TextEdit o2) {
                int p1 = o1.getStartOffset();
                int p2 = o1.getStartOffset();
                int diff = p1 - p2;
                if (diff != 0) {
                    return diff;
                }
                return edits.indexOf(o1) - edits.indexOf(o2);
            }
        };
    }
    
    public Collection<FileObject> getFilesToSave() {
        return toSave;
    }
    
    private void addTextOperation(TextDocumentEdit edit, boolean saveAll) {
        URI u;
        FileObject fo;
        try {
            u = URI.create(edit.getDocument());
            fo = URLMapper.findFileObject(u.toURL());
        } catch (IllegalArgumentException | MalformedURLException ex) {
            throw new ProjectOperationException(project, ProjectOperationException.State.ERROR, 
                    Bundle.ERR_UnsupportedFileSpec(edit.getDocument()), Collections.emptySet());
        }
         
        if (fo == null) {
            // check that creation preceded the edit
            if (!createFiles.containsKey(u)) {
                throw new ProjectOperationException(project, ProjectOperationException.State.ERROR, 
                        Bundle.ERR_WritingToMissingFile(edit.getDocument()), Collections.emptySet());
            }
        }
        if (saveAll) {
            toSave.add(fo);
        }
        TextDocumentEdit tde = fileModifications.get(fo);
        List<TextEdit> newEdits = new ArrayList<>(edit.getEdits());
        newEdits.sort(textEditComparator(edit.getEdits()));
        if (tde != null) {
            List<TextEdit> existing = tde.getEdits();
            int pos = 0;
            for (TextEdit e : newEdits) {
                if (pos >= existing.size()) {
                    existing.add(e);
                    pos++;
                    continue;
                }
                while (pos < existing.size()) {
                    TextEdit c = existing.get(pos);
                    if (c.getStartOffset() <= e.getStartOffset()) {
                        if (c.getEndOffset() > e.getStartOffset()) {
                            throw new ProjectOperationException(project, ProjectOperationException.State.ERROR, 
                                    Bundle.ERR_EditsOverlap(edit.getDocument()), Collections.emptySet());
                        }
                        pos++;
                    } else {
                        break;
                    }
                }
                existing.add(pos, e);
                if (pos == existing.size() - 1) {
                    pos++;
                }
            } 
        } else {
            fileModifications.put(u, new TextDocumentEdit(URLMapper.findURL(fo, URLMapper.EXTERNAL).toString(), newEdits));
        }
    }
    
    public boolean hasCustomEdits() {
        return !customModifications.isEmpty();
    }

    public List<Union2<TextDocumentEdit, ResourceOperation>> getEdits() {
        return edits();
    }

    public List<ModificationResult> getCustomModifications() {
        return customModifications;
    }

    public ModificationResult getCustomEdit() {
        if (customModifications.isEmpty()) {
            return null;
        }
        if (combinedResult != null) {
            return combinedResult;
        }
        return combinedResult = new CompoundModificationResult(customModifications);
    }
}

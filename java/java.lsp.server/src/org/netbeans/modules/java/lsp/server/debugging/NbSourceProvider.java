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
package org.netbeans.modules.java.lsp.server.debugging;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.sun.source.util.TreePath;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author martin
 */
public final class NbSourceProvider implements ISourceLookUpProvider {

    private static final Logger LOG = Logger.getLogger(NbSourceProvider.class.getName());
    
    ClassPath sources = ClassPath.EMPTY;

    public NbSourceProvider() {
    }

    @Override
    public boolean supportsRealtimeBreakpointVerification() {
        return false;
    }

    public void setSourcePath(ClassPath sources) {
        this.sources = sources;
    }

    @Override
    public String[] getFullyQualifiedName(String uri, int[] lines, int[] dummy) throws DebugException {
        List<String> result = new ArrayList<>();
        try {
            FileObject file = URLMapper.findFileObject(new URL(uri));
            if (file != null) {
                JavaSource javaSource = JavaSource.forFileObject(file);
                if (javaSource != null) {
                    javaSource.runUserActionTask(new Task<CompilationController>() {
                        @Override
                        public void run(CompilationController parameter) throws Exception {
                            parameter.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED); //XXX
                            for (int line : lines) {
                                int offsets = (int) parameter.getCompilationUnit().getLineMap().getStartPosition(line);
                                TreePath path = parameter.getTreeUtilities().pathFor(offsets);
                                while (path != null) {
                                    if (TreeUtilities.CLASS_TREE_KINDS.contains(path.getLeaf().getKind())) {
                                        result.add(parameter.getElements().getBinaryName((TypeElement) parameter.getTrees().getElement(path)).toString());
                                        break;
                                    }
                                    path = path.getParentPath();
                                }
                            }
                        }
                    }, true);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new DebugException(ex);
        }
        LOG.info("result=" + result);
        return result.toArray(new String[0]);
    }

    @Override
    public String getSourceFileURI(String fqn, String fileName) {
        FileObject source = sources.findResource(fileName);
        if (source != null) {
            return source.toURI().toString();
        }
        if (new File(fileName).exists()) {
            return fileName;
        }
        return null;
    }

    @Override
    public String getSourceContents(String arg0) {
        LOG.log(Level.INFO, "arg0={0}", arg0);
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}

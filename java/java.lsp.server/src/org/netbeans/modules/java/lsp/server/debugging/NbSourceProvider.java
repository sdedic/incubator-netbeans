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

import com.sun.source.util.TreePath;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.TypeElement;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.modules.java.lsp.server.debugging.protocol.Types;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
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
    
    /**
     * Find the source mapping for the specified source file name.
     */
    public static Types.Source convertDebuggerSourceToClient(String fullyQualifiedName, String sourceName, String relativeSourcePath,
            IDebugAdapterContext context) throws URISyntaxException {
        // use a lru cache for better performance
        String uri = context.getSourceLookupCache().computeIfAbsent(fullyQualifiedName, key -> {
            String fromProvider = context.getProvider(ISourceLookUpProvider.class).getSourceFileURI(key, relativeSourcePath);
            // avoid return null which will cause the compute function executed again
            return StringUtils.isBlank(fromProvider) ? "" : fromProvider;
        });

        if (!StringUtils.isBlank(uri)) {
            // The Source.path could be a file system path or uri string.
            if (uri.startsWith("file:")) {
                String clientPath = AdapterUtils.convertPath(uri, context.isDebuggerPathsAreUri(), context.isClientPathsAreUri());
                return new Types.Source(sourceName, clientPath, 0);
            } else {
                // If the debugger returns uri in the Source.path for the StackTrace response, VSCode client will try to find a TextDocumentContentProvider
                // to render the contents.
                // Language Support for Java by Red Hat extension has already registered a jdt TextDocumentContentProvider to parse the jdt-based uri.
                // The jdt uri looks like 'jdt://contents/rt.jar/java.io/PrintStream.class?=1.helloworld/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/
                // lib%5C/rt.jar%3Cjava.io(PrintStream.class'.
                return new Types.Source(sourceName, uri, 0);
            }
        } else {
            // If the source lookup engine cannot find the source file, then lookup it in the source directories specified by user.
            String absoluteSourcepath = AdapterUtils.sourceLookup(context.getSourcePaths(), relativeSourcePath);
            if (absoluteSourcepath != null) {
                return new Types.Source(sourceName, absoluteSourcepath, 0);
            } else {
                return null;
            }
        }
    }

}

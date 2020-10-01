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

import java.io.File;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.debug.Source;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.java.lsp.server.debugging.utils.AdapterUtils;
import org.openide.filesystems.FileObject;

/**
 *
 * @author martin
 */
public final class NbSourceProvider {

    private static final Logger LOG = Logger.getLogger(NbSourceProvider.class.getName());
    
    ClassPath sources = ClassPath.EMPTY;

    public NbSourceProvider() {
    }

    public void setSourcePath(ClassPath sources) {
        this.sources = sources;
    }

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

    public String getSourceContents(String arg0) {
        LOG.log(Level.INFO, "arg0={0}", arg0);
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    /**
     * Find the source mapping for the specified source file name.
     */
    public static Source convertDebuggerSourceToClient(String fullyQualifiedName, String sourceName, String relativeSourcePath,
            DebugAdapterContext context) throws URISyntaxException {
        // use a lru cache for better performance
        String uri = context.getSourceLookupCache().computeIfAbsent(fullyQualifiedName, key -> {
            String fromProvider = context.getSourceProvider().getSourceFileURI(key, relativeSourcePath);
            // avoid return null which will cause the compute function executed again
            return StringUtils.isBlank(fromProvider) ? "" : fromProvider;
        });

        if (!StringUtils.isBlank(uri)) {
            // The Source.path could be a file system path or uri string.
            Source source = new Source();
            source.setName(sourceName);
            source.setSourceReference(0);
            if (uri.startsWith("file:")) {
                String clientPath = AdapterUtils.convertPath(uri, context.isDebuggerPathsAreUri(), context.isClientPathsAreUri());
                source.setPath(clientPath);
            } else {
                // If the debugger returns uri in the Source.path for the StackTrace response, VSCode client will try to find a TextDocumentContentProvider
                // to render the contents.
                // Language Support for Java by Red Hat extension has already registered a jdt TextDocumentContentProvider to parse the jdt-based uri.
                // The jdt uri looks like 'jdt://contents/rt.jar/java.io/PrintStream.class?=1.helloworld/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/
                // lib%5C/rt.jar%3Cjava.io(PrintStream.class'.
                source.setPath(uri);
            }
            return source;
        } else {
            // If the source lookup engine cannot find the source file, then lookup it in the source directories specified by user.
            String absoluteSourcepath = AdapterUtils.sourceLookup(context.getSourcePaths(), relativeSourcePath);
            if (absoluteSourcepath != null) {
                Source source = new Source();
                source.setName(sourceName);
                source.setPath(absoluteSourcepath);
                source.setSourceReference(0);
                return source;
            } else {
                return null;
            }
        }
    }

}
